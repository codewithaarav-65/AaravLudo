package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CloudLudoRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun unwrapFirestoreException(error: Throwable): Throwable {
        var curr: Throwable? = error
        while (curr != null) {
            if (curr is com.google.firebase.firestore.FirebaseFirestoreException) return curr
            curr = curr.cause
        }
        return error
    }

    // --- 1. Personal Match History (/users/{userId}/match_history/{matchId}) ---

    fun observeMatchHistory(userId: String): Flow<List<MatchHistoryRecord>> {
        val path = "users/$userId/match_history"
        return db.collection("users").document(userId).collection("match_history")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { MatchHistoryRecord.fromDocument(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreException(error)
                if (unwrapped is Exception) handleFirestoreError(unwrapped, OperationType.LIST, path)
                throw unwrapped
            }
    }

    suspend fun recordMatchHistory(
        gameMode: String,
        winnerName: String,
        playerWon: Boolean,
        turnsPlayed: Int,
        capturesMade: Int,
        aiDifficulty: String
    ): Result<String> {
        val uid = requireUserId()
        val matchId = "match_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("match_history").document(matchId)
        val record = MatchHistoryRecord(
            id = matchId,
            userId = uid,
            gameMode = gameMode,
            winnerName = winnerName,
            playerWon = playerWon,
            turnsPlayed = turnsPlayed,
            capturesMade = capturesMade,
            aiDifficulty = aiDifficulty
        )
        return try {
            docRef.set(record.toCreateMap()).await()
            Result.success(matchId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getMatchHistoryById(targetUserId: String, matchId: String): Result<MatchHistoryRecord> {
        val docRef = db.collection("users").document(targetUserId).collection("match_history").document(matchId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                Result.failure(NoSuchElementException("Match record not found"))
            } else {
                Result.success(MatchHistoryRecord.fromDocument(snap))
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    // --- 2. Global Leaderboard (/leaderboard/{userId}) ---

    fun observeGlobalLeaderboard(): Flow<List<LeaderboardEntry>> {
        val path = "leaderboard"
        return db.collection(path)
            .whereEqualTo("isGlobal", true)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { LeaderboardEntry.fromDocument(it) }
                    .sortedWith(compareByDescending<LeaderboardEntry> { it.rating }.thenByDescending { it.wins })
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreException(error)
                if (unwrapped is Exception) handleFirestoreError(unwrapped, OperationType.LIST, path)
                throw unwrapped
            }
    }

    suspend fun syncLeaderboardStats(
        displayName: String,
        wins: Int,
        gamesPlayed: Int,
        captures: Int,
        rating: Int,
        aiTier: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("leaderboard").document(uid)
        val entry = LeaderboardEntry(
            userId = uid,
            displayName = displayName,
            wins = wins,
            gamesPlayed = gamesPlayed,
            captures = captures,
            rating = rating,
            aiTier = aiTier,
            isGlobal = true
        )
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(entry.toUpdateMap()).await()
            } else {
                docRef.set(entry.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 3. Real-Time Multiplayer Tournaments & Invite-Only Lobbies (/tournaments/{tournamentId}) ---

    fun observePublicTournaments(): Flow<List<TournamentLobby>> {
        val path = "tournaments"
        return db.collection(path)
            .whereEqualTo("isInviteOnly", false)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { TournamentLobby.fromDocument(it) }
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeMyTournaments(userId: String): Flow<List<TournamentLobby>> {
        val path = "tournaments"
        return db.collection(path)
            .whereArrayContains("playerUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { TournamentLobby.fromDocument(it) }
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeTournamentById(tournamentId: String): Flow<TournamentLobby?> {
        val docRef = db.collection("tournaments").document(tournamentId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) TournamentLobby.fromDocument(snapshot) else null
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun createTournamentLobby(
        hostName: String,
        title: String,
        inviteCode: String,
        isInviteOnly: Boolean,
        maxPlayers: Int,
        customRuleSafeZones: Boolean,
        customRuleQuickStart: Boolean,
        customRuleCaptureBonus: Boolean
    ): Result<TournamentLobby> {
        val uid = requireUserId()
        val cleanCode = inviteCode.uppercase().replace(Regex("[^A-Z0-9]"), "").ifBlank {
            UUID.randomUUID().toString().replace("-", "").uppercase().take(6)
        }
        val tournamentId = "tourney_$cleanCode"
        val docRef = db.collection("tournaments").document(tournamentId)
        val initialTokens = List(16) { index ->
            if (customRuleQuickStart && index % 4 == 0) 0 else -1
        }
        val lobby = TournamentLobby(
            id = tournamentId,
            hostUid = uid,
            hostName = hostName.ifBlank { "Royal Host" },
            title = title.ifBlank { "Royal Championship" },
            inviteCode = cleanCode,
            isInviteOnly = isInviteOnly,
            maxPlayers = maxPlayers.coerceIn(2, 4),
            customRuleSafeZones = customRuleSafeZones,
            customRuleQuickStart = customRuleQuickStart,
            customRuleCaptureBonus = customRuleCaptureBonus,
            playerUids = listOf(uid),
            playerNames = listOf(hostName.ifBlank { "Royal Host" }),
            status = "waiting",
            currentTurnIndex = 0,
            lastDiceRoll = 0,
            tokenPositions = initialTokens,
            lastActionText = "${hostName.ifBlank { "Host" }} opened the lobby ($cleanCode)",
            winnerName = ""
        )
        return try {
            docRef.set(lobby.toCreateMap()).await()
            Result.success(lobby)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinTournamentByCodeOrId(codeOrId: String, playerName: String): Result<TournamentLobby> {
        val uid = requireUserId()
        val cleanInput = codeOrId.trim()
        val tournamentId = if (cleanInput.startsWith("tourney_")) {
            cleanInput
        } else {
            "tourney_${cleanInput.uppercase().replace(Regex("[^A-Z0-9]"), "")}"
        }
        val docRef = db.collection("tournaments").document(tournamentId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(NoSuchElementException("Tournament lobby not found for code: $codeOrId"))
            }
            val current = TournamentLobby.fromDocument(snap)
            if (uid in current.playerUids) {
                return Result.success(current)
            }
            if (current.status != "waiting") {
                return Result.failure(IllegalStateException("Tournament has already started or completed."))
            }
            if (current.playerUids.size >= current.maxPlayers) {
                return Result.failure(IllegalStateException("Tournament lobby is full."))
            }
            val updatedUids = current.playerUids + uid
            val updatedNames = current.playerNames + playerName.ifBlank { "Player ${updatedUids.size}" }.take(60)
            val newStatus = if (updatedUids.size == current.maxPlayers) "in_progress" else "waiting"
            val updatePayload = mapOf(
                "playerUids" to updatedUids,
                "playerNames" to updatedNames,
                "status" to newStatus,
                "lastActionText" to "${playerName.ifBlank { "Player" }} joined the tournament!",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updatePayload).await()
            Result.success(
                current.copy(
                    playerUids = updatedUids,
                    playerNames = updatedNames,
                    status = newStatus
                )
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncTournamentState(
        tournamentId: String,
        status: String,
        currentTurnIndex: Int,
        lastDiceRoll: Int,
        tokenPositions: List<Int>,
        lastActionText: String,
        winnerName: String = ""
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("tournaments").document(tournamentId)
        val payload = mapOf(
            "status" to status,
            "currentTurnIndex" to currentTurnIndex.coerceIn(0, 3),
            "lastDiceRoll" to lastDiceRoll.coerceIn(0, 6),
            "tokenPositions" to if (tokenPositions.size == 16) tokenPositions else List(16) { -1 },
            "lastActionText" to lastActionText.ifBlank { "Turn updated" }.take(200),
            "winnerName" to winnerName.take(60),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
