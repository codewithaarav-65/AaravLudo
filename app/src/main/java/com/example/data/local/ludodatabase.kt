package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [SavedGameEntity::class, LocalPlayerStatsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LudoDatabase : RoomDatabase() {
    abstract fun ludoDao(): LudoDao

    companion object {
        @Volatile
        private var INSTANCE: LudoDatabase? = null

        fun getInstance(context: Context): LudoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LudoDatabase::class.java,
                    "ludo_royale_offline.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class LocalLudoRepository(private val dao: LudoDao) {
    val savedGames: Flow<List<SavedGameEntity>> = dao.observeSavedGames()
    val localLeaderboard: Flow<List<LocalPlayerStatsEntity>> = dao.observeAllPlayerStats()

    suspend fun saveGameProgress(game: SavedGameEntity): Long = dao.saveGame(game)

    suspend fun deleteSavedGame(id: Int) = dao.deleteSavedGame(id)

    suspend fun getOrCreatePlayerStats(playerName: String): LocalPlayerStatsEntity {
        return dao.getPlayerStats(playerName) ?: LocalPlayerStatsEntity(playerName = playerName)
    }

    suspend fun recordMatchOutcome(
        playerName: String,
        won: Boolean,
        captures: Int,
        tokensHome: Int
    ): LocalPlayerStatsEntity {
        val current = getOrCreatePlayerStats(playerName)
        val newStreak = if (won) current.currentWinStreak + 1 else 0
        val bestStreak = maxOf(current.bestWinStreak, newStreak)
        val ratingDelta = if (won) 45 + (captures * 8) else -25 + (captures * 4)
        val updatedScore = (current.aiPerformanceScore + ratingDelta).coerceIn(600, 2600)
        val updated = current.copy(
            gamesPlayed = current.gamesPlayed + 1,
            wins = current.wins + if (won) 1 else 0,
            losses = current.losses + if (won) 0 else 1,
            totalCaptures = current.totalCaptures + captures,
            tokensFinished = current.tokensFinished + tokensHome,
            currentWinStreak = newStreak,
            bestWinStreak = bestStreak,
            aiPerformanceScore = updatedScore,
            lastPlayedEpochMs = System.currentTimeMillis()
        )
        dao.upsertPlayerStats(updated)
        return updated
    }
}
