const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
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

function validVotePayload(userId = ALICE_UID, performerId = "creator_2", voteDelta = 5) {
  const now = new Date(Date.now() - 500);
  return {
    performerId,
    performerName: "Alicia Ray",
    targetId: performerId,
    targetType: "PERFORMER",
    category: "MUSIC",
    genre: "Neo-Soul & R&B",
    stageTitle: "Neo-Soul Vocalist & Producer",
    performanceId: "perf_music_1",
    topPerformanceTitle: "Midnight Reverie (Acoustic Soul)",
    userId,
    userName: "Alice Fan",
    voteDelta,
    voteTier: "SUPER_VOTE",
    status: "COUNTED",
    avatarColorHex: 0xFFF72585,
    createdAt: now,
    updatedAt: now,
  };
}

function validPerformerPayload(performerId = "performer_alice", ownerUid = ALICE_UID) {
  const now = new Date(Date.now() - 500);
  return {
    creatorId: performerId,
    ownerUid,
    name: "Alice Ray",
    stageTitle: "Neo-Soul Vocalist",
    bio: "Acoustic soul and lyrical melodies.",
    category: "MUSIC",
    avatarColorHex: 0xFFF72585,
    rating: 4.9,
    ratingCount: 120,
    totalVotes: 45,
    totalPerformances: 4,
    totalSessions: 2,
    socialInstagram: "@aliceray",
    visibility: "PUBLIC",
    createdAt: now,
    updatedAt: now,
  };
}

function validPerformancePayload(perfId = "perf_alice_1", ownerUid = ALICE_UID, artistId = "performer_alice") {
  const now = new Date(Date.now() - 500);
  return {
    id: perfId,
    ownerUid,
    title: "Midnight Reverie",
    artistId,
    artistName: "Alice Ray",
    avatarInitials: "AR",
    category: "MUSIC",
    genre: "Neo-Soul",
    durationSec: 210,
    lyricsOrScript: "Golden embers of a melody",
    visualizerTheme: "NEON_WAVES",
    audioType: "ACOUSTIC_SOUL",
    viewsCount: 10,
    likesCount: 3,
    votesCount: 5,
    cheersCount: 2,
    createdAtTimestamp: Date.now() - 1000,
    visibility: "PUBLIC",
    updatedAt: now,
  };
}

// --- VOTES COLLECTION TESTS ---

test("Unauthenticated user: cannot read or query votes, performers, or performances", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("votes").where("status", "==", "COUNTED").get());
  await assertFails(unauthDb.collection("performers").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauthDb.collection("performances").where("visibility", "==", "PUBLIC").get());
});

test("Unauthenticated user: cannot cast a vote in votes collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("votes").doc("vote_1").set(validVotePayload(ALICE_UID)));
});

test("Authenticated user: cannot spoof userId when casting a vote", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("votes").doc("spoof_vote").set(validVotePayload(BOB_UID)));
});

test("Shadow Update Test: rejects vote payload containing unauthorized ghost field", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const payloadWithGhost = {
    ...validVotePayload(ALICE_UID),
    isAdminBoosted: true,
  };
  await assertFails(aliceDb.collection("votes").doc("ghost_vote").set(payloadWithGhost));
});

test("Value Poisoning Test: rejects out-of-bounds voteDelta (0 or >100)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("votes").doc("zero_vote").set(validVotePayload(ALICE_UID, "creator_1", 0)));
  await assertFails(aliceDb.collection("votes").doc("huge_vote").set(validVotePayload(ALICE_UID, "creator_1", 500)));
});

test("Query Trust Test: fails unfiltered list queries on votes, performers, and performances", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("votes").get());
  await assertFails(aliceDb.collection("performers").get());
  await assertFails(aliceDb.collection("performances").get());
});

test("Cross-user isolation: Bob cannot read Alice's REVOKED vote or modify/delete Alice's vote", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("votes").doc("alice_vote_1").set(validVotePayload(ALICE_UID)));

  await assertFails(
    bobDb.collection("votes").doc("alice_vote_1").update({
      voteDelta: 10,
      voteTier: "STANDING_OVATION",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(bobDb.collection("votes").doc("alice_vote_1").delete());

  await assertSucceeds(
    aliceDb.collection("votes").doc("alice_vote_1").update({
      status: "REVOKED",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertFails(bobDb.collection("votes").doc("alice_vote_1").get());
  await assertFails(
    aliceDb.collection("votes").doc("alice_vote_1").update({
      status: "COUNTED",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

// --- PERFORMERS & PERFORMANCES COLLECTION TESTS ---

test("Performers collection: owner CRUD, public query alignment, audience rating sync, and cross-user protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice creates her performer profile
  await assertSucceeds(
    aliceDb.collection("performers").doc("performer_alice").set(validPerformerPayload("performer_alice", ALICE_UID))
  );

  // Bob cannot spoof ownerUid or edit Alice's bio/name or delete Alice's performer profile
  await assertFails(
    bobDb.collection("performers").doc("performer_spoof").set(validPerformerPayload("performer_spoof", ALICE_UID))
  );
  await assertFails(
    bobDb.collection("performers").doc("performer_alice").update({
      bio: "Hijacked bio",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(bobDb.collection("performers").doc("performer_alice").delete());

  // Bob CAN query PUBLIC performers and sync audience rating/totalVotes on a PUBLIC performer
  const listSnap = await assertSucceeds(
    bobDb.collection("performers").where("visibility", "==", "PUBLIC").get()
  );
  assert.equal(listSnap.docs.length, 1);

  await assertSucceeds(
    bobDb.collection("performers").doc("performer_alice").update({
      rating: 4.95,
      ratingCount: 121,
      totalVotes: 50,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Performances collection: owner publish, public query alignment, engagement counter sync, and cross-user protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("performances").doc("perf_alice_1").set(validPerformancePayload("perf_alice_1", ALICE_UID))
  );

  // Bob can query PUBLIC performances and update engagement counters, but cannot edit title or delete
  const snap = await assertSucceeds(
    bobDb.collection("performances").where("visibility", "==", "PUBLIC").get()
  );
  assert.equal(snap.docs.length, 1);

  await assertSucceeds(
    bobDb.collection("performances").doc("perf_alice_1").update({
      votesCount: 6,
      likesCount: 4,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  await assertFails(
    bobDb.collection("performances").doc("perf_alice_1").update({
      title: "Hijacked Track",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(bobDb.collection("performances").doc("perf_alice_1").delete());
});

test("Performances collection: supports creator video uploads (customVideoPath & hasVideo) and enforces schema bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const videoPayload = {
    ...validPerformancePayload("perf_video_1", ALICE_UID),
    audioType: "UPLOADED_VIDEO",
    customVideoPath: "/data/user/0/com.aistudio.versepulse.kxmyp/files/uploads/video_stage_1.mp4",
    hasVideo: true,
  };

  await assertSucceeds(aliceDb.collection("performances").doc("perf_video_1").set(videoPayload));

  // Bob cannot hijack customVideoPath on Alice's performance
  await assertFails(
    bobDb.collection("performances").doc("perf_video_1").update({
      customVideoPath: "/malicious/path.mp4",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Rejects non-boolean hasVideo or oversized customVideoPath (>500 chars)
  await assertFails(
    aliceDb.collection("performances").doc("perf_bad_video_1").set({
      ...videoPayload,
      id: "perf_bad_video_1",
      hasVideo: "yes",
    })
  );
  await assertFails(
    aliceDb.collection("performances").doc("perf_bad_video_2").set({
      ...videoPayload,
      id: "perf_bad_video_2",
      customVideoPath: "v".repeat(505),
    })
  );
});

test("Authenticated users can cast votes and query COUNTED votes for real-time leaderboard aggregation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("votes").doc("v_alice_1").set(validVotePayload(ALICE_UID, "creator_2", 5)));
  await assertSucceeds(bobDb.collection("votes").doc("v_bob_1").set(validVotePayload(BOB_UID, "creator_2", 10)));
  await assertSucceeds(bobDb.collection("votes").doc("v_bob_2").set(validVotePayload(BOB_UID, "creator_1", 3)));

  const snapshot = await assertSucceeds(
    aliceDb.collection("votes").where("status", "==", "COUNTED").get()
  );

  assert.equal(snapshot.docs.length, 3);
});

// --- USER ACCOUNT REGISTRATION TESTS (/users/{userId}) ---

function validUserAccountPayload(uid = ALICE_UID, role = "CREATOR") {
  const now = new Date(Date.now() - 500);
  return {
    uid,
    email: `${uid}@versepulse.stage`,
    displayName: "Alice Ray",
    role,
    stageName: "Alice Ray",
    stageTitle: role === "CREATOR" ? "Neo-Soul Vocalist" : "Stage Spectator",
    bio: "Music & spoken word enthusiast.",
    primaryCategory: "MUSIC",
    registrationComplete: true,
    createdAt: now,
    updatedAt: now,
  };
}

test("Users collection: Performer and Spectator registration, role switching, and strict owner isolation", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Unauthenticated cannot read or register
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUserAccountPayload(ALICE_UID, "CREATOR")));

  // Alice registers as a Performer (CREATOR) and Bob registers as a Spectator (SPECTATOR)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserAccountPayload(ALICE_UID, "CREATOR"))
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set(validUserAccountPayload(BOB_UID, "SPECTATOR"))
  );

  // Alice can read her own user account, and Bob can upgrade his own role from SPECTATOR to CREATOR
  const aliceDoc = await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  assert.equal(aliceDoc.data().role, "CREATOR");

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).update({
      role: "CREATOR",
      stageTitle: "Spoken Word Poet",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Cross-user isolation: Bob CANNOT read, overwrite, update, or delete Alice's /users/{ALICE_UID} document
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({
    role: "SPECTATOR",
    updatedAt: new Date(Date.now() - 100),
  }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());

  // Invalid role enum and unfiltered list query must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "SUPERADMIN",
      updatedAt: new Date(Date.now() - 50),
    })
  );
  await assertFails(aliceDb.collection("users").get());
});

// --- LIVE EVENTS COLLECTION TESTS (/events/{eventId}) ---

function validLiveEventPayload(ownerUid = ALICE_UID, category = "MUSIC", participantCount = 24) {
  const now = new Date(Date.now() - 500);
  return {
    ownerUid,
    hostId: `host_${ownerUid}`,
    hostName: "Alice Ray",
    title: category === "MUSIC" ? "Midnight Neo-Soul Jam" : "Velvet Mic Poetry Slam",
    category,
    genre: category === "MUSIC" ? "Neo-Soul" : "Spoken Word",
    description: "Interactive live stage showcase.",
    participantCount,
    isLive: true,
    visibility: "PUBLIC",
    createdAt: now,
    updatedAt: now,
  };
}

test("Events collection: create live Music & Spoken Word events, query live events, sync participantCount, and enforce security", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Unauthenticated cannot query or create live events
  await assertFails(unauthDb.collection("events").where("isLive", "==", true).get());
  await assertFails(unauthDb.collection("events").doc("evt_unauth").set(validLiveEventPayload(ALICE_UID)));

  // Authenticated host creates both Music and Spoken Word live events
  await assertSucceeds(
    aliceDb.collection("events").doc("evt_music_1").set(validLiveEventPayload(ALICE_UID, "MUSIC", 38))
  );
  await assertSucceeds(
    bobDb.collection("events").doc("evt_spoken_1").set(validLiveEventPayload(BOB_UID, "SPOKEN_WORD", 19))
  );

  // Unfiltered list query is rejected (Query Trust Test)
  await assertFails(aliceDb.collection("events").get());

  // Filtered live query succeeds and returns both live Music & Spoken Word events
  const liveSnap = await assertSucceeds(
    aliceDb.collection("events").where("isLive", "==", true).get()
  );
  assert.equal(liveSnap.docs.length, 2);

  // Spectator Bob can increment participantCount on Alice's public live event, but CANNOT change title or end it
  await assertSucceeds(
    bobDb.collection("events").doc("evt_music_1").update({
      participantCount: 39,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    bobDb.collection("events").doc("evt_music_1").update({
      title: "Hijacked Title",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(bobDb.collection("events").doc("evt_music_1").delete());

  // Shadow Update & Value Poisoning rejected
  await assertFails(
    aliceDb.collection("events").doc("evt_ghost").set({
      ...validLiveEventPayload(ALICE_UID),
      isSponsored: true,
    })
  );
  await assertFails(
    aliceDb.collection("events").doc("evt_neg").set(validLiveEventPayload(ALICE_UID, "MUSIC", -3))
  );
  await assertFails(
    aliceDb.collection("events").doc("evt_bad_cat").set(validLiveEventPayload(ALICE_UID, "GAMING", 10))
  );

  // Terminal State Locking: Once Alice ends her live event (isLive: false), further participant updates are blocked
  await assertSucceeds(
    aliceDb.collection("events").doc("evt_music_1").update({
      isLive: false,
      updatedAt: new Date(Date.now() - 50),
    })
  );
  await assertFails(
    bobDb.collection("events").doc("evt_music_1").update({
      participantCount: 40,
      updatedAt: new Date(Date.now() - 10),
    })
  );
});

// --- LIVE PERFORMANCE STAGES & SPECTATOR VOTING COUNTERS TESTS (/stages/{stageId}) ---

function validStagePayload(ownerUid = ALICE_UID, status = "LIVE", category = "MUSIC") {
  const now = new Date(Date.now() - 500);
  return {
    ownerUid,
    hostId: `host_${ownerUid}`,
    hostName: "Alice Ray",
    title: "Main Arena Live Stage",
    category,
    genre: category === "MUSIC" ? "Neo-Soul" : "Spoken Word",
    description: "Real-time stage showcase with spectator voting counters.",
    status,
    activePerformerId: "performer_alice",
    activePerformerName: "Alice Ray",
    activePerformanceId: "perf_alice_1",
    activePerformanceTitle: "Midnight Reverie",
    visualizerTheme: "STAGE_LIGHTS",
    audioType: "ACOUSTIC_SOUL",
    spectatorCount: 12,
    totalStageVotes: 0,
    totalBallotsCast: 0,
    totalCheersCount: 0,
    visibility: "PUBLIC",
    createdAt: now,
    updatedAt: now,
  };
}

function validStageCounterPayload(stageId = "stage_1", performerId = "performer_alice", totalVotes = 0) {
  const now = new Date(Date.now() - 500);
  return {
    stageId,
    performerId,
    performerName: "Alice Ray",
    category: "MUSIC",
    performanceId: "perf_alice_1",
    performanceTitle: "Midnight Reverie",
    genre: "Neo-Soul",
    avatarInitials: "AR",
    avatarColorHex: 0xFFF72585,
    isActiveOnStage: true,
    totalVotes,
    standardVotesCount: totalVotes > 0 ? 1 : 0,
    superVotesCount: 0,
    ovationVotesCount: 0,
    ballotsCast: totalVotes > 0 ? 1 : 0,
    uniqueVoterCount: totalVotes > 0 ? 1 : 0,
    cheersCount: 0,
    visibility: "PUBLIC",
    createdAt: now,
    updatedAt: now,
  };
}

function validSpectatorVotePayload(
  stageId = "stage_1",
  performerId = "performer_alice",
  voterUid = BOB_UID,
  voteWeight = 5,
  voteTier = "SUPER_VOTE"
) {
  const now = new Date(Date.now() - 500);
  return {
    stageId,
    performerId,
    performerName: "Alice Ray",
    category: "MUSIC",
    performanceId: "perf_alice_1",
    performanceTitle: "Midnight Reverie",
    voterUid,
    voterName: "Bob Spectator",
    voteWeight,
    voteTier,
    status: "COUNTED",
    createdAt: now,
    updatedAt: now,
  };
}

test("Stages & Voting Counters: host stage creation, atomic spectator vote & counter batch, query trust, and security guards", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // 1. Unauthenticated cannot read or create stages or voting counters
  await assertFails(unauthDb.collection("stages").where("status", "==", "LIVE").get());
  await assertFails(unauthDb.collection("stages").doc("stage_unauth").set(validStagePayload(ALICE_UID)));

  // 2. Host Alice atomically creates a LIVE stage + initial performer voting counter in a single batch
  const initBatch = aliceDb.batch();
  const stageRef = aliceDb.collection("stages").doc("stage_1");
  const aliceCounterRef = stageRef.collection("voting_counters").doc("performer_alice");
  initBatch.set(stageRef, validStagePayload(ALICE_UID, "LIVE", "MUSIC"));
  initBatch.set(aliceCounterRef, validStageCounterPayload("stage_1", "performer_alice", 0));
  await assertSucceeds(initBatch.commit());

  // 3. Query Trust Test: Unfiltered list queries MUST fail; filtered queries succeed
  await assertFails(bobDb.collection("stages").get());
  await assertFails(bobDb.collection("stages").doc("stage_1").collection("voting_counters").get());
  await assertFails(bobDb.collection("stages").doc("stage_1").collection("spectator_votes").get());

  const liveStagesSnap = await assertSucceeds(
    bobDb.collection("stages").where("status", "==", "LIVE").get()
  );
  assert.equal(liveStagesSnap.docs.length, 1);

  const countersSnap = await assertSucceeds(
    bobDb.collection("stages").doc("stage_1").collection("voting_counters").where("visibility", "==", "PUBLIC").get()
  );
  assert.equal(countersSnap.docs.length, 1);

  // 4. Spectator Bob casts an atomic +5 SUPER_VOTE batch updating spectator_votes, voting_counters, and stage totals
  const voteNow = new Date(Date.now() - 200);
  const voteBatch = bobDb.batch();
  const bobStageRef = bobDb.collection("stages").doc("stage_1");
  const bobCounterRef = bobStageRef.collection("voting_counters").doc("performer_alice");
  const bobVoteRef = bobStageRef.collection("spectator_votes").doc("svote_bob_1");

  voteBatch.set(bobVoteRef, validSpectatorVotePayload("stage_1", "performer_alice", BOB_UID, 5, "SUPER_VOTE"));
  voteBatch.update(bobCounterRef, {
    totalVotes: 5,
    superVotesCount: 1,
    ballotsCast: 1,
    uniqueVoterCount: 1,
    updatedAt: voteNow,
  });
  voteBatch.update(bobStageRef, {
    spectatorCount: 13,
    totalStageVotes: 5,
    totalBallotsCast: 1,
    updatedAt: voteNow,
  });
  await assertSucceeds(voteBatch.commit());

  // Verify counted spectator votes query works
  const votesListSnap = await assertSucceeds(
    aliceDb.collection("stages").doc("stage_1").collection("spectator_votes").where("status", "==", "COUNTED").get()
  );
  assert.equal(votesListSnap.docs.length, 1);

  // 5. Anti-spoofing, cross-user isolation, Shadow Update, and Value Poisoning checks
  // Bob cannot spoof Alice's voterUid
  await assertFails(
    bobStageRef.collection("spectator_votes").doc("svote_spoof").set(
      validSpectatorVotePayload("stage_1", "performer_alice", ALICE_UID, 5, "SUPER_VOTE")
    )
  );
  // Bob cannot hijack stage title or end Alice's stage
  await assertFails(
    bobStageRef.update({
      title: "Hijacked Stage",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    bobStageRef.update({
      status: "ENDED",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  // Bob cannot delete Alice's stage or voting counter
  await assertFails(bobStageRef.delete());
  await assertFails(bobCounterRef.delete());

  // Shadow Update: ghost field rejected on stage and counter
  await assertFails(
    aliceDb.collection("stages").doc("stage_ghost").set({
      ...validStagePayload(ALICE_UID),
      adminMultiplier: 99,
    })
  );
  await assertFails(
    bobStageRef.collection("spectator_votes").doc("svote_ghost").set({
      ...validSpectatorVotePayload("stage_1", "performer_alice", BOB_UID, 1, "VOTE"),
      rigged: true,
    })
  );

  // Value Poisoning: Bob cannot spike performer voting counter by +5000 in one update or write negative votes
  await assertFails(
    bobCounterRef.update({
      totalVotes: 5000,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    bobStageRef.collection("spectator_votes").doc("svote_poison").set(
      validSpectatorVotePayload("stage_1", "performer_alice", BOB_UID, 0, "VOTE")
    )
  );

  // 6. Spectator Bob can revoke his own vote, and Alice cannot revoke Bob's vote
  await assertFails(
    aliceDb.collection("stages").doc("stage_1").collection("spectator_votes").doc("svote_bob_1").update({
      status: "REVOKED",
      updatedAt: new Date(Date.now() - 90),
    })
  );
  await assertSucceeds(
    bobVoteRef.update({
      status: "REVOKED",
      updatedAt: new Date(Date.now() - 80),
    })
  );
  // Terminal vote status: Bob cannot un-revoke a REVOKED vote
  await assertFails(
    bobVoteRef.update({
      status: "COUNTED",
      updatedAt: new Date(Date.now() - 70),
    })
  );

  // 7. Terminal Stage State Locking: Once Alice ends stage_1 (status: "ENDED"), all votes & counter updates fail
  await assertSucceeds(
    stageRef.update({
      status: "ENDED",
      updatedAt: new Date(Date.now() - 50),
    })
  );
  await assertFails(
    bobStageRef.collection("spectator_votes").doc("svote_after_end").set(
      validSpectatorVotePayload("stage_1", "performer_alice", BOB_UID, 1, "VOTE")
    )
  );
  await assertFails(
    bobCounterRef.update({
      totalVotes: 6,
      standardVotesCount: 1,
      updatedAt: new Date(Date.now() - 20),
    })
  );
  await assertFails(
    stageRef.update({
      status: "LIVE",
      updatedAt: new Date(Date.now() - 10),
    })
  );
});

test("Account Deletion Rules: host can list & delete own stage subcollections and users can collectionGroup-delete own spectator votes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice creates a stage + counter; Bob casts a spectator vote on Alice's stage
  const stageRef = aliceDb.collection("stages").doc("stage_cleanup_1");
  await assertSucceeds(stageRef.set(validStagePayload(ALICE_UID, "LIVE", "MUSIC")));
  await assertSucceeds(
    stageRef.collection("voting_counters").doc("performer_alice").set(
      validStageCounterPayload("stage_cleanup_1", "performer_alice", 0)
    )
  );
  await assertSucceeds(
    bobDb.collection("stages").doc("stage_cleanup_1").collection("spectator_votes").doc("sv_bob_clean").set(
      validSpectatorVotePayload("stage_cleanup_1", "performer_alice", BOB_UID, 5, "SUPER_VOTE")
    )
  );

  // Bob ALSO creates his own stage, and Alice casts a spectator vote on Bob's stage
  const bobStageRef = bobDb.collection("stages").doc("stage_bob_keep");
  await assertSucceeds(bobStageRef.set(validStagePayload(BOB_UID, "LIVE", "MUSIC")));
  await assertSucceeds(
    aliceDb.collection("stages").doc("stage_bob_keep").collection("spectator_votes").doc("sv_alice_on_bob").set(
      validSpectatorVotePayload("stage_bob_keep", "performer_bob", ALICE_UID, 1, "VOTE")
    )
  );

  // Unfiltered collectionGroup query or cross-user collectionGroup query by Bob for Alice's voterUid MUST fail
  await assertFails(
    bobDb.collectionGroup("spectator_votes").where("voterUid", "==", ALICE_UID).get()
  );

  // Alice CAN list & delete subcollections of her own stage (including Bob's vote receipt) before deleting stage_cleanup_1
  const countersToDelete = await assertSucceeds(stageRef.collection("voting_counters").get());
  assert.equal(countersToDelete.docs.length, 1);
  for (const doc of countersToDelete.docs) {
    await assertSucceeds(doc.ref.delete());
  }
  const stageVotesToDelete = await assertSucceeds(stageRef.collection("spectator_votes").get());
  assert.equal(stageVotesToDelete.docs.length, 1);
  for (const doc of stageVotesToDelete.docs) {
    await assertSucceeds(doc.ref.delete());
  }
  await assertSucceeds(stageRef.delete());

  // Alice CAN collectionGroup-query her own spectator_votes across other hosts' stages and delete them
  const aliceVotesEverywhere = await assertSucceeds(
    aliceDb.collectionGroup("spectator_votes").where("voterUid", "==", ALICE_UID).get()
  );
  assert.equal(aliceVotesEverywhere.docs.length, 1);
  for (const doc of aliceVotesEverywhere.docs) {
    await assertSucceeds(doc.ref.delete());
  }

  // Bob's stage remains intact
  const bobStageSnap = await assertSucceeds(bobStageRef.get());
  assert.equal(bobStageSnap.exists, true);
});

// --- LIVE SESSION MODERATION, SPECTATOR REACTIONS & STAGE CHAT TESTS ---

function validLiveSessionPayload(sessionId = "session_mod_1", ownerUid = ALICE_UID) {
  const now = new Date(Date.now() - 500);
  return {
    id: sessionId,
    creatorId: `creator_${ownerUid}`,
    ownerUid,
    creatorName: "Alice Ray",
    title: "Midnight Cipher Live",
    category: "MUSIC",
    genre: "Neo-Soul",
    visualizerTheme: "STAGE_LIGHTS",
    audioType: "ACOUSTIC_SOUL",
    spectatorCount: 18,
    isLive: true,
    slowModeSeconds: 0,
    isChatLocked: false,
    pinnedAnnouncement: "Welcome to the arena — drop +10 ovations for your favorite verse!",
    fireCount: 2,
    micDropCount: 1,
    ovationCount: 0,
    heartCount: 3,
    encoreCount: 0,
    visibility: "PUBLIC",
    startedAt: Date.now() - 2000,
    updatedAt: now,
  };
}

function validChatMessagePayload(
  messageId = "msg_1",
  sessionId = "session_mod_1",
  sessionOwnerUid = ALICE_UID,
  senderUid = BOB_UID,
  text = "Incredible vocal run!"
) {
  const now = new Date(Date.now() - 400);
  return {
    id: messageId,
    sessionId,
    sessionOwnerUid,
    senderUid,
    senderName: senderUid === ALICE_UID ? "Alice Ray" : "Bob Spectator",
    text,
    isCheer: false,
    isHiddenByHost: false,
    senderAvatarColorHex: 0xFF00F5D4,
    badge: senderUid === ALICE_UID ? "HOST" : "FAN",
    createdAt: now,
    updatedAt: now,
  };
}

test("Live Sessions Moderation & Crowd Reactions: host controls, bounded spectator reactions, chat lock, and message hiding", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Unauthenticated cannot create or read live sessions or chat
  await assertFails(unauthDb.collection("live_sessions").doc("session_mod_1").set(validLiveSessionPayload()));

  // Host Alice creates live session with moderation fields
  const aliceSessionRef = aliceDb.collection("live_sessions").doc("session_mod_1");
  await assertSucceeds(aliceSessionRef.set(validLiveSessionPayload("session_mod_1", ALICE_UID)));

  // Spectator Bob can increment bounded crowd reactions while session is live, but CANNOT spike +500 or toggle moderation
  const bobSessionRef = bobDb.collection("live_sessions").doc("session_mod_1");
  await assertSucceeds(
    bobSessionRef.update({
      fireCount: 3,
      micDropCount: 2,
      encoreCount: 1,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    bobSessionRef.update({
      fireCount: 500,
      updatedAt: new Date(Date.now() - 250),
    })
  );
  await assertFails(
    bobSessionRef.update({
      isChatLocked: true,
      updatedAt: new Date(Date.now() - 250),
    })
  );
  await assertFails(
    bobSessionRef.update({
      pinnedAnnouncement: "Hijacked banner",
      updatedAt: new Date(Date.now() - 250),
    })
  );

  // Spectator Bob posts a valid chat message while chat is unlocked
  await assertSucceeds(
    bobSessionRef.collection("chat_messages").doc("msg_bob_1").set(
      validChatMessagePayload("msg_bob_1", "session_mod_1", ALICE_UID, BOB_UID, "That chorus hits!")
    )
  );

  // Query Trust Test: Unfiltered list on chat_messages fails for spectator; filtered visible query succeeds
  await assertFails(bobSessionRef.collection("chat_messages").get());
  const visibleChatSnap = await assertSucceeds(
    bobSessionRef.collection("chat_messages").where("isHiddenByHost", "==", false).get()
  );
  assert.equal(visibleChatSnap.docs.length, 1);

  // Anti-spoofing: Bob cannot spoof senderUid or sessionOwnerUid or pre-hide a message
  await assertFails(
    bobSessionRef.collection("chat_messages").doc("msg_spoof").set(
      validChatMessagePayload("msg_spoof", "session_mod_1", ALICE_UID, ALICE_UID, "Fake host")
    )
  );
  await assertFails(
    bobSessionRef.collection("chat_messages").doc("msg_spoof_owner").set(
      validChatMessagePayload("msg_spoof_owner", "session_mod_1", BOB_UID, BOB_UID, "Fake owner")
    )
  );

  // Host Alice hides Bob's disruptive message; Bob cannot unhide it himself
  await assertSucceeds(
    aliceSessionRef.collection("chat_messages").doc("msg_bob_1").update({
      isHiddenByHost: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    bobSessionRef.collection("chat_messages").doc("msg_bob_1").update({
      isHiddenByHost: false,
      updatedAt: new Date(Date.now() - 180),
    })
  );

  // Visible chat query now returns 0 messages for spectators, while host Alice can query all room messages via sessionOwnerUid
  const afterHideSnap = await assertSucceeds(
    bobSessionRef.collection("chat_messages").where("isHiddenByHost", "==", false).get()
  );
  assert.equal(afterHideSnap.docs.length, 0);

  const hostModSnap = await assertSucceeds(
    aliceSessionRef.collection("chat_messages").where("sessionOwnerUid", "==", ALICE_UID).get()
  );
  assert.equal(hostModSnap.docs.length, 1);

  // Host Alice enables Slow Mode (5s) and locks spectator chat
  await assertSucceeds(
    aliceSessionRef.update({
      slowModeSeconds: 5,
      isChatLocked: true,
      pinnedAnnouncement: "Chat paused during spoken-word finale.",
      updatedAt: new Date(Date.now() - 150),
    })
  );

  // While chat is locked, Spectator Bob CANNOT post new chat messages, whereas Host Alice CAN post an announcement message
  await assertFails(
    bobSessionRef.collection("chat_messages").doc("msg_bob_locked").set(
      validChatMessagePayload("msg_bob_locked", "session_mod_1", ALICE_UID, BOB_UID, "Trying while locked")
    )
  );
  await assertSucceeds(
    aliceSessionRef.collection("chat_messages").doc("msg_alice_host").set(
      validChatMessagePayload("msg_alice_host", "session_mod_1", ALICE_UID, ALICE_UID, "Thank you for listening!")
    )
  );

  // Account Deletion Cleanup: Bob can collectionGroup-list & delete his own chat_messages; Alice can delete remaining room chat_messages & session
  const bobMsgsEverywhere = await assertSucceeds(
    bobDb.collectionGroup("chat_messages").where("senderUid", "==", BOB_UID).get()
  );
  assert.equal(bobMsgsEverywhere.docs.length, 1);
  for (const doc of bobMsgsEverywhere.docs) {
    await assertSucceeds(doc.ref.delete());
  }

  const aliceRoomMsgs = await assertSucceeds(
    aliceSessionRef.collection("chat_messages").where("sessionOwnerUid", "==", ALICE_UID).get()
  );
  for (const doc of aliceRoomMsgs.docs) {
    await assertSucceeds(doc.ref.delete());
  }
  await assertSucceeds(aliceSessionRef.delete());
});


