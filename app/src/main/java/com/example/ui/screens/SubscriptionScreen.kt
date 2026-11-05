package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AuthorizedAdmins
import com.example.data.model.PaymentSubmission
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SubscriptionScreen(
    viewModel: SubTranslateViewModel,
    onNavigateBack: () -> Unit,
    onShowBanner: (String) -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val userAccount by viewModel.userAccount.collectAsState()
    val userPayments by viewModel.userPayments.collectAsState()
    val platformSettings by viewModel.platformSettings.collectAsState()
    var confirmationShown by remember { mutableStateOf(false) }
    var lastUploadedFileName by remember { mutableStateOf("") }

    val displayPriceUzs = platformSettings.subscriptionPriceUzs
    val displayCardNumber = platformSettings.paymentCardNumber.ifBlank { AuthorizedAdmins.PAYMENT_CARD_NUMBER }
    val displayAccountHolder = platformSettings.paymentAccountHolder.ifBlank { AuthorizedAdmins.PAYMENT_ACCOUNT_HOLDER }
    val displayDurationDays = platformSettings.subscriptionDurationDays

    // Supports JPG, PNG, and PDF payment receipt uploads
    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val fileName = uri.lastPathSegment?.substringAfterLast('/')
                    ?: if (mimeType == "application/pdf") "receipt.pdf" else "receipt.jpg"

                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    var bitmapForAi: Bitmap? = null
                    val encodedBase64 = if (mimeType.startsWith("image/")) {
                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        bitmapForAi = decoded
                        if (decoded != null) {
                            val scaled = Bitmap.createScaledBitmap(
                                decoded,
                                600,
                                (decoded.height * (600f / decoded.width.coerceAtLeast(1))).toInt().coerceAtLeast(1),
                                true
                            )
                            val baos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        } else {
                            Base64.encodeToString(bytes.take(150_000).toByteArray(), Base64.NO_WRAP)
                        }
                    } else {
                        Base64.encodeToString(bytes.take(150_000).toByteArray(), Base64.NO_WRAP)
                    }

                    lastUploadedFileName = fileName
                    viewModel.submitPaymentReceipt(
                        fileName = fileName,
                        mimeType = mimeType,
                        base64Data = encodedBase64,
                        bitmapForAiInspection = bitmapForAi,
                        onComplete = {
                            confirmationShown = true
                        }
                    )
                }
            } catch (e: Exception) {
                onShowBanner("Receipt upload error: ${e.localizedMessage}")
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
        // Current Subscription Status Banner
        val acct = userAccount
        if (acct != null) {
            val isPro = acct.isSubscriptionCurrentlyActive()
            val isExpired = acct.subscriptionStatus == "EXPIRED" ||
                (acct.subscriptionStatus == "ACTIVE" && acct.subscriptionExpiresAtMillis in 1..System.currentTimeMillis())

            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        when {
                            isPro -> EmeraldSuccess
                            isExpired -> RoseError
                            else -> NavyCardBorder
                        },
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isPro -> "Pro Subscription Active ($displayDurationDays Days)"
                                isExpired -> "Subscription Expired — Renewal Required"
                                else -> "Free Tier Plan (${acct.freeTranslationsRemaining} / ${platformSettings.initialFreeTranslations} Free Left)"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = when {
                                isPro -> EmeraldSuccess
                                isExpired -> RoseError
                                else -> TextWhite
                            }
                        )
                        Surface(
                            color = NavySurfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = acct.subscriptionStatus,
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (acct.subscriptionStartAtMillis > 0L && acct.subscriptionExpiresAtMillis > 0L) {
                        Text(
                            text = "Start: ${formatEpochDate(acct.subscriptionStartAtMillis)} • Expires: ${formatEpochDate(acct.subscriptionExpiresAtMillis)} (${acct.remainingSubscriptionDays()} days remaining)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        Text(
                            text = "Completed Translations: ${acct.completedTranslationsCount} • Free Remaining: ${acct.freeTranslationsRemaining}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Monthly Subscription Pricing & Payment Card Details
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ElectricPurple, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pro Monthly Subscription",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextWhite
                        )
                        Text(
                            text = "$displayDurationDays days unlimited access from administrator approval",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        color = ElectricPurple.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "%,d UZS".format(Locale.US, displayPriceUzs),
                            style = MaterialTheme.typography.headlineSmall,
                            color = CyberBlue,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                HorizontalDivider(color = NavyCardBorder)

                // Official Payment Card Visual Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ElectricPurple, NavySurfaceVariant)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = TextWhite)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "UZCARD / HUMO TRANSFER",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextWhite.copy(alpha = 0.85f)
                                )
                            }
                            Text(
                                text = "%,d UZS / %d Days".format(Locale.US, displayPriceUzs, displayDurationDays),
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberBlue
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayCardNumber,
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextWhite
                            )
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cb?.setPrimaryClip(ClipData.newPlainText("Card Number", displayCardNumber))
                                    onShowBanner("Copied card number $displayCardNumber!")
                                },
                                modifier = Modifier.testTag("copy_card_number_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy card number", tint = TextWhite)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Account Holder: $displayAccountHolder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                    }
                }

                // Payment Instructions
                Text(
                    text = "Payment Instructions:",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )
                InstructionStep("1. Transfer %,d UZS to card %s (Account holder: %s).".format(Locale.US, displayPriceUzs, displayCardNumber, displayAccountHolder))
                InstructionStep("2. Save your payment receipt as JPG, PNG, or PDF.")
                InstructionStep("3. Tap 'Upload Payment Receipt' below to attach your receipt.")
                InstructionStep("4. Status will be set to PENDING. An authorized administrator will verify your receipt and activate your $displayDurationDays-day subscription.")

                Surface(
                    color = AmberWarning.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Uploading a receipt does NOT activate a subscription automatically. Only an authorized administrator can approve the payment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AmberWarning
                        )
                    }
                }

                Button(
                    onClick = {
                        receiptPickerLauncher.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple, contentColor = TextWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("upload_payment_receipt_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Upload Payment Receipt (JPG / PNG / PDF)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (confirmationShown) {
                    Surface(
                        color = EmeraldSuccess.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Receipt '$lastUploadedFileName' submitted! Status: PENDING administrator review. You will be notified as soon as it is approved.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmeraldSuccess
                            )
                        }
                    }
                }
            }
        }

        // User's Payment History
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
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = CyberBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your Payment Receipt History (${userPayments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite
                    )
                }

                if (userPayments.isEmpty()) {
                    Text(
                        text = "No payment receipts submitted yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    userPayments.forEach { payment ->
                        PaymentHistoryRow(payment)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
    )
}

@Composable
private fun PaymentHistoryRow(payment: PaymentSubmission) {
    val badgeColor = when (payment.status) {
        "APPROVED" -> EmeraldSuccess
        "REJECTED" -> RoseError
        else -> AmberWarning
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${payment.amountUzs} UZS • ${payment.receiptFileName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = payment.status,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeColor
                        )
                    }
                }
            }
            if (payment.approvedAtMillis > 0L) {
                Text(
                    text = "Approved: ${formatEpochDate(payment.approvedAtMillis)} • Expires: ${formatEpochDate(payment.subscriptionExpiresAtMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldSuccess
                )
            }
            if (payment.adminNote.isNotBlank()) {
                Text(
                    text = "Admin Note: ${payment.adminNote}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

internal fun formatEpochDate(millis: Long): String {
    if (millis <= 0L) return "—"
    return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(millis))
}
