package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.GameModeType
import com.example.domain.LudoBoardGeometry
import com.example.domain.PlayerColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ludo.AI", appName)
    }

    @Test
    fun `verify local game modes and board geometry`() {
        assertEquals(2, GameModeType.SOLO_GEMINI.defaultPlayers)
        assertEquals(2, GameModeType.LOCAL_2P.defaultPlayers)
        assertEquals(3, GameModeType.LOCAL_3P.defaultPlayers)
        assertEquals(4, GameModeType.LOCAL_4P.defaultPlayers)

        val movesOnSix = LudoBoardGeometry.evaluateMovesForPlayer(
            playerIndex = PlayerColor.RED.index,
            diceRoll = 6,
            allTokenPositions = List(16) { -1 },
            activePlayerIndices = setOf(0, 1, 2, 3),
            safeZonesEnabled = true,
            aiDifficultyTier = "Tactician"
        )
        assertEquals(4, movesOnSix.size)
        assertTrue(movesOnSix.all { it.willExitYard })
    }
}
