package io.joinasr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(label, style = AsrType.Label, color = AsrColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(AsrColors.Field, RoundedCornerShape(14.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 15.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AsrType.Field.copy(color = AsrColors.TextPrimary),
                cursorBrush = SolidColor(AsrColors.Accent),
                keyboardOptions = keyboardOptions,
                visualTransformation =
                    if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
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
        }
    }
}
