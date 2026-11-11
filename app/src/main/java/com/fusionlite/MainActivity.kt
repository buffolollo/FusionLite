package com.fusionlite

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fusionlite.api.EnergyFlowData
import com.fusionlite.api.FusionSolarConfig
import com.fusionlite.api.Session
import com.fusionlite.api.Station
import com.fusionlite.data.AuthExpiredException
import com.fusionlite.data.FlowMetrics
import com.fusionlite.data.FusionRepository
import com.fusionlite.data.SessionStore
import com.fusionlite.data.StatisticsPeriod
import com.fusionlite.data.StatisticsSnapshot
import com.fusionlite.data.TariffStore
import com.fusionlite.data.rangeLabel
import com.fusionlite.data.shiftAnchor
import com.fusionlite.ui.AccountScreen
import com.fusionlite.ui.AppTab
import com.fusionlite.ui.CostsScreen
import com.fusionlite.ui.DashboardScreen
import com.fusionlite.ui.FullscreenChartScreen
import com.fusionlite.ui.FusionTheme
import com.fusionlite.ui.LiquidBottomBar
import com.fusionlite.ui.LoginScreen
import com.fusionlite.ui.StatisticsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FusionTheme {
                SurfaceRoot { FusionLiteApp() }
            }
        }
    }
}

@Composable
private fun SurfaceRoot(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        content()
    }
}

data class UiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,       // login error / info
    val error: String? = null,         // dashboard error
    val flowError: String? = null,
    val diag: String? = null,
    val session: Session? = null,
    val stations: List<Station> = emptyList(),
    val selectedDn: String? = null,
    val flow: EnergyFlowData? = null,
    val flowMetrics: FlowMetrics? = null,
    val lastUpdated: Long = 0L,
    val refreshIntervalSeconds: Int = 30,
    // Navigation + statistics dashboard
    val tab: AppTab = AppTab.HOME,
    val statsPeriod: StatisticsPeriod = StatisticsPeriod.DAY,
    val statsAnchor: Long = System.currentTimeMillis(),
    val stats: StatisticsSnapshot? = null,
    val statsLoading: Boolean = false,
    val statsError: String? = null,
    // Manual tariff / costs page
    val costsPeriod: StatisticsPeriod = StatisticsPeriod.MONTH,
    val costsAnchor: Long = System.currentTimeMillis(),
    val costs: StatisticsSnapshot? = null,
    val costsLoading: Boolean = false,
    val costsError: String? = null,
    val buyPriceCents: String = "",
    val sellPriceCents: String = "",
)

@Composable
private fun FusionLiteApp() {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val store = remember { SessionStore(context) }
    val tariffs = remember { TariffStore(context) }
    val repository = remember { FusionRepository(context) }
    val scope = rememberCoroutineScope()

    val initialSaved = remember { store.load() }
    val clientId = remember { store.appClientId() }

    var state by remember {
        mutableStateOf(
            UiState(
                session = initialSaved?.toSession(),
                loggedIn = initialSaved?.toSession() != null,
                selectedDn = initialSaved?.selectedDn,
                refreshIntervalSeconds = 30,
                buyPriceCents = tariffs.buyCents(),
                sellPriceCents = tariffs.sellCents(),
            )
        )
    }
    var user by remember { mutableStateOf(initialSaved?.username.orEmpty()) }
    var pass by remember { mutableStateOf(initialSaved?.password.orEmpty()) }
    var host by remember { mutableStateOf(initialSaved?.host ?: FusionSolarConfig.DEFAULT_HOST) }
    var port by remember { mutableStateOf(initialSaved?.port ?: FusionSolarConfig.DEFAULT_PORT) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiContext = LocalContext.current
    val activity = generateSequence(uiContext) { context ->
        (context as? android.content.ContextWrapper)?.baseContext
    }.firstOrNull { it is android.app.Activity } as? android.app.Activity
    // Full-screen landscape presentation of the production/power curve.
    var chartFullscreen by rememberSaveable { mutableStateOf(false) }

    // Forcing landscape would normally recreate the activity; the manifest
    // declares orientation/screenSize as handled config changes so the whole
    // UiState survives rotation and the user stays on the Analytics tab.
    DisposableEffect(chartFullscreen, activity) {
        val window = activity?.window
        if (chartFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler(enabled = chartFullscreen) { chartFullscreen = false }

    // Serialises renewals: the dashboard loop and the analytics tab can both hit
    // an expired session at the same time and must not race each other.
    val renewMutex = remember { Mutex() }
    var hostRecoveryUsed by remember { mutableStateOf(false) }

    fun friendlyError(e: Throwable): String = when (e) {
        is AuthExpiredException -> "Session expired — please sign in again."
        is HttpException -> "HTTP ${e.code()} ${e.message()}".trim()
        is IOException -> "Network error: ${e.localizedMessage ?: "cannot reach server"}"
        else -> e.localizedMessage ?: e.javaClass.simpleName
    }

    /**
     * Renews the saved session. The order mirrors the shipped app:
     *  1. `grantType=refreshtoken` against the current data host
     *     (SessionOutInterceptor.buildAutoLoginRequestBody), then
     *  2. password login against the configured auth host, which also
     *     re-resolves `regionFloatIp` when the regional host has moved.
     *
     * Returns null only when both credentials are definitively rejected.
     * Transient IO/5xx failures are rethrown so the caller keeps the current
     * session and retries later instead of signing the user out.
     */
    suspend fun renewSession(current: Session?): Session? = renewMutex.withLock {
        val saved = withContext(Dispatchers.IO) { store.load() } ?: return@withLock null
        val stored = saved.toSession()
        // Another coroutine already renewed while this one was waiting.
        if (current != null && stored != null && stored.accessToken != current.accessToken) {
            return@withLock stored
        }

        var transient: IOException? = null
        val base = stored ?: current
        if (base != null && !saved.refreshToken.isNullOrBlank() && !saved.username.isNullOrBlank()) {
            try {
                val renewed = repository.renewWithRefreshToken(
                    session = base,
                    username = saved.username,
                    appClientId = saved.appClientId,
                )
                if (renewed != null) {
                    withContext(Dispatchers.IO) {
                        store.saveSession(renewed, saved.appClientId)
                    }
                    return@withLock renewed
                }
            } catch (e: IOException) {
                transient = e
            } catch (e: HttpException) {
                if (e.code() in 500..599 || e.code() == 429) {
                    transient = IOException("HTTP ${e.code()}", e)
                }
            }
        }

        if (!saved.canRelogin) {
            transient?.let { throw it }
            return@withLock null
        }
        val username = saved.username
        val password = saved.password
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            transient?.let { throw it }
            return@withLock null
        }
        try {
            val session = repository.login(username, password, saved.host, saved.port, saved.appClientId)
            withContext(Dispatchers.IO) {
                store.saveLogin(
                    username = username,
                    password = password,
                    loginHost = saved.host,
                    loginPort = saved.port,
                    session = session,
                    appClientId = saved.appClientId,
                )
            }
            session
        } catch (e: IOException) {
            // Transient outage: keep the current dashboard/session instead of
            // dumping the user back on the login screen.
            throw e
        } catch (e: HttpException) {
            if (e.code() in 500..599 || e.code() == 429) throw IOException("HTTP ${e.code()}", e)
            transient?.let { throw it }
            null
        } catch (e: AuthExpiredException) {
            // Wrong saved password / rejected login. If the refresh-token path
            // failed only because of the network, that error is more useful.
            transient?.let { throw it }
            null
        } catch (e: Exception) {
            transient?.let { throw it }
            null
        }
    }

    suspend fun performRefresh() {
        val current = state
        if (current.loading || !current.loggedIn) return
        var session = current.session ?: return
        state = current.copy(loading = true, error = null, message = null)

        repeat(2) { attempt ->
            try {
                val snapshot = try {
                    withContext(Dispatchers.IO) { repository.loadDashboard(session, current.selectedDn) }
                } catch (e: AuthExpiredException) {
                    val renewed = renewSession(session)
                        ?: throw AuthExpiredException("Your saved session expired. Sign in again.")
                    session = renewed
                    withContext(Dispatchers.IO) { repository.loadDashboard(renewed, current.selectedDn) }
                }

                val selected = snapshot.selectedDn
                if (selected != current.selectedDn) {
                    withContext(Dispatchers.IO) { store.saveSelectedStation(selected) }
                }
                val hasStations = snapshot.stations.isNotEmpty()
                state = state.copy(
                    loggedIn = true,
                    loading = false,
                    session = session,
                    stations = snapshot.stations,
                    selectedDn = selected,
                    flow = snapshot.flow.data,
                    flowMetrics = snapshot.flow.metrics,
                    flowError = snapshot.flow.error.takeIf { hasStations },
                    error = when {
                        hasStations -> null
                        snapshot.flow.error != null -> "No stations · ${snapshot.flow.error}"
                        else -> "No plants are linked to this account."
                    },
                    diag = snapshot.diag,
                    lastUpdated = System.currentTimeMillis(),
                )
                return
            } catch (e: AuthExpiredException) {
                state = state.copy(
                    loggedIn = false,
                    loading = false,
                    session = null,
                    message = friendlyError(e),
                )
                return
            } catch (e: Exception) {
                // The first load of a run can hit a data host that has moved
                // while the app was closed (regionFloatIp is a floating IP).
                // Re-authenticate once, which re-resolves the region host, then
                // retry before surfacing the error.
                val hostLevel = e is IOException ||
                    (e is HttpException && e.code() in listOf(400, 404, 405, 421, 502, 503, 504))
                if (attempt == 0 && !hostRecoveryUsed && hostLevel) {
                    hostRecoveryUsed = true
                    try {
                        val renewed = renewSession(session)
                        if (renewed != null) {
                            session = renewed
                            return@repeat
                        }
                    } catch (_: Exception) {
                        // Fall through to the friendly error below.
                    }
                }
                state = state.copy(
                    loading = false,
                    error = friendlyError(e),
                )
                return
            }
        }
    }

    /**
     * Loads the statistics window. Kept separate from [performRefresh] so the
     * heavier historical request only runs when the Analytics tab is visible
     * or its filters change.
     */
    suspend fun performStatistics() {
        val current = state
        if (!current.loggedIn) return
        val dn = current.selectedDn ?: return
        var session = current.session ?: return
        state = state.copy(statsLoading = true, statsError = null)
        try {
            val snapshot = try {
                withContext(Dispatchers.IO) {
                    repository.loadStatistics(session, dn, current.statsPeriod, current.statsAnchor)
                }
            } catch (e: AuthExpiredException) {
                val renewed = renewSession(session)
                    ?: throw AuthExpiredException("Your saved session expired. Sign in again.")
                session = renewed
                withContext(Dispatchers.IO) {
                    repository.loadStatistics(renewed, dn, current.statsPeriod, current.statsAnchor)
                }
            }
            state = state.copy(
                statsLoading = false,
                stats = snapshot,
                statsError = snapshot.error,
                session = session,
            )
        } catch (e: AuthExpiredException) {
            state = state.copy(
                loggedIn = false,
                statsLoading = false,
                session = null,
                message = friendlyError(e),
            )
        } catch (e: Exception) {
            state = state.copy(
                statsLoading = false,
                statsError = friendlyError(e),
            )
        }
    }

    /**
     * Loads the energy window used by the Costs page. Kept separate from the
     * Analytics snapshot because the two pages can select different periods.
     */
    suspend fun performCosts() {
        val current = state
        if (!current.loggedIn) return
        val dn = current.selectedDn ?: return
        var session = current.session ?: return
        state = state.copy(costsLoading = true, costsError = null)
        try {
            val snapshot = try {
                withContext(Dispatchers.IO) {
                    repository.loadStatistics(session, dn, current.costsPeriod, current.costsAnchor)
                }
            } catch (e: AuthExpiredException) {
                val renewed = renewSession(session)
                    ?: throw AuthExpiredException("Your saved session expired. Sign in again.")
                session = renewed
                withContext(Dispatchers.IO) {
                    repository.loadStatistics(renewed, dn, current.costsPeriod, current.costsAnchor)
                }
            }
            state = state.copy(
                costsLoading = false,
                costs = snapshot,
                costsError = snapshot.error,
                session = session,
            )
        } catch (e: AuthExpiredException) {
            state = state.copy(
                loggedIn = false,
                costsLoading = false,
                session = null,
                message = friendlyError(e),
            )
        } catch (e: Exception) {
            state = state.copy(
                costsLoading = false,
                costsError = friendlyError(e),
            )
        }
    }

    suspend fun performLogin(username: String, password: String, loginHost: String, loginPort: String) {
        hostRecoveryUsed = false
        state = state.copy(loading = true, message = null, error = null)
        try {
            val session = repository.login(username, password, loginHost, loginPort, clientId)
            withContext(Dispatchers.IO) {
                store.saveLogin(
                    username = username,
                    password = password,
                    loginHost = loginHost,
                    loginPort = loginPort,
                    session = session,
                    appClientId = clientId,
                )
            }
            state = state.copy(
                loggedIn = true,
                session = session,
                loading = false,
                message = null,
                error = null,
            )
            performRefresh()
        } catch (e: AuthExpiredException) {
            state = state.copy(loading = false, loggedIn = false, message = friendlyError(e))
        } catch (e: Exception) {
            state = state.copy(loading = false, loggedIn = false, message = friendlyError(e))
        }
    }

    // First launch: restore the persisted session, or renew it silently when
    // the access token has expired. The app can come back after days/weeks, so
    // renewal must not depend on the encrypted password still being readable.
    LaunchedEffect(Unit) {
        if (state.loggedIn) {
            performRefresh()
        } else {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved?.canRelogin == true) {
                state = state.copy(loading = true)
                try {
                    val session = renewSession(null)
                    if (session != null) {
                        state = state.copy(
                            loggedIn = true,
                            session = session,
                            loading = false,
                            message = null,
                            error = null,
                        )
                        performRefresh()
                    } else {
                        state = state.copy(loading = false)
                    }
                } catch (e: Exception) {
                    state = state.copy(loading = false, message = friendlyError(e))
                }
            } else if (saved != null) {
                state = state.copy(
                    message = "Your saved session expired. Please sign in again.",
                )
            }
        }
    }

    // Dynamic refresh loop: interval is read each tick, so changing the chip
    // takes effect immediately without restarting the session.
    LaunchedEffect(state.loggedIn) {
        if (!state.loggedIn) return@LaunchedEffect
        while (true) {
            delay(state.refreshIntervalSeconds * 1000L)
            if (!state.loading) performRefresh()
        }
    }

    // Load statistics when the Analytics tab is first opened and whenever the
    // plant, period, window or session changes. Stats are not refetched by the
    // live auto-refresh loop (historical data does not change every 15 s).
    LaunchedEffect(
        state.loggedIn,
        state.tab,
        state.selectedDn,
        state.statsPeriod,
        state.statsAnchor,
        state.session?.accessToken,
    ) {
        if (state.loggedIn && state.tab == AppTab.STATS && state.selectedDn != null) {
            val current = state.stats
            val stale = current == null ||
                current.stationDn != state.selectedDn ||
                current.period != state.statsPeriod ||
                current.anchorMillis != state.statsAnchor
            if (stale) performStatistics()
        }
    }

    LaunchedEffect(
        state.loggedIn,
        state.tab,
        state.selectedDn,
        state.costsPeriod,
        state.costsAnchor,
        state.session?.accessToken,
    ) {
        if (state.loggedIn && state.tab == AppTab.COSTS && state.selectedDn != null) {
            val current = state.costs
            val stale = current == null ||
                current.stationDn != state.selectedDn ||
                current.period != state.costsPeriod ||
                current.anchorMillis != state.costsAnchor
            if (stale) performCosts()
        }
    }

    // Refresh immediately when the app returns to the foreground.
    DisposableEffect(lifecycleOwner, state.loggedIn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state.loggedIn) {
                scope.launch { performRefresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (chartFullscreen && state.loggedIn) {
        val station = state.stations.firstOrNull { it.dn == state.selectedDn }
            ?: state.stations.firstOrNull()
        FullscreenChartScreen(
            snapshot = state.stats,
            period = state.statsPeriod,
            loading = state.statsLoading,
            stationName = station?.name ?: "Solar plant",
            range = rangeLabel(state.statsPeriod, state.statsAnchor),
            onClose = { chartFullscreen = false },
            modifier = Modifier.fillMaxSize(),
        )
    } else if (state.loggedIn) {
        val logout: () -> Unit = {
            chartFullscreen = false
            state = UiState(
                refreshIntervalSeconds = state.refreshIntervalSeconds,
                // Tariffs are device preferences, not account data.
                buyPriceCents = state.buyPriceCents,
                sellPriceCents = state.sellPriceCents,
                costsPeriod = state.costsPeriod,
                costsAnchor = state.costsAnchor,
            )
            pass = ""
            scope.launch(Dispatchers.IO) { store.clear() }
        }
        val selectStation: (String) -> Unit = { dn ->
            state = state.copy(
                selectedDn = dn,
                stats = null,
                statsError = null,
                costs = null,
                costsError = null,
            )
            scope.launch { performRefresh() }
        }
        val refresh: () -> Unit = { scope.launch { performRefresh() } }

        Box(Modifier.fillMaxSize()) {
            when (state.tab) {
                AppTab.HOME -> DashboardScreen(
                    state = state,
                    onRefresh = refresh,
                    onSelectStation = selectStation,
                    onIntervalChange = { seconds ->
                        state = state.copy(refreshIntervalSeconds = seconds)
                    },
                    onLogout = logout,
                )

                AppTab.STATS -> StatisticsScreen(
                    state = state,
                    onPeriod = { period ->
                        state = state.copy(statsPeriod = period, stats = null, statsError = null)
                    },
                    onShift = { delta ->
                        state = state.copy(
                            statsAnchor = shiftAnchor(state.statsPeriod, state.statsAnchor, delta),
                            stats = null,
                            statsError = null,
                        )
                    },
                    onRefresh = { scope.launch { performStatistics() } },
                    onExpandChart = { chartFullscreen = true },
                )

                AppTab.COSTS -> CostsScreen(
                    state = state,
                    onPeriod = { period ->
                        state = state.copy(costsPeriod = period, costs = null, costsError = null)
                    },
                    onShift = { delta ->
                        state = state.copy(
                            costsAnchor = shiftAnchor(state.costsPeriod, state.costsAnchor, delta),
                            costs = null,
                            costsError = null,
                        )
                    },
                    onRefresh = { scope.launch { performCosts() } },
                    onBuyPrice = { value ->
                        state = state.copy(buyPriceCents = value)
                        tariffs.saveBuy(value)
                    },
                    onSellPrice = { value ->
                        state = state.copy(sellPriceCents = value)
                        tariffs.saveSell(value)
                    },
                )

                AppTab.ACCOUNT -> AccountScreen(
                    state = state,
                    onSelectStation = selectStation,
                    onIntervalChange = { seconds ->
                        state = state.copy(refreshIntervalSeconds = seconds)
                    },
                    onRefresh = refresh,
                    onLogout = logout,
                )
            }

            LiquidBottomBar(
                selected = state.tab,
                onSelect = { tab -> state = state.copy(tab = tab) },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp),
            )
        }
    } else {
        LoginScreen(
            user = user,
            onUser = { user = it },
            pass = pass,
            onPass = { pass = it },
            host = host,
            onHost = { host = it },
            port = port,
            onPort = { port = it },
            loading = state.loading,
            message = state.message,
            onLogin = {
                val loginHost = host.trim()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore('/')
                    .ifBlank { FusionSolarConfig.DEFAULT_HOST }
                scope.launch {
                    performLogin(user.trim(), pass, loginHost, port.trim().ifBlank { FusionSolarConfig.DEFAULT_PORT })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 0.dp),
        )
    }
}
