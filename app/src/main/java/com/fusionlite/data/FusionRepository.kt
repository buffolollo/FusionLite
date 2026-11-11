package com.fusionlite.data

import android.content.Context
import com.fusionlite.api.ApiFactory
import com.fusionlite.api.EnergyBalanceData
import com.fusionlite.api.EnergyFlowData
import com.fusionlite.api.Envelope
import com.fusionlite.api.FusionSolarApi
import com.fusionlite.api.FusionSolarConfig
import com.fusionlite.api.LoginRequest
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

    suspend fun login(
        username: String,
        password: String,
        loginHost: String,
        loginPort: String,
        appClientId: String,
    ): Session = withContext(Dispatchers.IO) {
        val api = ApiFactory.create(appContext, null)
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
            loginHost,
            loginPort,
        )
        dataSession.copy(
            accessToken = data.accessToken!!,
            roaRand = data.roaRand.orEmpty(),
        )
    }

    suspend fun loadDashboard(session: Session, preferredDn: String?): DashboardSnapshot =
        withContext(Dispatchers.IO) {
            val api = ApiFactory.create(appContext, session)

            val listEnv = try {
                api.stationList(StationListArg())
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) throw AuthExpiredException()
                throw e
            } catch (e: IOException) {
                throw IOException("Network error: ${e.localizedMessage ?: "unreachable"}", e)
            }
            val authCode = listEnv.failCode ?: listEnv.code?.toLong()
            if (authCode == 401L || authCode == 403L) throw AuthExpiredException()
            listEnv.failMsg?.let { msg ->
                if (msg.contains("session", ignoreCase = true) &&
                    msg.contains("expire", ignoreCase = true)
                ) throw AuthExpiredException(msg)
            }
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

        var kpi: StationRealKpi? = null
        try {
            val env = api.stationRealKpi(stationRealKpiRequest(stationDn))
            assertNotExpired(env.failCode ?: env.code?.toLong(), env.failMsg)
            if (env.failCode == null || env.failCode == 0L) {
                kpi = env.data
            } else {
                warnings += "real-kpi: ${env.failMsg ?: "code ${env.failCode}"}"
            }
        } catch (e: AuthExpiredException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) throw AuthExpiredException()
            warnings += "real-kpi: HTTP ${e.code()}"
        } catch (e: IOException) {
            warnings += "real-kpi: ${e.localizedMessage ?: "network"}"
        }

        val body = energyBalanceRequest(stationDn, period, anchorMillis)
        var balance: EnergyBalanceData? = null
        var lastError: String? = null
        val loaders: List<Pair<String, suspend () -> Envelope<EnergyBalanceData>>> = listOf(
            "v1" to { api.energyBalance(body) },
            "v2" to { api.energyBalanceV2(body) },
        )
        for ((version, load) in loaders) {
            try {
                val env = load()
                assertNotExpired(env.failCode ?: env.code?.toLong(), env.failMsg)
                val fail = env.failCode ?: env.code?.toLong()
                if (fail != null && fail != 0L) {
                    lastError = "energy-balance $version: ${env.failMsg ?: "code $fail"}"
                    continue
                }
                val data = env.data
                if (data == null || (data.xAxis.isNullOrEmpty() && data.totalProductPower == null)) {
                    lastError = "energy-balance $version: empty series"
                    continue
                }
                balance = data
                break
            } catch (e: AuthExpiredException) {
                throw e
            } catch (e: HttpException) {
                lastError = "energy-balance $version: HTTP ${e.code()}"
                if (e.code() != 404 && e.code() != 405 && e.code() != 400) break
            } catch (e: IOException) {
                lastError = "energy-balance $version: ${e.localizedMessage ?: "network"}"
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
            error = if (balance == null) lastError ?: "No statistics returned." else null,
            warnings = warnings,
        )
    }

    private fun assertNotExpired(failCode: Long?, failMsg: String?) {
        if (failCode == 401L || failCode == 403L) throw AuthExpiredException()
        failMsg?.let { msg ->
            if (msg.contains("session", ignoreCase = true) &&
                msg.contains("expire", ignoreCase = true)
            ) throw AuthExpiredException(msg)
        }
    }

    private fun loginErrorMessage(code: Int?, description: String?, exceptionId: String?): String {
        val detail = listOfNotNull(exceptionId, description).firstOrNull { !it.isNullOrBlank() }
        return detail ?: "Login failed (code=${code ?: "?"})"
    }

    fun defaultHost() = FusionSolarConfig.DEFAULT_HOST
    fun defaultPort() = FusionSolarConfig.DEFAULT_PORT
}
