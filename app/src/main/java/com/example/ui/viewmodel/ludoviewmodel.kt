package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalLudoRepository
import com.example.data.local.LocalPlayerStatsEntity
import com.example.data.local.SavedGameEntity
import com.example.data.remote.AiChatMessage
import com.example.data.remote.CloudLudoRepository
import com.example.data.remote.GeminiClient
import com.example.data.remote.GeminiModelTier
import com.example.data.remote.GeminiRolePersona
import com.example.data.remote.LeaderboardEntry
import com.example.data.remote.MatchHistoryRecord
import com.example.data.remote.TournamentLobby
import com.example.domain.GameModeType
import com.example.domain.LocalTableChatMessage
import com.example.domain.LudoBoardGeometry
import com.example.domain.PlayerColor
import com.example.domain.PlayerSeat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.random.Random

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ActiveLudoGameState(
    val gameMode: GameModeType = GameModeType.SOLO_GEMINI,
    val seats: List<PlayerSeat> = listOf(
        PlayerSeat(PlayerColor.RED, "Player 1", isActiveInMatch = true, isAi = false),
        PlayerSeat(PlayerColor.GREEN, "Player 2", isActiveInMatch = false, isAi = true),
        PlayerSeat(PlayerColor.YELLOW, "Gemini AI", isActiveInMatch = true, isAi = true),
        PlayerSeat(PlayerColor.BLUE, "Player 4", isActiveInMatch = false, isAi = false)
    ),
    val tokenPositions: List<Int> = List(16) { -1 },
    val animatingGlobalTokenIndex: Int? = null,
    val animatingIntermediateStep: Int? = null,
    val animatingTrailSteps: List<Int> = emptyList(),
    val isHopImpulse: Boolean = false,
    val currentTurnIndex: Int = 0, // 0..3
    val lastDiceRoll: Int = 0,
    val playerDiceRolls: List<Int> = listOf(6, 1, 1, 1), // Separate dice value per player!
    val isDiceRolling: Boolean = false,
    val hasRolledThisTurn: Boolean = false,
    val consecutiveSixes: Int = 0,
    val legalTokenSlotsForCurrentPlayer: Set<Int> = emptySet(),
    val statusBanner: String = "Tap your Dice Box to roll!",
    val lastAiReasoning: String = "Switch between 1P AI, 2P Local, 3P Local, and 4P Local at the top bar anytime.",
    val aiDifficultyTier: String = "Tactician",
    val playerPerformanceScore: Int = 1000,
    val customRuleSafeZones: Boolean = true,
    val customRuleQuickStart: Boolean = false,
    val customRuleCaptureBonus: Boolean = true,
    val turnsPlayed: Int = 0,
    val winnerSeat: PlayerSeat? = null,
    val localChatMessages: List<LocalTableChatMessage> = listOf(
        LocalTableChatMessage(
            id = "welcome_1",
            senderName = "Ludo.AI",
            playerColorIndex = 0,
            message = "Welcome to Ludo.AI! Tap 1P, 2P Local, 3P Local, or 4P Local at the top to switch modes anytime.",
            isAiMessage = true
        )
    ),
    val activeOnlineTournamentId: String? = null,
    val activeOnlineInviteCode: String? = null,
    val statusToast: String? = null
)

class LudoViewModel(
    private val localRepository: LocalLudoRepository,
    private val cloudRepository: CloudLudoRepository?,
    private val currentUserId: String?,
    private val currentUserDisplayName: String,
    initialMode: GameModeType = GameModeType.SOLO_GEMINI
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _gameState = MutableStateFlow(ActiveLudoGameState(gameMode = initialMode))
    val gameState: StateFlow<ActiveLudoGameState> = _gameState.asStateFlow()

    // Multi-turn Gemini Chatbot State
    private val _aiChatMessages = MutableStateFlow<List<AiChatMessage>>(
        listOf(
            AiChatMessage(
                id = "intro_msg",
                sender = "model",
                text = "Welcome to Ludo.AI, ${currentUserDisplayName.ifBlank { "Champion" }}! Ask me to analyze your current board, calculate capture odds, explain custom tournament rules, or switch my persona above!",
                modelUsed = GeminiModelTier.GENERAL_FLASH.displayName
            )
        )
    )
    val aiChatMessages: StateFlow<List<AiChatMessage>> = _aiChatMessages.asStateFlow()

    private val _selectedModelTier = MutableStateFlow(GeminiModelTier.GENERAL_FLASH)
    val selectedModelTier: StateFlow<GeminiModelTier> = _selectedModelTier.asStateFlow()

    private val _selectedPersona = MutableStateFlow(GeminiRolePersona.GRANDMASTER_COACH)
    val selectedPersona: StateFlow<GeminiRolePersona> = _selectedPersona.asStateFlow()

    private val _isAiChatLoading = MutableStateFlow(false)
    val isAiChatLoading: StateFlow<Boolean> = _isAiChatLoading.asStateFlow()

    // Offline Room Flows
    val savedGames: StateFlow<List<SavedGameEntity>> = localRepository.savedGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val localLeaderboard: StateFlow<List<LocalPlayerStatsEntity>> = localRepository.localLeaderboard
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Cloud Firestore Flows (Only active when authenticated and cloudRepository != null)
    val globalLeaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> =
        if (cloudRepository != null && currentUserId != null) {
            cloudRepository.observeGlobalLeaderboard()
                .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
                .catch { e ->
                    Log.w(TAG, "Error observing global leaderboard", e)
                    emit(UiState.Error(e.message ?: "Unable to load global leaderboard"))
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        } else {
            flowOf(UiState.Error("Sign in with Google to view the Global Cloud Leaderboard."))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        }

    val cloudMatchHistoryState: StateFlow<UiState<List<MatchHistoryRecord>>> =
        if (cloudRepository != null && currentUserId != null) {
            cloudRepository.observeMatchHistory(currentUserId)
                .map<List<MatchHistoryRecord>, UiState<List<MatchHistoryRecord>>> { UiState.Success(it) }
                .catch { e ->
                    Log.w(TAG, "Error observing match history", e)
                    emit(UiState.Error(e.message ?: "Unable to load cloud match history"))
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        } else {
            flowOf(UiState.Error("Sign in with Google to sync Cloud Match History."))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        }

    val publicTournamentsState: StateFlow<UiState<List<TournamentLobby>>> =
        if (cloudRepository != null && currentUserId != null) {
            cloudRepository.observePublicTournaments()
                .map<List<TournamentLobby>, UiState<List<TournamentLobby>>> { UiState.Success(it) }
                .catch { e ->
                    Log.w(TAG, "Error observing public tournaments", e)
                    emit(UiState.Error(e.message ?: "Unable to load public tournaments"))
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        } else {
            flowOf(UiState.Error("Sign in with Google to join Online Tournaments."))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        }

    val myTournamentsState: StateFlow<UiState<List<TournamentLobby>>> =
        if (cloudRepository != null && currentUserId != null) {
            cloudRepository.observeMyTournaments(currentUserId)
                .map<List<TournamentLobby>, UiState<List<TournamentLobby>>> { UiState.Success(it) }
                .catch { e ->
                    Log.w(TAG, "Error observing user tournaments", e)
                    emit(UiState.Error(e.message ?: "Unable to load your tournaments"))
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        } else {
            flowOf(UiState.Error("Sign in with Google to view your Tournament Lobbies."))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
        }

    private var onlineTournamentJob: Job? = null
    private var aiTurnJob: Job? = null

    init {
        initializeAdaptiveDifficultyAndStartGame(
            mode = initialMode,
            customNames = listOf(
                currentUserDisplayName.ifBlank { "You" },
                "Player 2",
                if (initialMode == GameModeType.SOLO_GEMINI) "Gemini AI" else "Player 3",
                "Player 4"
            ),
            safeZones = true,
            quickStart = false,
            captureBonus = true
        )
    }

    fun quickSwitchMode(mode: GameModeType) {
        val state = _gameState.value
        initializeAdaptiveDifficultyAndStartGame(
            mode = mode,
            customNames = listOf(
                state.seats[0].name.ifBlank { "Player 1" },
                "Player 2",
                "Player 3",
                "Player 4"
            ),
            safeZones = state.customRuleSafeZones,
            quickStart = state.customRuleQuickStart,
            captureBonus = state.customRuleCaptureBonus
        )
    }

    fun initializeAdaptiveDifficultyAndStartGame(
        mode: GameModeType,
        customNames: List<String>,
        safeZones: Boolean,
        quickStart: Boolean,
        captureBonus: Boolean
    ) {
        onlineTournamentJob?.cancel()
        aiTurnJob?.cancel()

        viewModelScope.launch {
            val primaryName = customNames.firstOrNull()?.ifBlank { "You" } ?: "You"
            val stats = localRepository.getOrCreatePlayerStats(primaryName)
            val tier = LudoBoardGeometry.computeAiDifficultyTier(stats.aiPerformanceScore)

            val seats = when (mode) {
                GameModeType.SOLO_GEMINI -> listOf(
                    PlayerSeat(PlayerColor.RED, primaryName, isActiveInMatch = true, isAi = false),
                    PlayerSeat(PlayerColor.GREEN, "Green", isActiveInMatch = false, isAi = false),
                    PlayerSeat(PlayerColor.YELLOW, "Gemini ($tier)", isActiveInMatch = true, isAi = true),
                    PlayerSeat(PlayerColor.BLUE, "Blue", isActiveInMatch = false, isAi = false)
                )
                GameModeType.LOCAL_2P -> listOf(
                    PlayerSeat(PlayerColor.RED, customNames.getOrElse(0) { "Player 1" }.ifBlank { "Player 1" }, isActiveInMatch = true, isAi = false),
                    PlayerSeat(PlayerColor.GREEN, "Green", isActiveInMatch = false, isAi = false),
                    PlayerSeat(PlayerColor.YELLOW, customNames.getOrElse(1) { "Player 2" }.ifBlank { "Player 2" }, isActiveInMatch = true, isAi = false),
                    PlayerSeat(PlayerColor.BLUE, "Blue", isActiveInMatch = false, isAi = false)
                )
                GameModeType.LOCAL_3P -> listOf(
                    PlayerSeat(PlayerColor.RED, customNames.getOrElse(0) { "Player 1" }.ifBlank { "Player 1" }, isActiveInMatch = true, isAi = false),
                    PlayerSeat(PlayerColor.GREEN, customNames.getOrElse(1) { "Player 2" }.ifBlank { "Player 2" }, isActiveInMatch = true, isAi = false),
                    PlayerSeat(PlayerColor.YELLOW, customNames.getOrElse(2) { "Player 3" }.ifBlank { "Player 3" }, isActiveInMatch = true, isAi = false),
                    PlayerSeat(PlayerColor.BLUE, "Blue", isActiveInMatch = false, isAi = false)
                )
                GameModeType.LOCAL_4P, GameModeType.ONLINE_TOURNAMENT -> listOf(
                    PlayerSeat(PlayerColor.RED, customNames.getOrElse(0) { "Player 1" }.ifBlank { "Player 1" }, isActiveInMatch = true, isAi = false),
                    PlayerSeat(PlayerColor.GREEN, customNames.getOrElse(1) { "Player 2" }.ifBlank { "Player 2" }, isActiveInMatch = true, isAi = false),
                    PlayerSeat(PlayerColor.YELLOW, customNames.getOrElse(2) { "Player 3" }.ifBlank { "Player 3" }, isActiveInMatch = true, isAi = false),
                    PlayerSeat(PlayerColor.BLUE, customNames.getOrElse(3) { "Player 4" }.ifBlank { "Player 4" }, isActiveInMatch = true, isAi = false)
                )
            }

            val initialTokens = List(16) { idx ->
                val seatIdx = idx / 4
                val slotIdx = idx % 4
                if (seats[seatIdx].isActiveInMatch && quickStart && slotIdx == 0) 0 else -1
            }

            _gameState.value = ActiveLudoGameState(
                gameMode = mode,
                seats = seats,
                tokenPositions = initialTokens,
                currentTurnIndex = 0,
                lastDiceRoll = 0,
                playerDiceRolls = listOf(6, 1, 1, 1),
                hasRolledThisTurn = false,
                consecutiveSixes = 0,
                legalTokenSlotsForCurrentPlayer = emptySet(),
                statusBanner = "${seats[0].name}'s turn — Tap your Dice Box to roll!",
                lastAiReasoning = if (mode == GameModeType.SOLO_GEMINI) {
                    "Adaptive Gemini AI calibrated to $tier (${stats.aiPerformanceScore} Rating)"
                } else {
                    "Local Pass & Play active (${seats.count { it.isActiveInMatch }} Local Players on this device)"
                },
                aiDifficultyTier = tier,
                playerPerformanceScore = stats.aiPerformanceScore,
                customRuleSafeZones = safeZones,
                customRuleQuickStart = quickStart,
                customRuleCaptureBonus = captureBonus,
                turnsPlayed = 0,
                winnerSeat = null,
                localChatMessages = listOf(
                    LocalTableChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderName = "Ludo.AI",
                        playerColorIndex = 0,
                        message = "New ${mode.title} match started! Safe Zones: ${if (safeZones) "ON" else "OFF"}, Quick Start: ${if (quickStart) "ON" else "OFF"}.",
                        isAiMessage = true
                    )
                )
            )
        }
    }

    fun sendLocalTableChat(senderColorIndex: Int, messageText: String) {
        val clean = messageText.trim()
        if (clean.isEmpty()) return
        val state = _gameState.value
        val seat = state.seats.getOrElse(senderColorIndex.coerceIn(0, 3)) { state.seats.first() }
        val newMsg = LocalTableChatMessage(
            id = UUID.randomUUID().toString(),
            senderName = seat.name,
            playerColorIndex = seat.color.index,
            message = clean,
            isAiMessage = false
        )
        _gameState.update {
            it.copy(localChatMessages = (it.localChatMessages + newMsg).takeLast(50))
        }
    }

    fun rollDiceForPlayer(playerIndex: Int) {
        if (playerIndex != _gameState.value.currentTurnIndex) return
        rollDice()
    }

    fun rollDice() {
        val current = _gameState.value
        if (current.winnerSeat != null || current.isDiceRolling || current.hasRolledThisTurn || current.animatingGlobalTokenIndex != null) {
            return
        }

        viewModelScope.launch {
            _gameState.update { it.copy(isDiceRolling = true, statusToast = null) }
            delay(280L)
            val rolled = Random.nextInt(1, 7)
            val newSixCount = if (rolled == 6) current.consecutiveSixes + 1 else 0
            val updatedPlayerRolls = current.playerDiceRolls.toMutableList().apply {
                this[current.currentTurnIndex] = rolled
            }

            if (newSixCount >= 3) {
                val activeSeat = current.seats[current.currentTurnIndex]
                _gameState.update {
                    it.copy(
                        isDiceRolling = false,
                        lastDiceRoll = rolled,
                        playerDiceRolls = updatedPlayerRolls,
                        consecutiveSixes = 0,
                        hasRolledThisTurn = false,
                        statusBanner = "${activeSeat.name} rolled three 6s in a row! Turn forfeited."
                    )
                }
                delay(650L)
                advanceToNextTurn(grantBonusRoll = false)
                return@launch
            }

            val activeIndices = current.seats.filter { it.isActiveInMatch }.map { it.color.index }.toSet()
            val evaluations = LudoBoardGeometry.evaluateMovesForPlayer(
                playerIndex = current.currentTurnIndex,
                diceRoll = rolled,
                allTokenPositions = current.tokenPositions,
                activePlayerIndices = activeIndices,
                safeZonesEnabled = current.customRuleSafeZones,
                aiDifficultyTier = current.aiDifficultyTier
            )
            val legalSlots = evaluations.map { it.tokenIndexWithinPlayer }.toSet()
            val activeSeat = current.seats[current.currentTurnIndex]

            if (legalSlots.isEmpty()) {
                _gameState.update {
                    it.copy(
                        isDiceRolling = false,
                        lastDiceRoll = rolled,
                        playerDiceRolls = updatedPlayerRolls,
                        hasRolledThisTurn = true,
                        legalTokenSlotsForCurrentPlayer = emptySet(),
                        statusBanner = "${activeSeat.name} rolled a $rolled — No legal moves available."
                    )
                }
                delay(700L)
                advanceToNextTurn(grantBonusRoll = false)
                return@launch
            }

            _gameState.update {
                it.copy(
                    isDiceRolling = false,
                    lastDiceRoll = rolled,
                    playerDiceRolls = updatedPlayerRolls,
                    hasRolledThisTurn = true,
                    consecutiveSixes = newSixCount,
                    legalTokenSlotsForCurrentPlayer = legalSlots,
                    statusBanner = if (activeSeat.isAi) {
                        "${activeSeat.name} rolled a $rolled and is choosing a move..."
                    } else {
                        "${activeSeat.name} rolled a $rolled! Tap a highlighted pawn to move."
                    }
                )
            }

            if (activeSeat.isAi) {
                triggerAiMoveSelection(rolled, evaluations)
            } else if (legalSlots.size == 1) {
                delay(320L)
                moveToken(current.currentTurnIndex, legalSlots.first())
            }
        }
    }

    private fun triggerAiMoveSelection(
        diceRoll: Int,
        evaluations: List<com.example.domain.MoveEvaluation>
    ) {
        aiTurnJob?.cancel()
        aiTurnJob = viewModelScope.launch {
            delay(420L)
            val state = _gameState.value
            val bestHeuristic = evaluations.first()
            val boardSummary = evaluations.joinToString("\n") { it.summary }

            val aiDecision = GeminiClient.decideAdaptiveAiMove(
                aiDifficultyTier = state.aiDifficultyTier,
                playerPerformanceScore = state.playerPerformanceScore,
                diceRoll = diceRoll,
                validTokenIndices = evaluations.map { it.tokenIndexWithinPlayer },
                boardSummary = boardSummary,
                heuristicFallbackIndex = bestHeuristic.tokenIndexWithinPlayer,
                heuristicReason = bestHeuristic.summary
            )

            val aiSeat = state.seats[state.currentTurnIndex]
            val aiChat = LocalTableChatMessage(
                id = UUID.randomUUID().toString(),
                senderName = aiSeat.name,
                playerColorIndex = aiSeat.color.index,
                message = aiDecision.tableTalk,
                isAiMessage = true
            )

            _gameState.update {
                it.copy(
                    lastAiReasoning = aiDecision.reasoning,
                    localChatMessages = (it.localChatMessages + aiChat).takeLast(50)
                )
            }

            moveToken(state.currentTurnIndex, aiDecision.chosenTokenIndex)
        }
    }

    fun moveToken(playerIndex: Int, tokenSlot: Int) {
        val current = _gameState.value
        if (current.winnerSeat != null || current.animatingGlobalTokenIndex != null) return
        if (playerIndex != current.currentTurnIndex || !current.hasRolledThisTurn) return
        if (tokenSlot !in current.legalTokenSlotsForCurrentPlayer) return

        val globalTokenIdx = playerIndex * 4 + tokenSlot
        val startStep = current.tokenPositions[globalTokenIdx]
        val dice = current.lastDiceRoll
        val targetStep = if (startStep == -1) 0 else (startStep + dice).coerceAtMost(56)

        viewModelScope.launch {
            val trailHistory = mutableListOf<Int>()
            _gameState.update {
                it.copy(
                    legalTokenSlotsForCurrentPlayer = emptySet(),
                    animatingGlobalTokenIndex = globalTokenIdx,
                    animatingIntermediateStep = startStep,
                    animatingTrailSteps = emptyList(),
                    isHopImpulse = false
                )
            }

            if (startStep == -1) {
                _gameState.update { it.copy(isHopImpulse = true) }
                delay(110L)
                _gameState.update {
                    it.copy(
                        animatingIntermediateStep = 0,
                        isHopImpulse = false
                    )
                }
                delay(110L)
            } else {
                for (step in (startStep + 1)..targetStep) {
                    trailHistory.add(step - 1)
                    _gameState.update {
                        it.copy(
                            animatingIntermediateStep = step,
                            animatingTrailSteps = trailHistory.takeLast(5),
                            isHopImpulse = true
                        )
                    }
                    delay(75L)
                    _gameState.update { it.copy(isHopImpulse = false) }
                    delay(55L)
                }
            }

            val updatedPositions = current.tokenPositions.toMutableList()
            updatedPositions[globalTokenIdx] = targetStep

            val moverColor = PlayerColor.fromIndex(playerIndex)
            val landedGlobalTrackIdx = LudoBoardGeometry.toGlobalTrackIndex(moverColor, targetStep)
            val isSafeSquare = current.customRuleSafeZones &&
                landedGlobalTrackIdx != null &&
                landedGlobalTrackIdx in LudoBoardGeometry.SAFE_GLOBAL_INDICES

            var capturedCount = 0
            if (landedGlobalTrackIdx != null && !isSafeSquare) {
                for (otherPlayerIdx in 0 until 4) {
                    if (otherPlayerIdx == playerIndex || !current.seats[otherPlayerIdx].isActiveInMatch) continue
                    val otherColor = PlayerColor.fromIndex(otherPlayerIdx)
                    for (otherSlot in 0 until 4) {
                        val otherGlobalTokenIdx = otherPlayerIdx * 4 + otherSlot
                        val otherPos = updatedPositions[otherGlobalTokenIdx]
                        val otherGlobalTrackIdx = LudoBoardGeometry.toGlobalTrackIndex(otherColor, otherPos)
                        if (otherGlobalTrackIdx != null && otherGlobalTrackIdx == landedGlobalTrackIdx) {
                            updatedPositions[otherGlobalTokenIdx] = -1
                            capturedCount++
                        }
                    }
                }
            }

            val updatedSeats = current.seats.mapIndexed { idx, seat ->
                if (idx == playerIndex && capturedCount > 0) {
                    seat.copy(capturesMade = seat.capturesMade + capturedCount)
                } else {
                    seat
                }
            }

            val activeSeat = updatedSeats[playerIndex]
            val allFourHome = (0 until 4).all { slot ->
                updatedPositions[playerIndex * 4 + slot] >= 56
            }

            val actionSummary = buildString {
                append("${activeSeat.name} moved Pawn #${tokenSlot + 1}")
                if (capturedCount > 0) append(" & CAPTURED $capturedCount opponent piece(s)!")
                else if (targetStep == 56) append(" into HOME!")
                else append(" ($dice steps).")
            }

            _gameState.update {
                it.copy(
                    seats = updatedSeats,
                    tokenPositions = updatedPositions,
                    animatingGlobalTokenIndex = null,
                    animatingIntermediateStep = null,
                    animatingTrailSteps = emptyList(),
                    isHopImpulse = false,
                    turnsPlayed = it.turnsPlayed + 1,
                    winnerSeat = if (allFourHome) activeSeat else null,
                    statusBanner = if (allFourHome) {
                        "🏆 ${activeSeat.name} WINS THE MATCH!"
                    } else {
                        actionSummary
                    }
                )
            }

            val activeTourneyId = _gameState.value.activeOnlineTournamentId
            if (activeTourneyId != null && cloudRepository != null) {
                val nextTurnIdx = if (allFourHome) {
                    playerIndex
                } else {
                    computeNextTurnIndex(
                        currentIndex = playerIndex,
                        seats = updatedSeats,
                        grantBonus = (dice == 6) || (capturedCount > 0 && current.customRuleCaptureBonus) || (targetStep == 56)
                    )
                }
                cloudRepository.syncTournamentState(
                    tournamentId = activeTourneyId,
                    status = if (allFourHome) "completed" else "in_progress",
                    currentTurnIndex = nextTurnIdx,
                    lastDiceRoll = dice,
                    tokenPositions = updatedPositions,
                    lastActionText = actionSummary,
                    winnerName = if (allFourHome) activeSeat.name else ""
                )
            }

            if (allFourHome) {
                recordCompletedMatchStats(winnerSeat = activeSeat)
            } else {
                val bonusRoll = (dice == 6) ||
                    (capturedCount > 0 && current.customRuleCaptureBonus) ||
                    (targetStep == 56)
                advanceToNextTurn(grantBonusRoll = bonusRoll)
            }
        }
    }

    private fun computeNextTurnIndex(
        currentIndex: Int,
        seats: List<PlayerSeat>,
        grantBonus: Boolean
    ): Int {
        if (grantBonus) return currentIndex
        for (offset in 1..4) {
            val candidate = (currentIndex + offset) % 4
            if (seats[candidate].isActiveInMatch) return candidate
        }
        return currentIndex
    }

    private fun advanceToNextTurn(grantBonusRoll: Boolean) {
        val current = _gameState.value
        if (current.winnerSeat != null) return

        val nextIndex = computeNextTurnIndex(
            currentIndex = current.currentTurnIndex,
            seats = current.seats,
            grantBonus = grantBonusRoll
        )
        val nextSeat = current.seats[nextIndex]

        _gameState.update {
            it.copy(
                currentTurnIndex = nextIndex,
                hasRolledThisTurn = false,
                consecutiveSixes = if (grantBonusRoll) it.consecutiveSixes else 0,
                legalTokenSlotsForCurrentPlayer = emptySet(),
                statusBanner = if (grantBonusRoll) {
                    "Bonus Roll for ${nextSeat.name}!"
                } else {
                    "${nextSeat.name}'s turn — Tap your Dice Box to roll!"
                }
            )
        }

        if (nextSeat.isAi) {
            viewModelScope.launch {
                delay(520L)
                rollDice()
            }
        }
    }

    private fun recordCompletedMatchStats(winnerSeat: PlayerSeat) {
        val state = _gameState.value
        val humanSeat = state.seats.first()
        val playerWon = winnerSeat.color == humanSeat.color
        val tokensHome = (0 until 4).count { slot -> state.tokenPositions[slot] >= 56 }

        viewModelScope.launch {
            val updatedLocal = localRepository.recordMatchOutcome(
                playerName = humanSeat.name,
                won = playerWon,
                captures = humanSeat.capturesMade,
                tokensHome = tokensHome
            )
            val newTier = LudoBoardGeometry.computeAiDifficultyTier(updatedLocal.aiPerformanceScore)
            _gameState.update {
                it.copy(
                    aiDifficultyTier = newTier,
                    playerPerformanceScore = updatedLocal.aiPerformanceScore,
                    lastAiReasoning = "Match recorded! Your performance rating is now ${updatedLocal.aiPerformanceScore} (AI Tier: $newTier)."
                )
            }

            if (cloudRepository != null && currentUserId != null) {
                cloudRepository.recordMatchHistory(
                    gameMode = state.gameMode.id,
                    winnerName = winnerSeat.name,
                    playerWon = playerWon,
                    turnsPlayed = state.turnsPlayed.coerceAtLeast(1),
                    capturesMade = humanSeat.capturesMade,
                    aiDifficulty = newTier
                )
                cloudRepository.syncLeaderboardStats(
                    displayName = humanSeat.name,
                    wins = updatedLocal.wins,
                    gamesPlayed = updatedLocal.gamesPlayed,
                    captures = updatedLocal.totalCaptures,
                    rating = updatedLocal.aiPerformanceScore,
                    aiTier = newTier
                )
            }
        }
    }

    // --- Offline Save & Load Game Progress (Room Database) ---

    fun saveCurrentGameOffline(customTitle: String = "") {
        val state = _gameState.value
        viewModelScope.launch {
            val saveTitle = customTitle.ifBlank {
                "${state.gameMode.title} • Turn ${state.turnsPlayed}"
            }
            val entity = SavedGameEntity(
                title = saveTitle,
                gameMode = state.gameMode.id,
                playerCount = state.seats.count { it.isActiveInMatch },
                playerNamesCsv = state.seats.joinToString("|") { it.name.replace("|", " ") },
                isAiFlagsCsv = state.seats.joinToString(",") { it.isAi.toString() },
                tokenPositionsCsv = state.tokenPositions.joinToString(","),
                currentTurnIndex = state.currentTurnIndex,
                lastDiceRoll = state.lastDiceRoll,
                hasRolledThisTurn = state.hasRolledThisTurn,
                consecutiveSixes = state.consecutiveSixes,
                turnsPlayed = state.turnsPlayed,
                capturesByPlayerCsv = state.seats.joinToString(",") { it.capturesMade.toString() },
                customRuleSafeZones = state.customRuleSafeZones,
                customRuleQuickStart = state.customRuleQuickStart,
                customRuleCaptureBonus = state.customRuleCaptureBonus,
                aiDifficultyTier = state.aiDifficultyTier,
                chatLogJson = json.encodeToString(state.localChatMessages)
            )
            localRepository.saveGameProgress(entity)
            _gameState.update {
                it.copy(statusToast = "Saved offline: \"$saveTitle\"")
            }
        }
    }

    fun loadSavedGameOffline(saved: SavedGameEntity) {
        onlineTournamentJob?.cancel()
        aiTurnJob?.cancel()

        val mode = GameModeType.entries.find { it.id == saved.gameMode } ?: GameModeType.SOLO_GEMINI
        val names = saved.playerNamesCsv.split("|")
        val aiFlags = saved.isAiFlagsCsv.split(",").map { it.toBooleanStrictOrNull() ?: false }
        val captures = saved.capturesByPlayerCsv.split(",").map { it.toIntOrNull() ?: 0 }
        val positions = saved.tokenPositionsCsv.split(",").mapNotNull { it.toIntOrNull() }
            .let { if (it.size == 16) it else List(16) { -1 } }

        val activeMask = when (mode) {
            GameModeType.SOLO_GEMINI, GameModeType.LOCAL_2P -> listOf(true, false, true, false)
            GameModeType.LOCAL_3P -> listOf(true, true, true, false)
            else -> listOf(true, true, true, true)
        }

        val seats = PlayerColor.entries.mapIndexed { idx, color ->
            PlayerSeat(
                color = color,
                name = names.getOrElse(idx) { "Player ${idx + 1}" },
                isActiveInMatch = activeMask[idx],
                isAi = aiFlags.getOrElse(idx) { false },
                capturesMade = captures.getOrElse(idx) { 0 }
            )
        }

        val chatHistory = try {
            json.decodeFromString<List<LocalTableChatMessage>>(saved.chatLogJson)
        } catch (_: Exception) {
            emptyList()
        }

        val restoredPlayerDice = MutableList(4) { 1 }.apply {
            this[saved.currentTurnIndex.coerceIn(0, 3)] = saved.lastDiceRoll.coerceIn(1, 6)
        }

        _gameState.value = ActiveLudoGameState(
            gameMode = mode,
            seats = seats,
            tokenPositions = positions,
            currentTurnIndex = saved.currentTurnIndex.coerceIn(0, 3),
            lastDiceRoll = saved.lastDiceRoll,
            playerDiceRolls = restoredPlayerDice,
            hasRolledThisTurn = false,
            consecutiveSixes = saved.consecutiveSixes,
            statusBanner = "Resumed saved game: ${seats[saved.currentTurnIndex.coerceIn(0, 3)].name}'s turn!",
            aiDifficultyTier = saved.aiDifficultyTier,
            customRuleSafeZones = saved.customRuleSafeZones,
            customRuleQuickStart = saved.customRuleQuickStart,
            customRuleCaptureBonus = saved.customRuleCaptureBonus,
            turnsPlayed = saved.turnsPlayed,
            localChatMessages = chatHistory,
            statusToast = "Loaded saved game: ${saved.title}"
        )
    }

    fun deleteSavedGameOffline(id: Int) {
        viewModelScope.launch {
            localRepository.deleteSavedGame(id)
        }
    }

    fun clearStatusToast() {
        _gameState.update { it.copy(statusToast = null) }
    }

    // --- Real-Time Online Tournaments & Invite-Only Lobbies ---

    fun createOnlineTournament(
        title: String,
        inviteCode: String,
        isInviteOnly: Boolean,
        maxPlayers: Int,
        safeZones: Boolean,
        quickStart: Boolean,
        captureBonus: Boolean,
        onCreated: (TournamentLobby) -> Unit
    ) {
        val repo = cloudRepository ?: run {
            _gameState.update { it.copy(statusToast = "Please sign in with Google first to host online tournaments.") }
            return
        }
        viewModelScope.launch {
            val hostName = currentUserDisplayName.ifBlank { "Host" }
            val result = repo.createTournamentLobby(
                hostName = hostName,
                title = title,
                inviteCode = inviteCode,
                isInviteOnly = isInviteOnly,
                maxPlayers = maxPlayers,
                customRuleSafeZones = safeZones,
                customRuleQuickStart = quickStart,
                customRuleCaptureBonus = captureBonus
            )
            result.onSuccess { lobby ->
                enterOnlineTournamentMatch(lobby)
                onCreated(lobby)
            }.onFailure { err ->
                _gameState.update {
                    it.copy(statusToast = err.localizedMessage ?: "Failed to create tournament")
                }
            }
        }
    }

    fun joinOnlineTournament(codeOrId: String, onJoined: () -> Unit) {
        val repo = cloudRepository ?: run {
            _gameState.update { it.copy(statusToast = "Please sign in with Google first to join online tournaments.") }
            return
        }
        viewModelScope.launch {
            val playerName = currentUserDisplayName.ifBlank { "Challenger" }
            val result = repo.joinTournamentByCodeOrId(codeOrId, playerName)
            result.onSuccess { lobby ->
                enterOnlineTournamentMatch(lobby)
                onJoined()
            }.onFailure { err ->
                _gameState.update {
                    it.copy(statusToast = err.localizedMessage ?: "Could not join lobby")
                }
            }
        }
    }

    fun enterOnlineTournamentMatch(lobby: TournamentLobby) {
        val repo = cloudRepository ?: return
        onlineTournamentJob?.cancel()
        onlineTournamentJob = viewModelScope.launch {
            repo.observeTournamentById(lobby.id)
                .catch { e ->
                    Log.w(TAG, "Error observing online tournament", e)
                }
                .collect { liveLobby ->
                    if (liveLobby == null) return@collect
                    val seats = PlayerColor.entries.mapIndexed { idx, color ->
                        val pName = liveLobby.playerNames.getOrElse(idx) { "Seat ${idx + 1}" }
                        val isActive = idx < liveLobby.maxPlayers
                        PlayerSeat(
                            color = color,
                            name = pName,
                            isActiveInMatch = isActive,
                            isAi = false
                        )
                    }
                    val winner = if (liveLobby.status == "completed" && liveLobby.winnerName.isNotBlank()) {
                        seats.find { it.name == liveLobby.winnerName } ?: seats.first()
                    } else {
                        null
                    }
                    _gameState.update { prev ->
                        val updatedRolls = prev.playerDiceRolls.toMutableList().apply {
                            val turnIdx = liveLobby.currentTurnIndex.coerceIn(0, 3)
                            if (liveLobby.lastDiceRoll in 1..6) {
                                this[turnIdx] = liveLobby.lastDiceRoll
                            }
                        }
                        prev.copy(
                            gameMode = GameModeType.ONLINE_TOURNAMENT,
                            seats = seats,
                            tokenPositions = liveLobby.tokenPositions,
                            currentTurnIndex = liveLobby.currentTurnIndex.coerceIn(0, 3),
                            lastDiceRoll = liveLobby.lastDiceRoll,
                            playerDiceRolls = updatedRolls,
                            customRuleSafeZones = liveLobby.customRuleSafeZones,
                            customRuleQuickStart = liveLobby.customRuleQuickStart,
                            customRuleCaptureBonus = liveLobby.customRuleCaptureBonus,
                            statusBanner = liveLobby.lastActionText,
                            winnerSeat = winner,
                            activeOnlineTournamentId = liveLobby.id,
                            activeOnlineInviteCode = liveLobby.inviteCode
                        )
                    }
                }
        }
    }

    // --- Multi-Turn Gemini Chatbot ---

    fun selectGeminiModelTier(tier: GeminiModelTier) {
        _selectedModelTier.value = tier
    }

    fun selectGeminiPersona(persona: GeminiRolePersona) {
        _selectedPersona.value = persona
    }

    fun sendGeminiCoachMessage(userPrompt: String) {
        val clean = userPrompt.trim()
        if (clean.isEmpty() || _isAiChatLoading.value) return

        val userMsg = AiChatMessage(
            id = UUID.randomUUID().toString(),
            sender = "user",
            text = clean,
            modelUsed = _selectedModelTier.value.displayName
        )
        val currentHistory = _aiChatMessages.value
        _aiChatMessages.value = currentHistory + userMsg
        _isAiChatLoading.value = true

        viewModelScope.launch {
            val state = _gameState.value
            val boardContext = buildString {
                append("Mode=${state.gameMode.title}, ")
                append("Turn=${state.seats[state.currentTurnIndex].name}, ")
                append("LastDice=${state.lastDiceRoll}, ")
                append("AI Difficulty=${state.aiDifficultyTier} (${state.playerPerformanceScore} Elo), ")
                append("TokenPositions=${state.tokenPositions.joinToString(",")}")
            }
            val replyText = GeminiClient.sendMultiTurnChat(
                history = currentHistory,
                newUserMessage = clean,
                boardContextSummary = boardContext,
                modelTier = _selectedModelTier.value,
                persona = _selectedPersona.value
            )
            val botMsg = AiChatMessage(
                id = UUID.randomUUID().toString(),
                sender = "model",
                text = replyText,
                modelUsed = _selectedModelTier.value.displayName
            )
            _aiChatMessages.update { it + botMsg }
            _isAiChatLoading.value = false
        }
    }

    private companion object {
        const val TAG = "LudoViewModel"
    }
}
