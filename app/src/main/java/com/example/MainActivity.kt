package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.GeminiTtsService
import com.example.audio.VoiceCallAudioEngine
import com.example.crypto.E2eeCryptoManager
import com.example.data.AuthRepository
import com.example.data.ChatRepository
import com.example.data.PreferencesRepository
import com.example.data.ThemeMode
import com.example.data.UiState
import com.example.firebase.configureAppCheck
import com.example.ui.MainViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.GoogleGLogo
import com.example.ui.screens.MapScreen
import com.example.ui.screens.PrivacyYouTubeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceCallScreen
import com.example.ui.theme.KinChatTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CHATS("Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, "nav_chats"),
    MAP("Map", Icons.Filled.Map, Icons.Outlined.Map, "nav_map"),
    WATCH("Watch", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "nav_watch"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory: Configure Firebase App Check with launch intent before any UI or ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = remember { Firebase.auth }
    val currentUser by AuthRepository.authStateFlow(auth)
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var lastObservedUid by remember { mutableStateOf(auth.currentUser?.uid) }
    var showGoogleSignInAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.uid) {
        val newUid = currentUser?.uid
        if (lastObservedUid == null && newUid != null) {
            showGoogleSignInAnimation = true
        }
        lastObservedUid = newUid
    }

    val user = currentUser
    Box(modifier = Modifier.fillMaxSize()) {
        if (user == null) {
            KinChatTheme(themeMode = ThemeMode.SYSTEM) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    AuthScreen(
                        onAuthSuccess = {
                            showGoogleSignInAnimation = true
                        }
                    )
                }
            }
        } else {
            key(user.uid) {
                AuthenticatedKinChatApp(currentUserId = user.uid)
            }
        }

        if (showGoogleSignInAnimation) {
            KinChatTheme(themeMode = ThemeMode.SYSTEM) {
                GoogleSignInTransitionOverlay(
                    onAnimationFinished = { showGoogleSignInAnimation = false }
                )
            }
        }
    }
}

@Composable
private fun GoogleSignInTransitionOverlay(
    onAnimationFinished: () -> Unit
) {
    val logoScale = remember { Animatable(0.45f) }
    val logoAlpha = remember { Animatable(1f) }
    val backdropAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Pop in at center
        logoScale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
        // Grow bigger and bigger in the center of the phone, then fade away
        coroutineScope {
            launch {
                logoScale.animateTo(
                    targetValue = 5.4f,
                    animationSpec = tween(durationMillis = 780, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(480)
                logoAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300)
                )
            }
            launch {
                delay(520)
                backdropAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 280)
                )
            }
        }
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = backdropAlpha.value))
            .testTag("google_signin_logo_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    alpha = logoAlpha.value
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                GoogleGLogo(modifier = Modifier.size(66.dp))
            }
        }
    }
}

@Composable
private fun AuthenticatedKinChatApp(currentUserId: String) {
    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val crypto = E2eeCryptoManager(app)
                val chatRepo = ChatRepository(db = db, crypto = crypto)
                val prefsRepo = PreferencesRepository(app)
                val ttsService = GeminiTtsService(app)
                val audioEngine = VoiceCallAudioEngine(app)
                MainViewModel(
                    chatRepo = chatRepo,
                    prefsRepo = prefsRepo,
                    ttsService = ttsService,
                    audioEngine = audioEngine,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val activeCallUi by viewModel.activeCallUi.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.CHATS) }
    var mapLat by remember { mutableStateOf(37.4220) }
    var mapLng by remember { mutableStateOf(-122.0841) }
    var mapLabel by remember { mutableStateOf("Googleplex Campus") }
    var youtubeVideoId by remember { mutableStateOf("aqz-KE-bpKQ") }
    var youtubeTitle by remember { mutableStateOf("Big Buck Bunny (Open Film)") }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    val chats = (chatsState as? UiState.Success)?.data.orEmpty()
    val openChatRoom = chats.firstOrNull { it.id == selectedChatId }

    KinChatTheme(
        themeMode = prefs.themeMode,
        appColor = prefs.appColor,
        chatColor = prefs.chatColor,
        highContrast = prefs.highContrast,
        textScale = prefs.textSizePreset.scale
    ) {
        when {
            activeCallUi != null -> {
                val call = activeCallUi!!
                val callChatTitle = chats.firstOrNull { it.id == call.call.chatId }?.title ?: "Encrypted Call"
                VoiceCallScreen(
                    callState = call,
                    chatTitle = callChatTitle,
                    viewModel = viewModel,
                    onEndCall = {
                        val room = chats.firstOrNull { it.id == call.call.chatId }
                        viewModel.endActiveCall(room)
                    }
                )
            }

            openChatRoom != null -> {
                ConversationScreen(
                    chat = openChatRoom,
                    viewModel = viewModel,
                    onBack = { viewModel.selectChat(null) },
                    onOpenMapLocation = { lat, lng, label ->
                        mapLat = lat
                        mapLng = lng
                        mapLabel = label
                        viewModel.selectChat(null)
                        currentTab = MainTab.MAP
                    },
                    onOpenYouTubeVideo = { vid, title ->
                        youtubeVideoId = vid
                        youtubeTitle = title
                        viewModel.selectChat(null)
                        currentTab = MainTab.WATCH
                    }
                )
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 4.dp
                                ) {
                                    MainTab.entries.forEach { tab ->
                                        val isSelected = currentTab == tab
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = { Text(tab.label) },
                                            modifier = Modifier.testTag(tab.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (useRail) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    MainTab.entries.forEach { tab ->
                                        val isSelected = currentTab == tab
                                        NavigationRailItem(
                                            selected = isSelected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = { Text(tab.label) },
                                            modifier = Modifier.testTag(tab.testTag)
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                when (currentTab) {
                                    MainTab.CHATS -> ChatsListScreen(
                                        viewModel = viewModel,
                                        onOpenChat = { chatId -> viewModel.selectChat(chatId) },
                                        onJoinCall = { call -> viewModel.joinExistingCall(call, false) }
                                    )
                                    MainTab.MAP -> MapScreen(
                                        initialLat = mapLat,
                                        initialLng = mapLng,
                                        initialLabel = mapLabel,
                                        viewModel = viewModel,
                                        onSharedToChat = { chatId ->
                                            viewModel.selectChat(chatId)
                                            currentTab = MainTab.CHATS
                                        }
                                    )
                                    MainTab.WATCH -> PrivacyYouTubeScreen(
                                        initialVideoId = youtubeVideoId,
                                        initialVideoTitle = youtubeTitle,
                                        viewModel = viewModel,
                                        onSharedToChat = { chatId ->
                                            viewModel.selectChat(chatId)
                                            currentTab = MainTab.CHATS
                                        }
                                    )
                                    MainTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
