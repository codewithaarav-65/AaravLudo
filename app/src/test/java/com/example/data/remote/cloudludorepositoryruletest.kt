package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CloudLudoRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun recordMatchHistory_authenticatedOwner_createsAndObservesRecord() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CloudLudoRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordMatchHistory(
                gameMode = "1P_GEMINI",
                winnerName = "Alice",
                playerWon = true,
                turnsPlayed = 32,
                capturesMade = 4,
                aiDifficulty = "Strategist"
            )
        }
        assertTrue("Expected match history write to succeed", result.isSuccess)
        val matchId = result.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMatchHistory(aliceUid).first { list -> list.any { it.id == matchId } }
        }
        assertTrue(observed.any { it.id == matchId && it.winnerName == "Alice" })
    }

    @Test
    fun getMatchHistoryById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CloudLudoRepository(firestore)
        val matchId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordMatchHistory(
                gameMode = "2P_LOCAL",
                winnerName = "Alice",
                playerWon = true,
                turnsPlayed = 18,
                capturesMade = 2,
                aiDifficulty = "Tactician"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getMatchHistoryById(aliceUid, matchId)
        }
        assertTrue("Cross-user access must fail", crossReadResult.isFailure)
        val ex = crossReadResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeGlobalLeaderboard_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CloudLudoRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeGlobalLeaderboard().first()
            }
            fail("Unauthenticated leaderboard observation should throw PERMISSION_DENIED")
        } catch (t: Throwable) {
            var cause: Throwable? = t
            var firestoreEx: FirebaseFirestoreException? = null
            while (cause != null) {
                if (cause is FirebaseFirestoreException) {
                    firestoreEx = cause
                    break
                }
                cause = cause.cause
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    @Test
    fun createAndJoinTournamentLobby_authenticatedPlayers_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = CloudLudoRepository(firestore)
        val uniqueCode = "CUP${UUID.randomUUID().toString().replace("-", "").uppercase().take(5)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTournamentLobby(
                hostName = "Alice",
                title = "Royal Invitational",
                inviteCode = uniqueCode,
                isInviteOnly = true,
                maxPlayers = 2,
                customRuleSafeZones = true,
                customRuleQuickStart = true,
                customRuleCaptureBonus = true
            )
        }
        assertTrue("Expected tournament creation to succeed", createResult.isSuccess)

        signInTestUser(BOB_EMAIL)
        val joinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.joinTournamentByCodeOrId(uniqueCode, "Bob")
        }
        assertTrue("Expected Bob joining by invite code to succeed", joinResult.isSuccess)
        assertEquals("in_progress", joinResult.getOrThrow().status)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_ludo@test.com"
        const val BOB_EMAIL = "bob_ludo@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
