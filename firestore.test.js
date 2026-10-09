const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read match_history, leaderboard, or tournaments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("match_history").get());
  await assertFails(unauthDb.collection("leaderboard").where("isGlobal", "==", true).get());
  await assertFails(unauthDb.collection("tournaments").where("isInviteOnly", "==", false).get());
});

test("Authenticated user: can create and read own match_history, but Bob cannot read Alice's", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("match_history").doc("match_1").set({
      id: "match_1",
      userId: ALICE_UID,
      gameMode: "1P_GEMINI",
      winnerName: "Alice",
      playerWon: true,
      turnsPlayed: 24,
      capturesMade: 3,
      aiDifficulty: "Master",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("match_history").doc("match_1").get()
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("match_history").doc("match_1").get()
  );
});

test("Shadow field injection fails on leaderboard create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      wins: 5,
      gamesPlayed: 10,
      captures: 8,
      rating: 1200,
      aiTier: "Strategist",
      isGlobal: true,
      isAdminGhost: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Leaderboard create, aligned query succeeds, unfiltered query fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      wins: 5,
      gamesPlayed: 10,
      captures: 8,
      rating: 1200,
      aiTier: "Strategist",
      isGlobal: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("leaderboard").where("isGlobal", "==", true).get()
  );

  await assertFails(
    aliceDb.collection("leaderboard").get()
  );
});

test("Tournament lobby create, join, move, and terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  const lobbyDoc = {
    id: "tourney_1",
    hostUid: ALICE_UID,
    hostName: "Alice",
    title: "Royal Ludo Cup",
    inviteCode: "LUDO99",
    isInviteOnly: false,
    maxPlayers: 4,
    customRuleSafeZones: true,
    customRuleQuickStart: false,
    customRuleCaptureBonus: true,
    playerUids: [ALICE_UID],
    playerNames: ["Alice"],
    status: "waiting",
    currentTurnIndex: 0,
    lastDiceRoll: 0,
    tokenPositions: [-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1],
    lastActionText: "Lobby created",
    winnerName: "",
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(
    aliceDb.collection("tournaments").doc("tourney_1").set(lobbyDoc)
  );

  // Bob joins waiting tournament
  await assertSucceeds(
    bobDb.collection("tournaments").doc("tourney_1").update({
      playerUids: [ALICE_UID, BOB_UID],
      playerNames: ["Alice", "Bob"],
      status: "in_progress",
      lastActionText: "Bob joined the tournament!",
      updatedAt: new Date(),
    })
  );

  // Alice makes a move and completes tournament
  await assertSucceeds(
    aliceDb.collection("tournaments").doc("tourney_1").update({
      status: "completed",
      currentTurnIndex: 1,
      lastDiceRoll: 6,
      tokenPositions: [0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1],
      lastActionText: "Alice won the tournament!",
      winnerName: "Alice",
      updatedAt: new Date(),
    })
  );

  // Terminal state lock: cannot update after completed
  await assertFails(
    bobDb.collection("tournaments").doc("tourney_1").update({
      lastDiceRoll: 4,
      lastActionText: "Late move attempt",
      updatedAt: new Date(),
    })
  );
});
