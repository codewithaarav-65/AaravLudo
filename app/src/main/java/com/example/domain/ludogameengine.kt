package com.example.domain

import kotlinx.serialization.Serializable

enum class PlayerColor(
    val index: Int,
    val label: String,
    val startTrackIndex: Int
) {
    // Matches the video layout:
    // 0: Red (Bottom-Left, "You") -> starts at track index 39 (row 13, col 6)
    // 1: Green (Top-Left) -> starts at track index 0 (row 6, col 1)
    // 2: Yellow (Top-Right) -> starts at track index 13 (row 1, col 8)
    // 3: Blue (Bottom-Right) -> starts at track index 26 (row 8, col 13)
    RED(0, "Crimson Red", 39),
    GREEN(1, "Emerald Green", 0),
    YELLOW(2, "Solar Yellow", 13),
    BLUE(3, "Royal Blue", 26);

    companion object {
        fun fromIndex(idx: Int): PlayerColor = entries[idx.coerceIn(0, 3)]
    }
}

enum class GameModeType(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultPlayers: Int
) {
    SOLO_GEMINI("1P_GEMINI", "1 Player vs Gemini AI", "Adaptive AI scales with your performance", 2),
    LOCAL_2P("2P_LOCAL", "2 Players Local", "Head-to-head pass & play + table chat", 2),
    LOCAL_3P("3P_LOCAL", "3 Players Local", "Three-way tactical pass & play", 3),
    LOCAL_4P("4P_LOCAL", "4 Players Local", "Full 4-house classic pass & play", 4),
    ONLINE_TOURNAMENT("ONLINE_TOURNAMENT", "Online Tournament", "Real-time Firestore sync & custom rules", 4)
}

@Serializable
data class LocalTableChatMessage(
    val id: String,
    val senderName: String,
    val playerColorIndex: Int,
    val message: String,
    val isAiMessage: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class PlayerSeat(
    val color: PlayerColor,
    val name: String,
    val isActiveInMatch: Boolean,
    val isAi: Boolean,
    val capturesMade: Int = 0
)

data class BoardCoordinate(
    val row: Float,
    val col: Float
)

data class MoveEvaluation(
    val tokenIndexWithinPlayer: Int,
    val currentStep: Int,
    val targetStep: Int,
    val willExitYard: Boolean,
    val willReachHomeGoal: Boolean,
    val willCaptureOpponent: Boolean,
    val landsOnSafeStar: Boolean,
    val heuristicScore: Int,
    val summary: String
)

object LudoBoardGeometry {
    // 52 perimeter cells clockwise starting at Top-Left Green's start square (row=6, col=1)
    val MAIN_TRACK_COORDS: List<Pair<Int, Int>> = listOf(
        // 0..4: Left arm top row 6 moving right (0 is Green start)
        6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5,
        // 5..10: Top arm left col 6 moving up
        5 to 6, 4 to 6, 3 to 6, 2 to 6, 1 to 6, 0 to 6,
        // 11..12: Top turn (11 is Yellow home stretch entry arrow)
        0 to 7, 0 to 8,
        // 13..17: Top arm right col 8 moving down (13 is Yellow start)
        1 to 8, 2 to 8, 3 to 8, 4 to 8, 5 to 8,
        // 18..23: Right arm top row 6 moving right
        6 to 9, 6 to 10, 6 to 11, 6 to 12, 6 to 13, 6 to 14,
        // 24..25: Right turn (24 is Blue home stretch entry arrow)
        7 to 14, 8 to 14,
        // 26..30: Right arm bottom row 8 moving left (26 is Blue start)
        8 to 13, 8 to 12, 8 to 11, 8 to 10, 8 to 9,
        // 31..36: Bottom arm right col 8 moving down
        9 to 8, 10 to 8, 11 to 8, 12 to 8, 13 to 8, 14 to 8,
        // 37..38: Bottom turn (37 is Red home stretch entry arrow)
        14 to 7, 14 to 6,
        // 39..43: Bottom arm left col 6 moving up (39 is Red start)
        13 to 6, 12 to 6, 11 to 6, 10 to 6, 9 to 6,
        // 44..49: Left arm bottom row 8 moving left
        8 to 5, 8 to 4, 8 to 3, 8 to 2, 8 to 1, 8 to 0,
        // 50..51: Left turn (50 is Green home stretch entry arrow)
        7 to 0, 6 to 0
    )

    val SAFE_GLOBAL_INDICES: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    // Yard positions matching video layout:
    // RED = Bottom-Left (rows 9..14, cols 0..5)
    // GREEN = Top-Left (rows 0..5, cols 0..5)
    // YELLOW = Top-Right (rows 0..5, cols 9..14)
    // BLUE = Bottom-Right (rows 9..14, cols 9..14)
    private val YARD_COORDS: Map<PlayerColor, List<BoardCoordinate>> = mapOf(
        PlayerColor.RED to listOf(
            BoardCoordinate(10.8f, 1.85f),
            BoardCoordinate(10.8f, 4.15f),
            BoardCoordinate(12.9f, 1.85f),
            BoardCoordinate(12.9f, 4.15f)
        ),
        PlayerColor.GREEN to listOf(
            BoardCoordinate(1.8f, 1.85f),
            BoardCoordinate(1.8f, 4.15f),
            BoardCoordinate(3.9f, 1.85f),
            BoardCoordinate(3.9f, 4.15f)
        ),
        PlayerColor.YELLOW to listOf(
            BoardCoordinate(1.8f, 10.85f),
            BoardCoordinate(1.8f, 13.15f),
            BoardCoordinate(3.9f, 10.85f),
            BoardCoordinate(3.9f, 13.15f)
        ),
        PlayerColor.BLUE to listOf(
            BoardCoordinate(10.8f, 10.85f),
            BoardCoordinate(10.8f, 13.15f),
            BoardCoordinate(12.9f, 10.85f),
            BoardCoordinate(12.9f, 13.15f)
        )
    )

    fun toGlobalTrackIndex(color: PlayerColor, relativeStep: Int): Int? {
        if (relativeStep !in 0..50) return null
        return (color.startTrackIndex + relativeStep) % 52
    }

    fun getBoardCoordinate(color: PlayerColor, tokenSlot: Int, step: Int): BoardCoordinate {
        if (step < 0) {
            return YARD_COORDS[color]?.getOrNull(tokenSlot.coerceIn(0, 3))
                ?: BoardCoordinate(11f, 2f)
        }
        if (step in 0..50) {
            val globalIdx = (color.startTrackIndex + step) % 52
            val (r, c) = MAIN_TRACK_COORDS[globalIdx]
            return BoardCoordinate(r.toFloat() + 0.5f, c.toFloat() + 0.5f)
        }
        if (step in 51..55) {
            val offset = step - 50 // 1..5
            return when (color) {
                PlayerColor.RED -> BoardCoordinate((14 - offset).toFloat() + 0.5f, 7.5f)
                PlayerColor.GREEN -> BoardCoordinate(7.5f, offset.toFloat() + 0.5f)
                PlayerColor.YELLOW -> BoardCoordinate(offset.toFloat() + 0.5f, 7.5f)
                PlayerColor.BLUE -> BoardCoordinate(7.5f, (14 - offset).toFloat() + 0.5f)
            }
        }
        // step >= 56: Home Goal center pocket matching each house's home stretch direction
        val slotShift = (tokenSlot - 1.5f) * 0.22f
        return when (color) {
            PlayerColor.RED -> BoardCoordinate(8.55f, 7.5f + slotShift)
            PlayerColor.GREEN -> BoardCoordinate(7.5f + slotShift, 6.45f)
            PlayerColor.YELLOW -> BoardCoordinate(6.45f, 7.5f + slotShift)
            PlayerColor.BLUE -> BoardCoordinate(7.5f + slotShift, 8.55f)
        }
    }

    fun isLegalMove(currentStep: Int, diceRoll: Int): Boolean {
        if (diceRoll !in 1..6) return false
        if (currentStep == -1) return diceRoll == 6
        if (currentStep >= 56) return false
        return currentStep + diceRoll <= 56
    }

    fun evaluateMovesForPlayer(
        playerIndex: Int,
        diceRoll: Int,
        allTokenPositions: List<Int>,
        activePlayerIndices: Set<Int>,
        safeZonesEnabled: Boolean,
        aiDifficultyTier: String
    ): List<MoveEvaluation> {
        val playerColor = PlayerColor.fromIndex(playerIndex)
        val evaluations = mutableListOf<MoveEvaluation>()

        for (slot in 0 until 4) {
            val globalTokenIdx = playerIndex * 4 + slot
            val currentStep = allTokenPositions.getOrElse(globalTokenIdx) { -1 }
            if (!isLegalMove(currentStep, diceRoll)) continue

            val targetStep = if (currentStep == -1) 0 else currentStep + diceRoll
            val willExitYard = currentStep == -1 && targetStep == 0
            val willReachHome = targetStep == 56
            val targetGlobalIdx = toGlobalTrackIndex(playerColor, targetStep)
            val landsOnSafe = safeZonesEnabled && targetGlobalIdx != null && targetGlobalIdx in SAFE_GLOBAL_INDICES

            var willCapture = false
            if (targetGlobalIdx != null && !landsOnSafe) {
                for (otherPlayerIdx in activePlayerIndices) {
                    if (otherPlayerIdx == playerIndex) continue
                    val otherColor = PlayerColor.fromIndex(otherPlayerIdx)
                    for (otherSlot in 0 until 4) {
                        val otherPos = allTokenPositions.getOrElse(otherPlayerIdx * 4 + otherSlot) { -1 }
                        val otherGlobal = toGlobalTrackIndex(otherColor, otherPos)
                        if (otherGlobal != null && otherGlobal == targetGlobalIdx) {
                            willCapture = true
                        }
                    }
                }
            }

            var score = 0
            when (aiDifficultyTier) {
                "Novice" -> {
                    if (willExitYard) score += 70
                    if (willCapture) score += 50
                    if (willReachHome) score += 60
                    score += targetStep
                }
                "Tactician" -> {
                    if (willCapture) score += 140
                    if (willExitYard) score += 100
                    if (willReachHome) score += 110
                    if (landsOnSafe) score += 45
                    score += targetStep
                }
                "Strategist" -> {
                    if (willCapture) score += 200
                    if (willReachHome) score += 160
                    if (willExitYard) score += 120
                    if (landsOnSafe) score += 85
                    if (currentStep in 0..50 && safeZonesEnabled) {
                        val currGlobal = toGlobalTrackIndex(playerColor, currentStep)
                        if (currGlobal in SAFE_GLOBAL_INDICES) score -= 40
                    }
                    score += targetStep * 2
                }
                else -> {
                    if (willCapture) score += 260
                    if (willReachHome) score += 210
                    if (landsOnSafe) score += 130
                    if (willExitYard) score += 145
                    if (targetStep in 51..55) score += 95
                    if (currentStep in 0..50 && safeZonesEnabled) {
                        val currGlobal = toGlobalTrackIndex(playerColor, currentStep)
                        if (currGlobal in SAFE_GLOBAL_INDICES) score -= 55
                    }
                    score += targetStep * 3
                }
            }

            val summaryParts = mutableListOf<String>()
            if (willExitYard) summaryParts.add("Exits yard onto starting square")
            if (willCapture) summaryParts.add("CAPTURES opponent token")
            if (willReachHome) summaryParts.add("Reaches HOME goal")
            if (landsOnSafe) summaryParts.add("Lands on Star Safe Zone")
            if (summaryParts.isEmpty()) summaryParts.add("Advances from step $currentStep to $targetStep")

            evaluations.add(
                MoveEvaluation(
                    tokenIndexWithinPlayer = slot,
                    currentStep = currentStep,
                    targetStep = targetStep,
                    willExitYard = willExitYard,
                    willReachHomeGoal = willReachHome,
                    willCaptureOpponent = willCapture,
                    landsOnSafeStar = landsOnSafe,
                    heuristicScore = score,
                    summary = "Token #${slot + 1}: ${summaryParts.joinToString(", ")}"
                )
            )
        }

        return evaluations.sortedByDescending { it.heuristicScore }
    }

    fun computeAiDifficultyTier(performanceScore: Int): String = when {
        performanceScore >= 1450 -> "Grandmaster"
        performanceScore >= 1180 -> "Strategist"
        performanceScore >= 940 -> "Tactician"
        else -> "Novice"
    }
}
