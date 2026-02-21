package passkeytools.wuyuan.dev.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import passkeytools.wuyuan.dev.ui.navigation.*
import passkeytools.wuyuan.dev.ui.screens.*
import passkeytools.wuyuan.dev.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasskeyToolsApp() {
    val navController = rememberNavController()

    // Shared ViewModels hoisted at app level
    val credentialListVm: CredentialListViewModel = viewModel()
    val logVm: LogViewModel = viewModel()
    val importExportVm: ImportExportViewModel = viewModel()

    // Determine if we're at a root (bottom nav) destination
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isRootDest = bottomNavItems.any { item ->
        currentDest?.route == item.screen.route
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isRootDest) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDest?.route == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.CredentialList.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
            exitTransition = { slideOutHorizontally { -it / 3 } + fadeOut() },
            popEnterTransition = { slideInHorizontally { -it / 3 } + fadeIn() },
            popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
        ) {
            composable(Screen.CredentialList.route) {
                CredentialListScreen(
                    onCredentialClick = { id ->
                        navController.navigate(Screen.CredentialDetail.createRoute(id))
                    },
                    onCreateNew = {
                        navController.navigate(Screen.CredentialCreate.route)
                    },
                    viewModel = credentialListVm
                )
            }

            composable(Screen.CredentialDetail.route) { backStackEntry ->
                val credentialId = backStackEntry.arguments?.getString("credentialId") ?: return@composable
                val detailVm: CredentialDetailViewModel = viewModel(
                    key = "detail_$credentialId",
                    factory = CredentialDetailViewModelFactory(credentialId)
                )
                CredentialDetailScreen(
                    credentialId = credentialId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Screen.CredentialEdit.createRoute(credentialId)) },
                    viewModel = detailVm
                )
            }

            composable(Screen.CredentialEdit.route) { backStackEntry ->
                val credentialId = backStackEntry.arguments?.getString("credentialId") ?: return@composable
                val detailVm: CredentialDetailViewModel = viewModel(
                    key = "detail_$credentialId",
                    factory = CredentialDetailViewModelFactory(credentialId)
                )
                CredentialEditScreen(
                    credentialId = credentialId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    viewModel = detailVm
                )
            }

            composable(Screen.CredentialCreate.route) {
                CredentialCreateScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { credentialId ->
                        navController.navigate(Screen.CredentialDetail.createRoute(credentialId)) {
                            popUpTo(Screen.CredentialList.route)
                        }
                    }
                )
            }

            composable(Screen.Logs.route) {
                LogScreen(viewModel = logVm)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToImportExport = { navController.navigate(Screen.ImportExport.route) },
                    credentialVm = credentialListVm,
                    logVm = logVm,
                )
            }

            composable(Screen.ImportExport.route) {
                ImportExportScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = importExportVm,
                )
            }
        }
    }
}

