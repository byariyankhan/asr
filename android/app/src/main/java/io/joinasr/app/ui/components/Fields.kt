package io.joinasr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType

/**
 * A labelled text field: 13sp label, then a 58dp box with a 14dp radius and
 * a one-pixel border. Built on BasicTextField rather than Material's
 * OutlinedTextField, which brings its own label animation, its own 56dp
 * height and its own container colours — fighting those to arrive back at
 * this design costs more than drawing it.
 */
@Composable
fun AsrTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isPassword: Boolean = false,
    // For a field whose value and its display differ -- the date, which
    // stores eight digits and shows DD / MM / YYYY. Doing it this way rather
    // than reformatting the value is what keeps the caret where the person
    // put it; see DateMask.
    visualTransformation: VisualTransformation? = null,
) {
    // A password is hidden until the person asks to see it, and stays seen
    // until they hide it again -- across a rotation too, which is where a
    // toggle that forgets is most annoying. Per field: showing the new
    // password does not show the current one beside it.
    var revealed by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(label, style = AsrType.Label, color = AsrColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(AsrColors.Field, RoundedCornerShape(14.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp))
                .padding(start = 15.dp, end = if (isPassword) 6.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AsrType.Field.copy(color = AsrColors.TextPrimary),
                cursorBrush = SolidColor(AsrColors.Accent),
                keyboardOptions = keyboardOptions,
                visualTransformation = when {
                    isPassword && !revealed -> PasswordVisualTransformation()
                    isPassword -> VisualTransformation.None
                    visualTransformation != null -> visualTransformation
                    else -> VisualTransformation.None
                },
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    // Both are emitted into one Box so the placeholder sits
                    // *behind* the text rather than above it — two siblings
                    // here would stack, and the caret would jump down a line
                    // on the first keystroke.
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = AsrType.Field,
                                color = AsrColors.TextSecondary,
                            )
                        }
                        inner()
                    }
                },
            )
            if (isPassword) {
                // A word, not an eye: the app has no icon set, and "Show" is
                // what the person is deciding. Padded out to a target a
                // thumb can hit without landing in the text.
                Text(
                    if (revealed) "Hide" else "Show",
                    style = AsrType.Label,
                    color = AsrColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { revealed = !revealed }
                        .padding(horizontal = 9.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * The search box on the app picker: 52dp, a leading magnifier, no label.
 *
 * Separate from [AsrTextField] rather than a flag on it. The two differ in
 * height, in having a label at all and in carrying a leading glyph, and a
 * shared function with three booleans deciding which of two shapes it draws
 * is harder to read than two functions that each draw one.
 */
@Composable
fun AsrSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(AsrColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, AsrColors.SurfaceBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", style = AsrType.display(22), color = AsrColors.TextSecondary)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AsrType.Field.copy(color = AsrColors.TextPrimary),
            cursorBrush = SolidColor(AsrColors.Accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = AsrType.Field, color = AsrColors.TextSecondary)
                    }
                    inner()
                }
            },
        )
    }
}
