package com.example.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppLanguage
import com.example.data.model.AppNotification
import com.example.data.model.AuthorizedAdmins
import com.example.data.model.PaymentSubmission
import com.example.data.model.PlatformSettings
import com.example.data.model.SubtitleCue
import com.example.data.model.SupportMessage
import com.example.data.model.TranslationRecord
import com.example.data.model.UserAccount
import com.example.data.remote.AiSubtitleEngine
import com.example.data.remote.YouTubeExtractor
import com.example.data.remote.YouTubeVideoMetadata
import com.example.data.repository.PlatformRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TranslationUiStage {
    data object Idle : TranslationUiStage
    data class ValidatingLink(val message: String) : TranslationUiStage
    data class TranslatingWithAi(val progressPercent: Int, val stageLabel: String) : TranslationUiStage
    data class Completed(val record: TranslationRecord, val summary: String) : TranslationUiStage
    data class Error(val message: String, val allowFileUploadFallback: Boolean = true) : TranslationUiStage
}

class SubTranslateViewModel(
    private val repository: PlatformRepository,
    private val currentUserId: String,
    val currentUserEmail: String,
    val currentUserDisplayName: String
) : ViewModel() {

    private val youTubeExtractor = YouTubeExtractor()
    private val aiEngine = AiSubtitleEngine()

    val isAdminUser: Boolean = AuthorizedAdmins.isAuthorizedAdmin(currentUserEmail)

    private val _uiLanguage = MutableStateFlow(AppLanguage.EN)
    val uiLanguage: StateFlow<AppLanguage> = _uiLanguage.asStateFlow()

    private val _youtubeUrl = MutableStateFlow("https://www.youtube.com/watch?v=aircAruvnKk")
    val youtubeUrl: StateFlow<String> = _youtubeUrl.asStateFlow()

    private val _targetLanguage = MutableStateFlow(AppLanguage.UZ)
    val targetLanguage: StateFlow<AppLanguage> = _targetLanguage.asStateFlow()

    private val _previewVideoMeta = MutableStateFlow<YouTubeVideoMetadata?>(null)
    val previewVideoMeta: StateFlow<YouTubeVideoMetadata?> = _previewVideoMeta.asStateFlow()

    private val _customUploadedTranscript = MutableStateFlow("")
    val customUploadedTranscript: StateFlow<String> = _customUploadedTranscript.asStateFlow()

    private val _customUploadedFileName = MutableStateFlow("")
    val customUploadedFileName: StateFlow<String> = _customUploadedFileName.asStateFlow()

    private val _translationStage = MutableStateFlow<TranslationUiStage>(TranslationUiStage.Idle)
    val translationStage: StateFlow<TranslationUiStage> = _translationStage.asStateFlow()

    private val _activeTranslation = MutableStateFlow<TranslationRecord?>(null)
    val activeTranslation: StateFlow<TranslationRecord?> = _activeTranslation.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _adminKeyTestResult = MutableStateFlow<String?>(null)
    val adminKeyTestResult: StateFlow<String?> = _adminKeyTestResult.asStateFlow()

    private val _isTestingAdminKey = MutableStateFlow(false)
    val isTestingAdminKey: StateFlow<Boolean> = _isTestingAdminKey.asStateFlow()

    // Image Understanding / Photo Analysis state (gemini-4-argon)
    private val _isAnalyzingImage = MutableStateFlow(false)
    val isAnalyzingImage: StateFlow<Boolean> = _isAnalyzingImage.asStateFlow()

    private val _imageAnalysisResult = MutableStateFlow<String?>(null)
    val imageAnalysisResult: StateFlow<String?> = _imageAnalysisResult.asStateFlow()

    // Reactive Firestore streams (Two-tier error handling per firebase-firestore-android)
    val platformSettings: StateFlow<PlatformSettings> = repository.observePlatformSettings()
        .catch { emit(PlatformSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PlatformSettings())

    val userAccount: StateFlow<UserAccount?> = repository.observeUserAccount(currentUserId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val userTranslations: StateFlow<List<TranslationRecord>> = repository.observeUserTranslations(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userPayments: StateFlow<List<PaymentSubmission>> = repository.observeUserPayments(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userSupportMessages: StateFlow<List<SupportMessage>> = repository.observeUserSupportMessages(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userNotifications: StateFlow<List<AppNotification>> = repository.observeUserNotifications(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Administrator reactive streams (only active when user is an authorized admin)
    val adminAllUsers: StateFlow<List<UserAccount>> = (if (isAdminUser) repository.observeAllUsers() else emptyFlow())
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminAllPayments: StateFlow<List<PaymentSubmission>> = (if (isAdminUser) repository.observeAllPayments() else emptyFlow())
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminAllTranslations: StateFlow<List<TranslationRecord>> = (if (isAdminUser) repository.observeAllTranslations() else emptyFlow())
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminAllSupportMessages: StateFlow<List<SupportMessage>> = (if (isAdminUser) repository.observeAllSupportMessages() else emptyFlow())
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminNotifications: StateFlow<List<AppNotification>> = (if (isAdminUser) repository.observeAdminNotifications() else emptyFlow())
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureOrGetUserAccount(
                uid = currentUserId,
                email = currentUserEmail,
                displayName = currentUserDisplayName
            )
        }
    }

    fun setUiLanguage(lang: AppLanguage) {
        _uiLanguage.value = lang
    }

    fun updateYoutubeUrl(url: String) {
        _youtubeUrl.value = url
    }

    fun selectTargetLanguage(lang: AppLanguage) {
        _targetLanguage.value = lang
    }

    fun setUploadedSubtitleOrTranscript(fileName: String, content: String) {
        _customUploadedFileName.value = fileName
        _customUploadedTranscript.value = content
        _bannerMessage.value = "Loaded authorized file: $fileName"
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun selectTranslationForPreview(record: TranslationRecord) {
        _activeTranslation.value = record
    }

    fun previewYouTubeLink() {
        val rawUrl = _youtubeUrl.value.trim()
        if (rawUrl.isBlank()) {
            _translationStage.value = TranslationUiStage.Error("Please paste a YouTube video link first.")
            return
        }
        viewModelScope.launch {
            _translationStage.value = TranslationUiStage.ValidatingLink("Validating YouTube URL & checking captions...")
            val metaResult = youTubeExtractor.fetchVideoMetadataAndCaptions(rawUrl)
            metaResult.fold(
                onSuccess = { meta ->
                    _previewVideoMeta.value = meta
                    _translationStage.value = TranslationUiStage.Idle
                },
                onFailure = { err ->
                    _translationStage.value = TranslationUiStage.Error(
                        err.localizedMessage ?: "Invalid or inaccessible YouTube video link.",
                        allowFileUploadFallback = true
                    )
                }
            )
        }
    }

    fun startVideoTranslation(onRequireSubscriptionNavigate: () -> Unit) {
        val account = userAccount.value
        if (account == null) {
            _translationStage.value = TranslationUiStage.Error("Loading account profile, please try again in a moment.")
            return
        }
        if (account.accountStatus != "ACTIVE") {
            _translationStage.value = TranslationUiStage.Error(
                "Your account is currently suspended. Please use Contact Admin for assistance.",
                allowFileUploadFallback = false
            )
            return
        }
        if (!account.canTranslate()) {
            _translationStage.value = TranslationUiStage.Error(
                "You have used all 5 free translations or your 30-day subscription has expired. Please subscribe (20,000 UZS/month) to continue.",
                allowFileUploadFallback = false
            )
            onRequireSubscriptionNavigate()
            return
        }

        val rawUrl = _youtubeUrl.value.trim()
        val uploadedContent = _customUploadedTranscript.value.trim()

        if (rawUrl.isBlank() && uploadedContent.isBlank()) {
            _translationStage.value = TranslationUiStage.Error("Please enter a YouTube URL or upload an authorized subtitle/transcript file.")
            return
        }

        viewModelScope.launch {
            _translationStage.value = TranslationUiStage.TranslatingWithAi(
                progressPercent = 18,
                stageLabel = "Validating YouTube video & extracting caption tracks..."
            )

            val meta = if (rawUrl.isNotBlank()) {
                youTubeExtractor.fetchVideoMetadataAndCaptions(rawUrl).getOrElse { err ->
                    if (uploadedContent.isNotBlank()) {
                        YouTubeVideoMetadata(
                            videoId = "uploaded_file",
                            url = _customUploadedFileName.value.ifBlank { "uploaded_subtitles.srt" },
                            title = _customUploadedFileName.value.ifBlank { "Uploaded Authorized Transcript" },
                            authorName = currentUserDisplayName.ifBlank { "User Upload" },
                            thumbnailUrl = "",
                            extractedCaptionText = uploadedContent,
                            captionLanguageCode = "auto",
                            hasAccessibleCaptions = true
                        )
                    } else {
                        _translationStage.value = TranslationUiStage.Error(
                            err.localizedMessage ?: "Could not access YouTube video. You can also upload an authorized .SRT/.VTT/.TXT file.",
                            allowFileUploadFallback = true
                        )
                        return@launch
                    }
                }
            } else {
                YouTubeVideoMetadata(
                    videoId = "uploaded_file",
                    url = _customUploadedFileName.value.ifBlank { "uploaded_subtitles.srt" },
                    title = _customUploadedFileName.value.ifBlank { "Uploaded Authorized Transcript" },
                    authorName = currentUserDisplayName.ifBlank { "User Upload" },
                    thumbnailUrl = "",
                    extractedCaptionText = uploadedContent,
                    captionLanguageCode = "auto",
                    hasAccessibleCaptions = true
                )
            }

            _previewVideoMeta.value = meta

            val currentSettings = platformSettings.value
            val providerName = if (currentSettings.shouldUseCustomGeminiKey()) {
                "Custom Gemini API Key (${currentSettings.aiModelName})"
            } else {
                "Firebase AI Logic (Gemini 4 Argon High Thinking)"
            }

            _translationStage.value = TranslationUiStage.TranslatingWithAi(
                progressPercent = 52,
                stageLabel = "Translating via $providerName into ${_targetLanguage.value.nativeName}..."
            )

            val aiResult = aiEngine.translateVideoSubtitles(
                videoMeta = meta,
                targetLanguage = _targetLanguage.value,
                customTranscriptOrFileContent = uploadedContent.takeIf { it.isNotBlank() },
                platformSettings = currentSettings
            )

            aiResult.fold(
                onSuccess = { aiResponse ->
                    _translationStage.value = TranslationUiStage.TranslatingWithAi(
                        progressPercent = 88,
                        stageLabel = "Synchronizing timestamps & saving to your translation history..."
                    )
                    val recordResult = repository.recordCompletedTranslation(
                        userAccount = account,
                        videoUrl = meta.url,
                        videoId = meta.videoId,
                        videoTitle = meta.title,
                        channelAuthor = meta.authorName,
                        sourceLanguage = meta.captionLanguageCode ?: "auto",
                        detectedLanguage = aiResponse.detectedSourceLanguage,
                        targetLanguage = _targetLanguage.value.code,
                        cues = aiResponse.cues
                    )
                    recordResult.fold(
                        onSuccess = { savedRecord ->
                            _activeTranslation.value = savedRecord
                            _translationStage.value = TranslationUiStage.Completed(
                                record = savedRecord,
                                summary = aiResponse.contextualSummary
                            )
                        },
                        onFailure = { dbErr ->
                            _translationStage.value = TranslationUiStage.Error(
                                dbErr.localizedMessage ?: "Failed to save translation record."
                            )
                        }
                    )
                },
                onFailure = { aiErr ->
                    // Do NOT deduct free translation balance when AI translation fails!
                    _translationStage.value = TranslationUiStage.Error(
                        aiErr.localizedMessage ?: "AI translation failed. Your free translation balance was not deducted.",
                        allowFileUploadFallback = true
                    )
                }
            )
        }
    }

    fun updateCueText(translationId: String, cueIndex: Int, newTranslatedText: String) {
        val current = _activeTranslation.value ?: return
        if (current.id != translationId) return
        val updatedCues = current.parsedCues().map { cue ->
            if (cue.index == cueIndex) cue.copy(translatedText = newTranslatedText) else cue
        }
        val updatedJson = SubtitleCue.listToJsonString(updatedCues)
        _activeTranslation.value = current.copy(
            subtitlesJson = updatedJson,
            translatedTranscriptPreview = updatedCues.joinToString(" ") { it.translatedText }
        )
        viewModelScope.launch {
            repository.updateTranslatedSubtitles(translationId, updatedCues)
                .onSuccess {
                    _bannerMessage.value = "Subtitle segment #$cueIndex saved."
                }
                .onFailure {
                    _bannerMessage.value = "Failed to save subtitle edit: ${it.localizedMessage}"
                }
        }
    }

    fun analyzePhotoWithGemini(bitmap: Bitmap, customPrompt: String) {
        viewModelScope.launch {
            _isAnalyzingImage.value = true
            _imageAnalysisResult.value = null
            val result = aiEngine.analyzeUploadedImage(
                bitmap = bitmap,
                userPrompt = customPrompt,
                targetLanguage = _targetLanguage.value,
                platformSettings = platformSettings.value
            )
            _isAnalyzingImage.value = false
            result.fold(
                onSuccess = { text ->
                    _imageAnalysisResult.value = text
                },
                onFailure = { err ->
                    _imageAnalysisResult.value = "Error analyzing photo: ${err.localizedMessage}"
                }
            )
        }
    }

    fun submitPaymentReceipt(
        fileName: String,
        mimeType: String,
        base64Data: String,
        bitmapForAiInspection: Bitmap? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val currentSettings = platformSettings.value
            var aiSummary = "Receipt uploaded ($fileName, $mimeType) for ${currentSettings.subscriptionPriceUzs} UZS monthly subscription."
            if (bitmapForAiInspection != null) {
                val aiCheck = aiEngine.analyzeUploadedImage(
                    bitmap = bitmapForAiInspection,
                    userPrompt = "Verify if this is a payment receipt for ${currentSettings.subscriptionPriceUzs} UZS to card ${currentSettings.paymentCardNumber} (${currentSettings.paymentAccountHolder}). Summarize the amount, date, and card details visible.",
                    targetLanguage = AppLanguage.EN,
                    platformSettings = currentSettings
                )
                aiCheck.onSuccess { summary ->
                    aiSummary = summary.take(1500)
                }
            }
            repository.submitPaymentReceipt(
                userEmail = currentUserEmail,
                receiptFileName = fileName,
                receiptMimeType = mimeType,
                receiptDataBase64 = base64Data,
                aiReceiptSummary = aiSummary
            ).fold(
                onSuccess = {
                    _bannerMessage.value = "Payment receipt submitted! Status: PENDING administrator approval."
                    onComplete()
                },
                onFailure = { err ->
                    _bannerMessage.value = "Receipt upload failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun submitSupportTicket(
        category: String,
        subject: String,
        message: String,
        attachmentFileName: String,
        attachmentMimeType: String,
        attachmentBase64: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.submitSupportMessage(
                userEmail = currentUserEmail,
                category = category,
                subject = subject,
                message = message,
                attachmentFileName = attachmentFileName,
                attachmentMimeType = attachmentMimeType,
                attachmentDataBase64 = attachmentBase64
            ).fold(
                onSuccess = {
                    _bannerMessage.value = "Your message has been sent to the administrators."
                    onComplete()
                },
                onFailure = { err ->
                    _bannerMessage.value = "Failed to send message: ${err.localizedMessage}"
                }
            )
        }
    }

    // --- Admin Actions ---

    fun adminSavePlatformSettings(settings: PlatformSettings) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.savePlatformSettings(settings, currentUserEmail)
                .onSuccess {
                    _bannerMessage.value = "Platform & AI settings saved by $currentUserEmail!"
                }
                .onFailure { err ->
                    _bannerMessage.value = "Failed to save settings: ${err.localizedMessage}"
                }
        }
    }

    fun adminTestCustomGeminiKey(apiKey: String, modelName: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            _isTestingAdminKey.value = true
            _adminKeyTestResult.value = null
            val res = aiEngine.testCustomGeminiApiKey(apiKey, modelName)
            _isTestingAdminKey.value = false
            res.fold(
                onSuccess = { msg ->
                    _adminKeyTestResult.value = msg
                    _bannerMessage.value = "Custom Gemini API key verified!"
                },
                onFailure = { err ->
                    _adminKeyTestResult.value = "Error: ${err.localizedMessage}"
                }
            )
        }
    }

    fun adminApprovePayment(payment: PaymentSubmission, note: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.approvePayment(
                payment = payment,
                adminEmail = currentUserEmail,
                adminNote = note.ifBlank { "Verified 20,000 UZS receipt — 30-day Pro subscription activated." }
            ).fold(
                onSuccess = {
                    _bannerMessage.value = "Approved payment for ${payment.userEmail}! 30-day subscription activated."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Approval failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun adminRejectPayment(payment: PaymentSubmission, reason: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.rejectPayment(
                payment = payment,
                adminEmail = currentUserEmail,
                adminNote = reason.ifBlank { "Payment receipt could not be verified. Please upload a valid 20,000 UZS receipt." }
            ).fold(
                onSuccess = {
                    _bannerMessage.value = "Rejected payment for ${payment.userEmail}. User notified."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Rejection failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun adminDeletePayment(payment: PaymentSubmission) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.adminDeletePayment(payment.id)
                .onSuccess {
                    _bannerMessage.value = "Deleted payment record (${payment.userEmail})."
                }
                .onFailure {
                    _bannerMessage.value = "Delete payment failed: ${it.localizedMessage}"
                }
        }
    }

    fun adminToggleUserSuspension(user: UserAccount) {
        if (!isAdminUser) return
        val nextStatus = if (user.accountStatus == "SUSPENDED") "ACTIVE" else "SUSPENDED"
        viewModelScope.launch {
            repository.setUserAccountStatus(user.userId.ifBlank { user.id }, nextStatus)
                .onSuccess {
                    _bannerMessage.value = "User ${user.email} status set to $nextStatus."
                }
                .onFailure {
                    _bannerMessage.value = "Failed to update user status: ${it.localizedMessage}"
                }
        }
    }

    fun adminActivateSubscription(user: UserAccount) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.activateUserSubscriptionManually(user.userId.ifBlank { user.id }, currentUserEmail)
                .onSuccess {
                    _bannerMessage.value = "Activated 30-day subscription for ${user.email}."
                }
                .onFailure {
                    _bannerMessage.value = "Failed to activate subscription: ${it.localizedMessage}"
                }
        }
    }

    fun adminUpdateUserFull(
        user: UserAccount,
        displayName: String,
        accountStatus: String,
        subscriptionStatus: String,
        freeRemaining: Long,
        completedCount: Long,
        paidCount: Long,
        subscriptionDays: Long
    ) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.adminUpdateUserFull(
                targetUserId = user.userId.ifBlank { user.id },
                displayName = displayName,
                accountStatus = accountStatus,
                subscriptionStatus = subscriptionStatus,
                freeTranslationsRemaining = freeRemaining,
                completedTranslationsCount = completedCount,
                paidTranslationsCount = paidCount,
                subscriptionDaysFromNow = subscriptionDays
            ).onSuccess {
                _bannerMessage.value = "Updated user ${user.email} profile & balances."
            }.onFailure {
                _bannerMessage.value = "Failed to update user: ${it.localizedMessage}"
            }
        }
    }

    fun adminDeleteUser(user: UserAccount) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.adminDeleteUser(user.userId.ifBlank { user.id })
                .onSuccess {
                    _bannerMessage.value = "Deleted user ${user.email}."
                }
                .onFailure {
                    _bannerMessage.value = "Failed to delete user: ${it.localizedMessage}"
                }
        }
    }

    fun adminUpdateTranslation(record: TranslationRecord, newTitle: String, newTranslatedPreview: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.adminUpdateTranslation(record.id, newTitle, newTranslatedPreview)
                .onSuccess {
                    _bannerMessage.value = "Updated translation record '${newTitle.take(30)}'."
                }
                .onFailure {
                    _bannerMessage.value = "Failed to update translation: ${it.localizedMessage}"
                }
        }
    }

    fun adminDeleteTranslation(record: TranslationRecord) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.adminDeleteTranslation(record.id)
                .onSuccess {
                    _bannerMessage.value = "Deleted translation record."
                }
                .onFailure {
                    _bannerMessage.value = "Failed to delete translation: ${it.localizedMessage}"
                }
        }
    }

    fun adminReplySupport(message: SupportMessage, replyText: String, markResolved: Boolean) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.replyToSupportMessage(message, replyText, currentUserEmail, markResolved)
                .onSuccess {
                    _bannerMessage.value = "Reply sent to ${message.userEmail}."
                }
                .onFailure {
                    _bannerMessage.value = "Failed to send reply: ${it.localizedMessage}"
                }
        }
    }

    fun adminDeleteSupportMessage(message: SupportMessage) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.adminDeleteSupportMessage(message.id)
                .onSuccess {
                    _bannerMessage.value = "Deleted support request from ${message.userEmail}."
                }
                .onFailure {
                    _bannerMessage.value = "Failed to delete support request: ${it.localizedMessage}"
                }
        }
    }

    fun adminDeleteNotification(notificationId: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            repository.adminDeleteNotification(notificationId)
                .onSuccess {
                    _bannerMessage.value = "Deleted notification."
                }
        }
    }

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }
}
