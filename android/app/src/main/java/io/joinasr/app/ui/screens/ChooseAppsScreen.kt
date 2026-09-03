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
import io.joinasr.app.apps.AppCatalog
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.apps.ChooseAppsViewModel
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrSearchField
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 06 — Setup / Choose Apps (node 55:2).
 *
 * The list is real: every app on the phone with a launcher entry, minus the
 * ones it would be dangerous to let a person block (see AppCatalog and
 * InstalledApps). A phone can have two hundred of them, so this is a
 * LazyColumn with the header scrolling above it rather than a Column in a
 * scroll container — the latter composes every row on first frame and takes
 * most of a second doing it.
 *
 * The design draws lettered tiles because Figma has no way to show a real
 * icon. Real icons are shipped instead: a person picking four apps out of
 * two hundred recognises them by their icon long before they read the name.
 * The lettered tile stays as the fallback for an app whose icon will not
 * load, which is exactly what the design describes.
 */
@Composable
fun ChooseAppsScreen(
    onBack: () -> Unit,
    onContinue: (List<AppEntry>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChooseAppsViewModel = viewModel(),
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val icons by viewModel.icons.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val enough = selected.size >= AppCatalog.MINIMUM_SELECTED

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
                    Text("SETUP 3 OF 6", style = AsrType.Eyebrow, color = AsrColors.Accent)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Choose the apps\nyou want to control.",
                        style = AsrType.display(34),
                        color = AsrColors.TextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Pick the apps that steal your attention. You'll set daily limits next.",
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

            items(visible, key = { it.packageName }) { entry ->
                AppRow(
                    entry = entry,
                    icon = icons[entry.packageName],
                    selected = entry.packageName in selected,
                    onToggle = { viewModel.toggle(entry.packageName) },
                )
            }

            // Three states, and the empty one is two different things: the
            // phone's apps are still being read, or the search matched
            // nothing. Saying "no apps found" during the first would be a lie
            // that arrives half a second before the list does.
            if (visible.isEmpty()) {
                item {
                    Text(
                        if (loading) {
                            "Reading the apps on your phone…"
                        } else {
                            "No app matches that name."
                        },
                        style = AsrType.Label,
                        color = AsrColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
        }

        // Outside the scrolling list: the count and the button are the two
        // things a person needs while choosing, and they should not have to
        // scroll two hundred rows to find out whether they can continue.
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    AppCatalog.selectionSummary(selected.size),
                    style = AsrType.Label,
                    color = if (enough) AsrColors.Accent else AsrColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                // The design shows this hint permanently. It is shown only
                // while it is still true: a rule a person has already met is
                // not guidance, it is noise sitting next to the button.
                if (!enough) {
                    Text(
                        "Select at least ${AppCatalog.MINIMUM_SELECTED} app",
                        style = AsrType.Legal.copy(fontSize = 12.sp),
                        color = AsrColors.TextTertiary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            AsrPrimaryButton(
                text = "Continue",
                onClick = { onContinue(viewModel.chosen()) },
                enabled = enough,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Only app names and usage time are used for your challenge.",
                style = AsrType.Legal.copy(fontSize = 10.sp),
                color = AsrColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One app: icon, name, and a tick that is the whole row's target. */
@Composable
private fun AppRow(
    entry: AppEntry,
    icon: ImageBitmap?,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(shape)
            .background(if (selected) AsrColors.SurfaceSelected else AsrColors.Surface)
            .border(1.dp, if (selected) AsrColors.Accent else AsrColors.SurfaceBorder, shape)
            // The whole row toggles, not just the tick. A 24dp checkbox is
            // below the minimum a thumb reliably hits, and there is nothing
            // else on the row a tap could have meant.
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry = entry, icon = icon, selected = selected)
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
        Tick(selected)
    }
}

@Composable
private fun AppIcon(entry: AppEntry, icon: ImageBitmap?, selected: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(AsrColors.Background)
            .border(1.dp, AsrColors.SurfaceBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                // Named, because a list of two hundred unlabelled images is
                // unusable with a screen reader — and the row's own text is
                // not attached to the image.
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
}

@Composable
private fun Tick(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) AsrColors.Accent else AsrColors.Background)
            .border(
                1.dp,
                if (selected) AsrColors.Accent else AsrColors.SurfaceBorder,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text("✓", style = AsrType.Button.copy(fontSize = 14.sp), color = AsrColors.OnAccent)
        }
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ChooseAppsPreview() {
    // No ViewModel in a preview: it would read the preview host's package
    // list. The screen is previewed through its parts on purpose.
    AsrTheme {
        Column(Modifier.fillMaxSize().background(AsrColors.Background).padding(24.dp)) {
            AppRow(AppEntry("com.instagram.android", "Instagram"), null, true) {}
            Spacer(Modifier.height(12.dp))
            AppRow(AppEntry("com.facebook.katana", "Facebook"), null, false) {}
        }
    }
}
