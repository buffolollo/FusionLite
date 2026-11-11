package com.fusionlite.data

import android.content.Context
import com.fusionlite.api.ApiFactory
import com.fusionlite.api.EnergyBalanceData
import com.fusionlite.api.EnergyFlowData
import com.fusionlite.api.Envelope
import com.fusionlite.api.FusionSolarApi
import com.fusionlite.api.FusionSolarConfig
import com.fusionlite.api.LoginRequest
import com.fusionlite.api.RefreshTokenRequest
import com.fusionlite.api.Session
import com.fusionlite.api.Station
import com.fusionlite.api.StationListArg
import com.fusionlite.api.StationRealKpi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class AuthExpiredException(message: String = "Session expired") : Exception(message)

data class FlowSnapshot(
    val data: EnergyFlowData?,
    val metrics: FlowMetrics?,
    val error: String? = null,
)

data class DashboardSnapshot(
    val stations: List<Station>,
    val total: Int?,
    val selectedDn: String?,
    val flow: FlowSnapshot,
    val diag: String,
)

/**
 * Thin data layer around [FusionSolarApi]. All network work happens on IO and
 * the energy-flow request mirrors the original app: GET with a stationDn
 * query, cascading through v1 -> v2 -> v3 regional endpoints.
 */
class FusionRepository(context: Context) {
    private val appContext = context.applicationContext
    // Remembers the energy-balance transport that worked for this host so a
    // 405-happy gateway is not probed again on every filter change.
    @Volatile
    private var preferredBalanceAttempt: String? = null
    @Volatile
    private var preferredKpiAttempt: String? = null

    suspend fun login(
        username: String,
        password: String,
        loginHost: String,
        loginPort: String,
        appClientId: String,
    ): Session = withContext(Dispatchers.IO) {
        val host = loginHost.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .ifBlank { FusionSolarConfig.DEFAULT_HOST }
        val port = loginPort.trim().ifBlank { FusionSolarConfig.DEFAULT_PORT }
        // `loginHost`/`loginPort` were previously ignored and every login went to
        // the built-in default host. Honour the configured auth host.
        val api = ApiFactory.create(appContext, null, "https://$host:$port")
        val body = api.login(
            LoginRequest(
                userName = username.trim(),
                value = password,
                appClientId = appClientId,
            )
        )
        val data = body.data
        if (data?.accessToken.isNullOrBlank()) {
            throw AuthExpiredException(loginErrorMessage(body.code, body.description, data?.exceptionId))
        }
        // BaseLoginTask switches data traffic to regionFloatIp / regionList.
        val dataSession = Session.parseDataHost(
            data.regionFloatIp ?: data.regionList?.firstOrNull()?.regionIp,
            host,
            port,
        )
        dataSession.copy(
            accessToken = data.accessToken!!,
            roaRand = data.roaRand.orEmpty(),
            refreshToken = data.refreshToken,
        )
    }

    /**
     * Silent renewal without the password, mirroring
     * `SessionOutInterceptor.buildAutoLoginRequestBody`:
     * POST `<data host>/rest/neteco/appauthen/v1/smapp/app/token` with
     * `{grantType:"refreshtoken", orgName:"default", userName, appClientId,
     * refreshToken}`. Returns null when the refresh token is rejected; network
     * and 5xx failures propagate so callers can keep the existing session.
     */
    suspend fun renewWithRefreshToken(
        session: Session,
        username: String,
        appClientId: String,
    ): Session? = withContext(Dispatchers.IO) {
        val refresh = session.refreshToken
        if (refresh.isNullOrBlank()) return@withContext null
        // The shipped app performs this call through its normal header/cookie
        // interceptor (SessionOutInterceptor.getNewHeaderRequest), so keep the
        // current session's headers on the renewal request too.
        val api = ApiFactory.create(appContext, session)
        val body = api.refreshToken(
            RefreshTokenRequest(
                userName = username,
                appClientId = appClientId,
                refreshToken = refresh,
            )
        )
        val data = body.data
        if (data?.accessToken.isNullOrBlank()) return@withContext null
        // The refresh response may carry a new regional float IP; if it does,
        // follow it. Otherwise keep the current data host.
        val region = data.regionFloatIp ?: data.regionList?.firstOrNull()?.regionIp
        val host = if (!region.isNullOrBlank()) {
            Session.parseDataHost(region, session.host, session.port)
        } else {
            session
        }
        host.copy(
            accessToken = data.accessToken!!,
            roaRand = data.roaRand ?: session.roaRand,
            refreshToken = data.refreshToken ?: refresh,
        )
    }

    suspend fun loadDashboard(session: Session, preferredDn: String?): DashboardSnapshot =
        withContext(Dispatchers.IO) {
            val api = ApiFactory.create(appContext, session)

            val listEnv = try {
                api.stationList(StationListArg())
            } catch (e: HttpException) {
                if (isSessionOut(e)) throw AuthExpiredException(sessionOutMessage(e))
                throw e
            } catch (e: IOException) {
                throw IOException("Network error: ${e.localizedMessage ?: "unreachable"}", e)
            }
            val authCode = listEnv.failCode ?: listEnv.code?.toLong()
            if (authCode == 401L || authCode == 403L) throw AuthExpiredException()
            val envelopeMessage = listEnv.failMsg ?: listEnv.description ?: listEnv.message ?: listEnv.msg
            if (looksExpired(envelopeMessage)) throw AuthExpiredException(envelopeMessage.orEmpty())
            val stations = listEnv.data?.list ?: listEnv.data?.stations ?: emptyList()
            val selectedDn = preferredDn?.takeIf { dn -> stations.any { it.dn == dn } }
                ?: stations.firstOrNull()?.dn

            var flow = FlowSnapshot(null, null, null)
            if (selectedDn != null) {
                flow = fetchFlow(api, selectedDn)
            }

            DashboardSnapshot(
                stations = stations,
                total = listEnv.data?.total,
                selectedDn = selectedDn,
                flow = flow,
                diag = "host=${session.host}:${session.port} stations=${stations.size}" +
                    (listEnv.data?.total?.let { " total=$it" } ?: ""),
            )
        }

    private suspend fun fetchFlow(api: FusionSolarApi, dn: String): FlowSnapshot {
        var lastError: String? = null
        val now = System.currentTimeMillis()
        val loaders: List<Pair<String, suspend () -> com.fusionlite.api.Envelope<EnergyFlowData>>> = listOf(
            "v1" to { api.energyFlow(dn, now) },
            "v2" to { api.energyFlowV2(dn, now) },
            "v3" to { api.energyFlowV3(dn, now) },
        )
        for ((version, load) in loaders) {
            try {
                val envelope = load()
                val fail = envelope.failCode ?: envelope.code?.toLong()
                if (fail != null && fail != 0L) {
                    lastError = "energy-flow $version: ${envelope.failMsg ?: "code $fail"}"
                    continue
                }
                val data = envelope.data
                if (data?.flow?.nodes.isNullOrEmpty()) {
                    lastError = "energy-flow $version: empty graph"
                    continue
                }
                return FlowSnapshot(data, FlowMetrics.from(data), null)
            } catch (e: HttpException) {
                if (isSessionOut(e)) throw AuthExpiredException(sessionOutMessage(e))
                // 404/405 mean this regional host does not serve this version;
                // try the next one instead of failing the whole dashboard.
                lastError = "energy-flow $version: HTTP ${e.code()}"
                if (e.code() != 404 && e.code() != 405 && e.code() != 400) break
            } catch (e: Exception) {
                lastError = "energy-flow $version: ${e.localizedMessage ?: e.javaClass.simpleName}"
                break
            }
        }
        return FlowSnapshot(null, null, lastError)
    }

    /**
     * Historical production for the statistics dashboard.
     *
     * Evidence chain (REA/JADX, APK 26.2.6.001):
     *  - `NormalPowerFragment.queryKpiManage` builds
     *    {stationDn, timeDim, dateStr, queryTime, timeZone, timeZoneStr, _}
     *    and calls `ReportPresenter.queryStationEnergyBalance`.
     *  - `PhotovoltaicStatisticsViewModel.fetchEnergyManageTotalData` uses the
     *    same selector (StatDim) against energy-balance v1/v2.
     *  - `ChartModeKpiHeaderViewModel.querySingleRealKpi` reads station-real-kpi.
     * Both responses are `BaseBeanBo<T>` = {failCode, failMsg, data}.
     */
    suspend fun loadStatistics(
        session: Session,
        stationDn: String,
        period: StatisticsPeriod,
        anchorMillis: Long,
    ): StatisticsSnapshot = withContext(Dispatchers.IO) {
        val api = ApiFactory.create(appContext, session)
        val warnings = mutableListOf<String>()

        // The same 405-transport problem affects station-real-kpi on some
        // regional hosts; try the POST form, then the GET form. The request is
        // anchored on the selected window, not "now", so the day/month/year
        // headline follows the ‹ › navigation like the energy-balance chart.
        val kpiRequest = stationRealKpiRequest(stationDn, queryTimeFor(period, anchorMillis))
        val kpiQuery = kpiRequest.asQueryParams()
        var kpi: StationRealKpi? = null
        val kpiFailures = mutableListOf<String>()
        val kpiAttempts: List<Pair<String, suspend () -> Envelope<StationRealKpi>>> = listOf(
            "POST" to { api.stationRealKpi(kpiRequest) },
            "GET" to { api.stationRealKpiGet(kpiQuery) },
        )
        val orderedKpi = kpiAttempts.sortedBy { if (it.first == preferredKpiAttempt) 0 else 1 }
        for ((label, load) in orderedKpi) {
            try {
                val env = load()
                assertNotExpired(env.failCode ?: env.code?.toLong(), env.failMsg)
                val fail = env.failCode ?: env.code?.toLong()
                if (fail != null && fail != 0L) {
                    kpiFailures += "$label: ${env.failMsg ?: "code $fail"}"
                    continue
                }
                kpi = env.data
                preferredKpiAttempt = label
                kpiFailures.clear()
                break
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: HttpException) {
                if (isSessionOut(e)) throw AuthExpiredException(sessionOutMessage(e))
                val allow = e.response()?.headers()?.get("Allow")?.let { " (Allow: $it)" }.orEmpty()
                kpiFailures += "$label: HTTP ${e.code()}$allow"
                if (e.code() != 404 && e.code() != 405 && e.code() != 400) break
            } catch (e: IOException) {
                kpiFailures += "$label: ${e.localizedMessage ?: "network"}"
                break
            }
        }
        if (kpi == null && kpiFailures.isNotEmpty()) {
            warnings += "real-kpi: ${kpiFailures.joinToString("; ")}"
        }

        val body = energyBalanceRequest(stationDn, period, anchorMillis)
        val query = body.asQueryParams()
        var balance: EnergyBalanceData? = null
        val failures = mutableListOf<String>()
        // 405 means the gateway does not accept the POST form for this regional
        // host, so the same fields are retried as a GET query. Keep every
        // failure so the UI can show what v1 actually said instead of only the
        // last version's error.
        val attempts: List<Pair<String, suspend () -> Envelope<EnergyBalanceData>>> = listOf(
            "v1 POST" to { api.energyBalance(body) },
            "v2 POST" to { api.energyBalanceV2(body) },
            "v1 GET" to { api.energyBalanceGet(query) },
            "v2 GET" to { api.energyBalanceV2Get(query) },
        )
        val ordered = attempts.sortedBy { if (it.first == preferredBalanceAttempt) 0 else 1 }
        for ((label, load) in ordered) {
            try {
                val env = load()
                assertNotExpired(env.failCode ?: env.code?.toLong(), env.failMsg)
                val fail = env.failCode ?: env.code?.toLong()
                if (fail != null && fail != 0L) {
                    failures += "$label: ${env.failMsg ?: "code $fail"}"
                    continue
                }
                val data = env.data
                if (data == null || (data.xAxis.isNullOrEmpty() && data.totalProductPower == null)) {
                    failures += "$label: empty series"
                    continue
                }
                balance = data
                preferredBalanceAttempt = label
                break
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: HttpException) {
                if (isSessionOut(e)) throw AuthExpiredException(sessionOutMessage(e))
                val allow = e.response()?.headers()?.get("Allow")?.let { " (Allow: $it)" }.orEmpty()
                failures += "$label: HTTP ${e.code()}$allow"
                if (e.code() != 404 && e.code() != 405 && e.code() != 400) break
            } catch (e: IOException) {
                failures += "$label: ${e.localizedMessage ?: "network"}"
                break
            }
        }

        StatisticsSnapshot(
            stationDn = stationDn,
            period = period,
            anchorMillis = anchorMillis,
            rangeLabel = rangeLabel(period, anchorMillis),
            kpi = kpi,
            balance = balance,
            error = if (balance == null) {
                failures.joinToString("; ").ifBlank { "No statistics returned." }
            } else null,
            warnings = warnings,
        )
    }

    private fun assertNotExpired(failCode: Long?, failMsg: String?) {
        if (failCode == 401L || failCode == 403L) throw AuthExpiredException()
        if (looksExpired(failMsg)) throw AuthExpiredException(failMsg.orEmpty())
    }

    /**
     * The API marks an expired session with a 302 whose Location points at the
     * unified-session login/logout pages (`SessionOutInterceptor.isMove`), or
     * with 401/403. Redirects are deliberately not followed in [ApiFactory].
     */
    private fun isSessionOut(e: HttpException): Boolean {
        val code = e.code()
        if (code == 401 || code == 403) return true
        if (code in 300..399) {
            val location = e.response()?.headers()?.get("Location").orEmpty()
            return location.contains("/unisess/v1/logout") || location.contains("/unisess/v1/auth")
        }
        return false
    }

    private fun sessionOutMessage(e: HttpException): String =
        if (e.code() in 300..399) "Session expired (redirect to login)" else "Session expired"

    private fun looksExpired(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val text = message.lowercase()
        val stale = text.contains("expire") || text.contains("invalid") ||
            text.contains("timeout") || text.contains("timed out") ||
            text.contains("not login") || text.contains("not logged") ||
            text.contains("unauthorized")
        val aboutSession = text.contains("session") || text.contains("token") || text.contains("login")
        return stale && aboutSession
    }

    private fun loginErrorMessage(code: Int?, description: String?, exceptionId: String?): String {
        val detail = listOfNotNull(exceptionId, description).firstOrNull { !it.isNullOrBlank() }
        return detail ?: "Login failed (code=${code ?: "?"})"
    }

    fun defaultHost() = FusionSolarConfig.DEFAULT_HOST
    fun defaultPort() = FusionSolarConfig.DEFAULT_PORT
}
