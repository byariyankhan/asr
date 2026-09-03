package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.legal.LegalDocument
import io.joinasr.app.legal.LegalTexts
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 36 and 37 — the privacy policy and the terms.
 *
 * One screen for both. The two frames are identical but for their words, and
 * two files of the same layout would be two places to keep the type scale
 * right. The words live in [LegalTexts], which is also where the two places
 * the frames describe the wrong permission are corrected.
 */
@Composable
fun LegalScreen(
    document: LegalDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(22.dp))
        Text(document.eyebrow, style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(12.dp))
        Text(document.title, style = AsrType.display(32), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            document.effective,
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextTertiary,
        )

        Spacer(Modifier.height(26.dp))
        for (section in document.sections) {
            Text(
                section.heading,
                style = AsrType.CardTitle.copy(fontSize = 17.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                section.body,
                style = AsrType.Legal.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = AsrColors.TextSecondary,
            )
            Spacer(Modifier.height(22.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun PrivacyPreview() {
    AsrTheme { LegalScreen(document = LegalTexts.privacy, onBack = {}) }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun TermsPreview() {
    AsrTheme { LegalScreen(document = LegalTexts.terms, onBack = {}) }
}
