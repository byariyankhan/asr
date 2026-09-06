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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.data.Me
import io.joinasr.app.profile.Choice
import io.joinasr.app.profile.Countries
import io.joinasr.app.profile.Genders
import io.joinasr.app.profile.PhotoPrep
import io.joinasr.app.ui.components.AsrProfilePhoto
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrSelectField
import io.joinasr.app.ui.components.AsrTextField
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Figma 29 — Profile / Personal Details (node 117:2).
 *
 * Everything on it is what the server holds, edited in place. Date of birth
 * is shown and not editable: it is the field the 13-or-older rule is
 * enforced on, and an account that can quietly change its age is not one
 * that has an age. Changing it is a support request, which is the same
 * answer every service that takes an age gives.
 *
 * The frame has no save button. One is here anyway, and only once something
 * has actually changed: a screen that writes to the server on every
 * keystroke is a screen that cannot be left alone halfway through a name.
 */
@Composable
fun PersonalDetailsScreen(
    me: Me,
    onBack: () -> Unit,
    onSave: (firstName: String, lastName: String, country: String, gender: String) -> Unit,
    onPhotoPicked: (ByteArray) -> Unit,
    onDeleteAccount: () -> Unit,
    deleteAvailable: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var firstName by remember(me.firstName) { mutableStateOf(me.firstName.orEmpty()) }
    var lastName by remember(me.lastName) { mutableStateOf(me.lastName.orEmpty()) }
    var country by remember(me.country) {
        mutableStateOf(Countries.all.firstOrNull { it.value == me.country })
    }
    var gender by remember(me.gender) {
        mutableStateOf(Genders.all.firstOrNull { it.value == me.gender })
    }
    var photoError by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    // Chosen but not sent. This screen has a Save button and every other
    // field on it waits for that button; a photo that uploaded itself the
    // moment it was picked meant there was no way to change your mind, and
    // no way to tell what "Save changes" was even for.
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }

    // The server's copy has arrived, so the local one has done its job.
    LaunchedEffect(me.image) {
        picked = null
        preview = null
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> pending = uri }

    LaunchedEffect(pending) {
        val uri = pending ?: return@LaunchedEffect
        photoError = null
        when (val result = PhotoPrep.prepare(context, uri)) {
            is PhotoPrep.Result.Ok -> {
                // Decoded from the bytes that will be sent, so what is on
                // screen is exactly what will be saved -- crop, rotation and
                // all -- rather than a hopeful preview of something else.
                picked = result.jpeg
                preview = BitmapFactory
                    .decodeByteArray(result.jpeg, 0, result.jpeg.size)
                    ?.asImageBitmap()
            }

            is PhotoPrep.Result.Failed -> photoError = result.message
        }
        pending = null
    }

    val changed = firstName.trim() != me.firstName.orEmpty() ||
        lastName.trim() != me.lastName.orEmpty() ||
        country?.value != me.country ||
        gender?.value != me.gender ||
        picked != null
    val ready = changed && firstName.isNotBlank() && country != null && gender != null && !submitting

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(26.dp))
        Text("PROFILE", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(12.dp))
        Text("Personal details", style = AsrType.display(32), color = AsrColors.TextPrimary)

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                // The locally decoded bytes while the upload is in flight,
                // the server's copy once it lands. Waiting for the round
                // trip would leave the old photo on screen for a second
                // after choosing a new one, which reads as nothing having
                // happened -- which is exactly how this screen was reported.
                val chosen = preview
                if (chosen == null) {
                    AsrProfilePhoto(imagePath = me.image, fallback = me.name, size = 70.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(AsrColors.Field)
                            .border(1.dp, AsrColors.FieldBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = chosen,
                            contentDescription = "The photo you chose",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (submitting) 0.45f else 1f),
                        )
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = AsrColors.Accent,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(AsrColors.SurfaceSunken)
                    .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(21.dp))
                    .clickable(role = Role.Button) {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Change photo",
                    style = AsrType.Field.copy(fontSize = 14.sp),
                    color = AsrColors.Accent,
                )
            }
        }
        photoError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(24.dp))
        AsrTextField(
            label = "First name",
            value = firstName,
            onValueChange = { firstName = it.take(40) },
            placeholder = "Your first name",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(16.dp))
        AsrTextField(
            label = "Last name",
            value = lastName,
            onValueChange = { lastName = it.take(40) },
            placeholder = "Your last name",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Spacer(Modifier.height(16.dp))
        ReadOnlyField(label = "Date of birth", value = readableDate(me.dateOfBirth))

        Spacer(Modifier.height(16.dp))
        AsrSelectField(
            label = "Country",
            selected = country,
            placeholder = "Select country",
            options = Countries.all,
            optionLabel = Choice::label,
            onSelect = { country = it },
            searchPlaceholder = "Search countries",
        )

        Spacer(Modifier.height(16.dp))
        AsrSelectField(
            label = "Gender",
            selected = gender,
            placeholder = "Select gender",
            options = Genders.all,
            optionLabel = Choice::label,
            onSelect = { gender = it },
        )

        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(22.dp))
        AsrPrimaryButton(
            text = if (submitting) "Saving…" else "Save changes",
            onClick = {
                val chosenCountry = country ?: return@AsrPrimaryButton
                val chosenGender = gender ?: return@AsrPrimaryButton
                // The photo first. It is the change that can fail on its own
                // -- storage, size, a format the server refuses -- and
                // sending it before the rest means a refusal is reported
                // while the fields still hold what was typed.
                picked?.let(onPhotoPicked)
                onSave(firstName.trim(), lastName.trim(), chosenCountry.value, chosenGender.value)
            },
            enabled = ready,
        )

        Spacer(Modifier.height(30.dp))
        Text("Account", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        DeleteRow(onClick = onDeleteAccount, enabled = deleteAvailable)
        Spacer(Modifier.height(28.dp))
    }
}

/** A value the server holds and this screen does not let anybody change. */
@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = AsrType.Label, color = AsrColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(AsrColors.SurfaceSunken, RoundedCornerShape(16.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 15.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                value,
                style = AsrType.Field,
                color = AsrColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeleteRow(onClick: () -> Unit, enabled: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(AsrColors.SurfaceSunken)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Delete account & data",
            style = AsrType.Field.copy(fontSize = 15.sp),
            color = if (enabled) AsrColors.Error else AsrColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "›",
            style = AsrType.display(24),
            color = if (enabled) AsrColors.TextSecondary else AsrColors.TextTertiary,
        )
    }
}

/**
 * "2000-02-14" as "14 February 2000", in the reader's own language.
 *
 * Anything unparseable is shown as it arrived rather than swallowed: a date
 * the app cannot read is worth seeing, not hiding behind a dash.
 */
private fun readableDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "Not set"
    return runCatching {
        LocalDate.parse(iso).format(
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()),
        )
    }.getOrDefault(iso)
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun PersonalDetailsPreview() {
    AsrTheme {
        PersonalDetailsScreen(
            me = Me(
                id = "1",
                name = "Ariyan Khan",
                firstName = "Ariyan",
                lastName = "Khan",
                email = "ariyan@example.com",
                dateOfBirth = "2000-02-14",
                country = "BD",
                gender = "male",
            ),
            onBack = {},
            onSave = { _, _, _, _ -> },
            onPhotoPicked = {},
            onDeleteAccount = {},
            deleteAvailable = false,
            submitting = false,
            errorMessage = null,
        )
    }
}
