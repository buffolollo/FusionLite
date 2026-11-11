package com.fusionlite

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.fusionlite.data.shiftAnchor
import com.fusionlite.ui.AccountScreen
import com.fusionlite.ui.AppTab
import com.fusionlite.ui.DashboardScreen
import com.fusionlite.ui.FusionTheme
import com.fusionlite.ui.LiquidBottomBar
import com.fusionlite.ui.LoginScreen
import com.fusionlite.ui.StatisticsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
)

@Composable
private fun FusionLiteApp() {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val store = remember { SessionStore(context) }
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
            )
        )
    }
    var user by remember { mutableStateOf(initialSaved?.username.orEmpty()) }
    var pass by remember { mutableStateOf(initialSaved?.password.orEmpty()) }
    var host by remember { mutableStateOf(initialSaved?.host ?: FusionSolarConfig.DEFAULT_HOST) }
    var port by remember { mutableStateOf(initialSaved?.port ?: FusionSolarConfig.DEFAULT_PORT) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun friendlyError(e: Throwable): String = when (e) {
        is AuthExpiredException -> "Session expired — please sign in again."
        is HttpException -> "HTTP ${e.code()} ${e.message()}".trim()
        is IOException -> "Network error: ${e.localizedMessage ?: "cannot reach server"}"
        else -> e.localizedMessage ?: e.javaClass.simpleName
    }

    suspend fun silentRelogin(): Session? {
        val saved = withContext(Dispatchers.IO) { store.load() } ?: return null
        if (!saved.canRelogin) return null
        val username = saved.username ?: return null
        val password = saved.password ?: return null
        return try {
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
            if (e.code() in 500..599 || e.code() == 429) throw e
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun performRefresh() {
        val current = state
        if (current.loading || !current.loggedIn) return
        var session = current.session ?: return
        state = current.copy(loading = true, error = null, message = null)

        try {
            val snapshot = try {
                withContext(Dispatchers.IO) { repository.loadDashboard(session, current.selectedDn) }
            } catch (e: AuthExpiredException) {
                val renewed = silentRelogin()
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
        } catch (e: AuthExpiredException) {
            state = state.copy(
                loggedIn = false,
                loading = false,
                session = null,
                message = friendlyError(e),
            )
        } catch (e: Exception) {
            state = state.copy(
                loading = false,
                error = friendlyError(e),
            )
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
                val renewed = silentRelogin()
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

    suspend fun performLogin(username: String, password: String, loginHost: String, loginPort: String) {
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

    // First launch: restore the persisted session, or renew it silently with the
    // saved credentials when the token has expired.
    LaunchedEffect(Unit) {
        if (state.loggedIn) {
            performRefresh()
        } else {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved?.canRelogin == true) {
                val username = saved.username ?: return@LaunchedEffect
                val password = saved.password ?: return@LaunchedEffect
                performLogin(username, password, saved.host, saved.port)
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

    if (state.loggedIn) {
        val logout: () -> Unit = {
            state = UiState(refreshIntervalSeconds = state.refreshIntervalSeconds)
            pass = ""
            scope.launch(Dispatchers.IO) { store.clear() }
        }
        val selectStation: (String) -> Unit = { dn ->
            state = state.copy(selectedDn = dn, stats = null, statsError = null)
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
