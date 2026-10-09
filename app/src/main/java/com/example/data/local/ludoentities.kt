package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val gameMode: String, // "1P_GEMINI", "2P_LOCAL", "3P_LOCAL", "4P_LOCAL"
    val playerCount: Int,
    val playerNamesCsv: String,
    val isAiFlagsCsv: String,
    val tokenPositionsCsv: String, // 16 ints comma-separated
    val currentTurnIndex: Int,
    val lastDiceRoll: Int,
    val hasRolledThisTurn: Boolean,
    val consecutiveSixes: Int,
    val turnsPlayed: Int,
    val capturesByPlayerCsv: String, // 4 ints comma-separated
    val customRuleSafeZones: Boolean,
    val customRuleQuickStart: Boolean,
    val customRuleCaptureBonus: Boolean,
    val aiDifficultyTier: String,
    val chatLogJson: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "local_player_stats")
data class LocalPlayerStatsEntity(
    @PrimaryKey val playerName: String,
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val totalCaptures: Int = 0,
    val tokensFinished: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val aiPerformanceScore: Int = 1000, // Adjusts Gemini AI difficulty dynamically
    val lastPlayedEpochMs: Long = System.currentTimeMillis()
)
