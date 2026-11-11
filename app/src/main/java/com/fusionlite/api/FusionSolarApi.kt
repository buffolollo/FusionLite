package com.fusionlite.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Endpoint map, headers and payload structures reverse-engineered from the
 * shipped Huawei FusionSolar APK (com.huawei.smartpvms, 26.2.6.001, sha256
 * f3fec57a...). Evidence: REA/JADX static decompilation + live probes.
 *
 *  - Login:     POST /rest/neteco/appauthen/v1/smapp/app/token   (UrlConstant.URL_LOGIN)
 *  - Stations:  POST /rest/pvms/web/station/v1/station/station-list
 *  - Energy flow: POST /rest/pvms/web/station/v1/overview/energy-flow
 *
 * Statistics (see README "statistics" rows; all from UrlConstant + RequestManager):
 *  - Real KPI:      POST /rest/pvms/web/station/v1/overview/station-real-kpi
 *                   (URL_STATION_REAL_KPI, RequestApi.queryOneStationRealKpi)
 *  - Energy series: POST /rest/pvms/web/station/v1/overview/energy-balance
 *                   (URL_STATION_ENERGY_BALANCE, RequestApi.queryStationEnergyBalance)
 *  - Energy v2:     POST /rest/pvms/web/station/v2/overview/energy-balance
 *  - Income chart:  POST /rest/pvms/web/report/v1/station/home-station-kpi-chart
 *                   (URL_STATION_HOME_STATION_KPI_CHART,
 *                    RequestApi.queryStationDetailRealKpiChart)
 *
 * Chart window selector (StationKpiChartArgBean.StatDim, dex <clinit>):
 *   DAY="2" · MONTH="4" · YEAR="5" · ALL="6" (lifetime)
 *
 * IMPORTANT (BaseLoginTask.h / g): the login host only serves auth. After a
 * successful login the app switches all data traffic to `regionFloatIp`
 * (or the matching entry of `regionList[].regionIp`), mirroring
 * IpAddressUtil.setIpPair(baseIp, regionFloatIp).
 */
object FusionSolarConfig {
    const val DEFAULT_HOST = "intl.fusionsolar.huawei.com"
    const val DEFAULT_PORT = "32800"
    const val PATH_LOGIN = "/rest/neteco/appauthen/v1/smapp/app/token"
    const val PATH_STATION_LIST = "/rest/pvms/web/station/v1/station/station-list"
    const val PATH_TOTAL_REAL_KPI = "/rest/pvms/web/station/v1/station/total-real-kpi"
    const val PATH_ENERGY_FLOW = "/rest/pvms/web/station/v1/overview/energy-flow"
    const val PATH_ENERGY_FLOW_V2 = "/rest/pvms/web/station/v2/overview/energy-flow"
    const val PATH_ENERGY_FLOW_V3 = "/rest/pvms/web/station/v3/overview/energy-flow"
    const val PATH_DEVICE_REAL_KPI = "/rest/pvms/web/device/v1/device-real-kpi"
    // Statistics / historical production (REA evidence: UrlConstant + RequestManager)
    const val PATH_STATION_REAL_KPI = "/rest/pvms/web/station/v1/overview/station-real-kpi"
    const val PATH_ENERGY_BALANCE = "/rest/pvms/web/station/v1/overview/energy-balance"
    const val PATH_ENERGY_BALANCE_V2 = "/rest/pvms/web/station/v2/overview/energy-balance"
    const val PATH_KPI_CHART = "/rest/pvms/web/report/v1/station/home-station-kpi-chart"
}

// ---------------------------------------------------------------------------
// Login payload (LoginConditionArg) and response (LoginBo)
// ---------------------------------------------------------------------------
@JsonClass(generateAdapter = true)
data class LoginRequest(
    val userName: String,
    val value: String,                      // password (grantType=password) or verify code
    val grantType: String = "password",
    val appClientId: String = "",
    val isNeedTwoVerify: Boolean = false,
    val verifyCode: String = "",
    val twoFactorAuthenticationCode: String? = null,
    val refreshToken: String? = null,
    val isRenew: Boolean = false,
    val orgName: String? = null,
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    // Observed live: {"description":"it is failed","code":-1,"data":{...}}
    val code: Int = 0,
    val description: String? = null,
    val data: LoginData? = null,
)

@JsonClass(generateAdapter = true)
data class LoginData(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val roaRand: String? = null,
    val expires: String? = null,
    val exceptionId: String? = null,
    val exceptionType: String? = null,
    val exceptionArgs: Map<String, List<String>?>? = null,
    // Post-login data host: regionFloatIp or regionList[].regionIp
    // ("--" or ":" separated host--port in some formats; see Session.parse)
    val regionFloatIp: String? = null,
    val regionList: List<RegionEntry>? = null,
)

@JsonClass(generateAdapter = true)
data class RegionEntry(
    val regionName: String? = null,
    val regionIp: String? = null,
)

// ---------------------------------------------------------------------------
// Station list / energy flow envelopes (BaseBeanBo / BaseEntityBo / PageBaseEntity)
// ---------------------------------------------------------------------------
@JsonClass(generateAdapter = true)
data class Envelope<T>(
    val failCode: Long? = null,
    val failMsg: String? = null,
    val code: Int? = null,
    val description: String? = null,
    val message: String? = null,
    val msg: String? = null,
    val data: T? = null,
)

@JsonClass(generateAdapter = true)
data class StationListArg(
    val curPage: Int = 1,
    val pageSize: Int = 100,
)

@JsonClass(generateAdapter = true)
data class PageData(
    val list: List<Station>? = null,
    val stations: List<Station>? = null,
    val total: Int? = null,
)

@JsonClass(generateAdapter = true)
data class Station(
    val dn: String? = null,
    val name: String? = null,
    val status: String? = null,
    val currentPower: String? = null,       // real-time PV production
    val dailyEnergy: String? = null,        // today's energy
    val cumulativeEnergy: String? = null,   // lifetime energy
    val batterySoc: String? = null,         // battery state of charge (SOC)
    val installedCapacity: String? = null,
    val plantStatus: String? = null,
)

// ---------------------------------------------------------------------------
// Energy flow: BaseBeanBo<StationEnergyFlowBo>
// ---------------------------------------------------------------------------
@JsonClass(generateAdapter = true)
data class EnergyFlowData(
    val flow: Flow? = null,
)

@JsonClass(generateAdapter = true)
data class Flow(
    val nodes: List<FlowNode>? = null,
    val links: List<FlowLink>? = null,
)

@JsonClass(generateAdapter = true)
data class FlowNode(
    val id: String? = null,
    val name: String? = null,
    val description: DescriptionItem? = null,
    // Some hosts put the live value directly on the node instead of description.
    val value: String? = null,
    val unit: String? = null,
    // Battery nodes carry SOC / BATTERY_POWER / ACTIVE_POWER here.
    val deviceTips: Map<String, Any?>? = null,
)

@JsonClass(generateAdapter = true)
data class DescriptionItem(
    val label: String? = null,
    val value: String? = null,
    val unit: String? = null,
)

@JsonClass(generateAdapter = true)
data class FlowLink(
    val fromNode: String? = null,
    val toNode: String? = null,
    val description: DescriptionItem? = null,
    val flowing: String? = null,
)

// Total KPI (production summary) response
@JsonClass(generateAdapter = true)
data class TotalKpiData(
    val currentPower: String? = null,
    val dailyEnergy: String? = null,
    val cumulativeEnergy: String? = null,
    val dailyIncome: String? = null,
)

// ---------------------------------------------------------------------------
// Statistics: station-real-kpi → BaseBeanBo<StationRealKpiBean>
// Fields observed in com.digitalpower.app.plat.chargeone.bean.StationRealKpiBean
// ---------------------------------------------------------------------------
@JsonClass(generateAdapter = true)
data class StationRealKpi(
    val dailyEnergy: Any? = null,
    val monthEnergy: Any? = null,
    val yearEnergy: Any? = null,
    val cumulativeEnergy: Any? = null,
    val dailyIncome: Any? = null,
    val currency: String? = null,
    val isPriceConfigured: Boolean? = null,
    val existMeter: Boolean? = null,
)

/**
 * Request body for station-real-kpi. Built from
 * `ChartModeKpiHeaderViewModel.querySingleRealKpi`:
 * {stationDn, queryTime, timeZone}.
 */
@JsonClass(generateAdapter = true)
data class StationRealKpiRequest(
    val stationDn: String,
    val queryTime: Long,
    val timeZone: String,
)

/**
 * Request body for energy-balance, built from
 * `NormalPowerFragment.queryKpiManage` / `qv2.k`:
 * {stationDn, timeDim, dateStr, queryTime, timeZone, timeZoneStr, _}.
 *
 * A typed body avoids Retrofit's "must not include a wildcard" rejection that
 * Kotlin's covariant Map<String, Any> produces.
 */
@JsonClass(generateAdapter = true)
data class EnergyBalanceRequest(
    val stationDn: String,
    val timeDim: String,
    val dateStr: String,
    val queryTime: Long,
    val timeZone: String,
    val timeZoneStr: String,
    @Json(name = "_") val timestamp: Long,
)

// ---------------------------------------------------------------------------
// Statistics: energy-balance → BaseBeanBo<StationEnergyManageBean>
// Series are parallel to xAxis; the original bean declares them List<String>,
// but some hosts send numbers, so parseNum() is applied at the UI layer.
// ---------------------------------------------------------------------------
@JsonClass(generateAdapter = true)
data class EnergyBalanceData(
    val xAxis: List<Any?>? = null,
    val productPower: List<Any?>? = null,          // PV production per bucket
    val usePower: List<Any?>? = null,              // consumption
    val onGridPower: List<Any?>? = null,           // exported to grid
    val buyPower: List<Any?>? = null,              // imported from grid
    val selfUsePower: List<Any?>? = null,          // self-consumed
    val chargePower: List<Any?>? = null,           // battery charge
    val dischargePower: List<Any?>? = null,        // battery discharge
    val radiationDosePower: List<Any?>? = null,    // irradiation
    val totalProductPower: Any? = null,
    val totalUsePower: Any? = null,
    val totalOnGridPower: Any? = null,
    val totalBuyPower: Any? = null,
    val totalSelfUsePower: Any? = null,
    val selfProvide: Any? = null,
    val selfUsePowerRatioByProduct: Any? = null,
    val onGridPowerRatio: Any? = null,
    val existMeter: Boolean? = null,
)

// StationKpiCharDataBo (income chart) — scaffolded for completeness.
@JsonClass(generateAdapter = true)
data class KpiChartData(
    val totalIncome: Any? = null,
    val curdayIncome: Any? = null,
    val hasMeter: Boolean? = null,
    val data: KpiChartList? = null,
)

// StationKpiChartArgBean (dex fields: statDim, statTime, timeZone,
// timeZoneStr, moList, statType, showNegativeRevenue)
@JsonClass(generateAdapter = true)
data class KpiChartRequest(
    val statDim: String,
    val statTime: Long,
    val timeZone: Double,
    val timeZoneStr: String,
    val moList: List<MoListParam>,
)

@JsonClass(generateAdapter = true)
data class MoListParam(
    val moType: Int,
    val moString: String,
)

@JsonClass(generateAdapter = true)
data class KpiChartList(
    val xAxis: List<Any?>? = null,
    val y2Axis: List<Any?>? = null,
    val yAxis: List<List<Any?>>? = null,
    val incomeUnit: String? = null,
    val title: String? = null,
    val subtext: String? = null,
)

// ---------------------------------------------------------------------------
// Retrofit interface
// ---------------------------------------------------------------------------
interface FusionSolarApi {
    @POST(FusionSolarConfig.PATH_LOGIN)
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST(FusionSolarConfig.PATH_STATION_LIST)
    suspend fun stationList(@Body body: StationListArg): Envelope<PageData>

    @POST(FusionSolarConfig.PATH_TOTAL_REAL_KPI)
    suspend fun totalRealKpi(@Body body: Map<String, String>): Envelope<TotalKpiData>

    // The original app requests the energy flow with GET + stationDn query
    // (RequestApi.queryStationEnergyFlow); POSTing to this path returns 405.
    // v2/v3 exist on newer regional hosts, so callers may cascade through them.
    @GET(FusionSolarConfig.PATH_ENERGY_FLOW)
    suspend fun energyFlow(
        @Query("stationDn") stationDn: String,
        @Query("_") timestamp: Long,
    ): Envelope<EnergyFlowData>

    @GET(FusionSolarConfig.PATH_ENERGY_FLOW_V2)
    suspend fun energyFlowV2(
        @Query("stationDn") stationDn: String,
        @Query("_") timestamp: Long,
    ): Envelope<EnergyFlowData>

    @GET(FusionSolarConfig.PATH_ENERGY_FLOW_V3)
    suspend fun energyFlowV3(
        @Query("stationDn") stationDn: String,
        @Query("_") timestamp: Long,
    ): Envelope<EnergyFlowData>

    // --- Statistics (historical production) --------------------------------
    // Body keys are built in data/Statistics.kt from the decompiled callers
    // (NormalPowerFragment.queryKpiManage + PhotovoltaicStatisticsViewModel):
    //   stationDn, timeDim, dateStr, queryTime, timeZone, timeZoneStr, _
    @POST(FusionSolarConfig.PATH_STATION_REAL_KPI)
    suspend fun stationRealKpi(@Body body: StationRealKpiRequest): Envelope<StationRealKpi>

    @POST(FusionSolarConfig.PATH_ENERGY_BALANCE)
    suspend fun energyBalance(@Body body: EnergyBalanceRequest): Envelope<EnergyBalanceData>

    @POST(FusionSolarConfig.PATH_ENERGY_BALANCE_V2)
    suspend fun energyBalanceV2(@Body body: EnergyBalanceRequest): Envelope<EnergyBalanceData>

    @POST(FusionSolarConfig.PATH_KPI_CHART)
    suspend fun kpiChart(@Body body: KpiChartRequest): Envelope<KpiChartData>
}

// ---------------------------------------------------------------------------
// Session + client factory
// ---------------------------------------------------------------------------
data class Session(
    val host: String,
    val port: String,
    val accessToken: String,
    val roaRand: String,
) {
    val baseUrl: String get() = "https://$host:$port"
    val cookie: String
        get() = "locale=en_US;data-theme=light;bspsession=$accessToken;dp-session=$accessToken"

    companion object {
        /**
         * Parse a region data-host string. Observed formats:
         * "host" | "host:port" | "host--port" (IpAddressUtil.IP_SPLIT).
         * Empty host falls back to the login host.
         */
        fun parseDataHost(raw: String?, loginHost: String, loginPort: String): Session {
            var h = raw?.trim().orEmpty().removePrefix("https://").removePrefix("http://")
            if (h.isEmpty()) return Session(loginHost, loginPort, "", "")
            // strip any path
            h = h.substringBefore('/')
            var p = loginPort
            var host = h
            when {
                h.contains("--") -> {
                    val i = h.lastIndexOf("--")
                    host = h.substring(0, i)
                    val port = h.substring(i + 2)
                    if (port.isNotEmpty()) p = port
                }
                h.contains(":") -> {
                    val i = h.indexOf(':')
                    host = h.substring(0, i)
                    val port = h.substring(i + 1)
                    if (port.isNotEmpty() && port.all { it.isDigit() }) p = port
                }
            }
            return Session(host.ifEmpty { loginHost }, p, "", "")
        }
    }
}

object ApiFactory {
    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    /**
     * Huawei FusionSolar serves its API through Huawei's internal PKI
     * (leaf: "fusionsolar.huawei.com" ← "Huawei Network Energy Product CA"
     * ← self-signed root "Huawei Equipment CA"), not in the Android system
     * trust store. The original app pins that root from assets/server.cer
     * (sha256 DA:2A:BE:92:...:D4) via FusCertManager. We load the same root
     * plus the system CAs — no blind trust of all certs.
     */
    private fun huaweiTrust(context: android.content.Context): Pair<SSLContext, X509TrustManager>? {
        return try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null)
            context.assets.open("huawei_equipment_ca.cer").use { ins: InputStream ->
                val cf = CertificateFactory.getInstance("X.509")
                keyStore.setCertificateEntry("huawei-equipment-ca", cf.generateCertificate(ins))
            }
            val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            systemTmf.init(null as KeyStore?)
            var i = 0
            for (ca in systemTmf.trustManagers) {
                if (ca is X509TrustManager) {
                    for (cert in ca.acceptedIssuers) {
                        keyStore.setCertificateEntry("system-${i++}", cert)
                    }
                }
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            val tm = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf(tm), null)
            ssl to tm
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Mirrors the original app's FusionHostnameVerifier: FusionSolar leaf
     * certificates carry no subjectAltNames (CN = "fusionsolar.huawei.com"),
     * so strict SAN matching fails for regional servers. The chain is already
     * validated against the pinned CA before this runs; we accept configured
     * FusionSolar hosts and delegate the rest to the system default verifier.
     */
    private fun hostVerifier(sessionHost: String?): javax.net.ssl.HostnameVerifier =
        javax.net.ssl.HostnameVerifier { hostname: String, session: SSLSession ->
            val host = hostname.lowercase()
            val allowed = host == sessionHost?.lowercase() ||
                host == "fusionsolar.huawei.com" ||
                host.endsWith(".fusionsolar.huawei.com") ||
                host.endsWith(".energyorb.com")
            allowed || javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
        }

    fun create(context: android.content.Context?, session: Session?): FusionSolarApi {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
        if (context != null) {
            huaweiTrust(context)?.let { (ssl, tm) ->
                builder.sslSocketFactory(ssl.socketFactory, tm)
                builder.hostnameVerifier(hostVerifier(session?.host))
            }
        }
        if (session?.accessToken?.isNotEmpty() == true) {
            builder.addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Cookie", session.cookie)
                        .header("csrf-token", session.roaRand)
                        .header("roarand", session.roaRand)
                        .header("x-timezone-offset", tzOffset())
                        .build()
                )
            }
        }
        return Retrofit.Builder()
            .baseUrl("${session?.baseUrl ?: "https://${FusionSolarConfig.DEFAULT_HOST}:${FusionSolarConfig.DEFAULT_PORT}"}/")
            .client(builder.build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FusionSolarApi::class.java)
    }

    private fun tzOffset(): String {
        val offsetMin = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        val sign = if (offsetMin >= 0) "+" else "-"
        val abs = kotlin.math.abs(offsetMin)
        return "%s%02d%02d".format(sign, abs / 60, abs % 60)
    }
}
