package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.TranslationRecord
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
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactAdminScreen(
    viewModel: SubTranslateViewModel,
    onNavigateBack: () -> Unit,
    onShowBanner: (String) -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val messages by viewModel.userSupportMessages.collectAsState()

    val categories = listOf(
        "QUESTION" to "Ask Question",
        "TECHNICAL_PROBLEM" to "Technical Problem",
        "TRANSLATION_ERROR" to "Translation Error",
        "PAYMENT_RECEIPT" to "Payment Receipt",
        "ASSISTANCE" to "Request Assistance"
    )

    var selectedCategory by remember { mutableStateOf("QUESTION") }
    var subject by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var attachmentName by remember { mutableStateOf("") }
    var attachmentMime by remember { mutableStateOf("") }
    var attachmentBase64 by remember { mutableStateOf("") }

    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment.jpg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val b64 = if (mime.startsWith("image/")) {
                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (decoded != null) {
                            val scaled = Bitmap.createScaledBitmap(
                                decoded,
                                520,
                                (decoded.height * (520f / decoded.width.coerceAtLeast(1))).toInt().coerceAtLeast(1),
                                true
                            )
                            val baos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        } else {
                            Base64.encodeToString(bytes.take(120_000).toByteArray(), Base64.NO_WRAP)
                        }
                    } else {
                        Base64.encodeToString(bytes.take(120_000).toByteArray(), Base64.NO_WRAP)
                    }
                    attachmentName = name
                    attachmentMime = mime
                    attachmentBase64 = b64
                    onShowBanner("Attached '$name'")
                }
            } catch (e: Exception) {
                onShowBanner("Attachment error: ${e.localizedMessage}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NavyCardBorder, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = CyberBlue, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CONTACT ADMIN",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite
                        )
                        Text(
                            text = "Send messages, report translation or technical issues, or attach screenshots & receipts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { (code, label) ->
                        FilterChip(
                            selected = selectedCategory == code,
                            onClick = { selectedCategory = code },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricPurple.copy(alpha = 0.28f),
                                selectedLabelColor = TextWhite
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_subject_input")
                )

                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it },
                    label = { Text("Your Message / Question / Bug Report") },
                    minLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_message_input")
                )

                OutlinedButton(
                    onClick = {
                        attachmentLauncher.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_attach_button")
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (attachmentName.isBlank()) "Attach Screenshot or Payment Receipt (Optional)"
                        else "Attached: $attachmentName"
                    )
                }

                Button(
                    onClick = {
                        if (subject.isBlank() || messageBody.isBlank()) {
                            onShowBanner("Please enter both a subject and message.")
                            return@Button
                        }
                        viewModel.submitSupportTicket(
                            category = selectedCategory,
                            subject = subject,
                            message = messageBody,
                            attachmentFileName = attachmentName,
                            attachmentMimeType = attachmentMime,
                            attachmentBase64 = attachmentBase64,
                            onComplete = {
                                subject = ""
                                messageBody = ""
                                attachmentName = ""
                                attachmentMime = ""
                                attachmentBase64 = ""
                            }
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple, contentColor = TextWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("send_support_message_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Message to Administrator", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Support Message History & Admin Replies
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NavyCardBorder, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Your Support Requests & Admin Replies (${messages.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )

                if (messages.isEmpty()) {
                    Text(
                        text = "No support messages sent yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    messages.forEach { msg ->
                        Surface(
                            color = NavyBackground,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NavyCardBorder, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "[${msg.category}] ${msg.subject}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextWhite
                                    )
                                    Surface(
                                        color = if (msg.status == "RESOLVED") EmeraldSuccess.copy(alpha = 0.2f)
                                        else AmberWarning.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = msg.status,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (msg.status == "RESOLVED") EmeraldSuccess else AmberWarning,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(msg.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                if (msg.attachmentFileName.isNotBlank()) {
                                    Text(
                                        text = "Attachment: ${msg.attachmentFileName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyberBlue
                                    )
                                }
                                if (msg.adminReply.isNotBlank()) {
                                    HorizontalDivider(color = NavyCardBorder)
                                    Text(
                                        text = "Admin Reply (${msg.repliedByEmail}): ${msg.adminReply}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EmeraldSuccess
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyTranslationsAndAccountScreen(
    viewModel: SubTranslateViewModel,
    onOpenTranslationInEditor: (TranslationRecord) -> Unit,
    onNavigateToSubscription: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val account by viewModel.userAccount.collectAsState()
    val translations by viewModel.userTranslations.collectAsState()
    val payments by viewModel.userPayments.collectAsState()
    val notifications by viewModel.userNotifications.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Personal User Dashboard Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NavyCardBorder, RoundedCornerShape(20.dp))
                .testTag("user_dashboard_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = CyberBlue, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = account?.displayName?.ifBlank { "My Account" } ?: "My Account",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite
                            )
                            Text(
                                text = viewModel.currentUserEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }

                HorizontalDivider(color = NavyCardBorder)

                val acct = account
                if (acct != null) {
                    DashboardStatRow("Account Status", acct.accountStatus)
                    DashboardStatRow("Subscription Status", acct.subscriptionStatus)
                    DashboardStatRow("Free Translations Remaining", "${acct.freeTranslationsRemaining} / 5")
                    DashboardStatRow("Total Completed Translations", "${acct.completedTranslationsCount}")
                    DashboardStatRow("Paid Subscription Translations", "${acct.paidTranslationsCount}")
                    DashboardStatRow("Subscription Start Date", formatEpochDate(acct.subscriptionStartAtMillis))
                    DashboardStatRow("Subscription Expiration Date", formatEpochDate(acct.subscriptionExpiresAtMillis))
                    DashboardStatRow("Remaining Subscription Days", "${acct.remainingSubscriptionDays()} days")

                    Button(
                        onClick = onNavigateToSubscription,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (acct.isSubscriptionCurrentlyActive()) "Manage / Extend Subscription"
                            else "Upgrade or Renew Pro Subscription (20,000 UZS)"
                        )
                    }
                }
            }
        }

        // 2. Notifications Card
        if (notifications.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyCardBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = AmberWarning)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Account Notifications (${notifications.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite
                        )
                    }
                    notifications.take(8).forEach { notif ->
                        Surface(
                            color = NavyBackground,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.markNotificationAsRead(notif.id) }
                                .border(
                                    1.dp,
                                    if (!notif.isRead) CyberBlue else NavyCardBorder,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(notif.title, style = MaterialTheme.typography.titleMedium, color = TextWhite)
                                Text(notif.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // 3. Translation History Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NavyCardBorder, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = CyberBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "My Video Translations (${translations.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite
                    )
                }

                if (translations.isEmpty()) {
                    Text(
                        text = "No completed translations yet. Paste a YouTube link on the Translate tab to get started!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    translations.forEach { item ->
                        val targetLang = AppLanguage.fromCode(item.targetLanguage)
                        Surface(
                            color = NavyBackground,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenTranslationInEditor(item) }
                                .border(1.dp, NavyCardBorder, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.videoTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextWhite,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        color = NavySurfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${targetLang.flagEmoji} ${targetLang.englishName}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CyberBlue,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${item.subtitleCount} synchronized cues • Source: ${item.detectedLanguage}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldSuccess
                                )
                                Text(
                                    text = item.translatedTranscriptPreview.take(140),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 2
                                )
                                Text(
                                    text = "Tap to preview, edit, or download .SRT / .VTT / .TXT →",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyberBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextWhite)
    }
}
