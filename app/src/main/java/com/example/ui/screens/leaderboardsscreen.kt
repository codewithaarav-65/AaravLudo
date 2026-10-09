package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalPlayerStatsEntity
import com.example.data.remote.LeaderboardEntry
import com.example.data.remote.MatchHistoryRecord
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.LudoBlue
import com.example.ui.theme.LudoGreen
import com.example.ui.theme.RoyalCard
import com.example.ui.theme.RoyalCardElevated
import com.example.ui.theme.RoyalObsidian
import com.example.ui.theme.RoyalSurface
import com.example.ui.viewmodel.UiState

@Composable
fun LeaderboardsScreen(
    globalLeaderboardState: UiState<List<LeaderboardEntry>>,
    localStats: List<LocalPlayerStatsEntity>,
    cloudMatchHistoryState: UiState<List<MatchHistoryRecord>>
) {
    val defaultTab = if (globalLeaderboardState is UiState.Error) 1 else 0
    var activeTab by remember { mutableIntStateOf(defaultTab) } // 0 = Global Cloud, 1 = Offline Historical, 2 = Match Log

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RoyalObsidian),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Hall of Champions & Statistics",
                style = MaterialTheme.typography.headlineMedium,
                color = ImperialGold
            )
            Text(
                text = "Track global Elo standings, local player performance, and cloud match history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("Global Cloud") },
                    leadingIcon = { Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tab_global_leaderboard")
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("Local Stats") },
                    leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tab_local_stats")
                )
                FilterChip(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    label = { Text("Match Log") },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tab_match_history")
                )
            }

            when (activeTab) {
                0 -> GlobalLeaderboardSection(globalLeaderboardState)
                1 -> LocalPlayerStatsSection(localStats)
                else -> CloudMatchHistorySection(cloudMatchHistoryState)
            }
        }
    }
}

@Composable
private fun GlobalLeaderboardSection(state: UiState<List<LeaderboardEntry>>) {
    when (state) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ImperialGold)
            }
        }
        is UiState.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = RoyalCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(state.message, color = Color(0xFFFFB4AB), modifier = Modifier.padding(16.dp))
            }
        }
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                EmptyLeaderboardPlaceholder(
                    title = "Global Leaderboard Ready",
                    subtitle = "Complete your first Ludo match to publish your Elo rating, captures, and adaptive AI tier to the global standings!"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(state.data, key = { _, item -> item.userId }) { idx, entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (idx < 3) RoyalCardElevated else RoyalCard
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (idx) {
                                                0 -> ImperialGold
                                                1 -> Color(0xFFCBD5E1)
                                                2 -> Color(0xFFD97706)
                                                else -> RoyalSurface
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (idx < 3) Color(0xFF1A1200) else Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.displayName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text(
                                        "${entry.wins} Wins / ${entry.gamesPlayed} Games • ${entry.captures} Captures • AI: ${entry.aiTier}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = ImperialGold.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "${entry.rating} Elo",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ImperialGold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPlayerStatsSection(localStats: List<LocalPlayerStatsEntity>) {
    if (localStats.isEmpty()) {
        EmptyLeaderboardPlaceholder(
            title = "No Local Player Records Yet",
            subtitle = "Play any 1P, 2P, 3P, or 4P match to automatically track win streaks, captures, and adaptive AI ratings offline."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(localStats, key = { _, item -> item.playerName }) { idx, stat ->
                val winRate = if (stat.gamesPlayed > 0) (stat.wins * 100) / stat.gamesPlayed else 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RoyalCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}. ${stat.playerName}",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Skill Score: ${stat.aiPerformanceScore}",
                                style = MaterialTheme.typography.labelLarge,
                                color = ImperialGold
                            )
                        }
                        Text(
                            text = "Wins: ${stat.wins} • Losses: ${stat.losses} • Win Rate: $winRate%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LudoGreen
                        )
                        Text(
                            text = "Captures: ${stat.totalCaptures} • Tokens Finished: ${stat.tokensFinished} • Best Streak: ${stat.bestWinStreak}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudMatchHistorySection(state: UiState<List<MatchHistoryRecord>>) {
    when (state) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ImperialGold)
            }
        }
        is UiState.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = RoyalCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(state.message, color = Color(0xFFFFB4AB), modifier = Modifier.padding(16.dp))
            }
        }
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                EmptyLeaderboardPlaceholder(
                    title = "No Cloud Match History Yet",
                    subtitle = "Completed matches are automatically backed up to your personal Firestore profile."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(state.data, key = { _, item -> item.id }) { _, match ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = RoyalCard)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Winner: ${match.winnerName} (${match.gameMode})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${match.turnsPlayed} Turns • ${match.capturesMade} Captures • AI: ${match.aiDifficulty}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = if (match.playerWon) LudoGreen.copy(alpha = 0.2f) else LudoBlue.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = if (match.playerWon) "VICTORY" else "COMPLETED",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (match.playerWon) LudoGreen else Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLeaderboardPlaceholder(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RoyalCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = ImperialGold,
                modifier = Modifier.size(44.dp)
            )
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
