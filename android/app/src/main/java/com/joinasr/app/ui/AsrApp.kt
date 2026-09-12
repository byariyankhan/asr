package com.joinasr.app.ui

import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.joinasr.app.apps.AppEntry
import com.joinasr.app.challenge.ChallengeDuration
import com.joinasr.app.enforcement.EnforcementService
import com.joinasr.app.enforcement.PactState
import com.joinasr.app.PendingInvite
import com.joinasr.app.permissions.PermissionState
import com.joinasr.app.permissions.Permissions
import com.joinasr.app.earn.EarnRules
import com.joinasr.app.earn.RideService
import com.joinasr.app.earn.cameraSpec
import com.joinasr.app.earn.earnOptions
import com.joinasr.app.earn.EarnViewModel
import com.joinasr.app.enforcement.PactViewModel
import com.joinasr.app.ui.components.AsrBottomNav
import com.joinasr.app.ui.components.AsrTab
import com.joinasr.app.ui.screens.AboutYouScreen
import com.joinasr.app.ui.screens.AddWitnessesScreen
import com.joinasr.app.ui.screens.BackgroundActivityScreen
import com.joinasr.app.ui.screens.BlockingDisclosureScreen
import com.joinasr.app.ui.screens.ChallengeStartedScreen
import com.joinasr.app.ui.screens.ChallengeDurationScreen
import com.joinasr.app.ui.screens.ActivityProgressScreen
import com.joinasr.app.ui.screens.ActivityTrackingScreen
import com.joinasr.app.ui.screens.CameraAccessScreen
import com.joinasr.app.ui.screens.CameraActivityScreen
import com.joinasr.app.challenge.ChallengeProgress
import com.joinasr.app.ui.screens.ChallengeEndedScreen
import com.joinasr.app.ui.screens.GiveUpScreen
import com.joinasr.app.ui.screens.ChooseActivityScreen
import com.joinasr.app.ui.screens.CircleScreen
import com.joinasr.app.ui.screens.CircleTab
import com.joinasr.app.ui.screens.AddAppScreen
import com.joinasr.app.ui.screens.ChooseAppsScreen
import com.joinasr.app.ui.screens.CheckEmailScreen
import com.joinasr.app.ui.screens.DailyLimitsScreen
import com.joinasr.app.ui.screens.DashboardScreen
import com.joinasr.app.ui.screens.DeleteAccountScreen
import com.joinasr.app.ui.screens.EarnedScreen
import com.joinasr.app.ui.screens.ForgotPasswordScreen
import com.joinasr.app.ui.screens.HelpAndSupportScreen
import com.joinasr.app.legal.LegalTexts
import com.joinasr.app.ui.screens.LegalScreen
import com.joinasr.app.ui.screens.LocationAccessScreen
import com.joinasr.app.ui.screens.LogInScreen
import com.joinasr.app.ui.screens.NotificationsScreen
import com.joinasr.app.ui.screens.PersonDetailScreen
import com.joinasr.app.ui.screens.PersonalDetailsScreen
import com.joinasr.app.ui.screens.ProfileDestination
import com.joinasr.app.ui.screens.ProfileScreen
import com.joinasr.app.ui.screens.ProgressScreen
import com.joinasr.app.ui.screens.ResetPasswordScreen
import com.joinasr.app.ui.screens.ReviewScreen
import com.joinasr.app.ui.screens.ProtectionLostScreen
import com.joinasr.app.ui.screens.ReactScreen
import com.joinasr.app.ui.screens.ProtectionScreen
import com.joinasr.app.ui.screens.SecurityScreen
import com.joinasr.app.ui.screens.SignUpScreen
import com.joinasr.app.ui.screens.UsageAccessScreen
import com.joinasr.app.ui.screens.WelcomeScreen
import com.joinasr.app.ui.screens.WitnessInviteScreen
import com.joinasr.app.DeepLink
import com.joinasr.app.witness.WitnessViewModel
import com.joinasr.app.ui.theme.AsrColors

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
    data object ForgotPassword : Destination

    /** Figma 34. Carries the address so the screen can name it. */
    data class CheckEmail(val email: String) : Destination

    /**
     * Figma 35. Carries both halves of what the reset needs: the code is
     * sent back with the new password in one request, and the address says
     * whose password it is.
     */
    data class NewPassword(val email: String, val code: String) : Destination
}

/**
 * How the signed-out screen survives a rotation: a tag, and the address for
 * the one destination that carries one. Everything in this file that says
 * where the person is used to be plain `remember`, which Android throws
 * away with the activity on every rotation, font-size change and split-
 * screen resize -- so turning the phone in the middle of setup dropped the
 * person on the dashboard with every choice gone.
 */
private val DestinationSaver = listSaver<Destination, String>(
    save = { where ->
        when (where) {
            Destination.Welcome -> listOf("welcome")
            Destination.SignUp -> listOf("sign_up")
            Destination.LogIn -> listOf("log_in")
            Destination.ForgotPassword -> listOf("forgot")
            is Destination.CheckEmail -> listOf("check_email", where.email)
            is Destination.NewPassword -> listOf("new_password", where.email, where.code)
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            "sign_up" -> Destination.SignUp
            "log_in" -> Destination.LogIn
            "forgot" -> Destination.ForgotPassword
            "check_email" -> Destination.CheckEmail(saved.getOrElse(1) { "" })
            "new_password" -> Destination.NewPassword(
                saved.getOrElse(1) { "" },
                saved.getOrElse(2) { "" },
            )
            else -> Destination.Welcome
        }
    },
)

/** The apps chosen during setup, flattened to strings for the saved state. */
private val AppEntriesSaver = listSaver<List<AppEntry>, String>(
    save = { apps -> apps.flatMap { listOf(it.packageName, it.label) } },
    restore = { flat -> flat.chunked(2).map { AppEntry(it[0], it[1]) } },
)

/** The limits chosen during setup: package, minutes, package, minutes. */
private val LimitsSaver = listSaver<Map<String, Int>, Any>(
    save = { limits -> limits.flatMap { (packageName, minutes) -> listOf(packageName, minutes) } },
    restore = { flat -> flat.chunked(2).associate { it[0] as String to it[1] as Int } },
)

/**
 * The setup steps that come after an account exists.
 *
 * The design numbers six of them, and the screens say so in their eyebrows:
 * duration, usage access, choose apps, daily limits, witnesses, protection.
 * All six exist.
 *
 * Setup ends when the pact is committed, not on a flag. That is why the
 * commit happens on the last step rather than partway through: a challenge
 * exists once everything it needs exists, and until then there is nothing to
 * enforce and nothing to come back to.
 */
/**
 * The setup flow, which no longer includes witnesses.
 *
 * Inviting them was step five and the pact was written at step eight, so
 * every invitation was issued against a challenge that did not exist yet.
 * Send the invitations, abandon setup, and the links still worked — which is
 * how somebody ended up listed as a witness to nothing. A witness belongs to
 * a challenge, so the challenge is created first and they are invited to it
 * on the way out.
 */
private enum class SetupStep {
    Duration,
    UsageAccess,
    ChooseApps,
    DailyLimits,
    Protection,
    BlockingDisclosure,
    /** Battery optimisation and the manufacturer's switches, from the Protection step. */
    Background,
    Review,
}

/**
 * The rows on the profile screen that lead somewhere. The rest are drawn and
 * not pressable, which somebody can see before they press rather than after.
 */
private val ProfileRoutes = setOf(
    ProfileDestination.PersonalDetails,
    ProfileDestination.EmailAndPassword,
    ProfileDestination.Permissions,
    ProfileDestination.HelpAndSupport,
    ProfileDestination.PrivacyPolicy,
    ProfileDestination.TermsOfService,
)

@Composable
fun AsrApp(
    /** The link the app was opened by, if any. */
    link: DeepLink? = null,
    onLinkHandled: () -> Unit = {},
    viewModel: SessionViewModel = viewModel(),
    pactViewModel: PactViewModel = viewModel(),
    witnessViewModel: WitnessViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel(),
    inboxViewModel: InboxViewModel = viewModel(),
    earnViewModel: EarnViewModel = viewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val pactState by pactViewModel.state.collectAsStateWithLifecycle()
    val restoringPact by pactViewModel.restoring.collectAsStateWithLifecycle()
    val endedUnseen by pactViewModel.endedUnseen.collectAsStateWithLifecycle()
    val addAppBusy by pactViewModel.addingApp.collectAsStateWithLifecycle()
    val addAppError by pactViewModel.addAppError.collectAsStateWithLifecycle()
    val appAdded by pactViewModel.appAdded.collectAsStateWithLifecycle()
    val witnesses by witnessViewModel.witnesses.collectAsStateWithLifecycle()
    val pendingShare by witnessViewModel.pendingShare.collectAsStateWithLifecycle()
    val inviting by witnessViewModel.inviting.collectAsStateWithLifecycle()
    val witnessError by witnessViewModel.error.collectAsStateWithLifecycle()
    val supporting by witnessViewModel.supporting.collectAsStateWithLifecycle()
    val witnessProgress by witnessViewModel.progress.collectAsStateWithLifecycle()
    val reactions by witnessViewModel.reactions.collectAsStateWithLifecycle()
    val knownWitnesses by witnessViewModel.knownWitnesses.collectAsStateWithLifecycle()
    val invite by witnessViewModel.invite.collectAsStateWithLifecycle()
    val inviteError by witnessViewModel.inviteError.collectAsStateWithLifecycle()
    val inviteBusy by witnessViewModel.inviteBusy.collectAsStateWithLifecycle()
    val inviteAnswered by witnessViewModel.inviteAnswered.collectAsStateWithLifecycle()
    val inboxItems by inboxViewModel.items.collectAsStateWithLifecycle()
    val unread by inboxViewModel.unread.collectAsStateWithLifecycle()
    val inboxLoaded by inboxViewModel.loaded.collectAsStateWithLifecycle()
    val activeActivity by earnViewModel.active.collectAsStateWithLifecycle()
    val earnedToday by earnViewModel.earned.collectAsStateWithLifecycle()
    val justEarned by earnViewModel.justEarned.collectAsStateWithLifecycle()
    val earnError by earnViewModel.error.collectAsStateWithLifecycle()
    val submitting by viewModel.submitting.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val accountBusy by accountViewModel.busy.collectAsStateWithLifecycle()
    val accountError by accountViewModel.error.collectAsStateWithLifecycle()
    val accountNotice by accountViewModel.notice.collectAsStateWithLifecycle()
    val accountDeleted by accountViewModel.deleted.collectAsStateWithLifecycle()
    val emailChanged by accountViewModel.emailChanged.collectAsStateWithLifecycle()
    val passwordReset by accountViewModel.reset.collectAsStateWithLifecycle()
    val resetEmailSentTo by accountViewModel.resetEmailSentTo.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // Saved state, all of it, for the reason DestinationSaver gives: where
    // the person is has to survive the activity being rebuilt under them.
    var destination by rememberSaveable(stateSaver = DestinationSaver) {
        mutableStateOf<Destination>(Destination.Welcome)
    }
    var setupStep by rememberSaveable { mutableStateOf(SetupStep.Duration) }
    var tab by rememberSaveable { mutableStateOf(AsrTab.Home) }
    // Where the Profile tab is: null is its own overview, anything else is a
    // screen stacked on top of it. One nullable value rather than a back
    // stack, for the same reason Destination is: three destinations do not
    // need a navigation library.
    var profileRoute by rememberSaveable { mutableStateOf<ProfileDestination?>(null) }
    var addingWitness by rememberSaveable { mutableStateOf(false) }

    /**
     * Whether the way out is open on screen.
     *
     * Not remembered across a kill on purpose, unlike the witness gate. That
     * gate holds because a challenge with nobody watching it must not be
     * left running; this is a question somebody was in the middle of being
     * asked, and coming back to the app should not be coming back to it.
     */
    var givingUp by remember { mutableStateOf(false) }
    // Figma 16's two halves, and Figma 17 stacked on the second of them.
    var circleTab by rememberSaveable { mutableStateOf(CircleTab.Mine) }
    // The open person by id, found again in the list after a rotation.
    var openPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    val openPerson = openPersonId?.let { id -> supporting.firstOrNull { it.id == id } }
    // Figma 31, which sits on top of Personal Details rather than in the
    // profile's row list: the design puts it at the bottom of that screen.
    var deletingAccount by rememberSaveable { mutableStateOf(false) }
    // Figma 27, opened from the NOT PROTECTED pill on the dashboard.
    var showingProtectionLost by rememberSaveable { mutableStateOf(false) }
    // Figma 19, opened from the bell.
    var showingNotifications by rememberSaveable { mutableStateOf(false) }
    // Figma 21-24. The package the block screen sent, held for as long as
    // the person is inside the earn flow.
    var earningFor by rememberSaveable { mutableStateOf<String?>(null) }
    /** The picker that brings one more app under a limit, over the dashboard. */
    var addingApp by rememberSaveable { mutableStateOf(false) }
    /**
     * True when the person has stepped back from Figma 23 while the walk or
     * the focus session goes on. The activity keeps running; the screen is
     * simply not in front. Back and the chevron set it; a new activity, or
     * the running one ending, clears it; the Earn button reopens it.
     */
    var activityMinimised by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(activeActivity?.id) { activityMinimised = false }
    // True while Figma 22 is showing, between choosing a walk and the grant.
    var askingForSteps by remember { mutableStateOf(false) }
    // Set when the grant comes back yes, so the walk (or run, or climb:
    // stepsWanted says which) starts without the person having to press
    // the same row twice.
    var stepsOnceGranted by remember { mutableStateOf(false) }
    var stepsWanted by remember { mutableStateOf(EarnRules.WALK) }

    // Re-read on resume as well, because a ride revoked in Settings and
    // re-granted there arrives through no callback.
    var stepsGranted by remember { mutableStateOf(Permissions.hasActivityRecognition(context)) }
    val askForSteps = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        askingForSteps = false
        stepsGranted = granted
        stepsOnceGranted = granted
    }
    // The same pair for the camera, between choosing a camera activity and
    // the grant; cameraWanted is which one, since four share the lens.
    var askingForCamera by remember { mutableStateOf(false) }
    var cameraWanted by remember { mutableStateOf<String?>(null) }
    var cameraOnceGranted by remember { mutableStateOf(false) }
    // A refusal is said, not swallowed: the chooser names it, and once
    // Android has stopped showing the dialog (two refusals) the camera
    // screen's button goes to the app's page in Settings instead, the way
    // notifications already do. A silent nothing is not an answer.
    var cameraRefused by remember { mutableStateOf(false) }
    var cameraDeniedForGood by remember { mutableStateOf(false) }
    // Read into state rather than asked on every composition, because a
    // grant made on the app's Settings page arrives through no callback:
    // the resume below re-reads it, and the screens follow.
    var cameraGranted by remember { mutableStateOf(Permissions.hasCamera(context)) }
    val askForCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        askingForCamera = false
        cameraOnceGranted = granted
        cameraRefused = !granted
        if (!granted) {
            val activity = context as? Activity
            cameraDeniedForGood = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        } else {
            cameraDeniedForGood = false
        }
    }
    val allowCamera: () -> Unit = {
        if (cameraDeniedForGood) {
            runCatching { context.startActivity(Permissions.appDetailsIntent(context)) }
        } else {
            askForCamera.launch(Manifest.permission.CAMERA)
        }
    }
    // And the same again for location, between choosing cycling and the
    // grant. Precise location is what a ride needs; Android 12 lets the
    // person grant the approximate half alone, which reads here as a
    // refusal with the Settings page as the way to the precise half.
    var askingForLocation by remember { mutableStateOf(false) }
    var rideOnceGranted by remember { mutableStateOf(false) }
    var locationRefused by remember { mutableStateOf(false) }
    var locationDeniedForGood by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(Permissions.hasPreciseLocation(context)) }
    val askForLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        askingForLocation = false
        locationGranted = granted
        rideOnceGranted = granted
        locationRefused = !granted
        if (!granted) {
            val activity = context as? Activity
            locationDeniedForGood = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            locationDeniedForGood = false
        }
    }
    val allowLocation: () -> Unit = {
        if (locationDeniedForGood) {
            runCatching { context.startActivity(Permissions.appDetailsIntent(context)) }
        } else {
            askForLocation.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    // Notifications, asked of a witness rather than of everybody at launch.
    // Somebody who has just agreed to be told when a friend breaks a pact
    // has said yes to the only thing this permission is for; asking them at
    // that moment is the difference between a grant and a reflex refusal.
    val askForNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    // From the card on the Notifications screen. A refusal there -- or a
    // dialog Android no longer shows after two of them -- goes straight on
    // to the app's own notification settings, because the person pressed a
    // button that said "turn on" and a silent nothing is not an answer.
    val askForNotificationsOrSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            runCatching { context.startActivity(Permissions.appNotificationSettingsIntent(context)) }
        }
    }
    // Typed, because its two branches would otherwise infer to () -> Any:
    // one ends in a launch, the other in a runCatching.
    val turnOnNotifications: () -> Unit = {
        if (Permissions.notificationsAreRequestable && !Permissions.hasNotifications(context)) {
            askForNotificationsOrSettings.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching { context.startActivity(Permissions.appNotificationSettingsIntent(context)) }
        }
    }
    // Figma 25, opened from a notification about somebody else. By id, so
    // it is still open after a rotation; gone if the inbox no longer has it.
    var reactingToId by rememberSaveable { mutableStateOf<String?>(null) }
    val reactingTo = reactingToId?.let { id -> inboxItems.firstOrNull { it.id == id } }
    // Figma 18. The code from a witness link, held until it is answered.
    var inviteCode by remember { mutableStateOf<String?>(null) }
    // True while somebody who opened an invite is going through sign-up.
    // The code is kept the whole time, so they land back on the invitation
    // with an account rather than on an empty dashboard wondering where the
    // link went.
    var inviteDeferred by rememberSaveable { mutableStateOf(false) }
    // True between committing a pact and pressing "Go to dashboard" on
    // Figma 12. Not written to disk: it is a moment, not a state of the
    // challenge, and a person who closes the app during it has still
    // started. It does survive the activity being rebuilt, so turning the
    // phone on that screen does not skip it.
    var justStarted by rememberSaveable { mutableStateOf(false) }
    /** Whether Continue has been pressed on the witness screen since this
     *  challenge started. Enabled only once an invitation has gone out. */
    var witnessesOffered by rememberSaveable { mutableStateOf(false) }

    /**
     * Whether the person is inside the setup flow.
     *
     * This used to be implied by having no pact, which put every new account
     * straight into six screens of permissions and app pickers. That was
     * wrong for the half of this product's users who never run a challenge
     * at all: somebody invited to witness a friend signed up, and the first
     * thing the app did was demand usage access to enforce limits they never
     * asked for. Signing in now lands on the dashboard, and setup is entered
     * from a button on it.
     *
     * Not written to disk. Backing out of the first step leaves nothing
     * behind, which is the point: nothing is committed until the review
     * screen. It is saved with the activity, though, along with the choices
     * below: rotating the phone on step four used to be the same as backing
     * out of all four.
     */
    var startingChallenge by rememberSaveable { mutableStateOf(false) }

    /**
     * The two grants that make a challenge mean anything, re-read whenever
     * the app comes back to the front.
     *
     * They are granted per install and can be taken away in Settings at any
     * moment, so this is a fact about right now and not something to
     * remember. A challenge is not allowed to run behind a screen that says
     * it is protected while neither of these is on.
     */
    var protection by remember { mutableStateOf(PermissionState.read(context)) }
    // Nothing tells an app that a Settings toggle flipped, so the only
    // honest moment to look is when it comes back to the front -- which is
    // exactly the moment somebody returns from granting one.
    LifecycleResumeEffect(Unit) {
        protection = PermissionState.read(context)
        cameraGranted = Permissions.hasCamera(context)
        if (cameraGranted) {
            // Back from Settings with the camera allowed: the refusal is
            // over, and the activity chosen before the trip starts now.
            cameraRefused = false
            cameraDeniedForGood = false
            if (askingForCamera) {
                askingForCamera = false
                cameraOnceGranted = true
            }
        }
        stepsGranted = Permissions.hasActivityRecognition(context)
        locationGranted = Permissions.hasPreciseLocation(context)
        if (locationGranted) {
            locationRefused = false
            locationDeniedForGood = false
            if (askingForLocation) {
                askingForLocation = false
                rideOnceGranted = true
            }
        }
        onPauseOrDispose {}
    }

    // Held here and nowhere else, for the length of the setup flow only. A
    // half-made challenge is not something the app should remember: it is
    // committed whole on the last step, or it never existed. After that it
    // is read back from storage, never from here.
    var chosenDays by rememberSaveable { mutableIntStateOf(ChallengeDuration.DEFAULT_DAYS) }
    var chosenApps by rememberSaveable(stateSaver = AppEntriesSaver) { mutableStateOf(emptyList<AppEntry>()) }
    var chosenLimits by rememberSaveable(stateSaver = LimitsSaver) { mutableStateOf(emptyMap<String, Int>()) }

    // The keep-it-running screen, opened from the dashboard's warning when
    // the loop is not alive. Not a tab and not setup: a repair, from where
    // the damage shows.
    var fixingProtection by rememberSaveable { mutableStateOf(false) }

    // The password just submitted, kept only until the answer comes back
    // and never saved with the activity. It is what signs the person in
    // afterwards without asking them to type it again.
    var resetSubmitted by remember { mutableStateOf<String?>(null) }

    // The one place the loop is started from inside the app. Starting an
    // already-running service costs one onStartCommand, and the service
    // stops itself when there is no pact, so there is nothing to guard.
    LaunchedEffect(pactState) {
        if (pactState is PactState.Active) EnforcementService.start(context)
        // A challenge can also go the other way: somebody moved it to
        // another phone, the loop here stood down and cleared it. Asking
        // again is what turns that into "it is running over there" rather
        // than into an offer to start a second one the server would refuse.
        if (pactState is PactState.None && session is Session.SignedIn) {
            pactViewModel.restoreFromServer()
        }
    }

    // Moving between the forms drops whatever the last one was refused for.
    // An error about a password left standing over a different screen reads
    // as a new failure.
    LaunchedEffect(destination) {
        viewModel.clearError()
        accountViewModel.clear()
    }
    LaunchedEffect(profileRoute, deletingAccount) { accountViewModel.clear() }

    // A link takes precedence over whatever screen was showing, because
    // opening one is the person saying where they want to be.
    LaunchedEffect(link) {
        when (val opened = link) {
            null -> Unit
            is DeepLink.Invite -> {
                inviteCode = opened.code
                // Accepting needs an account, and creating one means leaving
                // for an email app, from where Android is free to kill this
                // process. On disk it survives that; in a composable it does
                // not, and it would be lost at the exact point where the
                // work is done and one tap is left.
                PendingInvite.remember(context, opened.code)
                witnessViewModel.openInvite(opened.code)
                onLinkHandled()
            }
            is DeepLink.Earn -> {
                // One activity at a time, as on the dashboard: a set put
                // aside with the back chevron comes back rather than a
                // chooser whose rows would do nothing.
                if (activeActivity != null) {
                    activityMinimised = false
                } else {
                    earningFor = opened.packageName
                }
                tab = AsrTab.Home
                onLinkHandled()
            }
        }
    }

    // The app is in the challenge: back to the dashboard, where its row
    // now is, with one line saying so. The loop is already measuring it.
    LaunchedEffect(appAdded) {
        val label = appAdded ?: return@LaunchedEffect
        addingApp = false
        pactViewModel.acknowledgeAppAdded()
        Toast.makeText(context, "$label is in your challenge.", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(stepsOnceGranted, earningFor, pactState) {
        if (!stepsOnceGranted) return@LaunchedEffect
        stepsOnceGranted = false
        // A ride reads steps too (to tell a run from a ride), so it asks
        // for steps first and location second; a ride already running
        // that lost the step permission wants its service back.
        if (stepsWanted == EarnRules.RIDE) {
            if (!locationGranted) {
                askingForLocation = true
                return@LaunchedEffect
            }
            if (activeActivity?.isRide == true) {
                RideService.start(context)
                return@LaunchedEffect
            }
        }
        val pact = (pactState as? PactState.Active)?.pact ?: return@LaunchedEffect
        val app = earningFor?.let { pact.appFor(it) } ?: return@LaunchedEffect
        earnViewModel.start(pact, app, stepsWanted)
    }
    // A ride reopened with both permissions in hand gets its service
    // back: the service stops itself after half an hour of nothing, or
    // Android may have killed it, and the screen is the moment to resume.
    // Starting a service that is already running is a no-op.
    LaunchedEffect(activeActivity?.id, activityMinimised, stepsGranted, locationGranted) {
        val ride = activeActivity?.takeIf { it.isRide } ?: return@LaunchedEffect
        if (activityMinimised || !stepsGranted || !locationGranted) return@LaunchedEffect
        RideService.start(context)
    }
    LaunchedEffect(cameraOnceGranted, earningFor, pactState) {
        if (!cameraOnceGranted) return@LaunchedEffect
        cameraOnceGranted = false
        val type = cameraWanted ?: return@LaunchedEffect
        val pact = (pactState as? PactState.Active)?.pact ?: return@LaunchedEffect
        val app = earningFor?.let { pact.appFor(it) } ?: return@LaunchedEffect
        earnViewModel.start(pact, app, type)
    }
    LaunchedEffect(rideOnceGranted, earningFor, pactState) {
        if (!rideOnceGranted) return@LaunchedEffect
        rideOnceGranted = false
        // A ride already running (the permission revoked and re-granted
        // mid-way) wants its service back, not a second ride.
        if (activeActivity?.isRide == true) {
            RideService.start(context)
            return@LaunchedEffect
        }
        val pact = (pactState as? PactState.Active)?.pact ?: return@LaunchedEffect
        val app = earningFor?.let { pact.appFor(it) } ?: return@LaunchedEffect
        earnViewModel.start(pact, app, EarnRules.RIDE)
    }

    // What actually drives an activity forward.
    //
    // A walk reads the step counter, which is a running total the sensor hub
    // keeps whether or not this app is alive -- so listening only while the
    // screen is up loses nothing, and the difference from the baseline is
    // still right when somebody comes back.
    //
    // The camera activities are counted on the camera screen itself
    // (CameraActivityScreen), which is the only place the camera is open.
    //
    // Phone-free focus sessions, runs and climbs are owned by
    // EnforcementService, even when this UI is stopped or destroyed.
    // Compose must never award their time.
    LaunchedEffect(activeActivity?.id) {
        val running = activeActivity ?: return@LaunchedEffect
        if (running.isWalk) {
            earnViewModel.steps.readings().collect { earnViewModel.onSteps(it) }
        }
    }

    // A link with nothing left to decide.
    //
    // Their own, opened on their own phone -- testing it, or tapping it in
    // the thread they just shared it to -- or one they already accepted,
    // which happens because the link stays open for everybody else it was
    // sent to. Either way there is no question to put to them, and the
    // circle is where they were going.
    val settled = invite?.own == true || invite?.already == true
    LaunchedEffect(settled) {
        if (!settled) return@LaunchedEffect
        inviteCode = null
        inviteDeferred = false
        PendingInvite.clear(context)
        // Their own witnesses if it was their link; the people they support
        // if they are one of somebody else's.
        val mine = invite?.own == true
        witnessViewModel.clearInvite()
        tab = AsrTab.Witnesses
        circleTab = if (mine) CircleTab.Mine else CircleTab.Supporting
    }

    // Answered, so the screen has done its job. Accepting leaves the person
    // on their own app rather than on a confirmation: the list they have
    // just joined is the confirmation.
    LaunchedEffect(inviteAnswered) {
        if (!inviteAnswered) return@LaunchedEffect
        inviteCode = null
        PendingInvite.clear(context)
        witnessViewModel.clearInvite()
        tab = AsrTab.Witnesses
        circleTab = CircleTab.Supporting
        if (Permissions.notificationsAreRequestable && !Permissions.hasNotifications(context)) {
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Deletion is accepted by the server, so the token is spent: signing out
    // here rather than inside the account model keeps clearing it in one
    // place, which is the only way it reliably happens at all.
    // The address lives in the session's copy of the profile, and the
    // account screen changed it on the server: re-read, so the screen that
    // shows the address shows the new one.
    LaunchedEffect(emailChanged) {
        if (!emailChanged) return@LaunchedEffect
        accountViewModel.consumeEmailChanged()
        viewModel.refresh()
    }

    LaunchedEffect(accountDeleted) {
        if (!accountDeleted) return@LaunchedEffect
        accountViewModel.consumeDeleted()
        deletingAccount = false
        profileRoute = null
        tab = AsrTab.Home
        destination = Destination.Welcome
        viewModel.signOut()
    }

    // The code has been spent and the password is the new one. The whole
    // flow starts at the log-in screen, so nobody is signed in here and
    // there is no session to lose; what there is, is an address and a
    // password that are known to work together, so the person is signed
    // straight in rather than shown a form asking for what they just typed.
    // If the sign-in somehow fails they are on log in, which is where the
    // failure belongs.
    LaunchedEffect(passwordReset) {
        if (!passwordReset) return@LaunchedEffect
        accountViewModel.consumeReset()
        val password = resetSubmitted
        resetSubmitted = null
        val email = (destination as? Destination.NewPassword)?.email
        destination = Destination.LogIn
        if (email != null && password != null) viewModel.signIn(email, password)
    }

    // Figma 34 says the code was sent as a fact, so it is reached when the
    // server has taken the request and not when the button was pressed. A
    // request for a new code from that screen lands here too and leaves the
    // destination as it is, which keeps the notice on screen and whatever
    // has been typed into the boxes.
    LaunchedEffect(resetEmailSentTo) {
        val sentTo = resetEmailSentTo ?: return@LaunchedEffect
        accountViewModel.consumeResetEmailSent()
        destination = Destination.CheckEmail(sentTo)
    }

    // An invitation from before this launch: one left unanswered across a
    // process death, or — on the first launch after installing from Play —
    // the one whose link sent them to the listing. Either way the app opens
    // on what they were doing rather than on a welcome screen.
    LaunchedEffect(Unit) {
        if (inviteCode != null) return@LaunchedEffect
        val stored = PendingInvite.load(context) ?: return@LaunchedEffect
        inviteCode = stored
        witnessViewModel.openInvite(stored)
    }

    // Read into a local so the branch below can smart-cast it. A delegated
    // property cannot be, and `!!` on the thing that tells somebody their
    // challenge broke is not where to be casual.
    val ended = endedUnseen
    val code = inviteCode
    val signedIn = session is Session.SignedIn

    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        inviteDeferred = false
        // The auth screens are done with. Left as it was, the sign-in screen
        // stayed the "destination" for the whole session and its back
        // handler ate the first back press on the dashboard.
        destination = Destination.Welcome
        // A challenge belongs to the person, not to the install. If this
        // phone has none and the account does, this is where it comes back.
        pactViewModel.restoreFromServer()
        // And so do its witnesses. Without this the list was only ever asked
        // for by opening the Witnesses tab, so a phone that had just signed
        // in believed nobody was watching -- and the gate below acts on
        // exactly that belief.
        witnessViewModel.refresh()
    }

    // Sign-up has no name field, so the email's local part stands in until
    // About You. That placeholder was reaching the other person: the invite
    // screen took priority over About You, so a witness could accept while
    // still called "ariyanfiles", and the notification the inviter read said
    // so. The invitation waits; it is still pending after.
    val needsProfile = (session as? Session.SignedIn)?.me?.profileComplete == false

    // ---- System back ----
    //
    // One place decides what back does, and it mirrors what is on screen:
    // top of the stack first, in the order the screens are drawn below, and
    // doing what that screen's own chevron does. Null means nothing of ours
    // is open: the press falls through to the system, which closes the app
    // -- the right answer on a root screen, and the only honest one on a
    // gate drawn without an exit.
    //
    // Two handlers because they are two worlds. Signed out, back walks the
    // auth screens the way their chevrons do; signed in, it walks whatever
    // is stacked on the tabs. Each is enabled only in its own world, so a
    // `destination` left over from before sign-in can no longer swallow a
    // press on the dashboard -- which is what used to happen: the first
    // back did nothing, the second closed the app. And every setup step
    // after the first, the give-up question and the running-activity
    // screen used to ignore the button altogether.
    val invitationOpen = code != null && (signedIn || !inviteDeferred) && !needsProfile
    val earnAppNow = (pactState as? PactState.Active)?.pact?.let { pact -> earningFor?.let(pact::appFor) }
    // The chooser shows the earn error. Anywhere else -- a set put aside
    // with "Finish later" and stood down for running out of time, say --
    // the message would otherwise vanish with the set it explains.
    LaunchedEffect(earnError, earnAppNow) {
        val message = earnError ?: return@LaunchedEffect
        if (earnAppNow != null) return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        earnViewModel.clearError()
    }
    val backAction: (() -> Unit)? = when {
        invitationOpen -> ({
            inviteCode = null
            inviteDeferred = false
            PendingInvite.clear(context)
            witnessViewModel.clearInvite()
        })
        session !is Session.SignedIn -> null
        needsProfile -> ({
            destination = Destination.Welcome
            viewModel.signOut()
        })
        pactState is PactState.Loading || restoringPact -> null
        pactState is PactState.None && ended != null -> ({ pactViewModel.acknowledgeEnded() })
        startingChallenge && pactState is PactState.None -> when (setupStep) {
            SetupStep.Duration -> ({ startingChallenge = false })
            SetupStep.UsageAccess -> ({ setupStep = SetupStep.Duration })
            SetupStep.ChooseApps -> ({ setupStep = SetupStep.UsageAccess })
            SetupStep.DailyLimits -> ({ setupStep = SetupStep.ChooseApps })
            SetupStep.Protection -> ({ setupStep = SetupStep.DailyLimits })
            SetupStep.BlockingDisclosure, SetupStep.Background, SetupStep.Review ->
                ({ setupStep = SetupStep.Protection })
        }
        pactState is PactState.Active && (justStarted || knownWitnesses?.isEmpty() == true) ->
            // The witness gate holds until an invitation has gone out and is
            // drawn without an exit: back closes the app there, and the gate
            // is back on the next launch. Figma 12 is only a moment.
            if (witnesses.isEmpty() || !witnessesOffered) null else ({
                justStarted = false
                witnessesOffered = false
            })
        pactState is PactState.Active && !protection.requiredGranted -> null
        // Home, in the order the tab draws them.
        tab == AsrTab.Home && justEarned != null -> ({
            earnViewModel.acknowledgeEarned()
            earningFor = null
        })
        tab == AsrTab.Home && activeActivity != null && !activityMinimised -> ({
            activityMinimised = true
            earningFor = null
        })
        tab == AsrTab.Home && askingForSteps -> ({ askingForSteps = false })
        tab == AsrTab.Home && askingForCamera -> ({ askingForCamera = false })
        tab == AsrTab.Home && askingForLocation -> ({ askingForLocation = false })
        tab == AsrTab.Home && earnAppNow != null -> ({
            earningFor = null
            earnViewModel.clearError()
        })
        tab == AsrTab.Home && reactingTo != null -> ({ reactingToId = null })
        tab == AsrTab.Home && showingNotifications -> ({ showingNotifications = false })
        tab == AsrTab.Home && showingProtectionLost -> ({ showingProtectionLost = false })
        tab == AsrTab.Home && fixingProtection -> ({ fixingProtection = false })
        tab == AsrTab.Home && addingApp -> ({
            addingApp = false
            pactViewModel.clearAddAppError()
        })
        // Progress
        tab == AsrTab.Progress && givingUp -> ({ givingUp = false })
        // Witnesses
        tab == AsrTab.Witnesses && openPerson != null -> ({ openPersonId = null })
        tab == AsrTab.Witnesses && addingWitness -> ({ addingWitness = false })
        // Profile
        tab == AsrTab.Profile && deletingAccount -> ({ deletingAccount = false })
        tab == AsrTab.Profile && profileRoute != null -> ({ profileRoute = null })
        // Any other tab's root goes back to Home, which is what a bottom bar
        // implies and what every app with one does.
        tab != AsrTab.Home -> ({ tab = AsrTab.Home })
        else -> null
    }
    BackHandler(enabled = backAction != null) { backAction?.invoke() }

    // Signed out: the auth screens, walked the way their own chevrons walk
    // them. Somebody who got to sign-up from an invitation's Accept goes
    // back to the invitation, not to Welcome with the invitation hidden
    // behind it until the next launch.
    BackHandler(
        enabled = session is Session.SignedOut && !invitationOpen &&
            destination != Destination.Welcome,
    ) {
        if (inviteDeferred && code != null) {
            inviteDeferred = false
            destination = Destination.Welcome
        } else {
            destination = when (val where = destination) {
                Destination.ForgotPassword -> Destination.LogIn
                is Destination.CheckEmail -> Destination.ForgotPassword
                // Back to the code, not to the address: a wrong code is what
                // sends somebody back here, and the address was right.
                is Destination.NewPassword -> Destination.CheckEmail(where.email)
                else -> Destination.Welcome
            }
        }
    }


    if (code != null && (signedIn || !inviteDeferred) && !needsProfile) {
        // Figma 18, over everything. Opening the link is the person saying
        // where they want to be, and it works signed out because the person
        // being asked to vouch usually has no account yet.
        WitnessInviteScreen(
            invite = invite,
            errorMessage = inviteError,
            signedIn = signedIn,
            busy = inviteBusy,
            onBack = {
                inviteCode = null
                inviteDeferred = false
                PendingInvite.clear(context)
                witnessViewModel.clearInvite()
            },
            onAccept = {
                if (signedIn) {
                    witnessViewModel.answerInvite(code, accept = true)
                } else {
                    // The code survives sign-up; this only steps aside.
                    inviteDeferred = true
                    destination = Destination.SignUp
                }
            },
            onDecline = { witnessViewModel.answerInvite(code, accept = false) },
        )
    } else when (val current = session) {
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
                    initialFirstName = current.me.firstName.orEmpty(),
                    initialLastName = current.me.lastName.orEmpty(),
                    submitting = submitting,
                    errorMessage = error,
                )
            } else if (pactState is PactState.Loading || restoringPact) {
                // One read of a small file, and -- on a phone that has none
                // -- one request asking whether this account has a challenge
                // running somewhere else. Blank rather than a spinner, for
                // the same reason as above.
                //
                // `restoringPact` matters: "no pact on this phone" and "no
                // pact" are different answers, and offering to start a
                // challenge for the second it takes to find out would be
                // offering it to somebody who already has one.
                Box(Modifier.fillMaxSize().background(AsrColors.Background))
            } else if (pactState is PactState.None && ended != null) {
                // Figma 26. A challenge that ended is shown once, before
                // anything else: somebody whose pact broke overnight should
                // not open the app to a dashboard and have to work out what
                // happened from its absence.
                ChallengeEndedScreen(
                    outcome = ended,
                    onStartNew = {
                        setupStep = SetupStep.Duration
                        startingChallenge = true
                        pactViewModel.acknowledgeEnded()
                    },
                    onDismiss = pactViewModel::acknowledgeEnded,
                )
            } else if (startingChallenge && pactState is PactState.None) {
                when (setupStep) {
                    // Figma 04. Its frame has no chevron, drawn when setup
                    // was where everybody landed. It has one now, because
                    // there is a dashboard behind it to go back to.
                    SetupStep.Duration -> ChallengeDurationScreen(
                        onBack = { startingChallenge = false },
                        onContinue = { days ->
                            chosenDays = days
                            setupStep = SetupStep.UsageAccess
                        },
                    )

                    SetupStep.UsageAccess -> UsageAccessScreen(
                        onBack = { setupStep = SetupStep.Duration },
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
                            chosenLimits = limits
                            setupStep = SetupStep.Protection
                        },
                    )

                    // Figma 08. Invites go through Android's share sheet, so
                    // this step needs no server and works today.
                    SetupStep.Protection -> ProtectionScreen(
                        onBack = { setupStep = SetupStep.DailyLimits },
                        // Figma 10, which explains what the overlay reads
                        // before Settings opens rather than after.
                        onReviewBlocking = { setupStep = SetupStep.BlockingDisclosure },
                        onBackgroundActivity = { setupStep = SetupStep.Background },
                        onContinue = { setupStep = SetupStep.Review },
                    )

                    // Not in the Figma file: the two settings that stop a
                    // manufacturer's battery layer from killing the loop.
                    SetupStep.Background -> BackgroundActivityScreen(
                        onBack = { setupStep = SetupStep.Protection },
                        onDone = { setupStep = SetupStep.Protection },
                    )

                    // Figma 11. Everything chosen, in one place, before
                    // anything is written: a commitment nobody was shown in
                    // full is not one they agreed to.
                    SetupStep.Review -> ReviewScreen(
                        days = chosenDays,
                        apps = chosenApps,
                        limits = chosenLimits,
                        protectionReady = protection.requiredGranted,
                        onBack = { setupStep = SetupStep.Protection },
                        // The one place a challenge is committed. From here it
                        // survives the app being killed, which is the whole
                        // difference between a form and a pact.
                        onStart = {
                            pactViewModel.commit(chosenApps, chosenLimits, chosenDays)
                            justStarted = true
                            startingChallenge = false
                        },
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
            } else if (
                pactState is PactState.Active &&
                (justStarted || knownWitnesses?.isEmpty() == true)
            ) {
                // A challenge nobody is watching is a challenge in name only.
                // The pact is committed by now -- it has to be, or there is
                // nothing to invite anybody to -- so this is the one screen
                // between starting and using the app, and it does not let go
                // until an invitation has gone out.
                //
                // It has to be the server's answer and not this phone's copy.
                // The copy is read from this install's disk, so on a phone
                // that just took the challenge over it is empty -- and this
                // screen would demand a fresh invitation to a challenge
                // three people are already watching. Null is "not asked yet"
                // and stops nothing.
                val started = (pactState as PactState.Active).pact
                if (witnesses.isEmpty() || !witnessesOffered) {
                    // Figma 08, on the way out rather than on the way in.
                    // The challenge exists now, so the links it issues point
                    // at something real -- which is the whole reason it
                    // moved, and the reason it cannot come before Review.
                    AddWitnessesScreen(
                        challengeDays = started.durationDays,
                        witnesses = witnesses,
                        onBack = {},
                        onInvite = witnessViewModel::invite,
                        onContinue = { witnessesOffered = true },
                        pendingShare = pendingShare,
                        onShared = witnessViewModel::shared,
                        inviting = inviting,
                        errorMessage = witnessError,
                        showStepNumber = false,
                        showBack = false,
                    )
                } else {
                    // Figma 12.
                    ChallengeStartedScreen(
                        days = started.durationDays,
                        witnesses = witnesses,
                        protectionReady = protection.requiredGranted,
                        onContinue = {
                            justStarted = false
                            witnessesOffered = false
                        },
                    )
                }
            } else if (pactState is PactState.Active && !protection.requiredGranted) {
                // A running challenge and nothing able to enforce it. There
                // is no version of this worth showing a dashboard over: the
                // numbers would be honest and mean nothing, because no app
                // is being blocked and no limit can be measured.
                //
                // It is a gate rather than the banner it used to be because
                // of what the banner allowed. Revoke usage access and the
                // app went on drawing a challenge; sign in on a new phone
                // and the challenge arrived without the permissions, which
                // are per install. Both are the same hole, and both looked
                // from the outside like a perfect day -- no breaches,
                // because nothing was watching.
                //
                // Two hours of this and the witnesses are told in as many
                // words. The server counts that, not this screen: closing
                // the app has to not be a way out of it.
                ProtectionLostScreen(
                    onBack = {},
                    onDismiss = {},
                    dismissible = false,
                )
            } else {
                // Null when nothing is running, which every tab now handles.
                // The bar and its four screens are the app; a challenge is
                // something that happens inside it, not the price of entry.
                val activePact = (pactState as? PactState.Active)?.pact
                val signOut = {
                    if (activePact != null) {
                        // Signing out would leave the challenge with nothing
                        // enforcing it and no way to say so: the heartbeats
                        // need the session that just left. A day later the
                        // witnesses would be told the phone went dark, about
                        // somebody who pressed a button on the profile
                        // screen. The front door is Give up, on Progress.
                        Toast.makeText(
                            context,
                            "You have a challenge running. Finish it, or give it up " +
                                "from Progress, before signing out.",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        destination = Destination.Welcome
                        tab = AsrTab.Home
                        viewModel.signOut()
                    }
                }

                /**
                 * A tab button goes to that tab, from wherever you are.
                 *
                 * Each of the four has screens stacked on it -- a person's
                 * progress, the earn flow, Personal Details, the give-up
                 * question -- and pressing a tab only changed which of the
                 * four was drawn, so the stack you left was still there when
                 * you came back. Press Witnesses while reading somebody's
                 * progress, go to Profile, press Witnesses again: their
                 * progress, not your circle. The button did not do what it
                 * says.
                 *
                 * So every tab's own state goes back to its root first, for
                 * all four rather than only the one being opened -- what you
                 * left behind on the others is not somewhere you asked to
                 * return to either.
                 *
                 * What is deliberately not reset is anything that is not a
                 * tab: setup, the invite a link opened, and the screen a
                 * challenge that just ended is waiting on. The bar is not
                 * drawn during any of those.
                 */
                // `target`, not `destination`: that name is taken by the
                // signed-out flow above and shadowing it here would be a
                // trap for the next person editing this.
                val goToTab = { target: AsrTab ->
                    // Home
                    reactingToId = null
                    earningFor = null
                    earnViewModel.clearError()
                    addingApp = false
                    pactViewModel.clearAddAppError()
                    askingForSteps = false
                    stepsOnceGranted = false
                    askingForCamera = false
                    cameraOnceGranted = false
                    cameraRefused = false
                    askingForLocation = false
                    rideOnceGranted = false
                    locationRefused = false
                    showingNotifications = false
                    showingProtectionLost = false
                    fixingProtection = false
                    // A reward shown once. Coming back to Home tomorrow to be
                    // congratulated again for yesterday's walk is worse than
                    // not seeing it a second time.
                    earnViewModel.acknowledgeEarned()
                    // Progress
                    givingUp = false
                    // Witnesses
                    openPersonId = null
                    addingWitness = false
                    circleTab = CircleTab.Mine
                    // Profile
                    profileRoute = null
                    deletingAccount = false

                    tab = target
                }
                Column(Modifier.fillMaxSize().background(AsrColors.Background)) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            // Figma 13, 14, 15 and 28. One bar around four
                            // screens rather than a bar inside each of them:
                            // four copies would be four things to keep in
                            // agreement about which tab is selected.
                            AsrTab.Home -> {
                                val about = reactingTo
                                val running = activeActivity
                                val done = justEarned
                                val earnApp = activePact?.let { pact ->
                                    earningFor?.let(pact::appFor)
                                }
                                if (done != null) {
                                    // Figma 24.
                                    EarnedScreen(
                                        activity = done,
                                        earnedToday = earnedToday.forPackage(done.packageName),
                                        onUseNow = {
                                            earnViewModel.acknowledgeEarned()
                                            earningFor = null
                                            // The reward exists on the phone
                                            // already, so the app it is for
                                            // simply opens: the loop will not
                                            // block it again until the raised
                                            // allowance is spent.
                                            openApp(context, done.packageName)
                                        },
                                        onDismiss = {
                                            earnViewModel.acknowledgeEarned()
                                            earningFor = null
                                        },
                                    )
                                } else if (running != null && !activityMinimised && running.isCamera &&
                                    !cameraGranted
                                ) {
                                    // Revoked in Settings mid-set. Ask again
                                    // rather than open a camera that will
                                    // refuse; the activity and its count wait.
                                    CameraAccessScreen(
                                        spec = cameraSpec(running.type)!!,
                                        onBack = { activityMinimised = true },
                                        onAllow = allowCamera,
                                        onSkip = { activityMinimised = true },
                                        openSettings = cameraDeniedForGood,
                                    )
                                } else if (running != null && !activityMinimised && running.isRide &&
                                    !stepsGranted
                                ) {
                                    // The step permission revoked mid-ride: the
                                    // service has stopped itself; ask again.
                                    ActivityTrackingScreen(
                                        type = EarnRules.RIDE,
                                        onBack = { activityMinimised = true },
                                        onAllow = {
                                            stepsWanted = EarnRules.RIDE
                                            askForSteps.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                        },
                                        onSkip = { activityMinimised = true },
                                    )
                                } else if (running != null && !activityMinimised && running.isRide &&
                                    !locationGranted
                                ) {
                                    // Revoked in Settings mid-ride: the service has
                                    // stopped itself; ask again, and a grant restarts
                                    // it for the same ride.
                                    LocationAccessScreen(
                                        onBack = { activityMinimised = true },
                                        onAllow = allowLocation,
                                        onSkip = { activityMinimised = true },
                                        openSettings = locationDeniedForGood,
                                    )
                                } else if (running != null && !activityMinimised && running.isCamera) {
                                    CameraActivityScreen(
                                        activity = running,
                                        spec = cameraSpec(running.type)!!,
                                        onBack = {
                                            activityMinimised = true
                                            earningFor = null
                                        },
                                        onEnd = {
                                            earnViewModel.cancel()
                                            earningFor = null
                                        },
                                        onCounted = earnViewModel::onCounted,
                                        onStartedOver = earnViewModel::onStartedOver,
                                    )
                                } else if (running != null && !activityMinimised) {
                                    // Figma 23.
                                    ActivityProgressScreen(
                                        activity = running,
                                        onBack = {
                                            activityMinimised = true
                                            earningFor = null
                                        },
                                        onEnd = {
                                            earnViewModel.cancel()
                                            earningFor = null
                                        },
                                    )
                                } else if (earnApp != null && askingForLocation) {
                                    LocationAccessScreen(
                                        onBack = { askingForLocation = false },
                                        onAllow = allowLocation,
                                        onSkip = { askingForLocation = false },
                                        openSettings = locationDeniedForGood,
                                    )
                                } else if (earnApp != null && askingForCamera && cameraWanted != null) {
                                    CameraAccessScreen(
                                        spec = cameraSpec(cameraWanted!!)!!,
                                        onBack = { askingForCamera = false },
                                        onAllow = allowCamera,
                                        onSkip = { askingForCamera = false },
                                        openSettings = cameraDeniedForGood,
                                    )
                                } else if (earnApp != null && askingForSteps) {
                                    // Figma 22.
                                    ActivityTrackingScreen(
                                        type = stepsWanted,
                                        onBack = { askingForSteps = false },
                                        onAllow = {
                                            askForSteps.launch(
                                                Manifest.permission.ACTIVITY_RECOGNITION,
                                            )
                                        },
                                        onSkip = { askingForSteps = false },
                                    )
                                } else if (earnApp != null) {
                                    // Figma 21.
                                    ChooseActivityScreen(
                                        app = earnApp,
                                        earnedSoFar = earnedToday.forPackage(earnApp.packageName),
                                        options = earnOptions(
                                            stepsAvailable = earnViewModel.steps.available,
                                            cameraAvailable = Permissions.hasCameraHardware(context),
                                            barometerAvailable = Permissions.hasBarometer(context),
                                            accelerometerAvailable = Permissions.hasAccelerometer(context),
                                            gpsAvailable = Permissions.hasGps(context),
                                        ),
                                        onBack = {
                                            earningFor = null
                                            earnViewModel.clearError()
                                        },
                                        onStart = { option ->
                                            val pact = activePact
                                            when {
                                                pact == null -> earningFor = null
                                                // Anything on the step counter, and anything
                                                // on the camera, needs a permission first; the
                                                // screen that asks for it starts the activity
                                                // once it is granted.
                                                option.type in EarnRules.STEP_TYPES -> {
                                                    stepsWanted = option.type
                                                    if (Permissions.hasActivityRecognition(context)) {
                                                        earnViewModel.start(pact, earnApp, option.type)
                                                    } else {
                                                        askingForSteps = true
                                                    }
                                                }
                                                option.type in EarnRules.CAMERA_TYPES -> {
                                                    cameraWanted = option.type
                                                    if (cameraGranted) {
                                                        cameraRefused = false
                                                        earnViewModel.start(pact, earnApp, option.type)
                                                    } else {
                                                        askingForCamera = true
                                                    }
                                                }
                                                option.type == EarnRules.RIDE -> {
                                                    stepsWanted = EarnRules.RIDE
                                                    when {
                                                        !stepsGranted -> askingForSteps = true
                                                        !locationGranted -> askingForLocation = true
                                                        else -> {
                                                            locationRefused = false
                                                            earnViewModel.start(pact, earnApp, EarnRules.RIDE)
                                                        }
                                                    }
                                                }
                                                else -> earnViewModel.start(pact, earnApp, option.type)
                                            }
                                        },
                                        errorMessage = earnError ?: when {
                                            cameraRefused && !cameraGranted ->
                                                "Camera access was refused, so nothing can be counted on camera. " +
                                                    "Choose the activity again to allow it."
                                            locationRefused && !locationGranted ->
                                                "Precise location was refused, so a ride cannot be measured. " +
                                                    "Choose Cycle again to allow it."
                                            else -> null
                                        },
                                    )
                                } else if (about != null) {
                                    // Figma 25.
                                    val person = supporting.firstOrNull {
                                        it.user.id == about.aboutUserId
                                    }
                                    ReactScreen(
                                        item = about,
                                        chosen = about.eventId?.let { reactions[it] },
                                        busy = false,
                                        onBack = { reactingToId = null },
                                        onSend = { emoji ->
                                            val eventId = about.eventId
                                            if (person != null && eventId != null) {
                                                witnessViewModel.react(person.id, eventId, emoji)
                                            }
                                            reactingToId = null
                                        },
                                    )
                                } else if (showingNotifications) {
                                    // Figma 19.
                                    NotificationsScreen(
                                        items = inboxItems,
                                        unread = unread,
                                        loaded = inboxLoaded,
                                        onBack = { showingNotifications = false },
                                        onOpen = { item ->
                                            inboxViewModel.markRead(item.id)
                                            // Only a notification that names an
                                            // event of somebody this person
                                            // actually witnesses can be reacted
                                            // to. The rest are read and no more.
                                            val canReact = item.eventId != null &&
                                                supporting.any { it.user.id == item.aboutUserId }
                                            if (canReact) reactingToId = item.id
                                        },
                                        onMarkAllRead = inboxViewModel::markAllRead,
                                        onTurnOnNotifications = turnOnNotifications,
                                    )
                                } else if (showingProtectionLost) {
                                    // Figma 27.
                                    ProtectionLostScreen(
                                        onBack = { showingProtectionLost = false },
                                        onDismiss = { showingProtectionLost = false },
                                    )
                                } else if (fixingProtection) {
                                    BackgroundActivityScreen(
                                        onBack = { fixingProtection = false },
                                        onDone = { fixingProtection = false },
                                    )
                                } else if (addingApp && activePact != null) {
                                    // Not in the Figma file: one more app,
                                    // into the challenge that is running.
                                    AddAppScreen(
                                        excluded = activePact.apps.map { it.packageName }.toSet(),
                                        busy = addAppBusy,
                                        errorMessage = addAppError,
                                        onBack = {
                                            addingApp = false
                                            pactViewModel.clearAddAppError()
                                        },
                                        onAdd = { entry, minutes -> pactViewModel.addApp(entry, minutes) },
                                    )
                                } else {
                                    DashboardScreen(
                                        pact = activePact,
                                        onStartChallenge = {
                                            setupStep = SetupStep.Duration
                                            startingChallenge = true
                                        },
                                        onProtectionLost = { showingProtectionLost = true },
                                        onFixProtection = { fixingProtection = true },
                                        onNotifications = {
                                            inboxViewModel.refresh()
                                            showingNotifications = true
                                        },
                                        unreadNotifications = unread,
                                        earnedMinutes = earnedToday.minutesByPackage,
                                        runningActivity = activeActivity,
                                        onEarnTime = { app ->
                                            // One activity at a time: while one
                                            // runs, Earn shows it again rather
                                            // than offering a second.
                                            if (activeActivity != null) {
                                                activityMinimised = false
                                            } else {
                                                earningFor = app.packageName
                                            }
                                        },
                                        onAddApp = { addingApp = true },
                                    )
                                }
                            }

                            AsrTab.Progress -> {
                                val running = activePact
                                if (givingUp && running != null) {
                                    val progress = ChallengeProgress.of(
                                        running.startedAtMillis,
                                        running.durationDays,
                                    )
                                    GiveUpScreen(
                                        dayNumber = progress.dayNumber,
                                        totalDays = progress.totalDays,
                                        witnesses = witnesses,
                                        onKeepGoing = { givingUp = false },
                                        onGiveUp = {
                                            // The screen goes away here
                                            // rather than after the pact
                                            // does. Clearing it is what
                                            // Figma 26 waits for, and this
                                            // must not still be on top of
                                            // the thing it opens.
                                            givingUp = false
                                            pactViewModel.giveUp()
                                        },
                                        busy = false,
                                    )
                                } else {
                                    ProgressScreen(
                                        pact = running,
                                        earnedMinutes = earnedToday.minutesByPackage,
                                        onStartChallenge = {
                                            tab = AsrTab.Home
                                            setupStep = SetupStep.Duration
                                            startingChallenge = true
                                        },
                                        onGiveUp = { givingUp = true },
                                    )
                                }
                            }

                            AsrTab.Witnesses -> {
                                // Read again every time this screen is
                                // opened.
                                //
                                // The list is the server's and was fetched
                                // once, when the view model was made -- so
                                // a friend accepting an invitation half an
                                // hour later changed nothing on the phone
                                // that sent it, and "My witnesses · 0" sat
                                // there under somebody who had said yes.
                                // Nothing is going to tell this screen; it
                                // has to ask.
                                LaunchedEffect(Unit) { witnessViewModel.refresh() }

                                // And again on coming back to the app,
                                // because the usual way to send an invite
                                // is to leave for WhatsApp and return --
                                // sometimes to somebody who accepted while
                                // you were in there.
                                val owner = LocalLifecycleOwner.current
                                DisposableEffect(owner) {
                                    val watch = LifecycleEventObserver { _, event ->
                                        if (event == Lifecycle.Event.ON_RESUME) witnessViewModel.refresh()
                                    }
                                    owner.lifecycle.addObserver(watch)
                                    onDispose { owner.lifecycle.removeObserver(watch) }
                                }

                                val person = openPerson
                                if (person != null) {
                                    // Their numbers, again, whenever this
                                    // comes back to the front. Somebody
                                    // opens the app to see how the person
                                    // they are watching is doing, and the
                                    // answer they were shown yesterday is
                                    // not that.
                                    val watching = rememberUpdatedState(person.id)
                                    val personOwner = LocalLifecycleOwner.current
                                    DisposableEffect(personOwner) {
                                        val watch = LifecycleEventObserver { _, event ->
                                            if (event == Lifecycle.Event.ON_RESUME) {
                                                witnessViewModel.loadProgress(watching.value)
                                            }
                                        }
                                        personOwner.lifecycle.addObserver(watch)
                                        onDispose { personOwner.lifecycle.removeObserver(watch) }
                                    }

                                    // Figma 17.
                                    PersonDetailScreen(
                                        person = person,
                                        progress = witnessProgress[person.id],
                                        reactions = reactions,
                                        onBack = { openPersonId = null },
                                        onReact = { eventId, emoji ->
                                            witnessViewModel.react(person.id, eventId, emoji)
                                        },
                                    )
                                } else if (addingWitness) {
                                    AddWitnessesScreen(
                                        challengeDays = activePact?.durationDays
                                            ?: ChallengeDuration.DEFAULT_DAYS,
                                        witnesses = witnesses,
                                        onBack = { addingWitness = false },
                                        onInvite = witnessViewModel::invite,
                                        onContinue = { addingWitness = false },
                                        pendingShare = pendingShare,
                                        onShared = witnessViewModel::shared,
                                        inviting = inviting,
                                        errorMessage = witnessError,
                                        // Not a setup step here: the eyebrow
                                        // would be counting a flow the person is
                                        // not in.
                                        showStepNumber = false,
                                    )
                                } else {
                                    // Figma 16, which contains Figma 15 as its
                                    // first tab.
                                    CircleScreen(
                                        tab = circleTab,
                                        onTab = { circleTab = it },
                                        witnesses = witnesses,
                                        supporting = supporting,
                                        progress = witnessProgress,
                                        onLoadProgress = witnessViewModel::loadProgress,
                                        onOpenPerson = {
                                            openPersonId = it.id
                                            witnessViewModel.loadProgress(it.id)
                                        },
                                        onAdd = { addingWitness = true },
                                        hasChallenge = activePact != null,
                                    )
                                }
                            }

                            AsrTab.Profile -> when (profileRoute) {
                                null -> ProfileScreen(
                                    me = current.me,
                                    onOpen = { profileRoute = it },
                                    available = ProfileRoutes,
                                    onSignOut = signOut,
                                )

                                // Figma 29, with Figma 31 stacked on top of
                                // it when the delete row is pressed.
                                ProfileDestination.PersonalDetails -> if (deletingAccount) {
                                    DeleteAccountScreen(
                                        onBack = { deletingAccount = false },
                                        onDelete = accountViewModel::deleteAccount,
                                        busy = accountBusy,
                                        errorMessage = accountError,
                                    )
                                } else PersonalDetailsScreen(
                                    me = current.me,
                                    onBack = { profileRoute = null },
                                    onSave = { firstName, lastName, country, gender ->
                                        // The date of birth goes back
                                        // unchanged: it is the field the
                                        // thirteen-or-older rule rests on and
                                        // the screen does not let anybody
                                        // edit it.
                                        viewModel.saveProfile(
                                            firstName,
                                            lastName,
                                            current.me.dateOfBirth.orEmpty(),
                                            country,
                                            gender,
                                        )
                                    },
                                    onPhotoPicked = viewModel::uploadPhoto,
                                    onDeleteAccount = { deletingAccount = true },
                                    deleteAvailable = true,
                                    submitting = submitting,
                                    errorMessage = error,
                                )

                                // Figma 30.
                                ProfileDestination.EmailAndPassword -> SecurityScreen(
                                    email = current.me.email,
                                    emailVerified = current.me.emailVerified,
                                    onBack = { profileRoute = null },
                                    onChangePassword = accountViewModel::changePassword,
                                    onSendVerification = accountViewModel::sendVerification,
                                    onChangeEmail = accountViewModel::changeEmail,
                                    onSignOutOtherSessions =
                                        accountViewModel::signOutOtherSessions,
                                    busy = accountBusy,
                                    errorMessage = accountError,
                                    notice = accountNotice,
                                )

                                // Figma 35.
                                // The same rows as setup step six, because
                                // they are facts about the phone either way --
                                // and the one thing that belongs only here:
                                // Android's switch for the notification this
                                // app is not allowed to hide.
                                ProfileDestination.Permissions -> ProtectionScreen(
                                    onBack = { profileRoute = null },
                                    onReviewBlocking = {
                                        runCatching {
                                            context.startActivity(Permissions.overlayIntent(context))
                                        }
                                    },
                                    // Straight to Android's own switch. The
                                    // guided screen behind it belongs to the
                                    // dashboard's warning, where it is opened
                                    // because the loop has actually stopped;
                                    // it is drawn inside the Home tab and
                                    // would be a dead button from here.
                                    onBackgroundActivity = {
                                        runCatching {
                                            context.startActivity(Permissions.batteryOptimizationIntent())
                                        }
                                    },
                                    onContinue = { profileRoute = null },
                                    inSetup = false,
                                )

                                ProfileDestination.HelpAndSupport -> HelpAndSupportScreen(
                                    onBack = { profileRoute = null },
                                    accountEmail = current.me.email,
                                )

                                // Figma 36 and 37.
                                ProfileDestination.PrivacyPolicy -> LegalScreen(
                                    document = LegalTexts.privacy,
                                    onBack = { profileRoute = null },
                                )

                                ProfileDestination.TermsOfService -> LegalScreen(
                                    document = LegalTexts.terms,
                                    onBack = { profileRoute = null },
                                )

                                // Not built. Unreachable: ProfileRoutes is
                                // what the profile screen lets anybody press.
                                else -> ProfileScreen(
                                    me = current.me,
                                    onOpen = { profileRoute = it },
                                    available = ProfileRoutes,
                                    onSignOut = signOut,
                                )
                            }
                        }
                    }
                    AsrBottomNav(
                        selected = tab,
                        onSelect = goToTab,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        // The Profile tab is the person, so it is their face.
                        profileImage = current.me.image,
                        profileName = current.me.name,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

        Session.SignedOut -> when (val where = destination) {
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
                onForgotPassword = { destination = Destination.ForgotPassword },
                onCreateAccount = { destination = Destination.SignUp },
                submitting = submitting,
                errorMessage = error,
            )

            // Figma 33. Moving on happens whether or not the address has an
            // account, because the server answers identically either way and
            // a screen that only advanced for real accounts would be a way
            // to test whether an address has one.
            Destination.ForgotPassword -> ForgotPasswordScreen(
                onBack = { destination = Destination.LogIn },
                onSend = accountViewModel::sendResetCode,
                onBackToLogIn = { destination = Destination.LogIn },
                busy = accountBusy,
                errorMessage = accountError,
            )

            // Figma 34, which takes the code. Continue carries it to the
            // next screen and nothing checks it on the way: the server sees
            // it once, with the new password.
            is Destination.CheckEmail -> CheckEmailScreen(
                email = where.email,
                onBack = { destination = Destination.ForgotPassword },
                onContinue = { code -> destination = Destination.NewPassword(where.email, code) },
                onResend = { accountViewModel.sendResetCode(where.email) },
                onBackToLogIn = { destination = Destination.LogIn },
                busy = accountBusy,
                notice = accountNotice,
                errorMessage = accountError,
            )

            // Figma 35. Sends the address, the code and the password
            // together, so a code that has expired or was mistyped is
            // reported here -- and back is the screen that can send a new
            // one.
            is Destination.NewPassword -> ResetPasswordScreen(
                onBack = { destination = Destination.CheckEmail(where.email) },
                onSubmit = { password ->
                    resetSubmitted = password
                    accountViewModel.resetPassword(where.email, where.code, password)
                },
                busy = accountBusy,
                errorMessage = accountError,
            )
        }
    }
}

/**
 * Opens an app the person has just earned time for.
 *
 * Nothing is unblocked by doing this: the reward is already in the store and
 * the loop reads it on the next pass, so the app simply is not over its
 * allowance any more. If there is no launcher entry -- which can happen for
 * something installed and then disabled -- nothing happens, and the person
 * is left on a screen that still says the minutes are theirs.
 */
private fun openApp(context: android.content.Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    runCatching { context.startActivity(intent) }
}
