package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AppLanguage
import com.example.data.repository.PlatformRepository
import com.example.ui.i18n.AppStrings
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ContactAdminScreen
import com.example.ui.screens.MyTranslationsAndAccountScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.screens.TranslateVideoScreen
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import com.example.util.authStateFlow
import com.example.util.configureAppCheck
import com.example.util.signOutUser
import com.example.viewmodel.SubTranslateViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        AuthenticatedPlatformScaffold(
            currentUserId = user.uid,
            currentUserEmail = user.email.orEmpty(),
            currentUserDisplayName = user.displayName.orEmpty()
        )
    }
}

@Composable
private fun AuthenticatedPlatformScaffold(
    currentUserId: String,
    currentUserEmail: String,
    currentUserDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: SubTranslateViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SubTranslateViewModel(
                    repository = PlatformRepository(db),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserDisplayName = currentUserDisplayName
                )
            }
        }
    )

    val uiLanguage by viewModel.uiLanguage.collectAsState()
    val strings = AppStrings.forLanguage(uiLanguage)
    val bannerMessage by viewModel.bannerMessage.collectAsState()
    val userNotifications by viewModel.userNotifications.collectAsState()
    val unreadCount = userNotifications.count { !it.isRead }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val layoutDir = if (uiLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Scaffold(
            containerColor = NavyBackground,
            topBar = {
                TopSaaSBar(
                    uiLanguage = uiLanguage,
                    onSelectLanguage = viewModel::setUiLanguage,
                    isAdmin = viewModel.isAdminUser
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = NavySurface,
                    contentColor = TextWhite,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Translate, contentDescription = strings.navTranslate) },
                        label = { Text(strings.navTranslate) },
                        colors = navItemColors(),
                        modifier = Modifier.testTag("nav_tab_translate")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.CreditCard, contentDescription = strings.navSubscription) },
                        label = { Text(strings.navSubscription) },
                        colors = navItemColors(),
                        modifier = Modifier.testTag("nav_tab_subscription")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.SupportAgent, contentDescription = strings.navSupport) },
                        label = { Text(strings.navSupport) },
                        colors = navItemColors(),
                        modifier = Modifier.testTag("nav_tab_support")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge(containerColor = CyberBlue, contentColor = NavyBackground) {
                                            Text("$unreadCount")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Person, contentDescription = strings.navAccount)
                            }
                        },
                        label = { Text(strings.navAccount) },
                        colors = navItemColors(),
                        modifier = Modifier.testTag("nav_tab_account")
                    )
                    if (viewModel.isAdminUser) {
                        NavigationBarItem(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = strings.navAdmin) },
                            label = { Text(strings.navAdmin) },
                            colors = navItemColors(),
                            modifier = Modifier.testTag("nav_tab_admin")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(visible = !bannerMessage.isNullOrBlank()) {
                    Surface(
                        color = EmeraldSuccess.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { viewModel.clearBanner() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bannerMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextWhite
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> TranslateVideoScreen(
                            viewModel = viewModel,
                            onNavigateToSubscription = { selectedTab = 1 },
                            onShowBanner = {}
                        )
                        1 -> SubscriptionScreen(
                            viewModel = viewModel,
                            onNavigateBack = { selectedTab = 0 },
                            onShowBanner = {}
                        )
                        2 -> ContactAdminScreen(
                            viewModel = viewModel,
                            onNavigateBack = { selectedTab = 0 },
                            onShowBanner = {}
                        )
                        3 -> MyTranslationsAndAccountScreen(
                            viewModel = viewModel,
                            onOpenTranslationInEditor = { record ->
                                viewModel.selectTranslationForPreview(record)
                                selectedTab = 0
                            },
                            onNavigateToSubscription = { selectedTab = 1 },
                            onSignOut = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    onSignOutComplete = {},
                                    scope = scope
                                )
                            },
                            onNavigateBack = { selectedTab = 0 }
                        )
                        4 -> AdminDashboardScreen(
                            viewModel = viewModel,
                            onNavigateBack = { selectedTab = 0 }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopSaaSBar(
    uiLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    isAdmin: Boolean
) {
    Surface(
        color = NavySurface,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(ElectricPurple, CyberBlue))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "SubTranslate AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    if (isAdmin) {
                        Text(
                            text = "Administrator Verified",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldSuccess
                        )
                    }
                }
            }

            // 4-Language UI Switcher (UZ, EN, RU, AR)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppLanguage.entries.forEach { lang ->
                    val active = uiLanguage == lang
                    Surface(
                        color = if (active) ElectricPurple else NavySurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { onSelectLanguage(lang) }
                            .border(1.dp, if (active) CyberBlue else NavyCardBorder, RoundedCornerShape(8.dp))
                            .testTag("ui_lang_${lang.code}")
                    ) {
                        Text(
                            text = "${lang.flagEmoji} ${lang.code.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = CyberBlue,
    selectedTextColor = CyberBlue,
    indicatorColor = ElectricPurple.copy(alpha = 0.25f),
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary
)
