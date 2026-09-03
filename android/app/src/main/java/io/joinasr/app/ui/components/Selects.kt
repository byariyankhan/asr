package io.joinasr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType

/**
 * A field that opens a list instead of a keyboard, drawn to match
 * AsrTextField so a form of both does not look like two forms.
 *
 * The list arrives in a bottom sheet rather than a dropdown menu. A dropdown
 * anchored to a field near the bottom of the screen opens upward over the
 * thing you were reading, and it cannot hold 249 countries with a search box
 * at the top. A sheet does both, and is where an Android reader expects a
 * long choice to appear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AsrSelectField(
    label: String,
    selected: T?,
    placeholder: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Shown above the list. Null for a short list that needs no search. */
    searchPlaceholder: String? = null,
    /** Filter for the typed query. Defaults to a substring match on the label. */
    filter: ((String) -> List<T>)? = null,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = AsrType.Label, color = AsrColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AsrColors.Field)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) {
                    query = ""
                    open = true
                }
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = selected?.let(optionLabel) ?: placeholder,
                style = AsrType.Field,
                color = if (selected == null) AsrColors.TextSecondary else AsrColors.TextPrimary,
            )
            Text("⌄", style = AsrType.Field, color = AsrColors.TextSecondary)
        }
    }

    if (open) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = state,
            containerColor = AsrColors.Surface,
            contentColor = AsrColors.TextPrimary,
        ) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                Text(label, style = AsrType.Label, color = AsrColors.TextSecondary)
                Spacer(Modifier.height(12.dp))

                if (searchPlaceholder != null) {
                    AsrTextField(
                        label = "",
                        value = query,
                        onValueChange = { query = it },
                        placeholder = searchPlaceholder,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                val shown = if (searchPlaceholder != null && filter != null) filter(query) else options
                if (shown.isEmpty()) {
                    Text(
                        "Nothing matches that.",
                        style = AsrType.Body,
                        color = AsrColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    // Lazy, and capped: the country list is 249 rows and
                    // composing all of them to show eight is the difference
                    // between a sheet that opens and one that stutters.
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(shown) { option ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button) {
                                        onSelect(option)
                                        open = false
                                    }
                                    .padding(vertical = 14.dp, horizontal = 4.dp),
                            ) {
                                Text(
                                    optionLabel(option),
                                    style = AsrType.Body,
                                    color = if (option == selected) {
                                        AsrColors.Accent
                                    } else {
                                        AsrColors.TextPrimary
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
