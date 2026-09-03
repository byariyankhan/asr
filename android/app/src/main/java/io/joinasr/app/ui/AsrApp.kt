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
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.enforcement.PactViewModel
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.ui.screens.AboutYouScreen
import io.joinasr.app.ui.screens.BlockingDisclosureScreen
import io.joinasr.app.ui.screens.ChooseAppsScreen
import io.joinasr.app.ui.screens.DailyLimitsScreen
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

/**
 * The setup steps that come after an account exists.
 *
 * The design numbers six of them, and the screens themselves say so in their
 * eyebrows: duration, usage access, choose apps, daily limits, witnesses,
 * protection. Four exist so far. The numbering is left alone rather than
 * renumbered to match what is built, because it is what the finished flow
 * says and renumbering twice is how the labels end up disagreeing with the
 * screens.
 */
private sealed interface SetupStep {
    data object UsageAccess : SetupStep
    data object ChooseApps : SetupStep
    data object DailyLimits : SetupStep
    data object Protection : SetupStep
    data object BlockingDisclosure : SetupStep
}

@Composable
fun AsrApp(
    viewModel: SessionViewModel = viewModel(),
    pactViewModel: PactViewModel = viewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val pact by pactViewModel.pact.collectAsStateWithLifecycle()
    val submitting by viewModel.submitting.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var destination by remember { mutableStateOf<Destination>(Destination.Welcome) }
    var setupStep by remember { mutableStateOf<SetupStep>(SetupStep.UsageAccess) }
    // Held here, not in storage. A half-made challenge is not something the
    // app should remember: it is committed on the review screen, with its
    // limits and its witnesses, or it never existed.
    // Only for the length of the setup flow: the limits screen needs the
    // apps the picker chose. Once the pact is committed it is read back from
    // storage, never from here.
    var chosenApps by remember { mutableStateOf(emptyList<AppEntry>()) }
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
                        onGranted = { setupStep = SetupStep.ChooseApps },
                    )

                    // Figma 06.
                    SetupStep.ChooseApps -> ChooseAppsScreen(
                        onBack = { setupStep = SetupStep.UsageAccess },
                        onContinue = { apps ->
                            chosenApps = apps
                            setupStep = SetupStep.DailyLimits
                        },
                    )

                    // Figma 07. Screen 08 (witnesses) belongs between this
                    // and protection; until it exists the two run together.
                    SetupStep.DailyLimits -> DailyLimitsScreen(
                        apps = chosenApps,
                        onBack = { setupStep = SetupStep.ChooseApps },
                        onContinue = { limits ->
                            // The one place a challenge is committed. From
                            // here it survives the app being killed, which is
                            // the whole difference between a form and a pact.
                            pactViewModel.commit(chosenApps, limits)
                            setupStep = SetupStep.Protection
                        },
                    )

                    SetupStep.Protection -> ProtectionScreen(
                        onBack = { setupStep = SetupStep.DailyLimits },
                        // Figma 10, which explains what the overlay reads
                        // before Settings opens rather than after.
                        onReviewBlocking = { setupStep = SetupStep.BlockingDisclosure },
                        onContinue = { setupDone = true },
                    )

                    SetupStep.BlockingDisclosure -> BlockingDisclosureScreen(
                        onBack = { setupStep = SetupStep.Protection },
                        onGranted = { setupStep = SetupStep.Protection },
                        // "Not now" returns to the list rather than moving on:
                        // the grant is required, and the screen that says so
                        // is the one to come back to.
                        onSkip = { setupStep = SetupStep.Protection },
                    )
                }
            } else {
                SignedInScreen(
                    me = current.me,
                    pact = pact,
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
