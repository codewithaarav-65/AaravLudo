package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.TournamentLobby
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.LudoBlue
import com.example.ui.theme.LudoGreen
import com.example.ui.theme.RoyalCard
import com.example.ui.theme.RoyalCardElevated
import com.example.ui.theme.RoyalObsidian
import com.example.ui.theme.RoyalSurface
import com.example.ui.viewmodel.UiState

@Composable
fun TournamentsScreen(
    publicTournamentsState: UiState<List<TournamentLobby>>,
    myTournamentsState: UiState<List<TournamentLobby>>,
    onCreateTournament: (
        title: String,
        inviteCode: String,
        isInviteOnly: Boolean,
        maxPlayers: Int,
        safeZones: Boolean,
        quickStart: Boolean,
        captureBonus: Boolean
    ) -> Unit,
    onJoinByCode: (String) -> Unit,
    onEnterLobbyArena: (TournamentLobby) -> Unit
) {
    var inviteCodeInput by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Public, 1 = My Lobbies

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
            // Header & Host Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live Tournaments & Lobbies",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ImperialGold
                    )
                    Text(
                        text = "Host custom-rule brackets or join private invite-only rooms.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImperialGold,
                        contentColor = Color(0xFF1A1200)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("create_tournament_button")
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Host Lobby", fontWeight = FontWeight.Bold)
                }
            }

            // Join Invite-Only Lobby Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalCard)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = ImperialGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Join Private Invite-Only Lobby",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = inviteCodeInput,
                            onValueChange = { inviteCodeInput = it.uppercase() },
                            placeholder = { Text("Enter Invite Code (e.g. ROYAL7)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("invite_code_input")
                        )
                        Button(
                            onClick = {
                                if (inviteCodeInput.isNotBlank()) {
                                    onJoinByCode(inviteCodeInput)
                                    inviteCodeInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LudoGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("join_invite_button")
                        ) {
                            Text("Join Code", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Filter Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Public Open Tournaments") },
                    leadingIcon = {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("My Joined & Invite Lobbies") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            val activeListState = if (selectedTab == 0) publicTournamentsState else myTournamentsState

            when (activeListState) {
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
                        Text(
                            text = activeListState.message,
                            color = Color(0xFFFFB4AB),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val lobbies = activeListState.data
                    if (lobbies.isEmpty()) {
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
                                    modifier = Modifier.size(42.dp)
                                )
                                Text(
                                    text = "No Active Lobbies Yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "Tap 'Host Lobby' above to create a custom-rule tournament or private invite-only room!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(lobbies, key = { it.id }) { lobby ->
                                TournamentLobbyCard(
                                    lobby = lobby,
                                    onJoinOrEnter = {
                                        if (lobby.status == "waiting") {
                                            onJoinByCode(lobby.inviteCode)
                                        } else {
                                            onEnterLobbyArena(lobby)
                                        }
                                    },
                                    onDirectEnter = { onEnterLobbyArena(lobby) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTournamentDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, code, inviteOnly, maxP, safeZones, quickStart, captureBonus ->
                showCreateDialog = false
                onCreateTournament(title, code, inviteOnly, maxP, safeZones, quickStart, captureBonus)
            }
        )
    }
}

@Composable
private fun TournamentLobbyCard(
    lobby: TournamentLobby,
    onJoinOrEnter: () -> Unit,
    onDirectEnter: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ImperialGold.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RoyalCardElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lobby.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Host: ${lobby.hostName} • Code: ${lobby.inviteCode}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ImperialGold
                    )
                }
                Surface(
                    color = when (lobby.status) {
                        "waiting" -> LudoGreen.copy(alpha = 0.2f)
                        "in_progress" -> LudoBlue.copy(alpha = 0.2f)
                        else -> RoyalSurface
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "${lobby.playerNames.size}/${lobby.maxPlayers} • ${lobby.status.uppercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Custom Rule Badges
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleBadge("Safe Stars: ${if (lobby.customRuleSafeZones) "ON" else "OFF"}")
                RuleBadge("Quick Start: ${if (lobby.customRuleQuickStart) "ON" else "OFF"}")
                RuleBadge("Capture Bonus: ${if (lobby.customRuleCaptureBonus) "ON" else "OFF"}")
            }

            Text(
                text = "Players: ${lobby.playerNames.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDirectEnter,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Spectate / Open Board")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onJoinOrEnter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImperialGold,
                        contentColor = Color(0xFF1A1200)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (lobby.status == "waiting") "Join & Play" else "Enter Match",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleBadge(text: String) {
    Surface(
        color = RoyalSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CreateTournamentDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        inviteCode: String,
        isInviteOnly: Boolean,
        maxPlayers: Int,
        safeZones: Boolean,
        quickStart: Boolean,
        captureBonus: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf("Royal Grand Prix") }
    var inviteCode by remember { mutableStateOf("LUDO${(10..99).random()}") }
    var isInviteOnly by remember { mutableStateOf(false) }
    var maxPlayers by remember { mutableIntStateOf(4) }
    var safeZones by remember { mutableStateOf(true) }
    var quickStart by remember { mutableStateOf(true) }
    var captureBonus by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RoyalCard,
        title = {
            Text("Host Custom Tournament", style = MaterialTheme.typography.headlineMedium, color = ImperialGold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tournament Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase() },
                    label = { Text("Custom Invite Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Max Players:", color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 3, 4).forEach { count ->
                            FilterChip(
                                selected = maxPlayers == count,
                                onClick = { maxPlayers = count },
                                label = { Text("${count}P") }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Invite-Only Private Lobby", color = Color.White)
                    Switch(checked = isInviteOnly, onCheckedChange = { isInviteOnly = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Star Safe Zones", color = Color.White)
                    Switch(checked = safeZones, onCheckedChange = { safeZones = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quick Start (1 Piece Out)", color = Color.White)
                    Switch(checked = quickStart, onCheckedChange = { quickStart = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Capture Bonus Roll", color = Color.White)
                    Switch(checked = captureBonus, onCheckedChange = { captureBonus = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(title, inviteCode, isInviteOnly, maxPlayers, safeZones, quickStart, captureBonus)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImperialGold,
                    contentColor = Color(0xFF1A1200)
                )
            ) {
                Text("Create & Enter", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        }
    )
}
