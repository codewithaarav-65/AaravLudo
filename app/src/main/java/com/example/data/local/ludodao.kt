package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LudoDao {
    @Query("SELECT * FROM saved_games ORDER BY updatedAtEpochMs DESC")
    fun observeSavedGames(): Flow<List<SavedGameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGame(game: SavedGameEntity): Long

    @Query("DELETE FROM saved_games WHERE id = :id")
    suspend fun deleteSavedGame(id: Int)

    @Query("SELECT * FROM local_player_stats ORDER BY wins DESC, aiPerformanceScore DESC")
    fun observeAllPlayerStats(): Flow<List<LocalPlayerStatsEntity>>

    @Query("SELECT * FROM local_player_stats WHERE playerName = :playerName LIMIT 1")
    suspend fun getPlayerStats(playerName: String): LocalPlayerStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayerStats(stats: LocalPlayerStatsEntity)
}
