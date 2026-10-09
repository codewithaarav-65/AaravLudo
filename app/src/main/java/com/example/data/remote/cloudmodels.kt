package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

data class MatchHistoryRecord(
    val id: String = "",
    val userId: String = "",
    val gameMode: String = "1P_GEMINI",
    val winnerName: String = "Player 1",
    val playerWon: Boolean = false,
    val turnsPlayed: Int = 1,
    val capturesMade: Int = 0,
    val aiDifficulty: String = "Tactician",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "gameMode" to gameMode.take(40),
            "winnerName" to winnerName.ifBlank { "Player" }.take(60),
            "playerWon" to playerWon,
            "turnsPlayed" to turnsPlayed.coerceIn(1, 10000),
            "capturesMade" to capturesMade.coerceIn(0, 1000),
            "aiDifficulty" to aiDifficulty.ifBlank { "Tactician" }.take(40),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    companion object {
        fun fromDocument(doc: DocumentSnapshot): MatchHistoryRecord {
            val data = doc.data ?: emptyMap()
            return MatchHistoryRecord(
                id = (data["id"] as? String) ?: doc.id,
                userId = (data["userId"] as? String) ?: "",
                gameMode = (data["gameMode"] as? String) ?: "1P_GEMINI",
                winnerName = (data["winnerName"] as? String) ?: "Player",
                playerWon = (data["playerWon"] as? Boolean) ?: false,
                turnsPlayed = (data["turnsPlayed"] as? Number)?.toInt() ?: 1,
                capturesMade = (data["capturesMade"] as? Number)?.toInt() ?: 0,
                aiDifficulty = (data["aiDifficulty"] as? String) ?: "Tactician",
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class LeaderboardEntry(
    val userId: String = "",
    val displayName: String = "Player",
    val wins: Int = 0,
    val gamesPlayed: Int = 0,
    val captures: Int = 0,
    val rating: Int = 1000,
    val aiTier: String = "Novice",
    val isGlobal: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "displayName" to displayName.ifBlank { "Royal Player" }.take(60),
            "wins" to wins.coerceAtLeast(0),
            "gamesPlayed" to gamesPlayed.coerceAtLeast(0),
            "captures" to captures.coerceAtLeast(0),
            "rating" to rating.coerceIn(0, 100000),
            "aiTier" to aiTier.ifBlank { "Novice" }.take(40),
            "isGlobal" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "displayName" to displayName.ifBlank { "Royal Player" }.take(60),
            "wins" to wins.coerceAtLeast(0),
            "gamesPlayed" to gamesPlayed.coerceAtLeast(0),
            "captures" to captures.coerceAtLeast(0),
            "rating" to rating.coerceIn(0, 100000),
            "aiTier" to aiTier.ifBlank { "Novice" }.take(40),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    companion object {
        fun fromDocument(doc: DocumentSnapshot): LeaderboardEntry {
            val data = doc.data ?: emptyMap()
            return LeaderboardEntry(
                userId = (data["userId"] as? String) ?: doc.id,
                displayName = (data["displayName"] as? String) ?: "Royal Player",
                wins = (data["wins"] as? Number)?.toInt() ?: 0,
                gamesPlayed = (data["gamesPlayed"] as? Number)?.toInt() ?: 0,
                captures = (data["captures"] as? Number)?.toInt() ?: 0,
                rating = (data["rating"] as? Number)?.toInt() ?: 1000,
                aiTier = (data["aiTier"] as? String) ?: "Novice",
                isGlobal = (data["isGlobal"] as? Boolean) ?: true,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class TournamentLobby(
    val id: String = "",
    val hostUid: String = "",
    val hostName: String = "Host",
    val title: String = "Royal Ludo Cup",
    val inviteCode: String = "LUDO01",
    val isInviteOnly: Boolean = false,
    val maxPlayers: Int = 4,
    val customRuleSafeZones: Boolean = true,
    val customRuleQuickStart: Boolean = false,
    val customRuleCaptureBonus: Boolean = true,
    val playerUids: List<String> = emptyList(),
    val playerNames: List<String> = emptyList(),
    val status: String = "waiting",
    val currentTurnIndex: Int = 0,
    val lastDiceRoll: Int = 0,
    val tokenPositions: List<Int> = List(16) { -1 },
    val lastActionText: String = "Lobby created",
    val winnerName: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val normalizedPositions = if (tokenPositions.size == 16) tokenPositions else List(16) { -1 }
        return mapOf(
            "id" to id,
            "hostUid" to hostUid,
            "hostName" to hostName.ifBlank { "Host" }.take(60),
            "title" to title.ifBlank { "Royal Ludo Cup" }.take(80),
            "inviteCode" to inviteCode.ifBlank { "LUDO01" }.take(20),
            "isInviteOnly" to isInviteOnly,
            "maxPlayers" to maxPlayers.coerceIn(2, 4),
            "customRuleSafeZones" to customRuleSafeZones,
            "customRuleQuickStart" to customRuleQuickStart,
            "customRuleCaptureBonus" to customRuleCaptureBonus,
            "playerUids" to playerUids.take(4),
            "playerNames" to playerNames.map { it.ifBlank { "Player" }.take(60) }.take(4),
            "status" to "waiting",
            "currentTurnIndex" to 0,
            "lastDiceRoll" to 0,
            "tokenPositions" to normalizedPositions,
            "lastActionText" to lastActionText.ifBlank { "Lobby created" }.take(200),
            "winnerName" to winnerName.take(60),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    companion object {
        fun fromDocument(doc: DocumentSnapshot): TournamentLobby {
            val data = doc.data ?: emptyMap()
            val rawUids = (data["playerUids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val rawNames = (data["playerNames"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val rawPositions = (data["tokenPositions"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
            val positions = if (rawPositions != null && rawPositions.size == 16) rawPositions else List(16) { -1 }
            return TournamentLobby(
                id = (data["id"] as? String) ?: doc.id,
                hostUid = (data["hostUid"] as? String) ?: "",
                hostName = (data["hostName"] as? String) ?: "Host",
                title = (data["title"] as? String) ?: "Royal Ludo Cup",
                inviteCode = (data["inviteCode"] as? String) ?: "LUDO01",
                isInviteOnly = (data["isInviteOnly"] as? Boolean) ?: false,
                maxPlayers = (data["maxPlayers"] as? Number)?.toInt() ?: 4,
                customRuleSafeZones = (data["customRuleSafeZones"] as? Boolean) ?: true,
                customRuleQuickStart = (data["customRuleQuickStart"] as? Boolean) ?: false,
                customRuleCaptureBonus = (data["customRuleCaptureBonus"] as? Boolean) ?: true,
                playerUids = rawUids,
                playerNames = rawNames,
                status = (data["status"] as? String) ?: "waiting",
                currentTurnIndex = (data["currentTurnIndex"] as? Number)?.toInt() ?: 0,
                lastDiceRoll = (data["lastDiceRoll"] as? Number)?.toInt() ?: 0,
                tokenPositions = positions,
                lastActionText = (data["lastActionText"] as? String) ?: "Lobby ready",
                winnerName = (data["winnerName"] as? String) ?: "",
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}
