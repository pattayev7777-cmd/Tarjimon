package com.example

import com.example.data.model.AppLanguage
import com.example.data.model.AuthorizedAdmins
import com.example.data.model.SubtitleCue
import com.example.data.model.SubtitleExporter
import com.example.data.model.UserAccount
import com.example.data.remote.YouTubeExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun authorizedAdmins_matchesExactConfiguredEmailsAndRejectsOthers() {
        assertTrue(AuthorizedAdmins.isAuthorizedAdmin("muhammadadhamov0000@gamil.com"))
        assertTrue(AuthorizedAdmins.isAuthorizedAdmin("akbarakramov0000@gmail.com"))
        assertTrue(AuthorizedAdmins.isAuthorizedAdmin("pattayev7777@gamail.com"))
        assertTrue(AuthorizedAdmins.isAuthorizedAdmin("Pattayev7777@gmail.com"))
        assertFalse(AuthorizedAdmins.isAuthorizedAdmin("ordinaryuser@gmail.com"))
    }

    @Test
    fun subtitleExporter_exportsSrtVttAndTxtInUzbekRussianAndArabicRtl() {
        val cues = listOf(
            SubtitleCue(
                index = 1,
                startTime = "00:00:01,000",
                endTime = "00:00:04,250",
                speaker = "Host",
                originalText = "Welcome to artificial intelligence.",
                translatedText = "Sun’iy intellekt olamiga xush kelibsiz — O‘zbekiston."
            ),
            SubtitleCue(
                index = 2,
                startTime = "00:00:04,500",
                endTime = "00:00:08,000",
                speaker = "Guest",
                originalText = "Hello world",
                translatedText = "مرحباً بكم في عالم الذكاء الاصطناعي"
            )
        )

        val srt = SubtitleExporter.exportSrt(cues)
        assertTrue(srt.contains("00:00:01,000 --> 00:00:04,250"))
        assertTrue(srt.contains("O‘zbekiston"))

        val vttRtl = SubtitleExporter.exportVtt(cues, isRtl = AppLanguage.AR.isRtl)
        assertTrue(vttRtl.startsWith("WEBVTT"))
        assertTrue(vttRtl.contains("00:00:01.000 --> 00:00:04.250"))
        assertTrue(vttRtl.contains("\u202Bمرحباً بكم في عالم الذكاء الاصطناعي\u202C"))

        val txt = SubtitleExporter.exportTxt(cues)
        assertTrue(txt.contains("[00:00:01,000 - 00:00:04,250] Host:"))
    }

    @Test
    fun youTubeExtractor_extractsVideoIdFromStandardAndShortUrls() {
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeExtractor.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            "aircAruvnKk",
            YouTubeExtractor.extractVideoId("https://youtu.be/aircAruvnKk?si=12345")
        )
    }

    @Test
    fun userAccount_tracksFreeBalanceAndSubscriptionExpiration() {
        val now = 1_700_000_000_000L
        val freeUser = UserAccount(freeTranslationsRemaining = 5L, subscriptionStatus = "FREE")
        assertTrue(freeUser.canTranslate(now))

        val exhaustedFreeUser = freeUser.copy(freeTranslationsRemaining = 0L)
        assertFalse(exhaustedFreeUser.canTranslate(now))

        val activeProUser = exhaustedFreeUser.copy(
            subscriptionStatus = "ACTIVE",
            subscriptionStartAtMillis = now,
            subscriptionExpiresAtMillis = now + AuthorizedAdmins.SUBSCRIPTION_DURATION_MILLIS
        )
        assertTrue(activeProUser.canTranslate(now + 1000L))
        assertEquals(30, activeProUser.remainingSubscriptionDays(now))

        // After 30 days, subscription automatically expires
        val after31Days = now + AuthorizedAdmins.SUBSCRIPTION_DURATION_MILLIS + 1000L
        assertFalse(activeProUser.canTranslate(after31Days))
        assertEquals(0, activeProUser.remainingSubscriptionDays(after31Days))
    }
}
