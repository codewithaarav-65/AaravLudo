package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.SavedGameEntity
import com.example.domain.GameModeType
import com.example.domain.PlayerSeat
import com.example.ui.components.LudoBoardCanvas
import com.example.ui.components.drawTeardropLudoPawn
import com.example.ui.components.toComposeColor
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.LudoGreen
import com.example.ui.theme.RoyalCard
import com.example.ui.theme.RoyalCardElevated
import com.example.ui.theme.RoyalObsidian
import com.example.ui.theme.RoyalSurface
import com.example.ui.viewmodel.ActiveLudoGameState
import kotlin.math.roundToInt

@Composable
fun ArenaScreen(
    gameState: ActiveLudoGameState,
    savedGames: List<SavedGameEntity>,
    onQuickSelectMode: (GameModeType) -> Unit = {},
    onStartNewGame: (
        mode: GameModeType,
        customNames: List<String>,
        safeZones: Boolean,
        quickStart: Boolean,
        captureBonus: Boolean
    ) -> Unit,
    onRollDiceForPlayer: (playerIndex: Int) -> Unit,
    onMoveToken: (playerIndex: Int, tokenSlot: Int) -> Unit,
    onSendTableChat: (senderColorIndex: Int, message: String) -> Unit,
    onSaveGameOffline: (title: String) -> Unit,
    onLoadGameOffline: (SavedGameEntity) -> Unit,
    onDeleteSavedGame: (Int) -> Unit,
    onDismissToast: () -> Unit
) {
    var showModeSetupDialog by remember { mutableStateOf(false) }
    var showSavedGamesDialog by remember { mutableStateOf(false) }
    var showTableChatPanel by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A2458), Color(0xFF071636), RoyalObsidian)
                )
            )
    ) {
        val isWideScreen = maxWidth >= 700.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Top Player Dice Docks (Green Top-Left = index 1, Yellow Top-Right = index 2)
                    DualPlayerDiceRow(
                        leftPlayerIndex = 1,
                        rightPlayerIndex = 2,
                        gameState = gameState,
                        onRollDiceForPlayer = onRollDiceForPlayer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp)
                            .padding(bottom = 8.dp)
                    )

                    LudoBoardCanvas(
                        seats = gameState.seats,
                        tokenPositions = gameState.tokenPositions,
                        animatingGlobalTokenIndex = gameState.animatingGlobalTokenIndex,
                        animatingIntermediateStep = gameState.animatingIntermediateStep,
                        animatingTrailSteps = gameState.animatingTrailSteps,
                        isHopImpulse = gameState.isHopImpulse,
                        currentTurnIndex = gameState.currentTurnIndex,
                        legalTokenSlotsForCurrentPlayer = gameState.legalTokenSlotsForCurrentPlayer,
                        safeZonesEnabled = gameState.customRuleSafeZones,
                        onTokenClicked = onMoveToken,
                        modifier = Modifier.widthIn(max = 480.dp)
                    )

                    // Bottom Player Dice Docks (Red Bottom-Left = index 0, Blue Bottom-Right = index 3)
                    DualPlayerDiceRow(
                        leftPlayerIndex = 0,
                        rightPlayerIndex = 3,
                        gameState = gameState,
                        onRollDiceForPlayer = onRollDiceForPlayer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp)
                            .padding(top = 8.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArenaHeaderControls(
                        gameState = gameState,
                        savedGamesCount = savedGames.size,
                        onQuickSelectMode = onQuickSelectMode,
                        onOpenModeSetup = { showModeSetupDialog = true },
                        onSaveCurrent = { onSaveGameOffline("") },
                        onOpenSavedGames = { showSavedGamesDialog = true },
                        onToggleChat = { showTableChatPanel = !showTableChatPanel }
                    )
                    TurnStatusBannerCard(gameState = gameState)
                    AnimatedVisibility(visible = showTableChatPanel) {
                        LocalTableChatCard(
                            gameState = gameState,
                            onSendTableChat = onSendTableChat
                        )
                    }
                }
            }
        } else {
            // Portrait Mobile Handheld Layout matching the video!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArenaHeaderControls(
                    gameState = gameState,
                    savedGamesCount = savedGames.size,
                    onQuickSelectMode = onQuickSelectMode,
                    onOpenModeSetup = { showModeSetupDialog = true },
                    onSaveCurrent = { onSaveGameOffline("") },
                    onOpenSavedGames = { showSavedGamesDialog = true },
                    onToggleChat = { showTableChatPanel = !showTableChatPanel }
                )

                // Top Row: Separate Dice Docks for Green (Top-Left, index 1) and Yellow (Top-Right, index 2)
                DualPlayerDiceRow(
                    leftPlayerIndex = 1,
                    rightPlayerIndex = 2,
                    gameState = gameState,
                    onRollDiceForPlayer = onRollDiceForPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                )

                // Interactive Animated 15x15 Ludo Board
                LudoBoardCanvas(
                    seats = gameState.seats,
                    tokenPositions = gameState.tokenPositions,
                    animatingGlobalTokenIndex = gameState.animatingGlobalTokenIndex,
                    animatingIntermediateStep = gameState.animatingIntermediateStep,
                    animatingTrailSteps = gameState.animatingTrailSteps,
                    isHopImpulse = gameState.isHopImpulse,
                    currentTurnIndex = gameState.currentTurnIndex,
                    legalTokenSlotsForCurrentPlayer = gameState.legalTokenSlotsForCurrentPlayer,
                    safeZonesEnabled = gameState.customRuleSafeZones,
                    onTokenClicked = onMoveToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                )

                // Bottom Row: Separate Dice Docks for Red ("You", Bottom-Left, index 0) and Blue (Bottom-Right, index 3)
                DualPlayerDiceRow(
                    leftPlayerIndex = 0,
                    rightPlayerIndex = 3,
                    gameState = gameState,
                    onRollDiceForPlayer = onRollDiceForPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                )

                TurnStatusBannerCard(gameState = gameState)

                AnimatedVisibility(visible = showTableChatPanel) {
                    LocalTableChatCard(
                        gameState = gameState,
                        onSendTableChat = onSendTableChat
                    )
                }
            }
        }
    }

    if (showModeSetupDialog) {
        GameModeSetupDialog(
            currentState = gameState,
            onDismiss = { showModeSetupDialog = false },
            onConfirmStart = { mode, names, safeZones, quickStart, captureBonus ->
                showModeSetupDialog = false
                onStartNewGame(mode, names, safeZones, quickStart, captureBonus)
            }
        )
    }

    if (showSavedGamesDialog) {
        SavedGamesOfflineDialog(
            savedGames = savedGames,
            onDismiss = { showSavedGamesDialog = false },
            onLoadGame = { saved ->
                showSavedGamesDialog = false
                onLoadGameOffline(saved)
            },
            onDeleteGame = onDeleteSavedGame
        )
    }

    if (gameState.statusToast != null) {
        AlertDialog(
            onDismissRequest = onDismissToast,
            confirmButton = {
                TextButton(onClick = onDismissToast) {
                    Text("OK", color = ImperialGold)
                }
            },
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(gameState.statusToast) },
            containerColor = RoyalCard
        )
    }
}

@Composable
private fun DualPlayerDiceRow(
    leftPlayerIndex: Int,
    rightPlayerIndex: Int,
    gameState: ActiveLudoGameState,
    onRollDiceForPlayer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerSeparateDiceDock(
            playerIndex = leftPlayerIndex,
            seat = gameState.seats[leftPlayerIndex],
            diceValue = gameState.playerDiceRolls.getOrElse(leftPlayerIndex) { 6 },
            isCurrentTurn = gameState.currentTurnIndex == leftPlayerIndex,
            hasRolledThisTurn = gameState.hasRolledThisTurn,
            isDiceRolling = gameState.isDiceRolling && gameState.currentTurnIndex == leftPlayerIndex,
            isAnimatingMove = gameState.animatingGlobalTokenIndex != null,
            hasWinner = gameState.winnerSeat != null,
            alignRight = false,
            onRollClicked = { onRollDiceForPlayer(leftPlayerIndex) }
        )

        PlayerSeparateDiceDock(
            playerIndex = rightPlayerIndex,
            seat = gameState.seats[rightPlayerIndex],
            diceValue = gameState.playerDiceRolls.getOrElse(rightPlayerIndex) { 1 },
            isCurrentTurn = gameState.currentTurnIndex == rightPlayerIndex,
            hasRolledThisTurn = gameState.hasRolledThisTurn,
            isDiceRolling = gameState.isDiceRolling && gameState.currentTurnIndex == rightPlayerIndex,
            isAnimatingMove = gameState.animatingGlobalTokenIndex != null,
            hasWinner = gameState.winnerSeat != null,
            alignRight = true,
            onRollClicked = { onRollDiceForPlayer(rightPlayerIndex) }
        )
    }
}

@Composable
private fun PlayerSeparateDiceDock(
    playerIndex: Int,
    seat: PlayerSeat,
    diceValue: Int,
    isCurrentTurn: Boolean,
    hasRolledThisTurn: Boolean,
    isDiceRolling: Boolean,
    isAnimatingMove: Boolean,
    hasWinner: Boolean,
    alignRight: Boolean,
    onRollClicked: () -> Unit
) {
    val canTapThisDice = seat.isActiveInMatch &&
        isCurrentTurn &&
        !hasRolledThisTurn &&
        !isDiceRolling &&
        !isAnimatingMove &&
        !hasWinner &&
        !seat.isAi

    val diceRotation by animateFloatAsState(
        targetValue = if (isDiceRolling) 360f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "diceSpin_$playerIndex"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "arrowBounce_$playerIndex")
    val arrowBounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(420),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounceOffset_$playerIndex"
    )

    val dockAlpha = if (seat.isActiveInMatch) 1f else 0.28f
    val borderColor = when {
        isCurrentTurn && seat.isActiveInMatch -> Color(0xFFFFD700)
        else -> Color(0xFF64748B)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.alpha(dockAlpha)
    ) {
        // If aligned on the right side of the screen, show bouncing golden arrow to the left of the dock
        if (alignRight && isCurrentTurn && seat.isActiveInMatch) {
            GoldenTurnArrow(
                pointLeft = false,
                modifier = Modifier
                    .size(28.dp)
                    .offset { IntOffset((-arrowBounceOffset).roundToInt(), 0) }
            )
        }

        Column(
            horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Player Name & Seat Tag above their Separate Dice Box
            Surface(
                color = if (isCurrentTurn && seat.isActiveInMatch) {
                    seat.color.toComposeColor()
                } else {
                    Color(0xFF1E293B)
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (seat.isActiveInMatch) seat.name else "${seat.color.label} (Off)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentTurn && seat.isActiveInMatch && seat.color.index == 2) {
                        Color(0xFF1A1200)
                    } else {
                        Color.White
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // The Player's Dedicated Dice Dock (matching the video's blue/gold metallic box with Pawn + 3D Pipped Die!)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = if (isCurrentTurn && seat.isActiveInMatch) {
                                listOf(Color(0xFF1D4ED8), Color(0xFF0F296B))
                            } else {
                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                            }
                        )
                    )
                    .border(
                        width = if (isCurrentTurn && seat.isActiveInMatch) 2.6.dp else 1.4.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(enabled = canTapThisDice) { onRollClicked() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics {
                        contentDescription = "${seat.name} Dice Box"
                    }
                    .let { mod ->
                        if (isCurrentTurn) mod.testTag("roll_dice_button")
                        else mod.testTag("player_dice_dock_$playerIndex")
                    }
            ) {
                if (!alignRight) {
                    // Mini Teardrop Pawn Icon on left
                    Box(modifier = Modifier.size(34.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawTeardropLudoPawn(
                                houseColor = seat.color.toComposeColor(),
                                showDashedBaseRing = false,
                                isHighlightedLegal = isCurrentTurn,
                                ringRotationDegrees = 0f
                            )
                        }
                    }
                }

                // Player's Separate 3D Pipped Dice Cube
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .rotate(diceRotation)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = 1.8.dp,
                            color = if (canTapThisDice) Color(0xFFEF4444) else Color(0xFF94A3B8),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .testTag("player_dice_cube_$playerIndex"),
                    contentAlignment = Alignment.Center
                ) {
                    DicePipFaceCanvas(
                        diceValue = diceValue.coerceIn(1, 6),
                        isActiveTurn = isCurrentTurn && seat.isActiveInMatch
                    )
                }

                if (alignRight) {
                    // Mini Teardrop Pawn Icon on right
                    Box(modifier = Modifier.size(34.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawTeardropLudoPawn(
                                houseColor = seat.color.toComposeColor(),
                                showDashedBaseRing = false,
                                isHighlightedLegal = isCurrentTurn,
                                ringRotationDegrees = 0f
                            )
                        }
                    }
                }
            }
        }

        // If aligned on the left side of the screen (like "You" in the video), show bouncing golden arrow to the right of the dock!
        if (!alignRight && isCurrentTurn && seat.isActiveInMatch) {
            GoldenTurnArrow(
                pointLeft = true,
                modifier = Modifier
                    .size(28.dp)
                    .offset { IntOffset(arrowBounceOffset.roundToInt(), 0) }
            )
        }
    }
}

@Composable
private fun DicePipFaceCanvas(
    diceValue: Int,
    isActiveTurn: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Outer metallic silver bevel
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = if (isActiveTurn) {
                    listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                } else {
                    listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8))
                }
            ),
            cornerRadius = CornerRadius(w * 0.2f, h * 0.2f)
        )

        // Inner recessed die face
        val pad = w * 0.08f
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFE2E8F0)),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = w * 0.55f
            ),
            topLeft = Offset(pad, pad),
            size = Size(w - 2 * pad, h - 2 * pad),
            cornerRadius = CornerRadius(w * 0.16f, h * 0.16f)
        )

        // Pip Coordinates (3x3 grid inside die face)
        val leftX = w * 0.28f
        val midX = w * 0.50f
        val rightX = w * 0.72f
        val topY = h * 0.27f
        val midY = h * 0.50f
        val botY = h * 0.73f

        val pipOffsets = when (diceValue) {
            1 -> listOf(Offset(midX, midY))
            2 -> listOf(Offset(leftX, topY), Offset(rightX, botY))
            3 -> listOf(Offset(leftX, topY), Offset(midX, midY), Offset(rightX, botY))
            4 -> listOf(
                Offset(leftX, topY), Offset(rightX, topY),
                Offset(leftX, botY), Offset(rightX, botY)
            )
            5 -> listOf(
                Offset(leftX, topY), Offset(rightX, topY),
                Offset(midX, midY),
                Offset(leftX, botY), Offset(rightX, botY)
            )
            else -> listOf(
                Offset(leftX, topY), Offset(rightX, topY),
                Offset(leftX, midY), Offset(rightX, midY),
                Offset(leftX, botY), Offset(rightX, botY)
            )
        }

        val pipRadius = if (diceValue == 1) w * 0.115f else w * 0.088f
        val pipColor = if (diceValue == 1) Color(0xFFDC2626) else Color(0xFF0F172A)

        pipOffsets.forEach { pt ->
            drawCircle(
                color = pipColor,
                radius = pipRadius,
                center = pt
            )
        }
    }
}

@Composable
private fun GoldenTurnArrow(
    pointLeft: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (pointLeft) {
                moveTo(0f, h * 0.5f)
                lineTo(w * 0.55f, h * 0.12f)
                lineTo(w * 0.55f, h * 0.34f)
                lineTo(w, h * 0.34f)
                lineTo(w, h * 0.66f)
                lineTo(w * 0.55f, h * 0.66f)
                lineTo(w * 0.55f, h * 0.88f)
                close()
            } else {
                moveTo(w, h * 0.5f)
                lineTo(w * 0.45f, h * 0.12f)
                lineTo(w * 0.45f, h * 0.34f)
                lineTo(0f, h * 0.34f)
                lineTo(0f, h * 0.66f)
                lineTo(w * 0.45f, h * 0.66f)
                lineTo(w * 0.45f, h * 0.88f)
                close()
            }
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFEA79), Color(0xFFF59E0B))
            )
        )
        drawPath(
            path = path,
            color = Color(0xFF78350F),
            style = Stroke(width = 2f)
        )
    }
}

@Composable
private fun TurnStatusBannerCard(gameState: ActiveLudoGameState) {
    val activeSeat = gameState.seats[gameState.currentTurnIndex]
    Surface(
        color = RoyalCard.copy(alpha = 0.92f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(activeSeat.color.toComposeColor())
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = gameState.statusBanner,
                style = MaterialTheme.typography.bodyMedium,
                color = ImperialGold,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ArenaHeaderControls(
    gameState: ActiveLudoGameState,
    savedGamesCount: Int,
    onQuickSelectMode: (GameModeType) -> Unit,
    onOpenModeSetup: () -> Unit,
    onSaveCurrent: () -> Unit,
    onOpenSavedGames: () -> Unit,
    onToggleChat: () -> Unit
) {
    val quickModes = listOf(
        GameModeType.SOLO_GEMINI to "1P vs AI",
        GameModeType.LOCAL_2P to "2P Local",
        GameModeType.LOCAL_3P to "3P Local",
        GameModeType.LOCAL_4P to "4P Local"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Always-Visible Local Multiplayer & Solo AI Mode Bar (1P, 2P Local, 3P Local, 4P Local)
        Surface(
            color = RoyalCard,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ImperialGold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickModes.forEach { (mode, shortLabel) ->
                    val isSelected = gameState.gameMode == mode
                    Surface(
                        color = if (isSelected) ImperialGold else RoyalSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onQuickSelectMode(mode) }
                            .testTag("quick_mode_${mode.id}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = shortLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color(0xFF1A1200) else Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onOpenModeSetup,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalCardElevated,
                    contentColor = ImperialGold
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("mode_selector_button")
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Names & Rules", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onToggleChat,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("open_table_chat_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = stringResource(R.string.cd_table_chat),
                    tint = ImperialGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Local Chat (${gameState.localChatMessages.size})")
            }

            OutlinedButton(
                onClick = onSaveCurrent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_offline_button")
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = stringResource(R.string.cd_save_game),
                    tint = LudoGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Offline")
            }

            OutlinedButton(
                onClick = onOpenSavedGames,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("load_offline_button")
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = ImperialGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Saved ($savedGamesCount)")
            }
        }

        // Compact Adaptive Gemini AI Telemetry Strip
        Surface(
            color = RoyalCard.copy(alpha = 0.85f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ImperialGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (gameState.activeOnlineInviteCode != null) {
                        "Lobby Code: ${gameState.activeOnlineInviteCode} • AI Tier: ${gameState.aiDifficultyTier}"
                    } else {
                        "Gemini AI: ${gameState.aiDifficultyTier} (${gameState.playerPerformanceScore} Elo) • ${gameState.lastAiReasoning}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE2E8F0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LocalTableChatCard(
    gameState: ActiveLudoGameState,
    onSendTableChat: (senderColorIndex: Int, message: String) -> Unit
) {
    var selectedSenderIdx by remember { mutableIntStateOf(0) }
    var draftMessage by remember { mutableStateOf("") }
    val quickEmojis = listOf("🎲 Lucky 6!", "⚔️ Watch out!", "🛡️ Safe on Star!", "🔥 GG WP!")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RoyalCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Local Multiplayer Table Chat",
                style = MaterialTheme.typography.titleMedium,
                color = ImperialGold
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                gameState.seats.filter { it.isActiveInMatch && !it.isAi }.forEach { seat ->
                    FilterChip(
                        selected = selectedSenderIdx == seat.color.index,
                        onClick = { selectedSenderIdx = seat.color.index },
                        label = { Text(seat.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = seat.color.toComposeColor().copy(alpha = 0.25f),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 160.dp)
                    .background(RoyalSurface, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(gameState.localChatMessages, key = { it.id }) { msg ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${msg.senderName}: ",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (msg.isAiMessage) ImperialGold else Color(0xFF93C5FD)
                        )
                        Text(
                            text = msg.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickEmojis.forEach { reaction ->
                    Surface(
                        color = RoyalCardElevated,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable {
                            onSendTableChat(selectedSenderIdx, reaction)
                        }
                    ) {
                        Text(
                            text = reaction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draftMessage,
                    onValueChange = { draftMessage = it },
                    placeholder = { Text("Send table message...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("local_chat_input")
                )
                IconButton(
                    onClick = {
                        if (draftMessage.isNotBlank()) {
                            onSendTableChat(selectedSenderIdx, draftMessage)
                            draftMessage = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(ImperialGold, CircleShape)
                        .testTag("local_chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send table chat",
                        tint = Color(0xFF1A1200)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameModeSetupDialog(
    currentState: ActiveLudoGameState,
    onDismiss: () -> Unit,
    onConfirmStart: (
        mode: GameModeType,
        customNames: List<String>,
        safeZones: Boolean,
        quickStart: Boolean,
        captureBonus: Boolean
    ) -> Unit
) {
    var selectedMode by remember {
        mutableStateOf(
            if (currentState.gameMode == GameModeType.ONLINE_TOURNAMENT) GameModeType.SOLO_GEMINI else currentState.gameMode
        )
    }
    var p1Name by remember { mutableStateOf(currentState.seats[0].name) }
    var p2Name by remember { mutableStateOf("Player 2") }
    var p3Name by remember { mutableStateOf("Player 3") }
    var p4Name by remember { mutableStateOf("Player 4") }
    var safeZones by remember { mutableStateOf(currentState.customRuleSafeZones) }
    var quickStart by remember { mutableStateOf(currentState.customRuleQuickStart) }
    var captureBonus by remember { mutableStateOf(currentState.customRuleCaptureBonus) }

    val selectableModes = listOf(
        GameModeType.SOLO_GEMINI,
        GameModeType.LOCAL_2P,
        GameModeType.LOCAL_3P,
        GameModeType.LOCAL_4P
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RoyalCard,
        title = {
            Text("Configure Ludo.AI Match", style = MaterialTheme.typography.headlineMedium, color = ImperialGold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Mode:", style = MaterialTheme.typography.labelLarge, color = Color.White)
                selectableModes.forEach { mode ->
                    Surface(
                        color = if (selectedMode == mode) RoyalCardElevated else RoyalSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (selectedMode == mode) 1.5.dp else 0.dp,
                                color = if (selectedMode == mode) ImperialGold else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedMode = mode }
                            .testTag("select_mode_${mode.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(mode.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(mode.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = p1Name,
                    onValueChange = { p1Name = it },
                    label = { Text("Player 1 Name (Red)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedMode != GameModeType.SOLO_GEMINI) {
                    OutlinedTextField(
                        value = p2Name,
                        onValueChange = { p2Name = it },
                        label = { Text("Player 2 Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedMode == GameModeType.LOCAL_3P || selectedMode == GameModeType.LOCAL_4P) {
                    OutlinedTextField(
                        value = p3Name,
                        onValueChange = { p3Name = it },
                        label = { Text("Player 3 Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedMode == GameModeType.LOCAL_4P) {
                    OutlinedTextField(
                        value = p4Name,
                        onValueChange = { p4Name = it },
                        label = { Text("Player 4 Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Custom Rules:", style = MaterialTheme.typography.labelLarge, color = ImperialGold)

                RuleToggleRow("Star Safe Zones", safeZones) { safeZones = it }
                RuleToggleRow("Quick Start (1 Token Out)", quickStart) { quickStart = it }
                RuleToggleRow("Capture Bonus Roll", captureBonus) { captureBonus = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmStart(
                        selectedMode,
                        listOf(p1Name, p2Name, p3Name, p4Name),
                        safeZones,
                        quickStart,
                        captureBonus
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImperialGold,
                    contentColor = Color(0xFF1A1200)
                ),
                modifier = Modifier.testTag("confirm_start_match_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Match", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        }
    )
}

@Composable
private fun RuleToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SavedGamesOfflineDialog(
    savedGames: List<SavedGameEntity>,
    onDismiss: () -> Unit,
    onLoadGame: (SavedGameEntity) -> Unit,
    onDeleteGame: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RoyalCard,
        title = {
            Text("Offline Saved Games", style = MaterialTheme.typography.headlineMedium, color = ImperialGold)
        },
        text = {
            if (savedGames.isEmpty()) {
                Text(
                    "No offline games saved yet. Tap 'Save' on the Arena toolbar anytime to store your board progress locally!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedGames, key = { it.id }) { save ->
                        Surface(
                            color = RoyalSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onLoadGame(save) }
                                ) {
                                    Text(save.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text(
                                        "${save.playerCount} Players • AI Tier: ${save.aiDifficultyTier}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { onLoadGame(save) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ImperialGold,
                                        contentColor = Color(0xFF1A1200)
                                    )
                                ) {
                                    Text("Resume")
                                }
                                IconButton(onClick = { onDeleteGame(save.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete saved game", tint = Color(0xFFFB7185))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ImperialGold)
            }
        }
    )
}
