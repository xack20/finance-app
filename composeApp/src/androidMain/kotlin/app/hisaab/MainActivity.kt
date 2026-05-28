package app.hisaab

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContainer = AppContainer(applicationContext, this)
        setContent {
            CompositionLocalProvider(LocalAppContainer provides appContainer) {
                App()
            }
        }
    }
}
