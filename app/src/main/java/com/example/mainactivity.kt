package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.LocalLudoRepository
import com.example.data.local.LudoDatabase
import com.example.data.remote.CloudLudoRepository
import com.example.domain.GameModeType
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signOut
import com.example.ui.screens.ArenaScreen
import com.example.ui.screens.GeminiCoachScreen
import com.example.ui.screens.LeaderboardsScreen
import com.example.ui.screens.TournamentsScreen
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RoyalObsidian
import com.example.ui.theme.RoyalSurface
import com.example.ui.viewmodel.LudoViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LudoRoyaleAppRoot()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun LudoRoyaleAppRoot(auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    var localSessionMode by rememberSaveable { mutableStateOf<GameModeType?>(null) }

    if (user == null && localSessionMode == null) {
        AuthScreen(
            onAuthSuccess = { /* authStateFlow updates automatically */ },
            onStartLocalMode = { mode ->
                localSessionMode = mode
            }
        )
    } else {
        AuthenticatedLudoShell(
            currentUserId = user?.uid,
            currentUserDisplayName = user?.displayName
                ?: user?.email?.substringBefore("@")
                ?: "Player 1",
            initialMode = localSessionMode ?: GameModeType.SOLO_GEMINI,
            onExitLocalModeToSignIn = {
                localSessionMode = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedLudoShell(
    currentUserId: String?,
    currentUserDisplayName: String,
    initialMode: GameModeType = GameModeType.SOLO_GEMINI,
    onExitLocalModeToSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModelKey = currentUserId ?: "local_session_${initialMode.id}"
    val viewModel: LudoViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localDb = LudoDatabase.getInstance(app)
                // Strictly gate Firestore instantiation & CloudLudoRepository behind Google Sign-In (currentUserId != null)
                val cloudRepo = if (currentUserId != null) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    CloudLudoRepository(firestore)
                } else {
                    null
                }
                LudoViewModel(
                    localRepository = LocalLudoRepository(localDb.ludoDao()),
                    cloudRepository = cloudRepo,
                    currentUserId = currentUserId,
                    currentUserDisplayName = currentUserDisplayName,
                    initialMode = initialMode
                )
            }
        }
    )

    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val savedGames by viewModel.savedGames.collectAsStateWithLifecycle()
    val localStats by viewModel.localLeaderboard.collectAsStateWithLifecycle()
    val globalLeaderboardState by viewModel.globalLeaderboardState.collectAsStateWithLifecycle()
    val cloudMatchHistoryState by viewModel.cloudMatchHistoryState.collectAsStateWithLifecycle()
    val publicTournamentsState by viewModel.publicTournamentsState.collectAsStateWithLifecycle()
    val myTournamentsState by viewModel.myTournamentsState.collectAsStateWithLifecycle()
    val aiChatMessages by viewModel.aiChatMessages.collectAsStateWithLifecycle()
    val selectedModelTier by viewModel.selectedModelTier.collectAsStateWithLifecycle()
    val selectedPersona by viewModel.selectedPersona.collectAsStateWithLifecycle()
    val isAiChatLoading by viewModel.isAiChatLoading.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    if (currentTab != 0) {
        BackHandler {
            currentTab = 0
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = RoyalObsidian,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = ImperialGold,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        if (currentUserId != null) {
                            Text(
                                text = currentUserDisplayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            IconButton(
                                onClick = {
                                    signOut(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onSignOutComplete = onExitLocalModeToSignIn,
                                        scope = scope
                                    )
                                },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = stringResource(R.string.cd_sign_out),
                                    tint = ImperialGold
                                )
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {},
                                        onAuthError = { onExitLocalModeToSignIn() },
                                        scope = scope,
                                        onAuthCancelled = { onExitLocalModeToSignIn() }
                                    )
                                },
                                modifier = Modifier.testTag("top_bar_sign_in_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = stringResource(R.string.cd_sign_in),
                                    tint = ImperialGold
                                )
                                Text(
                                    text = "Local Mode • Sign In",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ImperialGold,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RoyalSurface
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(containerColor = RoyalSurface) {
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = { Icon(Icons.Default.Casino, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_play)) },
                            modifier = Modifier.testTag("nav_arena_tab")
                        )
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_tournaments)) },
                            modifier = Modifier.testTag("nav_tournaments_tab")
                        )
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            icon = { Icon(Icons.Default.Leaderboard, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_leaderboards)) },
                            modifier = Modifier.testTag("nav_leaderboards_tab")
                        )
                        NavigationBarItem(
                            selected = currentTab == 3,
                            onClick = { currentTab = 3 },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_gemini_chat)) },
                            modifier = Modifier.testTag("nav_gemini_coach_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(containerColor = RoyalSurface) {
                        NavigationRailItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = { Icon(Icons.Default.Casino, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_play)) },
                            modifier = Modifier.testTag("nav_arena_tab")
                        )
                        NavigationRailItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_tournaments)) },
                            modifier = Modifier.testTag("nav_tournaments_tab")
                        )
                        NavigationRailItem(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            icon = { Icon(Icons.Default.Leaderboard, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_leaderboards)) },
                            modifier = Modifier.testTag("nav_leaderboards_tab")
                        )
                        NavigationRailItem(
                            selected = currentTab == 3,
                            onClick = { currentTab = 3 },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_gemini_chat)) },
                            modifier = Modifier.testTag("nav_gemini_coach_tab")
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentTab) {
                        0 -> ArenaScreen(
                            gameState = gameState,
                            savedGames = savedGames,
                            onQuickSelectMode = viewModel::quickSwitchMode,
                            onStartNewGame = viewModel::initializeAdaptiveDifficultyAndStartGame,
                            onRollDiceForPlayer = viewModel::rollDiceForPlayer,
                            onMoveToken = viewModel::moveToken,
                            onSendTableChat = viewModel::sendLocalTableChat,
                            onSaveGameOffline = viewModel::saveCurrentGameOffline,
                            onLoadGameOffline = viewModel::loadSavedGameOffline,
                            onDeleteSavedGame = viewModel::deleteSavedGameOffline,
                            onDismissToast = viewModel::clearStatusToast
                        )
                        1 -> TournamentsScreen(
                            publicTournamentsState = publicTournamentsState,
                            myTournamentsState = myTournamentsState,
                            onCreateTournament = { title, code, inviteOnly, maxP, safe, quick, bonus ->
                                viewModel.createOnlineTournament(
                                    title = title,
                                    inviteCode = code,
                                    isInviteOnly = inviteOnly,
                                    maxPlayers = maxP,
                                    safeZones = safe,
                                    quickStart = quick,
                                    captureBonus = bonus,
                                    onCreated = { currentTab = 0 }
                                )
                            },
                            onJoinByCode = { code ->
                                viewModel.joinOnlineTournament(code) {
                                    currentTab = 0
                                }
                            },
                            onEnterLobbyArena = { lobby ->
                                viewModel.enterOnlineTournamentMatch(lobby)
                                currentTab = 0
                            }
                        )
                        2 -> LeaderboardsScreen(
                            globalLeaderboardState = globalLeaderboardState,
                            localStats = localStats,
                            cloudMatchHistoryState = cloudMatchHistoryState
                        )
                        3 -> GeminiCoachScreen(
                            messages = aiChatMessages,
                            selectedModelTier = selectedModelTier,
                            selectedPersona = selectedPersona,
                            isLoading = isAiChatLoading,
                            onSelectModelTier = viewModel::selectGeminiModelTier,
                            onSelectPersona = viewModel::selectGeminiPersona,
                            onSendMessage = viewModel::sendGeminiCoachMessage
                        )
                    }
                }
            }
        }
    }
}
