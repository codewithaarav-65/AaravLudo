package com.example.data.remote

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList()
)

@Serializable
data class Candidate(
    val content: Content? = null
)

enum class GeminiModelTier(
    val modelId: String,
    val displayName: String,
    val subtitle: String
) {
    FAST_LITE(
        modelId = "gemini-3.1-flash-lite-preview",
        displayName = "Flash Lite (Fast)",
        subtitle = "Instant table banter & quick roll reactions"
    ),
    GENERAL_FLASH(
        modelId = "gemini-3.5-flash",
        displayName = "Gemini 3.5 Flash",
        subtitle = "Balanced strategy coach & rule advisor"
    ),
    COMPLEX_PRO(
        modelId = "gemini-3.1-pro-preview",
        displayName = "Gemini 3.1 Pro",
        subtitle = "Deep board probability & tournament analysis"
    )
}

enum class GeminiRolePersona(
    val title: String,
    val systemPrompt: String
) {
    GRANDMASTER_COACH(
        title = "Grandmaster Tactician",
        systemPrompt = "You are the Royal Ludo Grandmaster Coach. Analyze board positions, safe star squares, capture probabilities, home-stretch bottlenecks, and adaptive AI strategy. Keep answers concise, sharp, and encouraging."
    ),
    ROYAL_REFEREE(
        title = "Tournament Arbiter",
        systemPrompt = "You are the Official Ludo Royale Tournament Arbiter. Explain classic and custom rules (Safe Star Zones, Quick Start, Capture Bonus, 3-Sixes penalty) clearly and resolve multiplayer disputes with royal authority."
    ),
    RIVAL_CHAMPION(
        title = "Witty Rival AI",
        systemPrompt = "You are Empress Astra, a witty, charismatic Ludo champion playing at the table. React to captures, lucky sixes, and board tension with playful, sportsmanlike table talk in 1-2 short sentences."
    )
}

data class AiChatMessage(
    val id: String,
    val sender: String, // "user" or "model"
    val text: String,
    val modelUsed: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class AiMoveDecision(
    val chosenTokenIndex: Int,
    val reasoning: String,
    val tableTalk: String
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApiService::class.java)
    }

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun sendMultiTurnChat(
        history: List<AiChatMessage>,
        newUserMessage: String,
        boardContextSummary: String,
        modelTier: GeminiModelTier,
        persona: GeminiRolePersona
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isApiKeyConfigured()) {
            return@withContext "Tip (${persona.title}): Configure your GEMINI_API_KEY in the AI Studio Secrets panel for live Gemini responses! Based on the current board ($boardContextSummary), prioritize anchoring tokens on star safe zones (steps 0, 8, 13, 21, 26, 34, 39, 47) while keeping one hunter token ready to capture opponents."
        }

        val contents = mutableListOf<Content>()
        history.takeLast(12).forEach { msg ->
            contents.add(
                Content(
                    role = if (msg.sender == "user") "user" else "model",
                    parts = listOf(Part(text = msg.text))
                )
            )
        }
        contents.add(
            Content(
                role = "user",
                parts = listOf(
                    Part(text = "[Current Ludo Board State: $boardContextSummary]\nUser: $newUserMessage")
                )
            )
        )

        val request = GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                topP = 0.95f
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = persona.systemPrompt))
            )
        )

        try {
            val response = api.generateContent(
                model = modelTier.modelId,
                apiKey = apiKey,
                request = request
            )
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "Stay focused on the star squares and time your home stretch sprint!"
        } catch (e: Exception) {
            "Coach Note: ${e.localizedMessage ?: "Unable to reach Gemini right now."} In this position ($boardContextSummary), protect your leading token and watch out for opponents within 6 steps behind you."
        }
    }

    suspend fun decideAdaptiveAiMove(
        aiDifficultyTier: String,
        playerPerformanceScore: Int,
        diceRoll: Int,
        validTokenIndices: List<Int>,
        boardSummary: String,
        heuristicFallbackIndex: Int,
        heuristicReason: String
    ): AiMoveDecision = withContext(Dispatchers.IO) {
        if (validTokenIndices.size == 1) {
            return@withContext AiMoveDecision(
                chosenTokenIndex = validTokenIndices.first(),
                reasoning = "Only legal move available ($heuristicReason)",
                tableTalk = "Moving Token #${validTokenIndices.first() + 1} with a $diceRoll!"
            )
        }

        if (!isApiKeyConfigured()) {
            return@withContext AiMoveDecision(
                chosenTokenIndex = heuristicFallbackIndex,
                reasoning = "Adaptive $aiDifficultyTier AI ($playerPerformanceScore Elo): $heuristicReason",
                tableTalk = "Playing $heuristicReason at $aiDifficultyTier difficulty!"
            )
        }

        val modelToUse = when (aiDifficultyTier) {
            "Grandmaster" -> GeminiModelTier.COMPLEX_PRO.modelId
            "Strategist" -> GeminiModelTier.GENERAL_FLASH.modelId
            else -> GeminiModelTier.FAST_LITE.modelId
        }

        val prompt = """
            You are playing Ludo as the AI opponent at adaptive difficulty tier: $aiDifficultyTier (Player skill rating: $playerPerformanceScore).
            Dice rolled: $diceRoll
            Legal token indices you can move: ${validTokenIndices.joinToString(", ")}
            Board state & move outcomes:
            $boardSummary
            
            Difficulty behavior:
            - Novice (rating < 950): Play casually, prioritize bringing tokens out or advancing random pieces.
            - Tactician (950-1199): Balance advancing leading tokens and taking obvious captures.
            - Strategist (1200-1499): Prioritize captures, star safe squares, and blocking opponent lanes.
            - Grandmaster (1500+): Play ruthlessly optimal Ludo, calculating capture risk, safe zone control, and home-stretch entry.
            
            Select one token index from ${validTokenIndices.joinToString(", ")}.
        """.trimIndent()

        val schema = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("chosenTokenIndex") {
                    put("type", "INTEGER")
                }
                putJsonObject("reasoning") {
                    put("type", "STRING")
                }
                putJsonObject("tableTalk") {
                    put("type", "STRING")
                }
            }
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.4f
            )
        )

        try {
            val response = api.generateContent(
                model = modelToUse,
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = request
            )
            val rawJson = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (rawJson != null) {
                val parsed = json.parseToJsonElement(rawJson).jsonObject
                val chosen = parsed["chosenTokenIndex"]?.jsonPrimitive?.intOrNull
                val reason = parsed["reasoning"]?.jsonPrimitive?.content ?: heuristicReason
                val talk = parsed["tableTalk"]?.jsonPrimitive?.content ?: "Let's see how you handle that move!"
                if (chosen != null && chosen in validTokenIndices) {
                    return@withContext AiMoveDecision(
                        chosenTokenIndex = chosen,
                        reasoning = "Gemini ($aiDifficultyTier): $reason",
                        tableTalk = talk
                    )
                }
            }
        } catch (_: Exception) {
            // Fall through to adaptive heuristic
        }

        AiMoveDecision(
            chosenTokenIndex = heuristicFallbackIndex,
            reasoning = "Adaptive $aiDifficultyTier AI: $heuristicReason",
            tableTalk = "Calculated move: $heuristicReason!"
        )
    }
}
