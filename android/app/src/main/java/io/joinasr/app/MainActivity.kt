package io.joinasr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.joinasr.app.ui.theme.AsrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AsrTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Placeholder(Modifier.padding(padding))
                }
            }
        }
    }
}

// Deliberately a placeholder and named one: the real first screen is 01
// Authentication, built from the Figma file. This exists so the toolchain
// can be proven end to end — compile, unit tests, installable APK — before
// any screen is written against it.
@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Asr", style = MaterialTheme.typography.headlineLarge)
        Text(BuildConfig.API_BASE_URL, style = MaterialTheme.typography.bodySmall)
    }
}
