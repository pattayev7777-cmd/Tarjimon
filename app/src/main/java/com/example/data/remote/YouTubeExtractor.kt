package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class YouTubeVideoMetadata(
    val videoId: String,
    val url: String,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String,
    val extractedCaptionText: String?,
    val captionLanguageCode: String?,
    val hasAccessibleCaptions: Boolean
)

class YouTubeExtractor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private val YOUTUBE_ID_PATTERNS = listOf(
            Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/|youtube\\.com/shorts/|youtube\\.com/v/)([a-zA-Z0-9_-]{11})"),
            Pattern.compile("^([a-zA-Z0-9_-]{11})$")
        )

        fun extractVideoId(input: String): String? {
            val trimmed = input.trim()
            for (pattern in YOUTUBE_ID_PATTERNS) {
                val matcher = pattern.matcher(trimmed)
                if (matcher.find()) {
                    return matcher.group(1)
                }
            }
            return null
        }

        fun formatSecondsToSrtTime(secondsDouble: Double): String {
            val totalMs = (secondsDouble * 1000.0).toLong().coerceAtLeast(0L)
            val hours = totalMs / 3600000L
            val minutes = (totalMs % 3600000L) / 60000L
            val seconds = (totalMs % 60000L) / 1000L
            val millis = totalMs % 1000L
            return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
        }
    }

    suspend fun fetchVideoMetadataAndCaptions(rawUrl: String): Result<YouTubeVideoMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val videoId = extractVideoId(rawUrl)
                ?: throw IllegalArgumentException("Invalid YouTube link. Please paste a valid YouTube URL (e.g., https://www.youtube.com/watch?v=...).")

            val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
            var title = "YouTube Video ($videoId)"
            var authorName = "YouTube Channel"
            var thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            // 1. Fetch official YouTube oEmbed metadata
            try {
                val encoded = URLEncoder.encode(canonicalUrl, "UTF-8")
                val oembedReq = Request.Builder()
                    .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                    .header("User-Agent", "Mozilla/5.0 (Android 16; Mobile)")
                    .build()
                client.newCall(oembedReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            title = json.optString("title", title)
                            authorName = json.optString("author_name", authorName)
                            thumbnailUrl = json.optString("thumbnail_url", thumbnailUrl)
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue to watch page parsing if oEmbed is unreachable
            }

            // 2. Attempt to extract publicly available captionTracks from watch page
            var extractedCaptions: String? = null
            var captionLang: String? = null

            try {
                val watchReq = Request.Builder()
                    .url(canonicalUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                client.newCall(watchReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string().orEmpty()
                        val marker = "\"captionTracks\":"
                        val idx = html.indexOf(marker)
                        if (idx != -1) {
                            val arrayStart = html.indexOf('[', idx)
                            val arrayEnd = html.indexOf(']', arrayStart)
                            if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
                                val tracksJson = html.substring(arrayStart, arrayEnd + 1)
                                val arr = org.json.JSONArray(tracksJson)
                                if (arr.length() > 0) {
                                    val firstTrack = arr.getJSONObject(0)
                                    val baseUrl = firstTrack.optString("baseUrl", "")
                                        .replace("\\u0026", "&")
                                    captionLang = firstTrack.optString("languageCode", "auto")
                                    if (baseUrl.isNotBlank()) {
                                        extractedCaptions = fetchTimedTextXml(baseUrl)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Public caption track extraction is optional if the user uploads or uses AI transcript synthesis
            }

            YouTubeVideoMetadata(
                videoId = videoId,
                url = canonicalUrl,
                title = title,
                authorName = authorName,
                thumbnailUrl = thumbnailUrl,
                extractedCaptionText = extractedCaptions,
                captionLanguageCode = captionLang,
                hasAccessibleCaptions = !extractedCaptions.isNullOrBlank()
            )
        }
    }

    private fun fetchTimedTextXml(baseUrl: String): String? {
        return try {
            val req = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val xml = resp.body?.string().orEmpty()
                if (xml.isBlank()) return null
                val regex = Pattern.compile("<text start=\"([0-9.]+)\" dur=\"([0-9.]+)\"[^>]*>(.*?)</text>", Pattern.DOTALL)
                val matcher = regex.matcher(xml)
                val sb = StringBuilder()
                var count = 0
                while (matcher.find() && count < 120) {
                    val startSec = matcher.group(1)?.toDoubleOrNull() ?: 0.0
                    val durSec = matcher.group(2)?.toDoubleOrNull() ?: 3.0
                    val endSec = startSec + durSec
                    val rawText = matcher.group(3).orEmpty()
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("\n", " ")
                        .trim()
                    if (rawText.isNotBlank()) {
                        count++
                        sb.append("[$count] ${formatSecondsToSrtTime(startSec)} --> ${formatSecondsToSrtTime(endSec)} | $rawText\n")
                    }
                }
                if (sb.isNotEmpty()) sb.toString() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
