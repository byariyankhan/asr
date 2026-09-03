package io.joinasr.app.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {

    private fun app(packageName: String, label: String) = AppEntry(packageName, label)

    private val instagram = app("com.instagram.android", "Instagram")
    private val youtube = app("com.google.android.youtube", "YouTube")
    private val facebook = app("com.facebook.katana", "Facebook")
    private val calculator = app("com.example.calc", "Calculator")
    private val alarm = app("com.example.alarm", "Alarm")
    private val zebra = app("com.example.zebra", "zebra notes")

    @Test
    fun `the attention apps come first, in the order the design shows them`() {
        val ordered = AppCatalog.ordered(listOf(calculator, facebook, alarm, youtube, instagram))
        assertEquals(
            listOf("Instagram", "YouTube", "Facebook", "Alarm", "Calculator"),
            ordered.map { it.label },
        )
    }

    @Test
    fun `everything else is alphabetical regardless of case`() {
        // A list sorted by raw String order would put "zebra notes" before
        // "Alarm", because every capital letter sorts below every lowercase
        // one. That is not alphabetical to anybody reading the screen.
        val ordered = AppCatalog.ordered(listOf(zebra, calculator, alarm))
        assertEquals(listOf("Alarm", "Calculator", "zebra notes"), ordered.map { it.label })
    }

    @Test
    fun `two apps with the same name keep a stable order`() {
        val first = app("com.a.timer", "Timer")
        val second = app("com.b.timer", "Timer")
        assertEquals(
            listOf("com.a.timer", "com.b.timer"),
            AppCatalog.ordered(listOf(second, first)).map { it.packageName },
        )
    }

    @Test
    fun `settings is never offered, whatever else is`() {
        // The whole point: a person who can block Settings cannot revoke the
        // permissions this app runs on.
        val offered = AppCatalog.offerable(
            listOf(instagram, app("com.android.settings", "Settings")),
            alsoExcluded = emptySet(),
        )
        assertEquals(listOf("Instagram"), offered.map { it.label })
    }

    @Test
    fun `the device's own answers are excluded too`() {
        val launcher = app("com.oem.launcher", "Home")
        val offered = AppCatalog.offerable(
            listOf(instagram, launcher),
            alsoExcluded = setOf("com.oem.launcher"),
        )
        assertFalse(offered.any { it.packageName == "com.oem.launcher" })
        assertTrue(offered.any { it.packageName == "com.instagram.android" })
    }

    @Test
    fun `a blank search is not a search`() {
        val all = listOf(instagram, youtube)
        assertEquals(all, AppCatalog.search(all, ""))
        assertEquals(all, AppCatalog.search(all, "   "))
    }

    @Test
    fun `a name that starts with the query beats one that merely contains it`() {
        val linkedin = app("com.linkedin.android", "LinkedIn")
        // "in" is inside "LinkedIn" and at the front of "Instagram".
        assertEquals(
            listOf("Instagram", "LinkedIn"),
            AppCatalog.search(listOf(linkedin, instagram), "in").map { it.label },
        )
    }

    @Test
    fun `search ignores case and drops what does not match`() {
        val found = AppCatalog.search(listOf(instagram, youtube, facebook), "TUBE")
        assertEquals(listOf("YouTube"), found.map { it.label })
    }

    @Test
    fun `the count reads as a sentence at every size`() {
        assertEquals("No apps selected", AppCatalog.selectionSummary(0))
        assertEquals("1 app selected", AppCatalog.selectionSummary(1))
        assertEquals("2 apps selected", AppCatalog.selectionSummary(2))
    }

    @Test
    fun `this app cannot be used to block itself`() {
        assertTrue("io.joinasr.app" in AppCatalog.NeverOffered)
    }
}
