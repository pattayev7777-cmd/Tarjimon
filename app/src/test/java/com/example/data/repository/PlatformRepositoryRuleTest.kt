package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SubtitleCue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.UUID

@Config(sdk = [36])
class PlatformRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureOrGetUserAccount_createsAccountWithFiveFreeTranslations_andDeductsOnTranslation() = runBlocking {
        val uniqueEmail = "alice_${UUID.randomUUID().toString().take(8)}@test.com"
        val uid = signInTestUser(uniqueEmail)
        val repository = PlatformRepository(firestore)

        val accountResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureOrGetUserAccount(uid, uniqueEmail, "Alice")
        }
        assertTrue("Expected account creation to succeed: ${accountResult.exceptionOrNull()}", accountResult.isSuccess)
        val account = accountResult.getOrThrow()
        assertEquals(5L, account.freeTranslationsRemaining)
        assertEquals("FREE", account.subscriptionStatus)

        val cues = listOf(
            SubtitleCue(1, "00:00:01,000", "00:00:03,500", "Speaker 1", "Hello world", "Salom dunyo")
        )
        val recordResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordCompletedTranslation(
                userAccount = account,
                videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                videoId = "dQw4w9WgXcQ",
                videoTitle = "Sample Video",
                channelAuthor = "Creator",
                sourceLanguage = "en",
                detectedLanguage = "English",
                targetLanguage = "uz",
                cues = cues
            )
        }
        assertTrue("Expected translation recording to succeed: ${recordResult.exceptionOrNull()}", recordResult.isSuccess)

        val updatedAccount = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserAccount(uid).first { it != null && it.completedTranslationsCount == 1L }
        }
        assertNotNull(updatedAccount)
        assertEquals(4L, updatedAccount?.freeTranslationsRemaining)
    }

    @Test
    fun submitPaymentReceipt_andSupportMessage_succeedsForAuthenticatedUser() = runBlocking {
        val uniqueEmail = "payer_${UUID.randomUUID().toString().take(8)}@test.com"
        val uid = signInTestUser(uniqueEmail)
        val repository = PlatformRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureOrGetUserAccount(uid, uniqueEmail, "Payer")
        }

        val paymentResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.submitPaymentReceipt(
                userEmail = uniqueEmail,
                receiptFileName = "chek_20000.jpg",
                receiptMimeType = "image/jpeg",
                receiptDataBase64 = "base64data",
                aiReceiptSummary = "20,000 UZS receipt to Akbar"
            )
        }
        assertTrue("Expected payment submission to succeed: ${paymentResult.exceptionOrNull()}", paymentResult.isSuccess)

        val supportResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.submitSupportMessage(
                userEmail = uniqueEmail,
                category = "QUESTION",
                subject = "Help with Arabic subtitles",
                message = "How do I export VTT in RTL?"
            )
        }
        assertTrue("Expected support message to succeed: ${supportResult.exceptionOrNull()}", supportResult.isSuccess)
    }

    @Test
    fun crossUserRead_failsWithPermissionDenied() = runBlocking {
        val aliceEmail = "alice_${UUID.randomUUID().toString().take(8)}@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = PlatformRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureOrGetUserAccount(aliceUid, aliceEmail, "Alice").getOrThrow()
        }

        val bobEmail = "bob_${UUID.randomUUID().toString().take(8)}@test.com"
        signInTestUser(bobEmail)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED when Bob reads Alice's profile")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun observeUserTranslations_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = PlatformRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeUserTranslations("some_uid").first()
            }
            fail("Expected unauthenticated query to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun savePlatformSettings_nonAdmin_failsWithPermissionDenied_whileObserveReturnsDefaults() = runBlocking {
        val regularEmail = "regular_${UUID.randomUUID().toString().take(8)}@test.com"
        signInTestUser(regularEmail)
        val repository = PlatformRepository(firestore)

        val defaultSettings = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePlatformSettings().first()
        }
        assertEquals("FIREBASE_AI_LOGIC", defaultSettings.aiProviderMode)
        assertEquals(20000L, defaultSettings.subscriptionPriceUzs)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.savePlatformSettings(
                defaultSettings.copy(
                    aiProviderMode = "CUSTOM_GEMINI_KEY",
                    customGeminiApiKey = "AIzaSyUnauthorizedAttempt"
                ),
                adminEmail = regularEmail
            )
        }
        assertTrue("Expected non-admin savePlatformSettings to fail", saveResult.isFailure)
        val fsEx = saveResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

