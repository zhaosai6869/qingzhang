package com.qingxi.ledger

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qingxi.ledger.data.db.AppDatabase
import com.qingxi.ledger.data.settings.AppSettings
import com.qingxi.ledger.data.settings.ThemeMode
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.di.LocalAppGraph
import com.qingxi.ledger.di.ProvideAppGraph
import com.qingxi.ledger.ui.calendar.CalendarScreen
import com.qingxi.ledger.ui.flow.FlowScreen
import com.qingxi.ledger.ui.flow.SearchScreen
import com.qingxi.ledger.ui.lock.AppLockGate
import com.qingxi.ledger.ui.lock.LockManager
import com.qingxi.ledger.ui.record.RecordScreen
import com.qingxi.ledger.ui.settings.AboutScreen
import com.qingxi.ledger.ui.settings.BackupScreen
import com.qingxi.ledger.ui.settings.CategoryManageScreen
import com.qingxi.ledger.ui.settings.LockSettingsScreen
import com.qingxi.ledger.ui.settings.MineScreen
import com.qingxi.ledger.ui.stats.AnnualReportScreen
import com.qingxi.ledger.ui.stats.CategoryDetailScreen
import com.qingxi.ledger.ui.stats.CategoryDrill
import com.qingxi.ledger.ui.stats.StatsScreen
import com.qingxi.ledger.ui.theme.QingJiTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

const val ROUTE_FLOW = "flow"
const val ROUTE_CALENDAR = "calendar"
const val ROUTE_STATS = "stats"
const val ROUTE_MINE = "mine"
const val ROUTE_SEARCH = "search"
const val ROUTE_RECORD = "record"
const val ROUTE_RECORD_EDIT = "record/{editId}"
const val ROUTE_CATEGORY_MANAGE = "category_manage"
const val ROUTE_BACKUP = "backup"
const val ROUTE_LOCK_SETTINGS = "lock_settings"
const val ROUTE_ABOUT = "about"
const val ROUTE_ANNUAL_REPORT = "annual_report"
const val ROUTE_CATEGORY_DETAIL = "category_detail/{categoryId}/{start}/{end}"

private val TAB_ROUTES = listOf(ROUTE_FLOW, ROUTE_CALENDAR, ROUTE_STATS, ROUTE_MINE)

class MainActivity : FragmentActivity() {

    private val openRecord = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppDatabase.init(applicationContext)
        openRecord.value = intent?.getBooleanExtra(EXTRA_OPEN_RECORD, false) == true
        addOnNewIntentListener { incoming ->
            if (incoming.getBooleanExtra(EXTRA_OPEN_RECORD, false)) openRecord.value = true
        }
        setContent {
            ProvideAppGraph {
                val app = LocalAppGraph.current
                val settings by app.settings.settings
                    .collectAsStateWithLifecycle(initialValue = AppSettings())
                HideRecentTaskContent()
                // 导航栈必须活过应用锁：AppLockGate 上锁时会把 content 整棵移出组合，
                // 在这里持有 NavController 才能让记一笔页面和它的 ViewModel 在解锁后原样恢复。
                val navController = rememberNavController()
                QingJiTheme(
                    darkTheme = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                    },
                    palette = settings.palette
                ) {
                    AppLockGate {
                        QingJiRoot(
                            navController = navController,
                            openRecord = openRecord.value,
                            onRecordOpened = { openRecord.value = false }
                        )
                    }
                }
            }
        }
    }

    companion object {
        /** 小组件「记一笔」直达记账页的 extra。 */
        const val EXTRA_OPEN_RECORD = "open_record"
    }
}

@Composable
private fun QingJiRoot(
    navController: NavHostController,
    openRecord: Boolean,
    onRecordOpened: () -> Unit
) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showTabBar = currentRoute == null || currentRoute in TAB_ROUTES

    // 解锁之后（即真正进入应用）才检查更新，控制器内部保证一个进程只发一次请求。
    val graph = LocalAppGraph.current
    LaunchedEffect(Unit) { graph.updateController.autoCheckOnColdStart() }

    LaunchedEffect(openRecord) {
        if (openRecord) {
            navController.navigate(ROUTE_RECORD) { launchSingleTop = true }
            onRecordOpened()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showTabBar) {
                BottomTabBar(
                    selected = currentRoute ?: ROUTE_FLOW,
                    onTab = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(ROUTE_FLOW) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onRecord = { navController.navigate(ROUTE_RECORD) { launchSingleTop = true } }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_FLOW,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(ROUTE_FLOW) {
                FlowScreen(
                    onRecord = { navController.navigate(ROUTE_RECORD) },
                    onEdit = { id -> navController.navigate("$ROUTE_RECORD/$id") },
                    onSearch = { navController.navigate(ROUTE_SEARCH) }
                )
            }
            composable(ROUTE_CALENDAR) {
                CalendarScreen(onRecord = { navController.navigate(ROUTE_RECORD) })
            }
            composable(ROUTE_STATS) {
                StatsScreen(
                    onOpenCategory = { drill ->
                        navController.navigate(
                            "category_detail/${drill.categoryId}/${drill.start}/${drill.end}"
                        )
                    },
                    onOpenAnnualReport = { navController.navigate(ROUTE_ANNUAL_REPORT) }
                )
            }
            composable(ROUTE_MINE) {
                MineScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(ROUTE_ANNUAL_REPORT) {
                AnnualReportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCategory = { drill ->
                        navController.navigate(
                            "category_detail/${drill.categoryId}/${drill.start}/${drill.end}"
                        )
                    }
                )
            }
            composable(
                route = ROUTE_CATEGORY_DETAIL,
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.LongType },
                    navArgument("start") { type = NavType.LongType },
                    navArgument("end") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val drill = CategoryDrill(
                    categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L,
                    start = backStackEntry.arguments?.getLong("start") ?: 0L,
                    end = backStackEntry.arguments?.getLong("end") ?: 0L
                )
                CategoryDetailScreen(
                    request = drill,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("$ROUTE_RECORD/$id") }
                )
            }
            composable(ROUTE_SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("$ROUTE_RECORD/$id") }
                )
            }
            composable(ROUTE_RECORD) {
                RecordScreen(editId = null, onClose = { navController.popBackStack() })
            }
            composable(ROUTE_RECORD_EDIT) { backStackEntry ->
                val editId = backStackEntry.arguments?.getString("editId")?.toLongOrNull()
                RecordScreen(editId = editId, onClose = { navController.popBackStack() })
            }
            composable(ROUTE_CATEGORY_MANAGE) { CategoryManageScreen(onBack = { navController.popBackStack() }) }
            composable(ROUTE_BACKUP) { BackupScreen(onBack = { navController.popBackStack() }) }
            composable(ROUTE_LOCK_SETTINGS) { LockSettingsScreen(onBack = { navController.popBackStack() }) }
            composable(ROUTE_ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

@Composable
private fun HideRecentTaskContent() {
    val context = LocalContext.current
    val manager = remember(context) { LockManager(context) }
    val lock by manager.state.collectAsStateWithLifecycle(initialValue = null)
    val secure = lock?.enabled == true && lock?.hideContent == true
    LaunchedEffect(secure) {
        val window = (context as? FragmentActivity)?.window ?: return@LaunchedEffect
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private data class TabItem(val route: String, val emoji: String, val label: String)

private val TABS = listOf(
    TabItem(ROUTE_FLOW, "📋", "流水"),
    TabItem(ROUTE_CALENDAR, "📅", "日历"),
    TabItem(ROUTE_STATS, "📊", "统计"),
    TabItem(ROUTE_MINE, "⚙️", "我的")
)

@Composable
private fun BottomTabBar(
    selected: String,
    onTab: (String) -> Unit,
    onRecord: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.take(2).forEach { tab -> TabButton(tab, selected == tab.route, onTab) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onRecord),
                contentAlignment = Alignment.Center
            ) {
                RecordButton()
            }
            TABS.takeLast(2).forEach { tab -> TabButton(tab, selected == tab.route, onTab) }
        }
    }
}

@Composable
private fun RowScope.TabButton(tab: TabItem, selected: Boolean, onTab: (String) -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onTab(tab.route) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = tab.emoji,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.alpha(if (selected) 1f else 0.55f)
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordButton() {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .size(width = 62.dp, height = 38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "记一笔",
            style = MaterialTheme.typography.labelLarge,
            color = onPrimary
        )
    }
}
