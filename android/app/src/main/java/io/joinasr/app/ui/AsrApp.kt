package io.joinasr.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.ui.screens.AboutYouScreen
import io.joinasr.app.ui.screens.LogInScreen
import io.joinasr.app.ui.screens.SignUpScreen
import io.joinasr.app.ui.screens.ProtectionScreen
import io.joinasr.app.ui.screens.SignedInScreen
import io.joinasr.app.ui.screens.UsageAccessScreen
import io.joinasr.app.ui.screens.WelcomeScreen
import io.joinasr.app.ui.theme.AsrColors

/**
 * Which screen is showing while signed out. A sealed hierarchy and one piece
 * of state, not a navigation library: three destinations do not need a back
 * stack implementation, and that dependency is worth adding deliberately
 * when the graph is real rather than smuggled in alongside the first screens.
 */
private sealed interface Destination {
    data object Welcome : Destination
    data object SignUp : Destination
    data object LogIn : Destination
}

/** The setup steps that come after an account exists. */
private sealed interface SetupStep {
    data object UsageAccess : SetupStep
    data object Protection : SetupStep
}

@Composable
fun AsrApp(viewModel: SessionViewModel = viewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val submitting by viewModel.submitting.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var destination by remember { mutableStateOf<Destination>(Destination.Welcome) }
    var setupStep by remember { mutableStateOf<SetupStep>(SetupStep.UsageAccess) }
    // Seeded from the live state so somebody who already granted both never
    // sees the setup screens again.
    var setupDone by remember { mutableStateOf(PermissionState.read(context).requiredGranted) }

    // Moving between the forms drops whatever the last one was refused for.
    // An error about a password left standing over a different screen reads
    // as a new failure.
    LaunchedEffect(destination) { viewModel.clearError() }

    BackHandler(enabled = destination != Destination.Welcome) {
        destination = Destination.Welcome
    }

    when (val current = session) {
        // Between launch and the answer from /v1/me. Blank rather than a
        // spinner: it is one request against a warm connection, and a
        // spinner that flashes for 200ms is worse than nothing.
        Session.Unknown -> Box(Modifier.fillMaxSize().background(AsrColors.Background))

        is Session.SignedIn ->
            if (!current.me.profileComplete) {
                // Figma 03. Shown from what the server holds, not from a
                // local "already asked" flag, so reinstalling or signing in
                // on a second phone does not ask twice -- and skipping it is
                // not possible, because the next screens need an age.
                AboutYouScreen(
                    onBack = {
                        destination = Destination.Welcome
                        viewModel.signOut()
                    },
                    onSubmit = viewModel::saveProfile,
                    onPhotoPicked = viewModel::uploadPhoto,
                    initialName = current.me.name,
                    submitting = submitting,
                    errorMessage = error,
                )
            } else if (!setupDone) {
                // Figma 05 and 09. Whether setup is needed is read from the
                // system, not from a flag: these grants can be revoked in
                // Settings at any time, and an app that remembers "already
                // done" would carry on promising protection it cannot give.
                when (setupStep) {
                    SetupStep.UsageAccess -> UsageAccessScreen(
                        // The only "up" from the first setup step. Harsh, but
                        // a chevron that does nothing is worse, and there is
                        // no screen behind this one to return to.
                        onBack = {
                            destination = Destination.Welcome
                            viewModel.signOut()
                        },
                        onGranted = { setupStep = SetupStep.Protection },
                    )

                    SetupStep.Protection -> ProtectionScreen(
                        onBack = { setupStep = SetupStep.UsageAccess },
                        // Overlay is what draws the block screen over another
                        // app. Figma 10 explains an Accessibility-based
                        // mechanism instead; which of the two this app ships
                        // is still open, so this goes to the permission the
                        // current design in docs/ANDROID.md actually needs.
                        onReviewBlocking = {
                            runCatching {
                                context.startActivity(Permissions.overlayIntent(context))
                            }
                        },
                        onContinue = { setupDone = true },
                    )
                }
            } else {
                SignedInScreen(
                    me = current.me,
                    onSignOut = {
                        destination = Destination.Welcome
                        viewModel.signOut()
                    },
                )
            }

        Session.SignedOut -> when (destination) {
            Destination.Welcome -> WelcomeScreen(
                onContinue = { destination = Destination.SignUp },
                onLogIn = { destination = Destination.LogIn },
            )

            Destination.SignUp -> SignUpScreen(
                onBack = { destination = Destination.Welcome },
                onSubmit = viewModel::signUp,
                onLogIn = { destination = Destination.LogIn },
                submitting = submitting,
                errorMessage = error,
            )

            Destination.LogIn -> LogInScreen(
                onBack = { destination = Destination.Welcome },
                onSubmit = viewModel::signIn,
                onForgotPassword = {
                    // Figma 33-35 exist as designs and not yet as screens.
                    // Saying so beats a link that appears to do nothing.
                    Toast.makeText(
                        context,
                        "Password reset is designed but not built yet.",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onCreateAccount = { destination = Destination.SignUp },
                submitting = submitting,
                errorMessage = error,
            )
        }
    }
}
