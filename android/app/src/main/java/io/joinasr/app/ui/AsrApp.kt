package io.joinasr.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.joinasr.app.ui.screens.LogInScreen
import io.joinasr.app.ui.screens.SignUpScreen
import io.joinasr.app.ui.screens.WelcomeScreen

/**
 * Which screen is showing. A sealed hierarchy and one piece of state, not a
 * navigation library: three destinations do not need a back stack
 * implementation, and adding a dependency is a change worth making on its own
 * when the graph is real, not smuggled in alongside the first screens.
 */
private sealed interface Destination {
    data object Welcome : Destination
    data object SignUp : Destination
    data object LogIn : Destination
}

@Composable
fun AsrApp() {
    var destination by remember { mutableStateOf<Destination>(Destination.Welcome) }
    val context = LocalContext.current

    // Anything but the first screen sends the system back gesture home rather
    // than out of the app.
    BackHandler(enabled = destination != Destination.Welcome) {
        destination = Destination.Welcome
    }

    // The forms are drawn and typing works; nothing is sent anywhere yet.
    // Saying so out loud beats a button that looks broken.
    val notWiredYet: () -> Unit = {
        Toast.makeText(
            context,
            "The screens are in. Talking to the server is the next step.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    when (destination) {
        Destination.Welcome -> WelcomeScreen(
            onContinue = { destination = Destination.SignUp },
            onLogIn = { destination = Destination.LogIn },
        )

        Destination.SignUp -> SignUpScreen(
            onBack = { destination = Destination.Welcome },
            onSubmit = { _, _ -> notWiredYet() },
            onLogIn = { destination = Destination.LogIn },
        )

        Destination.LogIn -> LogInScreen(
            onBack = { destination = Destination.Welcome },
            onSubmit = { _, _ -> notWiredYet() },
            onForgotPassword = notWiredYet,
            onCreateAccount = { destination = Destination.SignUp },
        )
    }
}
