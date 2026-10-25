package it.promozionepuglia.gironeb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.promozionepuglia.gironeb.data.LeagueSnapshot
import it.promozionepuglia.gironeb.data.ThemeMode
import it.promozionepuglia.gironeb.ui.screens.HomeScreen
import it.promozionepuglia.gironeb.ui.screens.MatchesScreen
import it.promozionepuglia.gironeb.ui.screens.NewsScreen
import it.promozionepuglia.gironeb.ui.screens.ScorersScreen
import it.promozionepuglia.gironeb.ui.screens.StandingsScreen
import it.promozionepuglia.gironeb.ui.screens.TeamScreen
import it.promozionepuglia.gironeb.ui.theme.AppTheme

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    STANDINGS("classifica", "Classifica", Icons.Filled.BarChart),
    MATCHES("partite", "Partite", Icons.Filled.CalendarMonth),
    GOALS("gol", "Gol", Icons.Filled.SportsSoccer),
    NEWS("notizie", "Notizie", Icons.AutoMirrored.Filled.Article),
}

private const val TEAM_ROUTE = "squadra/{key}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }
    val c = AppTheme.colors

    // Aggiorna a ogni apertura e, con l'app visibile, segue le partite in diretta.
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onBackground() }
    }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (currentTab == null || currentTab == Tab.HOME) "Promozione B" else currentTab.label,
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        )
                        Text("Puglia · Girone B · 2026/27", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = c.muted)
                    }
                },
                navigationIcon = {
                    if (currentTab == null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    }
                },
                actions = { ThemeMenu(themeMode, c.isDark, viewModel::setThemeMode) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, titleContentColor = c.text),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = c.nav) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == currentTab || (currentTab == null && tab == Tab.STANDINGS && route == TEAM_ROUTE),
                        onClick = { nav.navigateToTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accent, selectedTextColor = c.accent,
                            indicatorColor = c.accent.copy(alpha = 0.14f),
                            unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.snapshot != null,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val snapshot = state.snapshot
            if (snapshot == null) {
                EmptyState(loading = state.loading, error = state.error, onRetry = viewModel::refresh)
            } else {
                AppNavHost(nav, snapshot, state, viewModel)
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, snapshot: LeagueSnapshot, state: UiState, viewModel: AppViewModel) {
    val openTeam: (String) -> Unit = { key -> nav.navigate("squadra/$key") }
    NavHost(nav, startDestination = Tab.HOME.route) {
        composable(Tab.HOME.route) {
            HomeScreen(snapshot, state.scorers, state.error, openTeam,
                onOpenMatches = { nav.navigateToTab(Tab.MATCHES) },
                onOpenStandings = { nav.navigateToTab(Tab.STANDINGS) },
                onOpenScorers = { nav.navigateToTab(Tab.GOALS) },
                onOpenNews = { nav.navigateToTab(Tab.NEWS) })
        }
        composable(Tab.STANDINGS.route) { StandingsScreen(snapshot, openTeam) }
        composable(Tab.MATCHES.route) { MatchesScreen(snapshot, openTeam) }
        composable(Tab.GOALS.route) { ScorersScreen(snapshot, state.scorers, openTeam) }
        composable(Tab.NEWS.route) { NewsScreen(snapshot.news) }
        composable(TEAM_ROUTE, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
            TeamScreen(snapshot, state.scorers, entry.arguments?.getString("key").orEmpty(), openTeam)
        }
    }
}

/**
 * Apre una scheda conservandone lo stato. La Home si raggiunge togliendo le schermate sopra:
 * navigare verso "home" con popUpTo(home) + restoreState ripristinerebbe la pila appena
 * salvata sotto la chiave di "home", cioè la scheda che si stava lasciando.
 */
private fun NavHostController.navigateToTab(tab: Tab) {
    if (tab == Tab.HOME) {
        popBackStack(Tab.HOME.route, inclusive = false, saveState = true)
        return
    }
    navigate(tab.route) {
        popUpTo(Tab.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ThemeMenu(mode: ThemeMode, isDark: Boolean, onSelect: (ThemeMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode, contentDescription = "Tema chiaro o scuro")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(ThemeMode.SYSTEM to "Come il telefono", ThemeMode.LIGHT to "Chiaro", ThemeMode.DARK to "Scuro").forEach { (m, label) ->
                DropdownMenuItem(
                    text = { Text(if (m == mode) "✓  $label" else "     $label") },
                    onClick = { onSelect(m); open = false },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(loading: Boolean, error: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator()
            Text("Carico classifica, partite e notizie…", color = AppTheme.colors.muted)
        } else {
            Text(error ?: "Nessun dato disponibile", textAlign = TextAlign.Center, color = AppTheme.colors.muted)
            Button(onClick = onRetry) { Text("Riprova") }
        }
    }
}
