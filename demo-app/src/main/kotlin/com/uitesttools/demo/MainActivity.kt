package com.uitesttools.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.uitesttools.demo.testids.TestArgs

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Test-directed IoC via intent extras (design §7 F). Instrumentation
        // `-e KEY VALUE` args never reach this app process, so the iOS
        // `ProcessInfo.arguments` model does NOT port — the app reads its flags
        // off its own launch intent here instead. Production code stays free of
        // any test-lib dependency: plain `intent` extras + the shared `TestArgs`
        // keys. A test launches with `--es SEED_DATASET 42` (or
        // `Intent.putTestArgs(...)`) and the counter starts seeded.
        val initialCounter = intent?.getStringExtra(TestArgs.SEED_DATASET)?.toIntOrNull() ?: 0

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true },
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(initialCounter = initialCounter)
                }
            }
        }
    }
}
