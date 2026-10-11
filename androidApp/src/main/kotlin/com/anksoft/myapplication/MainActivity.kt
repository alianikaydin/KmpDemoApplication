package com.anksoft.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import com.anksoft.myapplication.core.network.mock.DemoLaunchArguments
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Demo builds only (Maestro passes the extra); a build with a real backend ignores it.
        DemoLaunchArguments.apply(
            koin = GlobalContext.get(),
            versionPolicy = intent.getStringExtra(DemoLaunchArguments.VERSION_POLICY)
        )

        setContent {
            // Expose Compose testTags as resource-ids so UI automation (Maestro) can target them.
            Box(Modifier.semantics { testTagsAsResourceId = true }) {
                App()
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}