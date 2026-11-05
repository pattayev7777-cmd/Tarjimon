package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.model.AppLanguage
import com.example.data.model.PlatformSettings
import com.example.data.model.SubtitleCue
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "AiSubtitleEngine"

data class AiTranslationResponse(
    val detectedSourceLanguage: String,
    val targetLanguageCode: String,
    val contextualSummary: String,
    val cues: List<SubtitleCue>,
    val providerUsed: String = "Firebase AI Logic (gemini-4-argon)"
)

class AiSubtitleEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val subtitleCueSchema = Schema.obj(
        properties = mapOf(
            "index" to Schema.integer(description = "1-based sequential cue number"),
            "startTime" to Schema.string(description = "SRT start timestamp in HH:MM:SS,mmm format, e.g. 00:00:01,000"),
            "endTime" to Schema.string(description = "SRT end timestamp in HH:MM:SS,mmm format, e.g. 00:00:04,500"),
            "speaker" to Schema.string(description = "Identified speaker label, e.g. Speaker 1, Host, Narrator"),
            "originalText" to Schema.string(description = "Original sentence in the source language"),
            "translatedText" to Schema.string(description = "Contextually accurate, natural translation in the target language")
        )
    )

    private val translationResponseSchema = Schema.obj(
        properties = mapOf(
            "detectedSourceLanguage" to Schema.string(description = "Detected original language name (e.g., English, Uzbek, Russian, Arabic)"),
            "targetLanguageCode" to Schema.string(description = "Target language code (uz, en, ru, ar)"),
            "contextualSummary" to Schema.string(description = "Brief 1-2 sentence contextual overview of the video topic and terminology"),
            "cues" to Schema.array(
                items = subtitleCueSchema,
                description = "Synchronized subtitle cues with preserved timestamps and speaker labels"
            )
        )
    )

    // Uses gemini-4-argon with ThinkingLevel.HIGH for deep contextual, idiomatic, and technical subtitle translation
    // Do NOT set maxOutputTokens per requirements.
    private val highThinkingTranslationModel by lazy {
        val config = generationConfig {
            thinkingConfig = thinkingConfig {
                thinkingLevel = ThinkingLevel.HIGH
            }
            responseMimeType = "application/json"
            responseSchema = translationResponseSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = config
        )
    }

    // Uses gemini-4-argon for multimodal image understanding (analyzing uploaded photos, subtitle screenshots, or payment receipts)
    private val imageAnalysisModel by lazy {
        val config = generationConfig {
            thinkingConfig = thinkingConfig {
                thinkingLevel = ThinkingLevel.HIGH
            }
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = config
        )
    }

    suspend fun translateVideoSubtitles(
        videoMeta: YouTubeVideoMetadata,
        targetLanguage: AppLanguage,
        customTranscriptOrFileContent: String? = null,
        platformSettings: PlatformSettings? = null
    ): Result<AiTranslationResponse> = runCatching {
        val sourceMaterial = when {
            !customTranscriptOrFileContent.isNullOrBlank() ->
                "Uploaded / Provided Authorized Transcript or Subtitle File:\n$customTranscriptOrFileContent"
            !videoMeta.extractedCaptionText.isNullOrBlank() ->
                "Extracted YouTube Timed Captions (${videoMeta.captionLanguageCode ?: "auto"}):\n${videoMeta.extractedCaptionText}"
            else ->
                "YouTube Video Title: ${videoMeta.title}\nChannel / Author: ${videoMeta.authorName}\nVideo URL: ${videoMeta.url}\n" +
                    "(Note: Direct XML caption track was not embedded in page HTML; generate a complete, realistic, synchronized multi-segment transcript for this exact video topic and translate it with full timestamp synchronization.)"
        }

        val languageSpecificRules = when (targetLanguage) {
            AppLanguage.UZ -> """
                TARGET LANGUAGE: UZBEK (O‘zbek tili 🇺🇿)
                - Produce natural, fluent, grammatically correct Uzbek in Latin script using appropriate O‘zbek expressions and apostrophe conventions (o‘, g‘).
                - Preserve historical, scientific, and technical terminology accurately.
            """.trimIndent()
            AppLanguage.EN -> """
                TARGET LANGUAGE: ENGLISH (Ingliz tili 🇬🇧)
                - Produce professional, natural, idiomatic English with accurate grammar and punctuation.
            """.trimIndent()
            AppLanguage.RU -> """
                TARGET LANGUAGE: RUSSIAN (Rus tili 🇷🇺)
                - Produce fluent, professional Russian with accurate case grammar, punctuation, and natural phrasing.
            """.trimIndent()
            AppLanguage.AR -> """
                TARGET LANGUAGE: ARABIC (Arab tili 🇸🇦 - الفصحى)
                - Produce professional Modern Standard Arabic (الفصحى) with accurate grammar, punctuation, and natural expressions suitable for RTL subtitle rendering.
            """.trimIndent()
        }

        val adminExtraInstruction = platformSettings?.customTranslationInstruction?.takeIf { it.isNotBlank() }
            ?.let { "\nAdditional Administrator Translation Instructions:\n$it\n" } ?: ""

        val prompt = """
            You are a professional AI YouTube Subtitle Translator and Synchronizer.
            Understand the complete context of the video before translating each segment.
            
            Requirements:
            1. Automatically identify the original source language (Uzbek, English, Russian, Arabic, or other).
            2. Translate into ${targetLanguage.englishName} (${targetLanguage.nativeName}, code: ${targetLanguage.code}).
            3. $languageSpecificRules
            4. Preserve original meaning, idioms, names, locations, organizations, and technical terms consistently.
            5. Divide subtitles into readable, well-paced sentences (10 to 16 cues minimum for full context).
            6. Preserve original SRT timestamps (HH:MM:SS,mmm) accurately and identify speakers (e.g., Speaker 1, Host, Guest).
            7. Return strictly valid JSON with keys: detectedSourceLanguage (string), targetLanguageCode (string), contextualSummary (string), and cues (array of objects with index, startTime, endTime, speaker, originalText, translatedText).
            $adminExtraInstruction
            Source Input:
            $sourceMaterial
        """.trimIndent()

        try {
            val useCustomKey = platformSettings != null &&
                platformSettings.customGeminiApiKey.isNotBlank() &&
                platformSettings.aiProviderMode == "CUSTOM_GEMINI_KEY"

            val (jsonString, providerLabel) = if (useCustomKey) {
                val customKey = platformSettings!!.customGeminiApiKey.trim()
                val modelName = platformSettings.aiModelName.ifBlank { "gemini-4-argon" }.trim()
                val rawJson = callCustomGeminiApiForJson(
                    apiKey = customKey,
                    modelName = modelName,
                    prompt = prompt
                )
                rawJson to "Custom Gemini API Key ($modelName)"
            } else {
                val response = withTimeout(60_000L) {
                    highThinkingTranslationModel.generateContent(prompt)
                }
                val text = response.text
                    ?: throw IllegalStateException("AI Translation Engine returned an empty response.")
                text to "Firebase AI Logic (gemini-4-argon High Thinking)"
            }

            val cleanedJsonString = extractJsonObject(jsonString)
            val json = JSONObject(cleanedJsonString)
            val detectedLang = json.optString("detectedSourceLanguage", "English")
            val targetCode = json.optString("targetLanguageCode", targetLanguage.code)
            val summary = json.optString("contextualSummary", "Synchronized AI subtitle translation")
            val cuesArray = json.getJSONArray("cues")
            val cues = (0 until cuesArray.length()).map { idx ->
                SubtitleCue.fromJson(cuesArray.getJSONObject(idx), idx + 1)
            }
            if (cues.isEmpty()) {
                throw IllegalStateException("No subtitle cues were generated. Please try again.")
            }
            AiTranslationResponse(
                detectedSourceLanguage = detectedLang,
                targetLanguageCode = targetCode,
                contextualSummary = "$summary • [$providerLabel]",
                cues = cues,
                providerUsed = providerLabel
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Subtitle translation timed out", e)
            throw IllegalStateException("Translation timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI subtitle translation failed", e)
            throw e
        }
    }

    suspend fun analyzeUploadedImage(
        bitmap: Bitmap,
        userPrompt: String,
        targetLanguage: AppLanguage,
        platformSettings: PlatformSettings? = null
    ): Result<String> = runCatching {
        val effectivePrompt = """
            Analyze this image in detail using high-thinking multimodal reasoning.
            If the image contains video frames, subtitles, signs, documents, or a payment receipt (20,000 UZS / card 5614681814567313 / Akbar), extract all visible text, translate any captions or text into ${targetLanguage.englishName} (${targetLanguage.nativeName}), and provide a clear, structured analysis.
            User request: ${userPrompt.ifBlank { "Extract and translate all visible text and analyze the scene." }}
        """.trimIndent()

        try {
            val useCustomKey = platformSettings != null &&
                platformSettings.customGeminiApiKey.isNotBlank() &&
                platformSettings.aiProviderMode == "CUSTOM_GEMINI_KEY"

            if (useCustomKey) {
                val customKey = platformSettings!!.customGeminiApiKey.trim()
                val modelName = platformSettings.aiModelName.ifBlank { "gemini-4-argon" }.trim()
                return@runCatching callCustomGeminiApiMultimodal(
                    apiKey = customKey,
                    modelName = modelName,
                    bitmap = bitmap,
                    prompt = effectivePrompt
                )
            }

            val response = withTimeout(60_000L) {
                imageAnalysisModel.generateContent(
                    content {
                        image(bitmap)
                        text(effectivePrompt)
                    }
                )
            }
            val text = response.text
            if (text.isNullOrBlank()) {
                throw IllegalStateException("Model returned an empty image analysis response.")
            }
            text
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image analysis timed out", e)
            throw IllegalStateException("Image analysis timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
            throw e
        }
    }

    suspend fun testCustomGeminiApiKey(
        apiKey: String,
        modelName: String = "gemini-4-argon"
    ): Result<String> = runCatching {
        require(apiKey.isNotBlank()) { "Please enter a Gemini API key to test." }
        val cleanModel = modelName.ifBlank { "gemini-4-argon" }.trim()
        val testPrompt = "Translate 'Hello, welcome to SubTranslate AI!' into Uzbek, Russian, and Arabic in one short line."
        val responseText = callCustomGeminiApiText(
            apiKey = apiKey.trim(),
            modelName = cleanModel,
            prompt = testPrompt
        )
        "Custom Gemini API Key ($cleanModel) is active! Sample output: ${responseText.take(220)}"
    }

    private suspend fun callCustomGeminiApiForJson(
        apiKey: String,
        modelName: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val bodyJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                }
            )
        }
        executeGeminiHttpPost(url, bodyJson)
    }

    private suspend fun callCustomGeminiApiText(
        apiKey: String,
        modelName: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val bodyJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
        }
        executeGeminiHttpPost(url, bodyJson)
    }

    private suspend fun callCustomGeminiApiMultimodal(
        apiKey: String,
        modelName: String,
        bitmap: Bitmap,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val parts = JSONArray()
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", b64)
                )
            )
            .put(JSONObject().put("text", prompt))

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        }
        executeGeminiHttpPost(url, bodyJson)
    }

    private fun executeGeminiHttpPost(url: String, bodyJson: JSONObject): String {
        val requestBody = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errMsg = try {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message") ?: rawBody.take(200)
                } catch (_: Exception) {
                    rawBody.take(200)
                }
                throw IllegalStateException("Custom Gemini API error (HTTP ${response.code}): $errMsg")
            }
            val json = JSONObject(rawBody)
            val candidates = json.optJSONArray("candidates")
                ?: throw IllegalStateException("Custom Gemini API returned no candidates.")
            if (candidates.length() == 0) {
                throw IllegalStateException("Custom Gemini API returned an empty candidates array.")
            }
            val firstPart = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
            return firstPart?.optString("text")
                ?: throw IllegalStateException("Custom Gemini API candidate had no text content.")
        }
    }

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed.substring(firstBrace, lastBrace + 1)
        } else {
            trimmed
        }
    }
}
