package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.AuthorizedAdmins
import com.example.data.model.PaymentSubmission
import com.example.data.model.PlatformSettings
import com.example.data.model.SubtitleCue
import com.example.data.model.SupportMessage
import com.example.data.model.TranslationRecord
import com.example.data.model.UserAccount
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PlatformRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureOrGetUserAccount(
        uid: String = requireUserId(),
        email: String,
        displayName: String
    ): Result<UserAccount> = runCatching {
        val docRef = db.collection("users").document(uid)
        val snapshot = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        if (snapshot.exists()) {
            val existing = snapshot.toObject(
                UserAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw IllegalStateException("Failed to deserialize UserAccount")

            // Automatic server-verified subscription expiration check
            val nowMillis = System.currentTimeMillis()
            if (existing.subscriptionStatus == "ACTIVE" &&
                existing.subscriptionExpiresAtMillis in 1..nowMillis
            ) {
                try {
                    docRef.update(
                        mapOf(
                            "subscriptionStatus" to "EXPIRED",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    sendNotificationInternal(
                        recipientUserId = uid,
                        senderUserId = uid,
                        senderEmail = email,
                        type = "SUBSCRIPTION_EXPIRED",
                        title = "Subscription Expired",
                        body = "Your 30-day Pro translation subscription has expired. Please renew your subscription (20,000 UZS) to continue unlimited translations."
                    )
                    return@runCatching existing.copy(subscriptionStatus = "EXPIRED")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                }
            }
            existing
        } else {
            val safeEmail = email.ifBlank { "user@subtranslate.ai" }.take(200)
            val safeName = displayName.ifBlank { safeEmail.substringBefore("@") }.take(100)
            val payload = mapOf(
                "userId" to uid,
                "email" to safeEmail,
                "displayName" to safeName,
                "accountStatus" to "ACTIVE",
                "subscriptionStatus" to "FREE",
                "freeTranslationsRemaining" to AuthorizedAdmins.INITIAL_FREE_TRANSLATIONS,
                "completedTranslationsCount" to 0L,
                "paidTranslationsCount" to 0L,
                "subscriptionStartAtMillis" to 0L,
                "subscriptionExpiresAtMillis" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
                sendNotificationInternal(
                    recipientUserId = "ADMIN",
                    senderUserId = uid,
                    senderEmail = safeEmail,
                    type = "NEW_USER",
                    title = "New User Registered",
                    body = "New user $safeName ($safeEmail) registered with 5 free video translations."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }

            UserAccount(
                id = uid,
                userId = uid,
                email = safeEmail,
                displayName = safeName,
                accountStatus = "ACTIVE",
                subscriptionStatus = "FREE",
                freeTranslationsRemaining = AuthorizedAdmins.INITIAL_FREE_TRANSLATIONS,
                completedTranslationsCount = 0L,
                paidTranslationsCount = 0L
            )
        }
    }

    fun observeUserAccount(uid: String): Flow<UserAccount?> {
        val docRef = db.collection("users").document(uid)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    fun observeUserTranslations(uid: String): Flow<List<TranslationRecord>> {
        val path = "translations"
        return db.collection(path)
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    TranslationRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeUserPayments(uid: String): Flow<List<PaymentSubmission>> {
        val path = "payments"
        return db.collection(path)
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PaymentSubmission::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeUserSupportMessages(uid: String): Flow<List<SupportMessage>> {
        val path = "support_messages"
        return db.collection(path)
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SupportMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeUserNotifications(uid: String): Flow<List<AppNotification>> {
        val path = "notifications"
        return db.collection(path)
            .whereEqualTo("recipientUserId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AppNotification::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun recordCompletedTranslation(
        userAccount: UserAccount,
        videoUrl: String,
        videoId: String,
        videoTitle: String,
        channelAuthor: String,
        sourceLanguage: String,
        detectedLanguage: String,
        targetLanguage: String,
        cues: List<SubtitleCue>
    ): Result<TranslationRecord> = runCatching {
        val uid = requireUserId()
        require(userAccount.accountStatus == "ACTIVE") {
            "Your account is currently suspended. Please contact an administrator."
        }

        val nowMillis = System.currentTimeMillis()
        val isPaid = userAccount.isSubscriptionCurrentlyActive(nowMillis)
        if (!isPaid && userAccount.freeTranslationsRemaining <= 0L) {
            throw IllegalStateException("Free translation limit (5 videos) reached. Please purchase a 20,000 UZS monthly subscription.")
        }

        val docId = "tr_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("translations").document(docId)
        val subtitlesJson = SubtitleCue.listToJsonString(cues).take(200000)
        val origPreview = cues.joinToString(" ") { it.originalText }.take(20000)
        val transPreview = cues.joinToString(" ") { it.translatedText }.take(20000)

        val payload = mapOf(
            "userId" to uid,
            "userEmail" to userAccount.email.ifBlank { "user@subtranslate.ai" }.take(200),
            "videoUrl" to videoUrl.take(500),
            "videoId" to videoId.ifBlank { "video" }.take(64),
            "videoTitle" to videoTitle.ifBlank { "YouTube Video" }.take(300),
            "channelAuthor" to channelAuthor.ifBlank { "YouTube Creator" }.take(200),
            "sourceLanguage" to sourceLanguage,
            "detectedLanguage" to detectedLanguage.take(40),
            "targetLanguage" to targetLanguage,
            "isPaidTranslation" to isPaid,
            "subtitleCount" to cues.size.toLong(),
            "subtitlesJson" to subtitlesJson,
            "originalTranscriptPreview" to origPreview,
            "translatedTranscriptPreview" to transPreview,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }

        // Deduct usage only AFTER translation document is successfully created
        val userDocRef = db.collection("users").document(uid)
        val userUpdateMap = if (isPaid) {
            mapOf(
                "paidTranslationsCount" to (userAccount.paidTranslationsCount + 1L),
                "completedTranslationsCount" to (userAccount.completedTranslationsCount + 1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        } else {
            mapOf(
                "freeTranslationsRemaining" to (userAccount.freeTranslationsRemaining - 1L).coerceAtLeast(0L),
                "completedTranslationsCount" to (userAccount.completedTranslationsCount + 1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }

        try {
            userDocRef.update(userUpdateMap).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDocRef.path)
            throw e
        }

        TranslationRecord(
            id = docId,
            userId = uid,
            userEmail = userAccount.email,
            videoUrl = videoUrl,
            videoId = videoId,
            videoTitle = videoTitle,
            channelAuthor = channelAuthor,
            sourceLanguage = sourceLanguage,
            detectedLanguage = detectedLanguage,
            targetLanguage = targetLanguage,
            isPaidTranslation = isPaid,
            subtitleCount = cues.size.toLong(),
            subtitlesJson = subtitlesJson,
            originalTranscriptPreview = origPreview,
            translatedTranscriptPreview = transPreview
        )
    }

    suspend fun updateTranslatedSubtitles(
        translationId: String,
        updatedCues: List<SubtitleCue>
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("translations").document(translationId)
        val subtitlesJson = SubtitleCue.listToJsonString(updatedCues).take(200000)
        val transPreview = updatedCues.joinToString(" ") { it.translatedText }.take(20000)

        try {
            docRef.update(
                mapOf(
                    "subtitlesJson" to subtitlesJson,
                    "translatedTranscriptPreview" to transPreview,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun submitPaymentReceipt(
        userEmail: String,
        receiptFileName: String,
        receiptMimeType: String,
        receiptDataBase64: String,
        aiReceiptSummary: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = "pay_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("payments").document(docId)
        val safeMime = if (receiptMimeType in listOf("image/jpeg", "image/png", "application/pdf")) {
            receiptMimeType
        } else {
            "image/jpeg"
        }
        val safeEmail = userEmail.ifBlank { "user@subtranslate.ai" }.take(200)
        val payload = mapOf(
            "userId" to uid,
            "userEmail" to safeEmail,
            "amountUzs" to AuthorizedAdmins.SUBSCRIPTION_PRICE_UZS,
            "cardNumber" to AuthorizedAdmins.PAYMENT_CARD_NUMBER,
            "accountHolder" to AuthorizedAdmins.PAYMENT_ACCOUNT_HOLDER,
            "receiptFileName" to receiptFileName.ifBlank { "receipt.jpg" }.take(200),
            "receiptMimeType" to safeMime,
            "receiptDataBase64" to receiptDataBase64.take(350000),
            "aiReceiptSummary" to aiReceiptSummary.take(2000),
            "status" to "PENDING",
            "adminNote" to "",
            "reviewedByEmail" to "",
            "approvedAtMillis" to 0L,
            "subscriptionExpiresAtMillis" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            sendNotificationInternal(
                recipientUserId = "ADMIN",
                senderUserId = uid,
                senderEmail = safeEmail,
                type = "PAYMENT_SUBMITTED",
                title = "New Payment Receipt (20,000 UZS)",
                body = "$safeEmail uploaded receipt '$receiptFileName' for 20,000 UZS subscription. Status: PENDING approval."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
        docId
    }

    suspend fun submitSupportMessage(
        userEmail: String,
        category: String,
        subject: String,
        message: String,
        attachmentFileName: String = "",
        attachmentMimeType: String = "",
        attachmentDataBase64: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("support_messages").document(docId)
        val safeEmail = userEmail.ifBlank { "user@subtranslate.ai" }.take(200)
        val safeCategory = if (category in listOf("QUESTION", "TECHNICAL_PROBLEM", "TRANSLATION_ERROR", "PAYMENT_RECEIPT", "ASSISTANCE")) {
            category
        } else {
            "QUESTION"
        }
        val payload = mapOf(
            "userId" to uid,
            "userEmail" to safeEmail,
            "category" to safeCategory,
            "subject" to subject.ifBlank { "Support Request" }.take(200),
            "message" to message.ifBlank { "Support message" }.take(4000),
            "attachmentFileName" to attachmentFileName.take(200),
            "attachmentMimeType" to attachmentMimeType.take(100),
            "attachmentDataBase64" to attachmentDataBase64.take(350000),
            "status" to "OPEN",
            "adminReply" to "",
            "repliedByEmail" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            sendNotificationInternal(
                recipientUserId = "ADMIN",
                senderUserId = uid,
                senderEmail = safeEmail,
                type = "SUPPORT_MESSAGE",
                title = "Support Request [$safeCategory]",
                body = "$safeEmail: ${subject.take(80)}"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
        docId
    }

    // --- ADMINISTRATOR OPERATIONS ---

    fun observeAllUsers(): Flow<List<UserAccount>> {
        val path = "users"
        return db.collection(path)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    UserAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAllPayments(): Flow<List<PaymentSubmission>> {
        val path = "payments"
        return db.collection(path)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PaymentSubmission::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAllTranslations(): Flow<List<TranslationRecord>> {
        val path = "translations"
        return db.collection(path)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    TranslationRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAllSupportMessages(): Flow<List<SupportMessage>> {
        val path = "support_messages"
        return db.collection(path)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SupportMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAdminNotifications(): Flow<List<AppNotification>> {
        val path = "notifications"
        return db.collection(path)
            .whereEqualTo("recipientUserId", "ADMIN")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AppNotification::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun approvePayment(
        payment: PaymentSubmission,
        adminEmail: String,
        adminNote: String = "Approved 30-day subscription"
    ): Result<Unit> = runCatching {
        val adminUid = requireUserId()
        require(payment.status == "PENDING") {
            "Payment ${payment.id} has already been processed (${payment.status}). Duplicate approval blocked."
        }
        val approvedAtMillis = System.currentTimeMillis()
        val expiresAtMillis = approvedAtMillis + AuthorizedAdmins.SUBSCRIPTION_DURATION_MILLIS

        val paymentRef = db.collection("payments").document(payment.id)
        try {
            paymentRef.update(
                mapOf(
                    "status" to "APPROVED",
                    "adminNote" to adminNote.take(500),
                    "reviewedByEmail" to adminEmail.take(200),
                    "approvedAtMillis" to approvedAtMillis,
                    "subscriptionExpiresAtMillis" to expiresAtMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, paymentRef.path)
            throw e
        }

        val userRef = db.collection("users").document(payment.userId)
        try {
            userRef.update(
                mapOf(
                    "subscriptionStatus" to "ACTIVE",
                    "subscriptionStartAtMillis" to approvedAtMillis,
                    "subscriptionExpiresAtMillis" to expiresAtMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }

        sendNotificationInternal(
            recipientUserId = payment.userId,
            senderUserId = adminUid,
            senderEmail = adminEmail,
            type = "PAYMENT_APPROVED",
            title = "Payment Approved — 30-Day Subscription Active!",
            body = "Your 20,000 UZS payment receipt was approved by $adminEmail. Your 30-day Pro subscription is now active."
        )
    }

    suspend fun rejectPayment(
        payment: PaymentSubmission,
        adminEmail: String,
        adminNote: String = "Receipt could not be verified. Please upload a clear receipt."
    ): Result<Unit> = runCatching {
        val adminUid = requireUserId()
        require(payment.status == "PENDING") {
            "Payment ${payment.id} has already been processed (${payment.status})."
        }
        val paymentRef = db.collection("payments").document(payment.id)
        try {
            paymentRef.update(
                mapOf(
                    "status" to "REJECTED",
                    "adminNote" to adminNote.take(500),
                    "reviewedByEmail" to adminEmail.take(200),
                    "approvedAtMillis" to 0L,
                    "subscriptionExpiresAtMillis" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, paymentRef.path)
            throw e
        }

        sendNotificationInternal(
            recipientUserId = payment.userId,
            senderUserId = adminUid,
            senderEmail = adminEmail,
            type = "PAYMENT_REJECTED",
            title = "Payment Receipt Rejected",
            body = "Your payment receipt was not approved: $adminNote. You may upload a new receipt at any time."
        )
    }

    suspend fun setUserAccountStatus(
        targetUserId: String,
        newStatus: String // ACTIVE or SUSPENDED
    ): Result<Unit> = runCatching {
        requireUserId()
        require(newStatus in listOf("ACTIVE", "SUSPENDED")) { "Invalid status: $newStatus" }
        val userRef = db.collection("users").document(targetUserId)
        try {
            userRef.update(
                mapOf(
                    "accountStatus" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun activateUserSubscriptionManually(
        targetUserId: String,
        adminEmail: String
    ): Result<Unit> = runCatching {
        val adminUid = requireUserId()
        val nowMillis = System.currentTimeMillis()
        val expiresAtMillis = nowMillis + AuthorizedAdmins.SUBSCRIPTION_DURATION_MILLIS
        val userRef = db.collection("users").document(targetUserId)
        try {
            userRef.update(
                mapOf(
                    "subscriptionStatus" to "ACTIVE",
                    "subscriptionStartAtMillis" to nowMillis,
                    "subscriptionExpiresAtMillis" to expiresAtMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            sendNotificationInternal(
                recipientUserId = targetUserId,
                senderUserId = adminUid,
                senderEmail = adminEmail,
                type = "PAYMENT_APPROVED",
                title = "30-Day Subscription Activated",
                body = "An administrator ($adminEmail) activated a 30-day subscription on your account."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun replyToSupportMessage(
        message: SupportMessage,
        replyText: String,
        adminEmail: String,
        markResolved: Boolean
    ): Result<Unit> = runCatching {
        val adminUid = requireUserId()
        val msgRef = db.collection("support_messages").document(message.id)
        val newStatus = if (markResolved) "RESOLVED" else "OPEN"
        try {
            msgRef.update(
                mapOf(
                    "adminReply" to replyText.take(4000),
                    "repliedByEmail" to adminEmail.take(200),
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            sendNotificationInternal(
                recipientUserId = message.userId,
                senderUserId = adminUid,
                senderEmail = adminEmail,
                type = "SUPPORT_REPLY",
                title = "Admin Replied: ${message.subject.take(60)}",
                body = replyText.take(400)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            throw e
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> = runCatching {
        requireUserId()
        val notifRef = db.collection("notifications").document(notificationId)
        try {
            notifRef.update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, notifRef.path)
            throw e
        }
    }

    fun observePlatformSettings(): Flow<PlatformSettings> {
        val docRef = db.collection("app_settings").document("global_config")
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        PlatformSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: PlatformSettings()
                } else {
                    PlatformSettings()
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                emit(PlatformSettings())
            }
    }

    suspend fun savePlatformSettings(
        settings: PlatformSettings,
        adminEmail: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("app_settings").document("global_config")
        val existingSnap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        val safeMode = if (settings.aiProviderMode in listOf("FIREBASE_AI_LOGIC", "CUSTOM_GEMINI_KEY")) {
            settings.aiProviderMode
        } else {
            "FIREBASE_AI_LOGIC"
        }

        if (existingSnap.exists()) {
            val updateMap = mapOf(
                "subscriptionPriceUzs" to settings.subscriptionPriceUzs.coerceIn(0L, 100_000_000L),
                "paymentCardNumber" to settings.paymentCardNumber.trim().ifBlank { AuthorizedAdmins.PAYMENT_CARD_NUMBER }.take(64),
                "paymentAccountHolder" to settings.paymentAccountHolder.trim().ifBlank { AuthorizedAdmins.PAYMENT_ACCOUNT_HOLDER }.take(100),
                "subscriptionDurationDays" to settings.subscriptionDurationDays.coerceIn(1L, 3650L),
                "initialFreeTranslations" to settings.initialFreeTranslations.coerceIn(0L, 10_000L),
                "aiProviderMode" to safeMode,
                "customGeminiApiKey" to settings.customGeminiApiKey.trim().take(300),
                "aiModelName" to settings.aiModelName.trim().ifBlank { "gemini-4-argon" }.take(100),
                "customTranslationInstruction" to settings.customTranslationInstruction.trim().take(2000),
                "updatedByEmail" to adminEmail.take(200),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updateMap).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        } else {
            val createMap = mapOf(
                "settingId" to "global_config",
                "subscriptionPriceUzs" to settings.subscriptionPriceUzs.coerceIn(0L, 100_000_000L),
                "paymentCardNumber" to settings.paymentCardNumber.trim().ifBlank { AuthorizedAdmins.PAYMENT_CARD_NUMBER }.take(64),
                "paymentAccountHolder" to settings.paymentAccountHolder.trim().ifBlank { AuthorizedAdmins.PAYMENT_ACCOUNT_HOLDER }.take(100),
                "subscriptionDurationDays" to settings.subscriptionDurationDays.coerceIn(1L, 3650L),
                "initialFreeTranslations" to settings.initialFreeTranslations.coerceIn(0L, 10_000L),
                "aiProviderMode" to safeMode,
                "customGeminiApiKey" to settings.customGeminiApiKey.trim().take(300),
                "aiModelName" to settings.aiModelName.trim().ifBlank { "gemini-4-argon" }.take(100),
                "customTranslationInstruction" to settings.customTranslationInstruction.trim().take(2000),
                "updatedByEmail" to adminEmail.take(200),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(createMap).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        }
    }

    suspend fun adminUpdateUserFull(
        targetUserId: String,
        displayName: String,
        accountStatus: String,
        subscriptionStatus: String,
        freeTranslationsRemaining: Long,
        completedTranslationsCount: Long,
        paidTranslationsCount: Long,
        subscriptionDaysFromNow: Long
    ): Result<Unit> = runCatching {
        requireUserId()
        val safeAccountStatus = if (accountStatus in listOf("ACTIVE", "SUSPENDED")) accountStatus else "ACTIVE"
        val safeSubStatus = if (subscriptionStatus in listOf("FREE", "ACTIVE", "EXPIRED")) subscriptionStatus else "FREE"
        val nowMillis = System.currentTimeMillis()
        val startMillis = if (safeSubStatus == "ACTIVE") nowMillis else 0L
        val expiresMillis = if (safeSubStatus == "ACTIVE" && subscriptionDaysFromNow > 0L) {
            nowMillis + subscriptionDaysFromNow * 24L * 60L * 60L * 1000L
        } else {
            0L
        }

        val userRef = db.collection("users").document(targetUserId)
        try {
            userRef.update(
                mapOf(
                    "displayName" to displayName.trim().take(100),
                    "accountStatus" to safeAccountStatus,
                    "subscriptionStatus" to safeSubStatus,
                    "freeTranslationsRemaining" to freeTranslationsRemaining.coerceIn(0L, 100_000L),
                    "completedTranslationsCount" to completedTranslationsCount.coerceAtLeast(0L),
                    "paidTranslationsCount" to paidTranslationsCount.coerceAtLeast(0L),
                    "subscriptionStartAtMillis" to startMillis,
                    "subscriptionExpiresAtMillis" to expiresMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun adminDeleteUser(targetUserId: String): Result<Unit> = runCatching {
        requireUserId()
        val userRef = db.collection("users").document(targetUserId)
        try {
            userRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            throw e
        }
    }

    suspend fun adminDeletePayment(paymentId: String): Result<Unit> = runCatching {
        requireUserId()
        val paymentRef = db.collection("payments").document(paymentId)
        try {
            paymentRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, paymentRef.path)
            throw e
        }
    }

    suspend fun adminUpdateTranslation(
        translationId: String,
        videoTitle: String,
        translatedPreview: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("translations").document(translationId)
        try {
            docRef.update(
                mapOf(
                    "videoTitle" to videoTitle.trim().ifBlank { "YouTube Video" }.take(300),
                    "translatedTranscriptPreview" to translatedPreview.trim().take(20000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun adminDeleteTranslation(translationId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("translations").document(translationId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun adminDeleteSupportMessage(messageId: String): Result<Unit> = runCatching {
        requireUserId()
        val msgRef = db.collection("support_messages").document(messageId)
        try {
            msgRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, msgRef.path)
            throw e
        }
    }

    suspend fun adminDeleteNotification(notificationId: String): Result<Unit> = runCatching {
        requireUserId()
        val notifRef = db.collection("notifications").document(notificationId)
        try {
            notifRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, notifRef.path)
            throw e
        }
    }

    private suspend fun sendNotificationInternal(
        recipientUserId: String,
        senderUserId: String,
        senderEmail: String,
        type: String,
        title: String,
        body: String
    ) {
        val docId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("notifications").document(docId)
        val payload = mapOf(
            "recipientUserId" to recipientUserId.take(128),
            "senderUserId" to senderUserId.take(128),
            "senderEmail" to senderEmail.take(200),
            "type" to type,
            "title" to title.take(200),
            "body" to body.take(1500),
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }
}

