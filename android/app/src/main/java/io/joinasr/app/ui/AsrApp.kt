package io.joinasr.app.ui

import android.Manifest
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
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.challenge.ChallengeDuration
import io.joinasr.app.enforcement.EnforcementService
import io.joinasr.app.enforcement.PactState
import io.joinasr.app.PendingInvite
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.earn.EarnRules
import io.joinasr.app.earn.EarnViewModel
import io.joinasr.app.enforcement.PactViewModel
import io.joinasr.app.ui.components.AsrBottomNav
import io.joinasr.app.ui.components.AsrTab
import io.joinasr.app.ui.screens.AboutYouScreen
import io.joinasr.app.ui.screens.AddWitnessesScreen
import io.joinasr.app.ui.screens.BackgroundActivityScreen
import io.joinasr.app.ui.screens.BlockingDisclosureScreen
import io.joinasr.app.ui.screens.ChallengeStartedScreen
import io.joinasr.app.ui.screens.ChallengeDurationScreen
import io.joinasr.app.ui.screens.ActivityProgressScreen
import io.joinasr.app.ui.screens.ActivityTrackingScreen
import io.joinasr.app.challenge.ChallengeProgress
import io.joinasr.app.ui.screens.ChallengeEndedScreen
import io.joinasr.app.ui.screens.GiveUpScreen
import io.joinasr.app.ui.screens.ChooseActivityScreen
import io.joinasr.app.ui.screens.CircleScreen
import io.joinasr.app.ui.screens.CircleTab
import io.joinasr.app.ui.screens.AddAppScreen
import io.joinasr.app.ui.screens.ChooseAppsScreen
import io.joinasr.app.ui.screens.CheckEmailScreen
import io.joinasr.app.ui.screens.DailyLimitsScreen
import io.joinasr.app.ui.screens.DashboardScreen
import io.joinasr.app.ui.screens.DeleteAccountScreen
import io.joinasr.app.ui.screens.EarnedScreen
import io.joinasr.app.ui.screens.ForgotPasswordScreen
import io.joinasr.app.ui.screens.HelpAndSupportScreen
import io.joinasr.app.legal.LegalTexts
import io.joinasr.app.ui.screens.LegalScreen
import io.joinasr.app.ui.screens.LogInScreen
import io.joinasr.app.ui.screens.NotificationsScreen
import io.joinasr.app.ui.screens.PersonDetailScreen
import io.joinasr.app.ui.screens.PersonalDetailsScreen
import io.joinasr.app.ui.screens.ProfileDestination
import io.joinasr.app.ui.screens.ProfileScreen
import io.joinasr.app.ui.screens.ProgressScreen
import io.joinasr.app.ui.screens.ResetPasswordScreen
import io.joinasr.app.ui.screens.ReviewScreen
import io.joinasr.app.ui.screens.ProtectionLostScreen
import io.joinasr.app.ui.screens.ReactScreen
import io.joinasr.app.ui.screens.ProtectionScreen
import io.joinasr.app.ui.screens.SecurityScreen
import io.joinasr.app.ui.screens.SignUpScreen
import io.joinasr.app.ui.screens.UsageAccessScreen
import io.joinasr.app.ui.screens.WelcomeScreen
import io.joinasr.app.ui.screens.WitnessInviteScreen
import io.joinasr.app.DeepLink
import io.joinasr.app.witness.WitnessViewModel
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
    data object ForgotPassword : Destination

    /** Figma 34. Carries the address so the screen can name it. */
    data class CheckEmail(val email: String) : Destination
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
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            "sign_up" -> Destination.SignUp
            "log_in" -> Destination.LogIn
            "forgot" -> Destination.ForgotPassword
            "check_email" -> Destination.CheckEmail(saved.getOrElse(1) { "" })
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
    // Set when the grant comes back yes, so the walk starts without the
    // person having to press the same row twice.
    var walkOnceGranted by remember { mutableStateOf(false) }

    val askForSteps = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        askingForSteps = false
        walkOnceGranted = granted
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

    /**
     * The token from a reset link, while Figma 35 is open over everything.
     *
     * Over everything, signed in or not, and nobody is signed out to show
     * it. The link used to sign the phone out first -- before the token had
     * been tried -- so an expired or wrong link cost a working session, and
     * with it the challenge running on this phone: the pact was wiped, the
     * loop stopped, and a day later the witnesses heard the phone had gone
     * dark. Now the token is spent first, and what follows depends on
     * whether it worked; see the effect on `passwordReset`.
     */
    var resetToken by rememberSaveable { mutableStateOf<String?>(null) }
    // The password just submitted, kept only until the answer comes back
    // and never saved with the activity. It is what signs the person back
    // in afterwards without asking them to type it again.
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
    LaunchedEffect(profileRoute, deletingAccount, resetToken) { accountViewModel.clear() }

    // A link takes precedence over whatever screen was showing, because
    // opening one is the person saying where they want to be.
    //
    // A reset link opens the form and nothing else. Whether anybody ends up
    // signed out is decided when the token has actually been spent, below.
    LaunchedEffect(link) {
        when (val opened = link) {
            null -> Unit
            is DeepLink.Reset -> {
                resetToken = opened.token
                onLinkHandled()
            }
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
                earningFor = opened.packageName
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

    LaunchedEffect(walkOnceGranted, earningFor, pactState) {
        if (!walkOnceGranted) return@LaunchedEffect
        walkOnceGranted = false
        val pact = (pactState as? PactState.Active)?.pact ?: return@LaunchedEffect
        val app = earningFor?.let { pact.appFor(it) } ?: return@LaunchedEffect
        earnViewModel.start(pact, app, EarnRules.WALK)
    }

    // What actually drives an activity forward.
    //
    // A walk reads the step counter, which is a running total the sensor hub
    // keeps whether or not this app is alive -- so listening only while the
    // screen is up loses nothing, and the difference from the baseline is
    // still right when somebody comes back.
    //
    // A focus session is the clock. It cannot be beaten by leaving this
    // screen: the enforcement loop cancels the session the moment a
    // controlled app comes to the front, which is the only place on the
    // phone that can see it happen.
    LaunchedEffect(activeActivity?.id) {
        val running = activeActivity ?: return@LaunchedEffect
        if (running.isWalk) {
            earnViewModel.steps.readings().collect { earnViewModel.onSteps(it) }
        } else {
            while (true) {
                val elapsed = System.currentTimeMillis() - running.startedAtMillis
                earnViewModel.onFocusMinutes((elapsed / 60_000L).toInt())
                delay(1_000)
            }
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

    // The token has been spent and the server has revoked every session of
    // that account. Signed out, that is the log-in screen. Signed in, this
    // phone's own session is among the revoked -- so it signs straight back
    // in with the password just typed, and the challenge running here never
    // notices. Only when that cannot be done (the password was lost to a
    // rebuild of the activity mid-request) does the phone sign out the
    // ordinary way.
    LaunchedEffect(passwordReset) {
        if (!passwordReset) return@LaunchedEffect
        accountViewModel.consumeReset()
        val password = resetSubmitted
        resetSubmitted = null
        resetToken = null
        val me = (session as? Session.SignedIn)?.me
        when {
            me == null -> destination = Destination.LogIn
            password != null -> viewModel.reauthenticate(me.email, password)
            else -> {
                destination = Destination.LogIn
                viewModel.signOut()
            }
        }
    }

    // Figma 34 says "reset link sent" as a fact, so it is reached when the
    // server has taken the request and not when the button was pressed. A
    // resend from that screen lands here too and leaves the destination as
    // it is, which keeps the notice on screen.
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
    val backAction: (() -> Unit)? = when {
        resetToken != null -> ({
            resetToken = null
            if (!signedIn) destination = Destination.LogIn
        })
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
        enabled = session is Session.SignedOut && resetToken == null && !invitationOpen &&
            destination != Destination.Welcome,
    ) {
        if (inviteDeferred && code != null) {
            inviteDeferred = false
            destination = Destination.Welcome
        } else {
            destination = when (destination) {
                Destination.ForgotPassword -> Destination.LogIn
                is Destination.CheckEmail -> Destination.ForgotPassword
                else -> Destination.Welcome
            }
        }
    }


    val resetting = resetToken
    if (resetting != null) {
        // Figma 35, reached from the link in the email. Over everything,
        // signed in or not: it needs no session, and it takes none away.
        // Back goes to log in rather than to a previous screen when signed
        // out, because there is no previous screen when the app was opened
        // by an email.
        ResetPasswordScreen(
            onBack = {
                resetToken = null
                if (!signedIn) destination = Destination.LogIn
            },
            onSubmit = { password ->
                resetSubmitted = password
                accountViewModel.resetPassword(resetting, password)
            },
            busy = accountBusy,
            errorMessage = accountError,
        )
    } else if (code != null && (signedIn || !inviteDeferred) && !needsProfile) {
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
                    walkOnceGranted = false
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
                                        availableNow = earnedToday.forPackage(done.packageName),
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
                                } else if (earnApp != null && askingForSteps) {
                                    // Figma 22.
                                    ActivityTrackingScreen(
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
                                        stepsAvailable = earnViewModel.steps.available,
                                        onBack = {
                                            earningFor = null
                                            earnViewModel.clearError()
                                        },
                                        onWalk = {
                                            val pact = activePact
                                            if (pact == null) {
                                                earningFor = null
                                            } else if (Permissions.hasActivityRecognition(context)) {
                                                earnViewModel.start(pact, earnApp, EarnRules.WALK)
                                            } else {
                                                askingForSteps = true
                                            }
                                        },
                                        onFocus = {
                                            activePact?.let {
                                                earnViewModel.start(it, earnApp, EarnRules.FOCUS)
                                            }
                                        },
                                        errorMessage = earnError,
                                    )
                                } else if (about != null) {
                                    // Figma 25.
                                    val person = supporting.firstOrNull {
                                        it.user.id == about.aboutUserId
                                    }
                                    ReactScreen(
                                        item = about,
                                        personName = person?.user?.name,
                                        gender = person?.user?.gender ?: about.aboutUser?.gender,
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
                onSend = accountViewModel::sendResetEmail,
                onBackToLogIn = { destination = Destination.LogIn },
                busy = accountBusy,
                errorMessage = accountError,
            )

            // Figma 34.
            is Destination.CheckEmail -> CheckEmailScreen(
                email = where.email,
                onBack = { destination = Destination.ForgotPassword },
                onResend = { accountViewModel.sendResetEmail(where.email) },
                onBackToLogIn = { destination = Destination.LogIn },
                busy = accountBusy,
                notice = accountNotice,
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
