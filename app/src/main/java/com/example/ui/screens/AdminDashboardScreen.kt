package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.data.model.AuthorizedAdmins
import com.example.data.model.PaymentSubmission
import com.example.data.model.PlatformSettings
import com.example.data.model.SupportMessage
import com.example.data.model.TranslationRecord
import com.example.data.model.UserAccount
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import com.example.viewmodel.SubTranslateViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    viewModel: SubTranslateViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    if (!viewModel.isAdminUser) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Access Restricted: Administrator Role Required",
                style = MaterialTheme.typography.headlineSmall,
                color = RoseError
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Only authorized administrator accounts (${AuthorizedAdmins.EMAILS.joinToString(", ")}) can access the Administrator Dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        return
    }

    val platformSettings by viewModel.platformSettings.collectAsState()
    val isTestingKey by viewModel.isTestingAdminKey.collectAsState()
    val keyTestResult by viewModel.adminKeyTestResult.collectAsState()
    val allUsers by viewModel.adminAllUsers.collectAsState()
    val allPayments by viewModel.adminAllPayments.collectAsState()
    val allTranslations by viewModel.adminAllTranslations.collectAsState()
    val allSupport by viewModel.adminAllSupportMessages.collectAsState()
    val adminNotifs by viewModel.adminNotifications.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) }
    var searchEmailQuery by remember { mutableStateOf("") }

    val pendingPaymentsCount = allPayments.count { it.status == "PENDING" }
    val openSupportCount = allSupport.count { it.status == "OPEN" }
    val activeSubsCount = allUsers.count { it.isSubscriptionCurrentlyActive() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Admin Header & Statistics Summary
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberBlue, RoundedCornerShape(20.dp))
                .testTag("admin_dashboard_header")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = CyberBlue, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ADMINISTRATOR CONTROL CENTER",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite
                        )
                        Text(
                            text = "Full Admin Access: ${viewModel.currentUserEmail}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldSuccess
                        )
                    }
                }

                // Active AI Engine Banner
                Surface(
                    color = NavyBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ElectricPurple.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val engineDesc = if (platformSettings.shouldUseCustomGeminiKey()) {
                            "Active AI Engine: Custom Gemini API Key (${platformSettings.aiModelName})"
                        } else {
                            "Active AI Engine: Built-in Firebase AI Logic (gemini-4-argon High Thinking — No API Key Needed)"
                        }
                        Text(
                            text = engineDesc,
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberBlue
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatBadge("Users", "${allUsers.size}", CyberBlue)
                    StatBadge("Active Pro", "$activeSubsCount", EmeraldSuccess)
                    StatBadge("Pending Receipts", "$pendingPaymentsCount", AmberWarning)
                    StatBadge("Translations", "${allTranslations.size}", ElectricPurple)
                    StatBadge("Open Support", "$openSupportCount", RoseError)
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        "AI & Settings (API Key)",
                        "Payments ($pendingPaymentsCount)",
                        "Users (${allUsers.size})",
                        "Translations (${allTranslations.size})",
                        "Support ($openSupportCount)",
                        "Alerts (${adminNotifs.size})"
                    )
                    tabs.forEachIndexed { idx, title ->
                        FilterChip(
                            selected = selectedSubTab == idx,
                            onClick = { selectedSubTab = idx },
                            label = { Text(title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricPurple.copy(alpha = 0.35f),
                                selectedLabelColor = TextWhite
                            ),
                            modifier = Modifier.testTag("admin_tab_$idx")
                        )
                    }
                }
            }
        }

        when (selectedSubTab) {
            0 -> AdminAiAndPlatformSettingsSection(
                settings = platformSettings,
                isTestingKey = isTestingKey,
                keyTestResult = keyTestResult,
                onSaveSettings = { viewModel.adminSavePlatformSettings(it) },
                onTestCustomKey = { key, model -> viewModel.adminTestCustomGeminiKey(key, model) }
            )
            1 -> AdminPaymentsSection(
                payments = allPayments,
                onApprove = { p, note -> viewModel.adminApprovePayment(p, note) },
                onReject = { p, note -> viewModel.adminRejectPayment(p, note) },
                onDelete = { p -> viewModel.adminDeletePayment(p) }
            )
            2 -> AdminUsersSection(
                users = allUsers,
                searchQuery = searchEmailQuery,
                onSearchChange = { searchEmailQuery = it },
                onToggleSuspend = { viewModel.adminToggleUserSuspension(it) },
                onActivateSubscription = { viewModel.adminActivateSubscription(it) },
                onUpdateUserFull = { u, name, accSt, subSt, free, comp, paid, days ->
                    viewModel.adminUpdateUserFull(u, name, accSt, subSt, free, comp, paid, days)
                },
                onDeleteUser = { viewModel.adminDeleteUser(it) }
            )
            3 -> AdminTranslationsSection(
                translations = allTranslations,
                onUpdateTranslation = { rec, title, preview -> viewModel.adminUpdateTranslation(rec, title, preview) },
                onDeleteTranslation = { rec -> viewModel.adminDeleteTranslation(rec) }
            )
            4 -> AdminSupportSection(
                messages = allSupport,
                onReply = { msg, reply, resolved -> viewModel.adminReplySupport(msg, reply, resolved) },
                onDelete = { msg -> viewModel.adminDeleteSupportMessage(msg) }
            )
            5 -> AdminNotificationsSection(
                notifications = adminNotifs,
                onDelete = { id -> viewModel.adminDeleteNotification(id) }
            )
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = NavyBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminAiAndPlatformSettingsSection(
    settings: PlatformSettings,
    isTestingKey: Boolean,
    keyTestResult: String?,
    onSaveSettings: (PlatformSettings) -> Unit,
    onTestCustomKey: (String, String) -> Unit
) {
    var aiProviderMode by remember(settings.aiProviderMode) { mutableStateOf(settings.aiProviderMode) }
    var customGeminiKey by remember(settings.customGeminiApiKey) { mutableStateOf(settings.customGeminiApiKey) }
    var showApiKeyPlain by remember { mutableStateOf(false) }
    var aiModelName by remember(settings.aiModelName) { mutableStateOf(settings.aiModelName) }
    var customInstruction by remember(settings.customTranslationInstruction) { mutableStateOf(settings.customTranslationInstruction) }

    var subPriceText by remember(settings.subscriptionPriceUzs) { mutableStateOf(settings.subscriptionPriceUzs.toString()) }
    var cardNumberText by remember(settings.paymentCardNumber) { mutableStateOf(settings.paymentCardNumber) }
    var cardHolderText by remember(settings.paymentAccountHolder) { mutableStateOf(settings.paymentAccountHolder) }
    var subDurationText by remember(settings.subscriptionDurationDays) { mutableStateOf(settings.subscriptionDurationDays.toString()) }
    var initialFreeText by remember(settings.initialFreeTranslations) { mutableStateOf(settings.initialFreeTranslations.toString()) }

    LaunchedEffect(settings) {
        aiProviderMode = settings.aiProviderMode
        customGeminiKey = settings.customGeminiApiKey
        aiModelName = settings.aiModelName
        customInstruction = settings.customTranslationInstruction
        subPriceText = settings.subscriptionPriceUzs.toString()
        cardNumberText = settings.paymentCardNumber
        cardHolderText = settings.paymentAccountHolder
        subDurationText = settings.subscriptionDurationDays.toString()
        initialFreeText = settings.initialFreeTranslations.toString()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ElectricPurple, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = CyberBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Tarjima Dvigateli & Gemini API Kaliti (AI Engine & API Key)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )
            }

            Surface(
                color = EmeraldSuccess.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ma'lumot: Ilovada o'rnatilgan Firebase AI Logic (gemini-4-argon High Thinking) avtomatik ishlaydi (kalitsiz ham tarjima qiladi). Agar o'zingizning shaxsiy Gemini API kalitingizni kiritmoqchi bo'lsangiz, 'Custom Gemini API Key' rejimini tanlab kalitni kiriting — shunda barcha tarjimalar aynan siz kiritgan Gemini API kaliti orqali bajariladi!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldSuccess,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Text(
                text = "Select Active AI Translation Provider:",
                style = MaterialTheme.typography.labelLarge,
                color = TextWhite
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = aiProviderMode == "FIREBASE_AI_LOGIC",
                    onClick = { aiProviderMode = "FIREBASE_AI_LOGIC" },
                    label = { Text("1. Built-in Firebase AI Logic (Auto)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldSuccess.copy(alpha = 0.3f),
                        selectedLabelColor = TextWhite
                    ),
                    modifier = Modifier.testTag("mode_firebase_ai_logic")
                )
                FilterChip(
                    selected = aiProviderMode == "CUSTOM_GEMINI_KEY",
                    onClick = { aiProviderMode = "CUSTOM_GEMINI_KEY" },
                    label = { Text("2. Custom Gemini API Key (Admin Key)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBlue.copy(alpha = 0.3f),
                        selectedLabelColor = TextWhite
                    ),
                    modifier = Modifier.testTag("mode_custom_gemini_key")
                )
            }

            OutlinedTextField(
                value = customGeminiKey,
                onValueChange = {
                    customGeminiKey = it
                    if (it.isNotBlank() && aiProviderMode != "CUSTOM_GEMINI_KEY") {
                        aiProviderMode = "CUSTOM_GEMINI_KEY"
                    }
                },
                label = { Text("Gemini API Key (AIzaSy...)") },
                placeholder = { Text("Paste your Gemini API key here to translate via your key...") },
                visualTransformation = if (showApiKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    OutlinedButton(
                        onClick = { showApiKeyPlain = !showApiKeyPlain },
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(if (showApiKeyPlain) "Hide" else "Show")
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_gemini_api_key_input")
            )

            OutlinedTextField(
                value = aiModelName,
                onValueChange = { aiModelName = it },
                label = { Text("Gemini Model Name (e.g. gemini-4-argon, gemini-2.5-pro, gemini-2.5-flash)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_gemini_model_input")
            )

            OutlinedTextField(
                value = customInstruction,
                onValueChange = { customInstruction = it },
                label = { Text("Custom AI Translation Instructions / System Prompt Rules (Optional)") },
                placeholder = { Text("E.g. Always keep technical programming terms in brackets...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_custom_instruction_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onTestCustomKey(customGeminiKey, aiModelName) },
                    enabled = !isTestingKey && customGeminiKey.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_custom_gemini_key_button")
                ) {
                    Text(if (isTestingKey) "Testing Key..." else "Test Gemini API Key")
                }
            }

            if (!keyTestResult.isNullOrBlank()) {
                Surface(
                    color = if (keyTestResult.startsWith("Error")) RoseError.copy(alpha = 0.2f) else CyberBlue.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = keyTestResult,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (keyTestResult.startsWith("Error")) RoseError else CyberBlue,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            HorizontalDivider(color = NavyCardBorder)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CyberBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Obuna, Karta va Limit Sozlamalari (Subscription & Payment Settings)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = subPriceText,
                    onValueChange = { subPriceText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Monthly Price (UZS)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = subDurationText,
                    onValueChange = { subDurationText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Duration (Days)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = cardNumberText,
                    onValueChange = { cardNumberText = it },
                    label = { Text("Payment Card Number") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cardHolderText,
                    onValueChange = { cardHolderText = it },
                    label = { Text("Card Account Holder") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = initialFreeText,
                onValueChange = { initialFreeText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Default Free Translations for New Users") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val updated = settings.copy(
                        subscriptionPriceUzs = subPriceText.toLongOrNull() ?: AuthorizedAdmins.SUBSCRIPTION_PRICE_UZS,
                        paymentCardNumber = cardNumberText.ifBlank { AuthorizedAdmins.PAYMENT_CARD_NUMBER },
                        paymentAccountHolder = cardHolderText.ifBlank { AuthorizedAdmins.PAYMENT_ACCOUNT_HOLDER },
                        subscriptionDurationDays = subDurationText.toLongOrNull() ?: AuthorizedAdmins.SUBSCRIPTION_DURATION_DAYS,
                        initialFreeTranslations = initialFreeText.toLongOrNull() ?: AuthorizedAdmins.INITIAL_FREE_TRANSLATIONS,
                        aiProviderMode = aiProviderMode,
                        customGeminiApiKey = customGeminiKey.trim(),
                        aiModelName = aiModelName.ifBlank { "gemini-4-argon" }.trim(),
                        customTranslationInstruction = customInstruction.trim()
                    )
                    onSaveSettings(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple, contentColor = TextWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_platform_settings_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE ALL AI & PLATFORM SETTINGS", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AdminPaymentsSection(
    payments: List<PaymentSubmission>,
    onApprove: (PaymentSubmission, String) -> Unit,
    onReject: (PaymentSubmission, String) -> Unit,
    onDelete: (PaymentSubmission) -> Unit
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Submitted Payment Receipts (${payments.size})",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite
            )

            if (payments.isEmpty()) {
                Text("No payment receipts submitted yet.", color = TextSecondary)
            } else {
                payments.forEach { payment ->
                    var adminNote by remember(payment.id) { mutableStateOf(payment.adminNote) }
                    var showReceiptPreview by remember(payment.id) { mutableStateOf(false) }

                    Surface(
                        color = NavyBackground,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NavyCardBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(payment.userEmail, style = MaterialTheme.typography.titleMedium, color = TextWhite)
                                    Text(
                                        text = "${payment.amountUzs} UZS • File: ${payment.receiptFileName} (${payment.receiptMimeType})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CyberBlue
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = when (payment.status) {
                                            "APPROVED" -> EmeraldSuccess.copy(alpha = 0.2f)
                                            "REJECTED" -> RoseError.copy(alpha = 0.2f)
                                            else -> AmberWarning.copy(alpha = 0.2f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = payment.status,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = when (payment.status) {
                                                "APPROVED" -> EmeraldSuccess
                                                "REJECTED" -> RoseError
                                                else -> AmberWarning
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    IconButton(onClick = { onDelete(payment) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete payment", tint = RoseError)
                                    }
                                }
                            }

                            if (payment.aiReceiptSummary.isNotBlank()) {
                                Text(
                                    text = "AI Receipt Check: ${payment.aiReceiptSummary}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }

                            if (payment.receiptDataBase64.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { showReceiptPreview = !showReceiptPreview },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(if (showReceiptPreview) "Hide Receipt Attachment" else "Open Receipt Image / PDF")
                                }

                                if (showReceiptPreview) {
                                    val bmp = remember(payment.receiptDataBase64) {
                                        try {
                                            val bytes = Base64.decode(payment.receiptDataBase64, Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Receipt image from ${payment.userEmail}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    } else {
                                        Text(
                                            text = "PDF / Document Receipt Payload (${payment.receiptFileName}, ${payment.receiptDataBase64.length} base64 bytes verified)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CyberBlue
                                        )
                                    }
                                }
                            }

                            if (payment.status == "PENDING") {
                                OutlinedTextField(
                                    value = adminNote,
                                    onValueChange = { adminNote = it },
                                    label = { Text("Admin Verification Note") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onApprove(payment, adminNote) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = NavyBackground),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("approve_payment_${payment.id}")
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("APPROVE PAYMENT", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { onReject(payment, adminNote) },
                                        colors = ButtonDefaults.buttonColors(containerColor = RoseError, contentColor = TextWhite),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("reject_payment_${payment.id}")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("REJECT PAYMENT", fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Text(
                                    text = "Reviewed by ${payment.reviewedByEmail} • Expires: ${formatEpochDate(payment.subscriptionExpiresAtMillis)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminUsersSection(
    users: List<UserAccount>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onToggleSuspend: (UserAccount) -> Unit,
    onActivateSubscription: (UserAccount) -> Unit,
    onUpdateUserFull: (UserAccount, String, String, String, Long, Long, Long, Long) -> Unit,
    onDeleteUser: (UserAccount) -> Unit
) {
    val filtered = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter {
            it.email.contains(searchQuery, ignoreCase = true) ||
                it.displayName.contains(searchQuery, ignoreCase = true)
        }
    }

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
            Text("Registered Users — Full Admin Editor (${filtered.size})", style = MaterialTheme.typography.titleMedium, color = TextWhite)

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search users by email or name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberBlue) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_search_input")
            )

            filtered.forEach { user ->
                var isEditingUser by remember(user.id) { mutableStateOf(false) }
                var editName by remember(user.displayName) { mutableStateOf(user.displayName) }
                var editAccountStatus by remember(user.accountStatus) { mutableStateOf(user.accountStatus) }
                var editSubStatus by remember(user.subscriptionStatus) { mutableStateOf(user.subscriptionStatus) }
                var editFreeCount by remember(user.freeTranslationsRemaining) { mutableStateOf(user.freeTranslationsRemaining.toString()) }
                var editCompletedCount by remember(user.completedTranslationsCount) { mutableStateOf(user.completedTranslationsCount.toString()) }
                var editPaidCount by remember(user.paidTranslationsCount) { mutableStateOf(user.paidTranslationsCount.toString()) }
                var editSubDays by remember(user.subscriptionExpiresAtMillis) {
                    mutableStateOf(user.remainingSubscriptionDays().takeIf { it > 0 }?.toString() ?: "30")
                }

                Surface(
                    color = NavyBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NavyCardBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.email, style = MaterialTheme.typography.titleMedium, color = TextWhite)
                                Text(user.displayName, style = MaterialTheme.typography.labelMedium, color = CyberBlue)
                            }
                            Text(
                                text = "${user.accountStatus} • ${user.subscriptionStatus}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (user.accountStatus == "ACTIVE") EmeraldSuccess else RoseError
                            )
                        }
                        Text(
                            text = "Free Left: ${user.freeTranslationsRemaining} • Completed: ${user.completedTranslationsCount} (Paid: ${user.paidTranslationsCount})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        if (user.subscriptionExpiresAtMillis > 0L) {
                            Text(
                                text = "Subscription Expires: ${formatEpochDate(user.subscriptionExpiresAtMillis)} (${user.remainingSubscriptionDays()}d left)",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberBlue
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isEditingUser = !isEditingUser },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEditingUser) "Close Editor" else "Edit All Fields")
                            }
                            OutlinedButton(
                                onClick = { onActivateSubscription(user) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Activate 30d Sub")
                            }
                            OutlinedButton(
                                onClick = { onToggleSuspend(user) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (user.accountStatus == "SUSPENDED") "Reactivate" else "Suspend")
                            }
                            OutlinedButton(
                                onClick = { onDeleteUser(user) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = RoseError, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", color = RoseError)
                            }
                        }

                        if (isEditingUser) {
                            HorizontalDivider(color = NavyCardBorder)
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editFreeCount,
                                    onValueChange = { editFreeCount = it.filter { c -> c.isDigit() } },
                                    label = { Text("Free Translations Left") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = editSubDays,
                                    onValueChange = { editSubDays = it.filter { c -> c.isDigit() } },
                                    label = { Text("Sub Days (if ACTIVE)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editCompletedCount,
                                    onValueChange = { editCompletedCount = it.filter { c -> c.isDigit() } },
                                    label = { Text("Completed Total") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = editPaidCount,
                                    onValueChange = { editPaidCount = it.filter { c -> c.isDigit() } },
                                    label = { Text("Paid Count") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("ACTIVE", "SUSPENDED").forEach { st ->
                                    FilterChip(
                                        selected = editAccountStatus == st,
                                        onClick = { editAccountStatus = st },
                                        label = { Text("Account: $st") }
                                    )
                                }
                                listOf("FREE", "ACTIVE", "EXPIRED").forEach { sub ->
                                    FilterChip(
                                        selected = editSubStatus == sub,
                                        onClick = { editSubStatus = sub },
                                        label = { Text("Plan: $sub") }
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    onUpdateUserFull(
                                        user,
                                        editName,
                                        editAccountStatus,
                                        editSubStatus,
                                        editFreeCount.toLongOrNull() ?: user.freeTranslationsRemaining,
                                        editCompletedCount.toLongOrNull() ?: user.completedTranslationsCount,
                                        editPaidCount.toLongOrNull() ?: user.paidTranslationsCount,
                                        editSubDays.toLongOrNull() ?: 30L
                                    )
                                    isEditingUser = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = NavyBackground),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save User Changes", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSupportSection(
    messages: List<SupportMessage>,
    onReply: (SupportMessage, String, Boolean) -> Unit,
    onDelete: (SupportMessage) -> Unit
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
            Text("Support Requests & Attachments (${messages.size})", style = MaterialTheme.typography.titleMedium, color = TextWhite)

            if (messages.isEmpty()) {
                Text("No support requests submitted.", color = TextSecondary)
            } else {
                messages.forEach { msg ->
                    var replyText by remember(msg.id) { mutableStateOf(msg.adminReply) }
                    var showAttachment by remember(msg.id) { mutableStateOf(false) }

                    Surface(
                        color = NavyBackground,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NavyCardBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${msg.userEmail} • [${msg.category}] ${msg.subject} (${msg.status})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextWhite,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDelete(msg) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete support ticket", tint = RoseError)
                                }
                            }
                            Text(msg.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

                            if (msg.attachmentFileName.isNotBlank()) {
                                OutlinedButton(onClick = { showAttachment = !showAttachment }) {
                                    Text(if (showAttachment) "Hide Attachment" else "View Attachment (${msg.attachmentFileName})")
                                }
                                if (showAttachment && msg.attachmentDataBase64.isNotBlank()) {
                                    val bmp = remember(msg.attachmentDataBase64) {
                                        try {
                                            val bytes = Base64.decode(msg.attachmentDataBase64, Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Support attachment",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it },
                                label = { Text("Reply to ${msg.userEmail}") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onReply(msg, replyText, true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = NavyBackground)
                                ) {
                                    Text("Send Reply & Mark Resolved", fontWeight = FontWeight.Bold)
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
private fun AdminNotificationsSection(
    notifications: List<AppNotification>,
    onDelete: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NavyCardBorder, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AmberWarning)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Administrator Notifications (${notifications.size})", style = MaterialTheme.typography.titleMedium, color = TextWhite)
            }
            if (notifications.isEmpty()) {
                Text("No admin notifications yet.", color = TextSecondary)
            } else {
                notifications.forEach { item ->
                    Surface(color = NavyBackground, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.type}: ${item.title} — ${item.body}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextWhite,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDelete(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete notification", tint = RoseError)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTranslationsSection(
    translations: List<TranslationRecord>,
    onUpdateTranslation: (TranslationRecord, String, String) -> Unit,
    onDeleteTranslation: (TranslationRecord) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NavyCardBorder, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("All Platform Translations (${translations.size})", style = MaterialTheme.typography.titleMedium, color = TextWhite)
            if (translations.isEmpty()) {
                Text("No translations recorded yet.", color = TextSecondary)
            } else {
                translations.forEach { rec ->
                    var isEditing by remember(rec.id) { mutableStateOf(false) }
                    var editTitle by remember(rec.videoTitle) { mutableStateOf(rec.videoTitle) }
                    var editPreview by remember(rec.translatedTranscriptPreview) { mutableStateOf(rec.translatedTranscriptPreview) }

                    Surface(color = NavyBackground, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${rec.userEmail} • ${rec.videoTitle}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "${rec.detectedLanguage} → ${rec.targetLanguage.uppercase()} (${rec.subtitleCount} cues)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyberBlue
                                    )
                                }
                                Row {
                                    IconButton(onClick = { isEditing = !isEditing }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit translation", tint = CyberBlue)
                                    }
                                    IconButton(onClick = { onDeleteTranslation(rec) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete translation", tint = RoseError)
                                    }
                                }
                            }
                            if (isEditing) {
                                OutlinedTextField(
                                    value = editTitle,
                                    onValueChange = { editTitle = it },
                                    label = { Text("Video Title") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editPreview,
                                    onValueChange = { editPreview = it },
                                    label = { Text("Translated Transcript Preview") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        onUpdateTranslation(rec, editTitle, editPreview)
                                        isEditing = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = NavyBackground),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Translation Changes", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
