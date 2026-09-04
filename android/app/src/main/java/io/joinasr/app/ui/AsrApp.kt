package io.joinasr.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.challenge.ChallengeDuration
import io.joinasr.app.enforcement.EnforcementService
import io.joinasr.app.enforcement.PactState
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.enforcement.PactViewModel
import io.joinasr.app.ui.components.AsrBottomNav
import io.joinasr.app.ui.components.AsrTab
import io.joinasr.app.ui.screens.AboutYouScreen
import io.joinasr.app.ui.screens.AddWitnessesScreen
import io.joinasr.app.ui.screens.BlockingDisclosureScreen
import io.joinasr.app.ui.screens.ChallengeStartedScreen
import io.joinasr.app.ui.screens.ChallengeDurationScreen
import io.joinasr.app.ui.screens.ChallengeEndedScreen
import io.joinasr.app.ui.screens.ChooseAppsScreen
import io.joinasr.app.ui.screens.CheckEmailScreen
import io.joinasr.app.ui.screens.DailyLimitsScreen
import io.joinasr.app.ui.screens.DashboardScreen
import io.joinasr.app.ui.screens.DeleteAccountScreen
import io.joinasr.app.ui.screens.ForgotPasswordScreen
import io.joinasr.app.legal.LegalTexts
import io.joinasr.app.ui.screens.LegalScreen
import io.joinasr.app.ui.screens.LogInScreen
import io.joinasr.app.ui.screens.PersonalDetailsScreen
import io.joinasr.app.ui.screens.ProfileDestination
import io.joinasr.app.ui.screens.ProfileScreen
import io.joinasr.app.ui.screens.ProgressScreen
import io.joinasr.app.ui.screens.ResetPasswordScreen
import io.joinasr.app.ui.screens.ReviewScreen
import io.joinasr.app.ui.screens.ProtectionLostScreen
import io.joinasr.app.ui.screens.ProtectionScreen
import io.joinasr.app.ui.screens.SecurityScreen
import io.joinasr.app.ui.screens.SignUpScreen
import io.joinasr.app.ui.screens.UsageAccessScreen
import io.joinasr.app.ui.screens.WelcomeScreen
import io.joinasr.app.ui.screens.WitnessesScreen
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
    /** The token from a password-reset link, when the app was opened by one. */
    resetToken: String? = null,
    onResetTokenHandled: () -> Unit = {},
    viewModel: SessionViewModel = viewModel(),
    pactViewModel: PactViewModel = viewModel(),
    witnessViewModel: WitnessViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val pactState by pactViewModel.state.collectAsStateWithLifecycle()
    val endedUnseen by pactViewModel.endedUnseen.collectAsStateWithLifecycle()
    val witnesses by witnessViewModel.witnesses.collectAsStateWithLifecycle()
    val pendingShare by witnessViewModel.pendingShare.collectAsStateWithLifecycle()
    val inviting by witnessViewModel.inviting.collectAsStateWithLifecycle()
    val witnessError by witnessViewModel.error.collectAsStateWithLifecycle()
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
    // Figma 31, which sits on top of Personal Details rather than in the
    // profile's row list: the design puts it at the bottom of that screen.
    var deletingAccount by remember { mutableStateOf(false) }
    // Figma 27, opened from the NOT PROTECTED pill on the dashboard.
    var showingProtectionLost by remember { mutableStateOf(false) }
    // True between committing a pact and pressing "Go to dashboard" on
    // Figma 12. Not stored: it is a moment, not a state of the challenge,
    // and a person who closes the app during it has still started.
    var justStarted by remember { mutableStateOf(false) }

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

    // A reset link opens the reset screen from wherever the app was. It also
    // signs the person out first: the token is proof of reaching the inbox,
    // and the server revokes every session when it is spent, so staying on a
    // signed-in screen behind it would only mean the next request 401s.
    LaunchedEffect(resetToken) {
        val token = resetToken ?: return@LaunchedEffect
        viewModel.signOut()
        destination = Destination.ResetPassword(token)
        onResetTokenHandled()
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
            deletingAccount || showingProtectionLost,
    ) {
        when {
            showingProtectionLost -> showingProtectionLost = false
            deletingAccount -> deletingAccount = false
            profileRoute != null -> profileRoute = null
            addingWitness -> addingWitness = false
            else -> tab = AsrTab.Home
        }
    }

    // Read into a local so the branch below can smart-cast it. A delegated
    // property cannot be, and `!!` on the thing that tells somebody their
    // challenge broke is not where to be casual.
    val ended = endedUnseen

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
            } else if (pactState is PactState.Loading) {
                // One read of a small file. Blank rather than a spinner, for
                // the same reason as above.
                Box(Modifier.fillMaxSize().background(AsrColors.Background))
            } else if (pactState is PactState.None && ended != null) {
                // Figma 26. A challenge that ended is shown once, before the
                // setup flow: somebody whose pact broke overnight should not
                // open the app to a duration picker and have to work out
                // what happened from its absence.
                ChallengeEndedScreen(
                    outcome = ended,
                    onStartNew = {
                        setupStep = SetupStep.Duration
                        pactViewModel.acknowledgeEnded()
                    },
                    onDismiss = pactViewModel::acknowledgeEnded,
                )
            } else if (pactState is PactState.None) {
                when (setupStep) {
                    // Figma 04. The first step, and the only one with nothing
                    // behind it: its frame has no chevron for that reason.
                    SetupStep.Duration -> ChallengeDurationScreen(
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
                        fromName = current.me.name,
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
            } else if (justStarted) {
                // Figma 12.
                ChallengeStartedScreen(
                    days = (pactState as PactState.Active).pact.durationDays,
                    witnesses = witnesses.size,
                    protectionReady = PermissionState.read(context).requiredGranted,
                    onContinue = { justStarted = false },
                )
            } else {
                val activePact = (pactState as PactState.Active).pact
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
                            AsrTab.Home -> if (showingProtectionLost) {
                                // Figma 27.
                                ProtectionLostScreen(
                                    onBack = { showingProtectionLost = false },
                                    onDismiss = { showingProtectionLost = false },
                                )
                            } else {
                                DashboardScreen(
                                    pact = activePact,
                                    onProtectionLost = { showingProtectionLost = true },
                                )
                            }

                            AsrTab.Progress -> ProgressScreen(pact = activePact)

                            AsrTab.Witnesses -> if (addingWitness) {
                                AddWitnessesScreen(
                                    fromName = current.me.name,
                                    challengeDays = activePact.durationDays,
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
                                WitnessesScreen(
                                    witnesses = witnesses,
                                    onAdd = { addingWitness = true },
                                    addEnabled = witnesses.size < Relationships.SLOTS,
                                )
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
