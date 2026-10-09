package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.remote.AiChatMessage
import com.example.data.remote.GeminiModelTier
import com.example.data.remote.GeminiRolePersona
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.LudoBlue
import com.example.ui.theme.RoyalCard
import com.example.ui.theme.RoyalCardElevated
import com.example.ui.theme.RoyalObsidian
import com.example.ui.theme.RoyalSurface

@Composable
fun GeminiCoachScreen(
    messages: List<AiChatMessage>,
    selectedModelTier: GeminiModelTier,
    selectedPersona: GeminiRolePersona,
    isLoading: Boolean,
    onSelectModelTier: (GeminiModelTier) -> Unit,
    onSelectPersona: (GeminiRolePersona) -> Unit,
    onSendMessage: (String) -> Unit
) {
    var draftPrompt by remember { mutableStateOf("") }
    val quickPrompts = listOf(
        "Analyze my current Ludo board position",
        "How does adaptive AI difficulty adjust to my wins?",
        "Best tactic when Capture Bonus rule is enabled?",
        "Which token should I move out of the yard first?"
    )

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ImperialGold,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Gemini Ludo Coach & Arbiter",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ImperialGold
                    )
                    Text(
                        text = "Multi-turn AI strategist aware of your live board state & custom rules.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Gemini Model Selector (Pro 3.1, Flash 3.5, Flash Lite 3.1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GeminiModelTier.entries.forEach { tier ->
                    FilterChip(
                        selected = selectedModelTier == tier,
                        onClick = { onSelectModelTier(tier) },
                        label = { Text(tier.displayName) },
                        modifier = Modifier.testTag("select_gemini_model_${tier.name}")
                    )
                }
            }

            // System Instruction Role Persona Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GeminiRolePersona.entries.forEach { persona ->
                    FilterChip(
                        selected = selectedPersona == persona,
                        onClick = { onSelectPersona(persona) },
                        label = { Text(persona.title) },
                        modifier = Modifier.testTag("select_persona_${persona.name}")
                    )
                }
            }

            // Scrollable Multi-Turn Conversation Thread
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(RoyalSurface, RoundedCornerShape(18.dp))
                    .padding(12.dp)
                    .testTag("gemini_chat_thread"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.sender == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 500.dp),
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) LudoBlue.copy(alpha = 0.28f) else RoyalCardElevated
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (isUser) "You" else "${selectedPersona.title} • ${msg.modelUsed}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isUser) Color(0xFF93C5FD) else ImperialGold
                                )
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEach { prompt ->
                    Surface(
                        color = RoyalCard,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable(enabled = !isLoading) {
                            onSendMessage(prompt)
                        }
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Input Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draftPrompt,
                    onValueChange = { draftPrompt = it },
                    placeholder = { Text("Ask Gemini about Ludo strategy, board odds, or rules...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("gemini_chat_input")
                )
                IconButton(
                    onClick = {
                        if (draftPrompt.isNotBlank() && !isLoading) {
                            onSendMessage(draftPrompt)
                            draftPrompt = ""
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(50.dp)
                        .background(ImperialGold, CircleShape)
                        .testTag("gemini_chat_send_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFF1A1200),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message to Gemini",
                            tint = Color(0xFF1A1200)
                        )
                    }
                }
            }
        }
    }
}
