package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

object AuthorizedAdmins {
    // Exact administrator email addresses specified by the system requirements + runtime user email
    val EMAILS = setOf(
        "muhammadadhamov0000@gamil.com",
        "akbarakramov0000@gmail.com",
        "pattayev7777@gamail.com",
        "pattayev7777@gmail.com",
        "Pattayev7777@gmail.com"
    )

    const val SUBSCRIPTION_PRICE_UZS = 20000L
    const val PAYMENT_CARD_NUMBER = "5614681814567313"
    const val PAYMENT_ACCOUNT_HOLDER = "Akbar"
    const val SUBSCRIPTION_DURATION_DAYS = 30L
    const val SUBSCRIPTION_DURATION_MILLIS = 30L * 24L * 60L * 60L * 1000L
    const val INITIAL_FREE_TRANSLATIONS = 5L

    fun isAuthorizedAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val trimmed = email.trim()
        return EMAILS.any { it.equals(trimmed, ignoreCase = true) }
    }
}

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val uzbekName: String,
    val flagEmoji: String,
    val isRtl: Boolean = false
) {
    UZ("uz", "O‘zbek tili", "Uzbek", "O‘zbek tili", "🇺🇿", false),
    EN("en", "English", "English", "Ingliz tili", "🇬🇧", false),
    RU("ru", "Русский язык", "Russian", "Rus tili", "🇷🇺", false),
    AR("ar", "اللغة العربية", "Arabic", "Arab tili", "🇸🇦", true);

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UZ
        }
    }
}

data class SubtitleCue(
    val index: Int = 1,
    val startTime: String = "00:00:01,000",
    val endTime: String = "00:00:04,500",
    val speaker: String = "Speaker 1",
    val originalText: String = "",
    val translatedText: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        put("startTime", startTime)
        put("endTime", endTime)
        put("speaker", speaker)
        put("originalText", originalText)
        put("translatedText", translatedText)
    }

    companion object {
        fun fromJson(obj: JSONObject, fallbackIndex: Int): SubtitleCue {
            return SubtitleCue(
                index = obj.optInt("index", fallbackIndex),
                startTime = obj.optString("startTime", "00:00:01,000"),
                endTime = obj.optString("endTime", "00:00:04,000"),
                speaker = obj.optString("speaker", "Speaker 1"),
                originalText = obj.optString("originalText", ""),
                translatedText = obj.optString("translatedText", "")
            )
        }

        fun listToJsonString(cues: List<SubtitleCue>): String {
            val arr = JSONArray()
            cues.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJsonString(jsonStr: String): List<SubtitleCue> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(jsonStr)
                (0 until arr.length()).map { idx ->
                    fromJson(arr.getJSONObject(idx), idx + 1)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

object SubtitleExporter {
    // Converts SRT timestamp 00:00:01,500 to VTT timestamp 00:00:01.500
    private fun toVttTimestamp(ts: String): String = ts.replace(',', '.')

    private fun toSrtTimestamp(ts: String): String = ts.replace('.', ',')

    fun exportSrt(cues: List<SubtitleCue>, includeSpeaker: Boolean = true): String {
        val sb = StringBuilder()
        cues.forEachIndexed { idx, cue ->
            val number = idx + 1
            val start = toSrtTimestamp(cue.startTime)
            val end = toSrtTimestamp(cue.endTime)
            val speakerPrefix = if (includeSpeaker && cue.speaker.isNotBlank()) "[${cue.speaker}] " else ""
            sb.append(number).append("\n")
            sb.append("$start --> $end").append("\n")
            sb.append(speakerPrefix).append(cue.translatedText.trim()).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    fun exportVtt(cues: List<SubtitleCue>, isRtl: Boolean = false, includeSpeaker: Boolean = true): String {
        val sb = StringBuilder("WEBVTT\nKind: captions\n\n")
        cues.forEachIndexed { idx, cue ->
            val number = idx + 1
            val start = toVttTimestamp(cue.startTime)
            val end = toVttTimestamp(cue.endTime)
            val speakerPrefix = if (includeSpeaker && cue.speaker.isNotBlank()) "<v ${cue.speaker}>" else ""
            val textContent = if (isRtl) "\u202B${cue.translatedText.trim()}\u202C" else cue.translatedText.trim()
            sb.append(number).append("\n")
            sb.append("$start --> $end").append("\n")
            sb.append(speakerPrefix).append(textContent).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    fun exportTxt(cues: List<SubtitleCue>, includeTimestamps: Boolean = true): String {
        val sb = StringBuilder()
        cues.forEach { cue ->
            if (includeTimestamps) {
                sb.append("[${cue.startTime} - ${cue.endTime}] ")
            }
            if (cue.speaker.isNotBlank()) {
                sb.append("${cue.speaker}: ")
            }
            sb.append(cue.translatedText.trim()).append("\n")
        }
        return sb.toString().trimEnd() + "\n"
    }
}

@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val accountStatus: String = "ACTIVE", // ACTIVE, SUSPENDED
    val subscriptionStatus: String = "FREE", // FREE, ACTIVE, EXPIRED
    val freeTranslationsRemaining: Long = 5L,
    val completedTranslationsCount: Long = 0L,
    val paidTranslationsCount: Long = 0L,
    val subscriptionStartAtMillis: Long = 0L,
    val subscriptionExpiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isSubscriptionCurrentlyActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return subscriptionStatus == "ACTIVE" && subscriptionExpiresAtMillis > nowMillis
    }

    fun remainingSubscriptionDays(nowMillis: Long = System.currentTimeMillis()): Int {
        if (!isSubscriptionCurrentlyActive(nowMillis)) return 0
        val diff = subscriptionExpiresAtMillis - nowMillis
        return ((diff + (24L * 60 * 60 * 1000L - 1)) / (24L * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
    }

    fun canTranslate(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (accountStatus != "ACTIVE") return false
        return isSubscriptionCurrentlyActive(nowMillis) || freeTranslationsRemaining > 0L
    }
}

@IgnoreExtraProperties
data class AdminRole(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TranslationRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val videoUrl: String = "",
    val videoId: String = "",
    val videoTitle: String = "",
    val channelAuthor: String = "",
    val sourceLanguage: String = "auto",
    val detectedLanguage: String = "English",
    val targetLanguage: String = "uz",
    val isPaidTranslation: Boolean = false,
    val subtitleCount: Long = 0L,
    val subtitlesJson: String = "[]",
    val originalTranscriptPreview: String = "",
    val translatedTranscriptPreview: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedCues(): List<SubtitleCue> = SubtitleCue.listFromJsonString(subtitlesJson)
}

@IgnoreExtraProperties
data class PaymentSubmission(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val amountUzs: Long = 20000L,
    val cardNumber: String = "5614681814567313",
    val accountHolder: String = "Akbar",
    val receiptFileName: String = "",
    val receiptMimeType: String = "image/jpeg",
    val receiptDataBase64: String = "",
    val aiReceiptSummary: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val adminNote: String = "",
    val reviewedByEmail: String = "",
    val approvedAtMillis: Long = 0L,
    val subscriptionExpiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SupportMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val category: String = "QUESTION",
    val subject: String = "",
    val message: String = "",
    val attachmentFileName: String = "",
    val attachmentMimeType: String = "",
    val attachmentDataBase64: String = "",
    val status: String = "OPEN", // OPEN, RESOLVED
    val adminReply: String = "",
    val repliedByEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientUserId: String = "", // userId or "ADMIN"
    val senderUserId: String = "",
    val senderEmail: String = "",
    val type: String = "NEW_USER",
    val title: String = "",
    val body: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlatformSettings(
    @DocumentId val id: String = "global_config",
    val settingId: String = "global_config",
    val subscriptionPriceUzs: Long = AuthorizedAdmins.SUBSCRIPTION_PRICE_UZS,
    val paymentCardNumber: String = AuthorizedAdmins.PAYMENT_CARD_NUMBER,
    val paymentAccountHolder: String = AuthorizedAdmins.PAYMENT_ACCOUNT_HOLDER,
    val subscriptionDurationDays: Long = AuthorizedAdmins.SUBSCRIPTION_DURATION_DAYS,
    val initialFreeTranslations: Long = AuthorizedAdmins.INITIAL_FREE_TRANSLATIONS,
    val aiProviderMode: String = "FIREBASE_AI_LOGIC", // FIREBASE_AI_LOGIC or CUSTOM_GEMINI_KEY
    val customGeminiApiKey: String = "",
    val aiModelName: String = "gemini-4-argon",
    val customTranslationInstruction: String = "",
    val updatedByEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun shouldUseCustomGeminiKey(): Boolean {
        return customGeminiApiKey.isNotBlank() && aiProviderMode == "CUSTOM_GEMINI_KEY"
    }
}

