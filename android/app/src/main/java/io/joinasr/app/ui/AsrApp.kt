package io.joinasr.app.ui

import android.Manifest
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.challenge.ChallengeDuration
import io.joinasr.app.enforcement.EnforcementService
import io.joinasr.app.enforcement.PactState
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.earn.EarnRules
import io.joinasr.app.earn.EarnViewModel
import io.joinasr.app.enforcement.PactViewModel
import io.joinasr.app.ui.components.AsrBottomNav
import io.joinasr.app.ui.components.AsrTab
import io.joinasr.app.ui.screens.AboutYouScreen
import io.joinasr.app.ui.screens.AddWitnessesScreen
import io.joinasr.app.ui.screens.BlockingDisclosureScreen
import io.joinasr.app.ui.screens.ChallengeStartedScreen
import io.joinasr.app.ui.screens.ChallengeDurationScreen
import io.joinasr.app.ui.screens.ActivityProgressScreen
import io.joinasr.app.ui.screens.ActivityTrackingScreen
import io.joinasr.app.ui.screens.ChallengeEndedScreen
import io.joinasr.app.ui.screens.ChooseActivityScreen
import io.joinasr.app.ui.screens.CircleScreen
import io.joinasr.app.ui.screens.CircleTab
import io.joinasr.app.ui.screens.ChooseAppsScreen
import io.joinasr.app.ui.screens.CheckEmailScreen
import io.joinasr.app.ui.screens.DailyLimitsScreen
import io.joinasr.app.ui.screens.DashboardScreen
import io.joinasr.app.ui.screens.DeleteAccountScreen
import io.joinasr.app.ui.screens.EarnedScreen
import io.joinasr.app.ui.screens.ForgotPasswordScreen
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
import io.joinasr.app.data.InboxItem
import io.joinasr.app.data.SupportedPerson
import io.joinasr.app.witness.Relationships
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

    /** Figma 35, reached from the link in the email rather than from a tap. */
    data class ResetPassword(val token: String) : Destination
}

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
private sealed interface SetupStep {
    data object Duration : SetupStep
    data object UsageAccess : SetupStep
    data object ChooseApps : SetupStep
    data object DailyLimits : SetupStep
    data object Witnesses : SetupStep
    data object Protection : SetupStep
    data object BlockingDisclosure : SetupStep
    data object Review : SetupStep
}

/**
 * The rows on the profile screen that lead somewhere. The rest are drawn and
 * not pressable, which somebody can see before they press rather than after.
 */
private val ProfileRoutes = setOf(
    ProfileDestination.PersonalDetails,
    ProfileDestination.EmailAndPassword,
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
    val endedUnseen by pactViewModel.endedUnseen.collectAsStateWithLifecycle()
    val witnesses by witnessViewModel.witnesses.collectAsStateWithLifecycle()
    val pendingShare by witnessViewModel.pendingShare.collectAsStateWithLifecycle()
    val inviting by witnessViewModel.inviting.collectAsStateWithLifecycle()
    val witnessError by witnessViewModel.error.collectAsStateWithLifecycle()
    val supporting by witnessViewModel.supporting.collectAsStateWithLifecycle()
    val witnessProgress by witnessViewModel.progress.collectAsStateWithLifecycle()
    val reactions by witnessViewModel.reactions.collectAsStateWithLifecycle()
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
    val passwordReset by accountViewModel.reset.collectAsStateWithLifecycle()
    val resetEmailSentTo by accountViewModel.resetEmailSentTo.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var destination by remember { mutableStateOf<Destination>(Destination.Welcome) }
    var setupStep by remember { mutableStateOf<SetupStep>(SetupStep.Duration) }
    var tab by remember { mutableStateOf(AsrTab.Home) }
    // Where the Profile tab is: null is its own overview, anything else is a
    // screen stacked on top of it. One nullable value rather than a back
    // stack, for the same reason Destination is: three destinations do not
    // need a navigation library.
    var profileRoute by remember { mutableStateOf<ProfileDestination?>(null) }
    var addingWitness by remember { mutableStateOf(false) }
    // Figma 16's two halves, and Figma 17 stacked on the second of them.
    var circleTab by remember { mutableStateOf(CircleTab.Mine) }
    var openPerson by remember { mutableStateOf<SupportedPerson?>(null) }
    // Figma 31, which sits on top of Personal Details rather than in the
    // profile's row list: the design puts it at the bottom of that screen.
    var deletingAccount by remember { mutableStateOf(false) }
    // Figma 27, opened from the NOT PROTECTED pill on the dashboard.
    var showingProtectionLost by remember { mutableStateOf(false) }
    // Figma 19, opened from the bell.
    var showingNotifications by remember { mutableStateOf(false) }
    // Figma 21-24. The package the block screen sent, held for as long as
    // the person is inside the earn flow.
    var earningFor by remember { mutableStateOf<String?>(null) }
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
    // Figma 25, opened from a notification about somebody else.
    var reactingTo by remember { mutableStateOf<InboxItem?>(null) }
    // Figma 18. The code from a witness link, held until it is answered.
    var inviteCode by remember { mutableStateOf<String?>(null) }
    // True while somebody who opened an invite is going through sign-up.
    // The code is kept the whole time, so they land back on the invitation
    // with an account rather than on an empty dashboard wondering where the
    // link went.
    var inviteDeferred by remember { mutableStateOf(false) }
    // True between committing a pact and pressing "Go to dashboard" on
    // Figma 12. Not stored: it is a moment, not a state of the challenge,
    // and a person who closes the app during it has still started.
    var justStarted by remember { mutableStateOf(false) }

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
     * Not stored. Backing out of the first step leaves nothing behind,
     * which is the point: nothing is committed until the review screen.
     */
    var startingChallenge by remember { mutableStateOf(false) }

    // Held here and nowhere else, for the length of the setup flow only. A
    // half-made challenge is not something the app should remember: it is
    // committed whole on the last step, or it never existed. After that it
    // is read back from storage, never from here.
    var chosenDays by remember { mutableIntStateOf(ChallengeDuration.DEFAULT_DAYS) }
    var chosenApps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var chosenLimits by remember { mutableStateOf(emptyMap<String, Int>()) }

    // The one place the loop is started from inside the app. Starting an
    // already-running service costs one onStartCommand, and the service
    // stops itself when there is no pact, so there is nothing to guard.
    LaunchedEffect(pactState) {
        if (pactState is PactState.Active) EnforcementService.start(context)
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
    //
    // A reset link also signs them out first: the token is proof of reaching
    // the inbox, the server revokes every session when it is spent, and
    // staying on a signed-in screen behind it would only mean the next
    // request 401s.
    LaunchedEffect(link) {
        when (val opened = link) {
            null -> Unit
            is DeepLink.Reset -> {
                viewModel.signOut()
                destination = Destination.ResetPassword(opened.token)
                onLinkHandled()
            }
            is DeepLink.Invite -> {
                inviteCode = opened.code
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

    // Answered, so the screen has done its job. Accepting leaves the person
    // on their own app rather than on a confirmation: the list they have
    // just joined is the confirmation.
    LaunchedEffect(inviteAnswered) {
        if (!inviteAnswered) return@LaunchedEffect
        inviteCode = null
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
    LaunchedEffect(accountDeleted) {
        if (!accountDeleted) return@LaunchedEffect
        accountViewModel.consumeDeleted()
        deletingAccount = false
        profileRoute = null
        tab = AsrTab.Home
        destination = Destination.Welcome
        viewModel.signOut()
    }

    LaunchedEffect(passwordReset) {
        if (!passwordReset) return@LaunchedEffect
        accountViewModel.consumeReset()
        destination = Destination.LogIn
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

    BackHandler(enabled = destination != Destination.Welcome) {
        destination = Destination.Welcome
    }

    // Back from any other tab returns to Home rather than leaving the app,
    // which is what a bottom bar implies and what every app with one does.
    BackHandler(
        enabled = tab != AsrTab.Home || profileRoute != null || addingWitness ||
            deletingAccount || showingProtectionLost || openPerson != null ||
            showingNotifications || reactingTo != null || earningFor != null ||
            startingChallenge,
    ) {
        when {
            // Back out of setup step one. The later steps have their own
            // chevrons and walk backwards through the flow.
            startingChallenge && setupStep == SetupStep.Duration -> startingChallenge = false
            askingForSteps -> askingForSteps = false
            earningFor != null -> earningFor = null
            reactingTo != null -> reactingTo = null
            showingNotifications -> showingNotifications = false
            showingProtectionLost -> showingProtectionLost = false
            deletingAccount -> deletingAccount = false
            profileRoute != null -> profileRoute = null
            openPerson != null -> openPerson = null
            addingWitness -> addingWitness = false
            else -> tab = AsrTab.Home
        }
    }

    // Read into a local so the branch below can smart-cast it. A delegated
    // property cannot be, and `!!` on the thing that tells somebody their
    // challenge broke is not where to be casual.
    val ended = endedUnseen
    val code = inviteCode
    val signedIn = session is Session.SignedIn

    LaunchedEffect(signedIn) { if (signedIn) inviteDeferred = false }

    if (code != null && (signedIn || !inviteDeferred)) {
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
                    initialName = current.me.name,
                    submitting = submitting,
                    errorMessage = error,
                )
            } else if (pactState is PactState.Loading) {
                // One read of a small file. Blank rather than a spinner, for
                // the same reason as above.
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
                            setupStep = SetupStep.Witnesses
                        },
                    )

                    // Figma 08. Invites go through Android's share sheet, so
                    // this step needs no server and works today.
                    SetupStep.Witnesses -> AddWitnessesScreen(
                        challengeDays = chosenDays,
                        witnesses = witnesses,
                        onBack = { setupStep = SetupStep.DailyLimits },
                        onInvite = witnessViewModel::invite,
                        onContinue = { setupStep = SetupStep.Protection },
                        pendingShare = pendingShare,
                        onShared = witnessViewModel::shared,
                        inviting = inviting,
                        errorMessage = witnessError,
                    )

                    SetupStep.Protection -> ProtectionScreen(
                        onBack = { setupStep = SetupStep.Witnesses },
                        // Figma 10, which explains what the overlay reads
                        // before Settings opens rather than after.
                        onReviewBlocking = { setupStep = SetupStep.BlockingDisclosure },
                        onContinue = { setupStep = SetupStep.Review },
                    )

                    // Figma 11. Everything chosen, in one place, before
                    // anything is written: a commitment nobody was shown in
                    // full is not one they agreed to.
                    SetupStep.Review -> ReviewScreen(
                        days = chosenDays,
                        apps = chosenApps,
                        limits = chosenLimits,
                        witnesses = witnesses,
                        protectionReady = PermissionState.read(context).requiredGranted,
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
            } else if (justStarted && pactState is PactState.Active) {
                // Figma 12.
                ChallengeStartedScreen(
                    days = (pactState as PactState.Active).pact.durationDays,
                    witnesses = witnesses.size,
                    protectionReady = PermissionState.read(context).requiredGranted,
                    onContinue = { justStarted = false },
                )
            } else {
                // Null when nothing is running, which every tab now handles.
                // The bar and its four screens are the app; a challenge is
                // something that happens inside it, not the price of entry.
                val activePact = (pactState as? PactState.Active)?.pact
                val signOut = {
                    destination = Destination.Welcome
                    tab = AsrTab.Home
                    viewModel.signOut()
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
                                } else if (running != null) {
                                    // Figma 23.
                                    ActivityProgressScreen(
                                        activity = running,
                                        onBack = { earningFor = null },
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
                                        chosen = about.eventId?.let { reactions[it] },
                                        busy = false,
                                        onBack = { reactingTo = null },
                                        onSend = { emoji ->
                                            val eventId = about.eventId
                                            if (person != null && eventId != null) {
                                                witnessViewModel.react(person.id, eventId, emoji)
                                            }
                                            reactingTo = null
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
                                            if (canReact) reactingTo = item
                                        },
                                        onMarkAllRead = inboxViewModel::markAllRead,
                                    )
                                } else if (showingProtectionLost) {
                                    // Figma 27.
                                    ProtectionLostScreen(
                                        onBack = { showingProtectionLost = false },
                                        onDismiss = { showingProtectionLost = false },
                                    )
                                } else {
                                    DashboardScreen(
                                        pact = activePact,
                                        onStartChallenge = {
                                            setupStep = SetupStep.Duration
                                            startingChallenge = true
                                        },
                                        onProtectionLost = { showingProtectionLost = true },
                                        onNotifications = {
                                            inboxViewModel.refresh()
                                            showingNotifications = true
                                        },
                                        unreadNotifications = unread,
                                        earnedMinutes = earnedToday.minutesByPackage,
                                        onEarnTime = { earningFor = it.packageName },
                                    )
                                }
                            }

                            AsrTab.Progress -> ProgressScreen(
                                pact = activePact,
                                earnedMinutes = earnedToday.minutesByPackage,
                                onStartChallenge = {
                                    tab = AsrTab.Home
                                    setupStep = SetupStep.Duration
                                    startingChallenge = true
                                },
                            )

                            AsrTab.Witnesses -> {
                                val person = openPerson
                                if (person != null) {
                                    // Figma 17.
                                    PersonDetailScreen(
                                        person = person,
                                        progress = witnessProgress[person.id],
                                        reactions = reactions,
                                        onBack = { openPerson = null },
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
                                            openPerson = it
                                            witnessViewModel.loadProgress(it.id)
                                        },
                                        onAdd = { addingWitness = true },
                                        // On who accepted, not on who was
                                        // invited: a pending invite must not
                                        // stop somebody inviting anybody
                                        // else while they wait.
                                        addEnabled = witnesses.count { it.accepted } <
                                            Relationships.SLOTS,
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
                                    onSave = { name, country, gender ->
                                        // The date of birth goes back
                                        // unchanged: it is the field the
                                        // thirteen-or-older rule rests on and
                                        // the screen does not let anybody
                                        // edit it.
                                        viewModel.saveProfile(
                                            name,
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
                                    onSignOutOtherSessions =
                                        accountViewModel::signOutOtherSessions,
                                    busy = accountBusy,
                                    errorMessage = accountError,
                                    notice = accountNotice,
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
                        onSelect = { tab = it },
                        modifier = Modifier.padding(horizontal = 12.dp),
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

            // Figma 35, reached from the link. Back goes to log in rather
            // than to the previous screen: there is no previous screen when
            // the app was opened by an email.
            is Destination.ResetPassword -> ResetPasswordScreen(
                onBack = { destination = Destination.LogIn },
                onSubmit = { password ->
                    accountViewModel.resetPassword(where.token, password)
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
