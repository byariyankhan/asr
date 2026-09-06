package io.joinasr.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.apps.ChooseAppsViewModel
import io.joinasr.app.formatMinutes
import io.joinasr.app.limits.DailyLimit
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrSearchField
import io.joinasr.app.ui.components.AsrStepper
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * One more app, into a challenge that is already running.
 *
 * Not in the Figma file, which drew the app list as fixed for the duration.
 * It still is, in every direction but this one: an app can join, no app
 * leaves, and no limit moves. Screens 06 and 07 folded into one, because
 * this is one app and one number, and two screens for that would be
 * ceremony. The list is the same list screen 06 shows, minus the apps
 * already in the challenge; the stepper is screen 07's, on the same ladder.
 *
 * What the person is agreeing to is said above the button rather than
 * discovered afterwards: the limit counts against the whole of today, so an
 * app already over it locks the moment it is added, and there is no taking
 * it back out until the challenge ends.
 */
@Composable
fun AddAppScreen(
    /** Packages already in the challenge, which the list leaves out. */
    excluded: Set<String>,
    /** True while the server is being asked. The button says so. */
    busy: Boolean,
    /** Why the last attempt did not happen, or null. */
    errorMessage: String?,
    onBack: () -> Unit,
    onAdd: (AppEntry, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChooseAppsViewModel = viewModel(),
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val icons by viewModel.icons.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val offered = remember(visible, excluded) { visible.filter { it.packageName !in excluded } }
    // One app at a time, so the selection is the entry rather than a set.
    // The limit is remembered across rotation but not across a change of
    // app: each app starts on the default, as it does on screen 07.
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var minutes by rememberSaveable(chosen) { mutableStateOf(DailyLimit.DEFAULT_MINUTES) }
    val chosenEntry = offered.firstOrNull { it.packageName == chosen }

    Column(modifier = modifier.fillMaxSize().background(AsrColors.Background)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Spacer(Modifier.height(22.dp))
                    AsrBackChevron(onBack)

                    Spacer(Modifier.height(22.dp))
                    Text("YOUR CHALLENGE", style = AsrType.Eyebrow, color = AsrColors.Accent)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Add an app.",
                        style = AsrType.display(34),
                        color = AsrColors.TextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "It joins your challenge today, counts against today's minutes " +
                            "from now, and stays until the challenge ends.",
                        style = AsrType.Field,
                        color = AsrColors.TextSecondary,
                    )

                    Spacer(Modifier.height(22.dp))
                    AsrSearchField(
                        value = query,
                        onValueChange = viewModel::search,
                        placeholder = "Search apps",
                    )

                    Spacer(Modifier.height(20.dp))
                    Text(
                        "APPS ON YOUR PHONE",
                        style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                        color = AsrColors.TextTertiary,
                    )
                }
            }

            items(offered, key = { it.packageName }) { entry ->
                val selected = entry.packageName == chosen
                // The limit card opens under the chosen row, so the two
                // things being decided sit together on the screen.
                Column {
                    PickRow(
                        entry = entry,
                        icon = icons[entry.packageName],
                        selected = selected,
                        onSelect = { chosen = if (selected) null else entry.packageName },
                    )
                    if (selected) {
                        Spacer(Modifier.height(12.dp))
                        LimitPicker(
                            entry = entry,
                            minutes = minutes,
                            onChange = { minutes = it },
                        )
                    }
                }
            }

            if (offered.isEmpty()) {
                item {
                    Text(
                        when {
                            loading -> "Reading the apps on your phone…"
                            query.isNotBlank() -> "No app matches that name."
                            else -> "Every app on this phone is already in your challenge."
                        },
                        style = AsrType.Label,
                        color = AsrColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(14.dp))
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    style = AsrType.Label,
                    color = AsrColors.Danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                if (chosenEntry != null) {
                    "${chosenEntry.label} gets ${formatMinutes(minutes)} a day. If it has already " +
                        "had more than that today, it locks straight away."
                } else {
                    "Choose one app. You'll set its daily limit next."
                },
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            AsrPrimaryButton(
                text = if (busy) "Adding…" else "Add to challenge",
                onClick = { chosenEntry?.let { onAdd(it, minutes) } },
                enabled = chosenEntry != null && !busy,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One app: icon, name, and a single tick, since only one can be chosen. */
@Composable
private fun PickRow(
    entry: AppEntry,
    icon: ImageBitmap?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(shape)
            .background(if (selected) AsrColors.SurfaceSelected else AsrColors.Surface)
            .border(1.dp, if (selected) AsrColors.Accent else AsrColors.SurfaceBorder, shape)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AsrColors.Background)
                .border(1.dp, AsrColors.SurfaceBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = entry.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    entry.label.take(1).uppercase(),
                    style = AsrType.Button,
                    color = if (selected) AsrColors.Accent else AsrColors.TextPrimary,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            entry.label,
            style = AsrType.Field.copy(fontWeight = AsrType.RowTitle.fontWeight),
            color = AsrColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) AsrColors.Accent else AsrColors.Background)
                .border(1.dp, if (selected) AsrColors.Accent else AsrColors.SurfaceBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text("✓", style = AsrType.Button.copy(fontSize = 14.sp), color = AsrColors.OnAccent)
            }
        }
    }
}

/** Screen 07's card for the one app chosen, directly under its row. */
@Composable
private fun LimitPicker(entry: AppEntry, minutes: Int, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "DAILY LIMIT",
                style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                color = AsrColors.TextTertiary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Locks when it is spent, like the rest.",
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        AsrStepper(
            value = formatMinutes(minutes),
            label = "${entry.label} daily limit",
            canDecrease = DailyLimit.canDecrease(minutes),
            canIncrease = DailyLimit.canIncrease(minutes),
            onDecrease = { onChange(DailyLimit.decreased(minutes)) },
            onIncrease = { onChange(DailyLimit.increased(minutes)) },
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun AddAppPreview() {
    AsrTheme {
        Column(
            Modifier.fillMaxSize().background(AsrColors.Background).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PickRow(AppEntry("com.google.android.youtube", "YouTube"), icon = null, selected = false, onSelect = {})
            PickRow(AppEntry("com.zhiliaoapp.musically", "TikTok"), icon = null, selected = true, onSelect = {})
            LimitPicker(AppEntry("com.zhiliaoapp.musically", "TikTok"), minutes = 30, onChange = {})
        }
    }
}
