# Ludo Royale Security Specification

## 1. Data Invariants
1. **Authentication Prerequisite**: No unauthenticated user (`request.auth == null`) may read or write any document in any collection.
2. **Personal Match History Isolation (`/users/{userId}/match_history/{matchId}`)**: Only the authenticated owner (`request.auth.uid == userId`) can read, list, create, update, or delete their personal match history records. `userId` and `createdAt` are immutable.
3. **Leaderboard Integrity (`/leaderboard/{userId}`)**: A user can only create or update their own leaderboard document (`request.auth.uid == userId` and `incoming().userId == request.auth.uid`). List queries must filter by `isGlobal == true`.
4. **Tournament & Lobby Security (`/tournaments/{tournamentId}`)**:
   - Creation requires `hostUid == request.auth.uid` and `status == 'waiting'`.
   - Listing requires filtering by `isInviteOnly == false` (public tournaments) or `playerUids` containing `request.auth.uid`.
   - Terminal State Locking: Once `existing().status == 'completed'`, no further updates are permitted.
   - Action-based updates: Joining, making a move, or completing a tournament must only modify allowlisted fields and validate `isValidTournamentLobby(incoming())`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `auth = null` attempting `get` or `list` on `/users/alice/match_history`, `/leaderboard`, or `/tournaments`.
2. **Cross-User Match History Read**: `auth.uid = "bob"` attempting to read `/users/alice/match_history/m1`.
3. **Identity Spoofing on Leaderboard Create**: `auth.uid = "bob"` attempting to create `/leaderboard/alice` or setting `userId: "alice"`.
4. **Shadow Field Injection ("Ghost Field")**: Adding `"isAdmin": true` or `"hacked": 1` to any create or update payload.
5. **Value Poisoning on Update**: Updating `wins` on `/leaderboard/{userId}` with a negative integer `-50` or string `"many"`.
6. **Resource Poisoning (Oversized String)**: Submitting a 5,000-character `displayName` or `title` exceeding `.size()` bounds.
7. **ID Poisoning**: Creating a document with invalid characters or >128 length ID.
8. **Immutable Field Mutation**: Attempting to modify `createdAt`, `userId`, or `hostUid` during an `update`.
9. **Future Timestamp Injection**: Submitting a `createdAt` or `updatedAt` timestamp in the future (`> request.time`) or non-timestamp integer.
10. **Terminal State Bypass**: Attempting to update a tournament document whose `existing().status == 'completed'`.
11. **Unfiltered Query Scraping**: Executing `db.collection("tournaments").get()` without `isInviteOnly == false` or `playerUids` filter.
12. **Unauthorized Tournament Move**: A non-participant (`!(request.auth.uid in existing().playerUids)`) attempting to update board state or turn index in an active tournament.
