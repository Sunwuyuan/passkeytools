package passkeytools.wuyuan.dev.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object CredentialList : Screen("credentials")
    object CredentialDetail : Screen("credential/{credentialId}") {
        fun createRoute(credentialId: String) = "credential/$credentialId"
    }
    object CredentialEdit : Screen("credential/{credentialId}/edit") {
        fun createRoute(credentialId: String) = "credential/$credentialId/edit"
    }
    object CredentialCreate : Screen("credential/create")
    object Logs : Screen("logs")
    object Settings : Screen("settings")
    object ImportExport : Screen("import_export")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.CredentialList, "凭据", Icons.Filled.Key, Icons.Outlined.Key),
    BottomNavItem(Screen.Logs, "日志", Icons.Filled.History, Icons.Outlined.History),
    BottomNavItem(Screen.Settings, "设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

