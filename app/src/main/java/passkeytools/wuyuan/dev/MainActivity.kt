package passkeytools.wuyuan.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import passkeytools.wuyuan.dev.ui.PasskeyToolsApp
import passkeytools.wuyuan.dev.ui.theme.PasskeyToolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PasskeyToolsTheme {
                PasskeyToolsApp()
            }
        }
    }
}