package io.joinasr.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.joinasr.app.profile.Choice
import io.joinasr.app.profile.Countries
import io.joinasr.app.profile.DateOfBirth
import io.joinasr.app.profile.Genders
import io.joinasr.app.profile.PhotoPrep
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrSelectField
import io.joinasr.app.ui.components.AsrTextField
import io.joinasr.app.ui.components.DateMask
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 03 — Profile Setup / About You (node 44:2).
 *
 * One deliberate departure from the frame: the photo is **optional**, where
 * the design marks it required. The founder's call, and the reason it can be
 * made at all is that nothing in the app needs a face to work — a witness
 * sees a name. Requiring one before somebody has used the app costs
 * sign-ups for decoration.
 *
 * The other three fields are required, and date of birth genuinely is: the
 * server refuses an account under thirteen, so it has to be asked.
 */
@Composable
fun AboutYouScreen(
    onBack: () -> Unit,
    onSubmit: (name: String, dobIso: String, country: String, gender: String) -> Unit,
    onPhotoPicked: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(initialName) }
    var dob by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<Choice?>(null) }
    var gender by remember { mutableStateOf<Choice?>(null) }
    var preview by remember { mutableStateOf<ByteArray?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Uri?>(null) }

    // PickVisualMedia rather than a storage permission: it hands back one
    // item the person chose and nothing else, and needs no permission at all.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> pending = uri }

    LaunchedEffect(pending) {
        val uri = pending ?: return@LaunchedEffect
        photoError = null
        when (val result = PhotoPrep.prepare(context, uri)) {
            is PhotoPrep.Result.Ok -> {
                preview = result.jpeg
                onPhotoPicked(result.jpeg)
            }
            is PhotoPrep.Result.Failed -> photoError = result.message
        }
        pending = null
    }

    val dobResult = DateOfBirth.validate(dob)
    val ready = name.isNotBlank() &&
        dobResult is DateOfBirth.Result.Valid &&
        country != null &&
        gender != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", style = AsrType.display(30), color = AsrColors.TextPrimary)
        }

        Spacer(Modifier.height(24.dp))
        Text("COMPLETE PROFILE", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(12.dp))
        Text("Tell us about you.", style = AsrType.display(36), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "This helps personalize your experience and measure progress.",
            style = AsrType.Body.copy(fontSize = AsrType.Label.fontSize),
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(AsrColors.Field)
                    .border(1.dp, AsrColors.FieldBorder, CircleShape)
                    .clickable(role = Role.Button) {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                val bytes = preview
                if (bytes == null) {
                    Text("+", style = AsrType.display(28), color = AsrColors.Accent)
                } else {
                    // Decoded from the bytes that were prepared for upload,
                    // so what is shown is exactly what will be sent -- crop,
                    // rotation and all.
                    val bitmap = remember(bytes) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Your profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (preview == null) "Add profile photo" else "Change photo",
                    style = AsrType.Field.copy(fontWeight = AsrType.Button.fontWeight),
                    color = AsrColors.TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    photoError ?: "Optional",
                    style = AsrType.Legal.copy(fontSize = AsrType.Label.fontSize),
                    color = if (photoError == null) AsrColors.TextSecondary else AsrColors.Error,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        AsrTextField(
            label = "Full name",
            value = name,
            onValueChange = { name = it.take(80) },
            placeholder = "Your name",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(16.dp))
        AsrTextField(
            label = "Date of birth",
            value = dob,
            // The value is the digits; the separators are drawn over them by
            // DateMask. Reformatting the value here instead is what made this
            // field impossible to type into.
            onValueChange = { dob = DateOfBirth.digitsOf(it) },
            placeholder = "DD / MM / YYYY",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = DateMask,
        )
        (dobResult as? DateOfBirth.Result.Invalid)?.let {
            Spacer(Modifier.height(6.dp))
            Text(it.message, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(16.dp))
        AsrSelectField(
            label = "Country",
            selected = country,
            placeholder = "Select country",
            options = Countries.all,
            optionLabel = { it.label },
            onSelect = { country = it },
            searchPlaceholder = "Search countries",
            filter = { Countries.search(it) },
        )

        Spacer(Modifier.height(16.dp))
        AsrSelectField(
            label = "Gender",
            selected = gender,
            placeholder = "Select gender",
            options = Genders.all,
            optionLabel = { it.label },
            onSelect = { gender = it },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Options include “Prefer not to say”.",
            style = AsrType.Legal,
            color = AsrColors.TextTertiary,
        )

        Spacer(Modifier.height(28.dp))
        AsrPrimaryButton(
            text = if (submitting) "Saving…" else "Continue",
            onClick = {
                val iso = (dobResult as? DateOfBirth.Result.Valid)?.iso ?: return@AsrPrimaryButton
                val chosenCountry = country ?: return@AsrPrimaryButton
                val chosenGender = gender ?: return@AsrPrimaryButton
                onSubmit(name.trim(), iso, chosenCountry.value, chosenGender.value)
            },
            enabled = ready && !submitting,
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                errorMessage,
                style = AsrType.Label,
                color = AsrColors.Error,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "You can change profile details later.",
            style = AsrType.Legal,
            color = AsrColors.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun AboutYouPreview() {
    AsrTheme {
        AboutYouScreen(
            onBack = {},
            onSubmit = { _, _, _, _ -> },
            onPhotoPicked = {},
            initialName = "ariyanfiles",
        )
    }
}
