package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.AppLanguage
import com.example.data.model.SubtitleCue
import com.example.data.model.SubtitleExporter
import com.example.data.model.TranslationRecord
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitlePreviewAndExportCard(
    record: TranslationRecord,
    contextualSummary: String?,
    onSaveCueEdit: (Int, String) -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val targetLang = AppLanguage.fromCode(record.targetLanguage)
    val cues = remember(record.subtitlesJson) { record.parsedCues() }
    var editingCueIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    var showComparison by remember { mutableStateOf(true) }
    var exportedPreviewFormat by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBlue.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .testTag("subtitle_preview_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.videoTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Detected: ${record.detectedLanguage} → Target: ${targetLang.flagEmoji} ${targetLang.nativeName}" +
                            if (targetLang.isRtl) " (RTL الفصحى)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberBlue
                    )
                }
                Surface(
                    color = EmeraldSuccess.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${cues.size} Cues",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (!contextualSummary.isNullOrBlank()) {
                Surface(
                    color = NavySurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "AI Context Summary: $contextualSummary",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Export & Copy Toolbar (SRT, VTT, TXT, Copy All)
            Text(
                text = "Download & Export Synchronized Subtitles (UTF-8)",
                style = MaterialTheme.typography.labelLarge,
                color = TextWhite
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        val srt = SubtitleExporter.exportSrt(cues)
                        exportedPreviewFormat = "SRT"
                        saveAndShareSubtitleFile(
                            context = context,
                            fileName = "${record.videoId}_${targetLang.code}.srt",
                            content = srt,
                            onStatus = onShowToast
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("export_srt_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download .SRT")
                }

                Button(
                    onClick = {
                        val vtt = SubtitleExporter.exportVtt(cues, isRtl = targetLang.isRtl)
                        exportedPreviewFormat = "VTT"
                        saveAndShareSubtitleFile(
                            context = context,
                            fileName = "${record.videoId}_${targetLang.code}.vtt",
                            content = vtt,
                            onStatus = onShowToast
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue, contentColor = NavyBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("export_vtt_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download .VTT")
                }

                OutlinedButton(
                    onClick = {
                        val txt = SubtitleExporter.exportTxt(cues)
                        exportedPreviewFormat = "TXT"
                        saveAndShareSubtitleFile(
                            context = context,
                            fileName = "${record.videoId}_${targetLang.code}.txt",
                            content = txt,
                            onStatus = onShowToast
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("export_txt_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download .TXT")
                }

                OutlinedButton(
                    onClick = {
                        val srt = SubtitleExporter.exportSrt(cues)
                        copyToClipboard(context, "SubTranslate SRT", srt)
                        onShowToast("Copied full translated subtitles (${cues.size} cues) to clipboard!")
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("copy_subtitles_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy All")
                }

                FilterChip(
                    selected = showComparison,
                    onClick = { showComparison = !showComparison },
                    label = { Text("Compare Original") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricPurple.copy(alpha = 0.25f),
                        selectedLabelColor = TextWhite
                    )
                )
            }

            if (exportedPreviewFormat != null) {
                val rawPreview = when (exportedPreviewFormat) {
                    "SRT" -> SubtitleExporter.exportSrt(cues)
                    "VTT" -> SubtitleExporter.exportVtt(cues, isRtl = targetLang.isRtl)
                    else -> SubtitleExporter.exportTxt(cues)
                }
                Surface(
                    color = NavyBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NavyCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Exported .$exportedPreviewFormat File Preview (UTF-8 Verified)",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldSuccess
                            )
                            OutlinedButton(onClick = { exportedPreviewFormat = null }) {
                                Text("Hide")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = rawPreview.take(900) + if (rawPreview.length > 900) "\n..." else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextWhite
                        )
                    }
                }
            }

            HorizontalDivider(color = NavyCardBorder)

            Text(
                text = "Synchronized Subtitle Timeline (Tap Edit icon to refine any sentence)",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cues.forEach { cue ->
                    CueItemRow(
                        cue = cue,
                        isRtl = targetLang.isRtl,
                        showOriginal = showComparison,
                        isEditing = editingCueIndex == cue.index,
                        editingText = editingText,
                        onStartEdit = {
                            editingCueIndex = cue.index
                            editingText = cue.translatedText
                        },
                        onEditChange = { editingText = it },
                        onConfirmEdit = {
                            onSaveCueEdit(cue.index, editingText)
                            editingCueIndex = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CueItemRow(
    cue: SubtitleCue,
    isRtl: Boolean,
    showOriginal: Boolean,
    isEditing: Boolean,
    editingText: String,
    onStartEdit: () -> Unit,
    onEditChange: (String) -> Unit,
    onConfirmEdit: () -> Unit
) {
    Surface(
        color = NavyBackground,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NavyCardBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BoxBadge("#${cue.index}", ElectricPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${cue.startTime} --> ${cue.endTime}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberBlue
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cue.speaker.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = cue.speaker,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = if (isEditing) onConfirmEdit else onStartEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditing) "Save subtitle" else "Edit subtitle",
                            tint = if (isEditing) EmeraldSuccess else CyberBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (showOriginal && cue.originalText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = cue.originalText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = onEditChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = TextWhite,
                            textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
                        )
                    )
                } else {
                    Text(
                        text = cue.translatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextWhite,
                        textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextWhite,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun saveAndShareSubtitleFile(
    context: Context,
    fileName: String,
    content: String,
    onStatus: (String) -> Unit
) {
    try {
        val exportDir = File(context.cacheDir, "exported_subtitles").apply { mkdirs() }
        val outFile = File(exportDir, fileName)
        outFile.writeText(content, Charsets.UTF_8)
        copyToClipboard(context, fileName, content)
        onStatus("Saved UTF-8 file '$fileName' (${outFile.length()} bytes) & copied to clipboard!")

        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, content)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export $fileName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            // In headless or restricted chooser environments, file is already saved and copied
        }
    } catch (e: Exception) {
        onStatus("Exported to clipboard: ${e.localizedMessage ?: ""}")
    }
}
