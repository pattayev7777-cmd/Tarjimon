package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import com.example.viewmodel.SubTranslateViewModel
import com.example.viewmodel.TranslationUiStage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslateVideoScreen(
    viewModel: SubTranslateViewModel,
    onNavigateToSubscription: () -> Unit,
    onShowBanner: (String) -> Unit
) {
    val context = LocalContext.current
    val uiLang by viewModel.uiLanguage.collectAsState()
    val strings = AppStrings.forLanguage(uiLang)

    val youtubeUrl by viewModel.youtubeUrl.collectAsState()
    val targetLanguage by viewModel.targetLanguage.collectAsState()
    val previewMeta by viewModel.previewVideoMeta.collectAsState()
    val translationStage by viewModel.translationStage.collectAsState()
    val activeTranslation by viewModel.activeTranslation.collectAsState()
    val userAccount by viewModel.userAccount.collectAsState()
    val customUploadedFileName by viewModel.customUploadedFileName.collectAsState()

    val isAnalyzingImage by viewModel.isAnalyzingImage.collectAsState()
    val imageAnalysisResult by viewModel.imageAnalysisResult.collectAsState()
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imagePrompt by remember { mutableStateOf("Extract any subtitles, captions, or receipt details and translate them.") }

    // Authorized subtitle / transcript file picker fallback (.srt, .vtt, .txt)
    val subtitleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "uploaded_subtitles.srt"
                    viewModel.setUploadedSubtitleOrTranscript(name, text)
                }
            } catch (e: Exception) {
                onShowBanner("Failed to read file: ${e.localizedMessage}")
            }
        }
    }

    // Zero-permission Android Photo Picker for Gemini 4 Argon Image Analysis
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        selectedBitmap = bmp
                        viewModel.analyzePhotoWithGemini(bmp, imagePrompt)
                    }
                }
            } catch (e: Exception) {
                onShowBanner("Failed to load photo: ${e.localizedMessage}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero SaaS Header Card with Decorative Canvas Wave & Quota Pill
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NavyCardBorder, RoundedCornerShape(22.dp))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ElectricPurple.copy(alpha = 0.28f), NavySurface.copy(alpha = 0f)),
                            center = Offset(size.width * 0.85f, size.height * 0.2f),
                            radius = size.width * 0.6f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(CyberBlue.copy(alpha = 0.20f), NavySurface.copy(alpha = 0f)),
                            center = Offset(size.width * 0.15f, size.height * 0.85f),
                            radius = size.width * 0.5f
                        )
                    )
                }

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = ElectricPurple.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = CyberBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Gemini 4 Argon • High Thinking",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyberBlue
                                )
                            }
                        }

                        val acct = userAccount
                        if (acct != null) {
                            val isPro = acct.isSubscriptionCurrentlyActive()
                            Surface(
                                color = if (isPro) EmeraldSuccess.copy(alpha = 0.2f) else NavySurfaceVariant,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clickable { onNavigateToSubscription() }
                                    .border(1.dp, if (isPro) EmeraldSuccess else NavyCardBorder, RoundedCornerShape(50))
                            ) {
                                Text(
                                    text = if (isPro) {
                                        "PRO • ${acct.remainingSubscriptionDays()}d left"
                                    } else {
                                        "Free: ${acct.freeTranslationsRemaining}/5 left"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isPro) EmeraldSuccess else TextWhite,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = strings.headline,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextWhite
                    )

                    Text(
                        text = strings.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. YouTube URL Input & Language Selector Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NavyCardBorder, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "1. YouTube Video Link or Authorized Caption File",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )

                OutlinedTextField(
                    value = youtubeUrl,
                    onValueChange = viewModel::updateYoutubeUrl,
                    placeholder = { Text(strings.urlPlaceholder) },
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null, tint = CyberBlue)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_url_input")
                )

                // Quick Preset Video Chips + Preview & Upload Buttons
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { viewModel.previewYouTubeLink() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("preview_video_button")
                    ) {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verify & Preview Link")
                    }

                    OutlinedButton(
                        onClick = { subtitleFileLauncher.launch("*/*") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("upload_subtitle_file_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (customUploadedFileName.isBlank()) "Upload .SRT / .VTT / .TXT"
                            else "Loaded: $customUploadedFileName"
                        )
                    }
                }

                // Video Metadata Preview Card (when validated)
                val meta = previewMeta
                if (meta != null) {
                    Surface(
                        color = NavyBackground,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NavyCardBorder, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (meta.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = meta.thumbnailUrl,
                                    contentDescription = meta.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextWhite,
                                    maxLines = 2
                                )
                                Text(
                                    text = "${meta.authorName} • ID: ${meta.videoId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyberBlue
                                )
                                Text(
                                    text = if (meta.hasAccessibleCaptions) {
                                        "Direct Caption Track Found (${meta.captionLanguageCode ?: "auto"})"
                                    } else {
                                        "AI Context Transcript + Timestamp Synchronization Ready"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldSuccess
                                )
                            }
                        }
                    }
                }

                // Target Language Selector (Uzbek, English, Russian, Arabic)
                Text(
                    text = "2. ${strings.selectTargetLanguage}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = targetLanguage == lang
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectTargetLanguage(lang) },
                            label = {
                                Text(
                                    text = "${lang.flagEmoji} ${lang.englishName} (${lang.nativeName})",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = CyberBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricPurple.copy(alpha = 0.30f),
                                selectedLabelColor = TextWhite
                            ),
                            modifier = Modifier.testTag("lang_chip_${lang.code}")
                        )
                    }
                }

                // Main TRANSLATE VIDEO CTA Button
                val isBusy = translationStage is TranslationUiStage.ValidatingLink ||
                    translationStage is TranslationUiStage.TranslatingWithAi

                Button(
                    onClick = {
                        viewModel.startVideoTranslation(
                            onRequireSubscriptionNavigate = onNavigateToSubscription
                        )
                    },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricPurple,
                        contentColor = TextWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("translate_video_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${strings.translateButton} → ${targetLanguage.flagEmoji} ${targetLanguage.englishName.uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Translation Progress / Error Feedback
                when (val stage = translationStage) {
                    is TranslationUiStage.Idle -> Unit
                    is TranslationUiStage.ValidatingLink -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyberBlue, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stage.message, color = CyberBlue, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is TranslationUiStage.TranslatingWithAi -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stage.stageLabel, color = CyberBlue, style = MaterialTheme.typography.bodyMedium)
                                Text("${stage.progressPercent}%", color = TextWhite, style = MaterialTheme.typography.labelMedium)
                            }
                            LinearProgressIndicator(
                                progress = { stage.progressPercent / 100f },
                                color = CyberBlue,
                                trackColor = NavySurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                    is TranslationUiStage.Error -> {
                        Surface(
                            color = RoseError.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stage.message,
                                    color = RoseError,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (stage.allowFileUploadFallback) {
                                    OutlinedButton(
                                        onClick = { subtitleFileLauncher.launch("*/*") }
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Upload Authorized Subtitle / Transcript File")
                                    }
                                }
                            }
                        }
                    }
                    is TranslationUiStage.Completed -> {
                        Surface(
                            color = EmeraldSuccess.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Translation completed! Synchronized ${stage.record.subtitleCount} cues in ${targetLanguage.nativeName}.",
                                color = EmeraldSuccess,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Synchronized Subtitle Preview, Editor & Downloader (SRT, VTT, TXT)
        val currentRecord = activeTranslation
            ?: (translationStage as? TranslationUiStage.Completed)?.record
        if (currentRecord != null) {
            SubtitlePreviewAndExportCard(
                record = currentRecord,
                contextualSummary = (translationStage as? TranslationUiStage.Completed)?.summary,
                onSaveCueEdit = { cueIdx, newText ->
                    viewModel.updateCueText(currentRecord.id, cueIdx, newText)
                },
                onShowToast = onShowBanner
            )
        }

        // 4. Multimodal Image Understanding Card (gemini-4-argon + ThinkingLevel.HIGH)
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NavyCardBorder, RoundedCornerShape(20.dp))
                .testTag("image_analysis_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = strings.photoAnalysisTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite
                        )
                        Text(
                            text = strings.photoAnalysisSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                OutlinedTextField(
                    value = imagePrompt,
                    onValueChange = { imagePrompt = it },
                    label = { Text("Analysis & Translation Prompt") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isAnalyzingImage,
                    colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceVariant, contentColor = CyberBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_photo_analyze_button")
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAnalyzingImage) "Analyzing Photo with Gemini 4 Argon..." else "Select Photo to Analyze & Translate")
                }

                val bmp = selectedBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected photo for Gemini analysis",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                if (isAnalyzingImage) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyberBlue, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Running Gemini 4 Argon high-thinking vision analysis...", color = CyberBlue)
                    }
                }

                if (!imageAnalysisResult.isNullOrBlank()) {
                    Surface(
                        color = NavyBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NavyCardBorder, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = imageAnalysisResult ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextWhite,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
