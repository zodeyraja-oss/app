const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const EVE_UID = "eve_uid_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("invite_codes").doc("KIN12345").get());
  await assertFails(unauthDb.collection("email_directory").doc("abc123").get());
  await assertFails(unauthDb.collection("name_lookup").doc("name_abc").collection("members").get());
  await assertFails(unauthDb.collection("chats").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("calls").where("participantUids", "array-contains", ALICE_UID).get());
});

test("UserPrivate PII isolation & synced preferences: owner succeeds, peer denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      shareCode: "KINALICE",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      keyFingerprint: "AA11BB22CC33",
      appColor: "EMERALD",
      chatColor: "MATCH_APP",
      themeMode: "SYSTEM",
      highContrast: false,
      textSizePreset: "COMFORT",
      easyMode: false,
      ttsVoice: "KORE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read Alice's private PII doc
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  // Ghost field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  // Valid partial update of displayName and theme/color preferences succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      appColor: "OCEAN",
      chatColor: "COBALT",
      themeMode: "DARK",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Invite codes, email_directory, and name_lookup: exact lookup allowed, scraping & hijacking denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("invite_codes").doc("KINALICE").set({
      userId: ALICE_UID,
      displayName: "Alice",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      keyFingerprint: "AA11BB22CC33",
      updatedAt: now,
    })
  );

  await assertSucceeds(bobDb.collection("invite_codes").doc("KINALICE").get());
  await assertFails(bobDb.collection("invite_codes").get());
  await assertFails(bobDb.collection("invite_codes").doc("KINALICE").update({ displayName: "Hijack", updatedAt: now }));

  // Hashed email directory
  await assertSucceeds(
    aliceDb.collection("email_directory").doc("e3b0c44298fc1c149afbf4c8996fb924").set({
      userId: ALICE_UID,
      displayName: "Alice",
      shareCode: "KINALICE",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      keyFingerprint: "AA11BB22CC33",
      updatedAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("email_directory").doc("e3b0c44298fc1c149afbf4c8996fb924").get());
  await assertFails(bobDb.collection("email_directory").get());
  await assertFails(
    bobDb.collection("email_directory").doc("e3b0c44298fc1c149afbf4c8996fb924").update({
      displayName: "Spoof",
      updatedAt: now,
    })
  );

  // Hashed name lookup
  await assertSucceeds(
    aliceDb.collection("name_lookup").doc("name_alice123").collection("members").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      shareCode: "KINALICE",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      keyFingerprint: "AA11BB22CC33",
      updatedAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("name_lookup").doc("name_alice123").collection("members").get());
  await assertFails(bobDb.collection("name_lookup").get());
  // Bob cannot write under Alice's memberUid
  await assertFails(
    bobDb.collection("name_lookup").doc("name_alice123").collection("members").doc(ALICE_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      shareCode: "KINBOB12",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9876543210",
      keyFingerprint: "DD44EE55FF66",
      updatedAt: now,
    })
  );
});

test("Owner contacts subcollection: owner CRUD succeeds, peer denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc(BOB_UID).set({
      contactUid: BOB_UID,
      displayName: "Bob",
      shareCode: "KINBOB12",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9876543210",
      keyFingerprint: "DD44EE55FF66",
      createdAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("contacts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("contacts").get());
});

test("Encrypted chats & messages: participant access, non-participant denied, unfiltered query denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_ab").set({
      title: "Family Chat",
      chatType: "direct",
      creatorId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      wrappedKeys: {
        [ALICE_UID]: "wrappedKeyA",
        [BOB_UID]: "wrappedKeyB",
      },
      lastMessageCiphertext: "cipher_init",
      lastMessageIv: "iv_12345678",
      lastMessageType: "none",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Brand-new user checks existing chats via participantUids query (empty) before creating vault
  await assertSucceeds(eveDb.collection("chats").where("participantUids", "array-contains", EVE_UID).get());
  await assertFails(eveDb.collection("chats").doc("vault_missing").get());
  await assertSucceeds(bobDb.collection("chats").where("participantUids", "array-contains", BOB_UID).get());
  await assertFails(bobDb.collection("chats").get());
  await assertFails(eveDb.collection("chats").doc("chat_ab").get());

  await assertSucceeds(
    bobDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_1").set({
      chatId: "chat_ab",
      senderId: BOB_UID,
      senderName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      messageType: "text",
      ciphertext: "encryptedBase64Payload==",
      iv: "base64Iv1234",
      keyFingerprint: "DD44EE55FF66",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_ab").update({
      lastMessageCiphertext: "encryptedPreview==",
      lastMessageIv: "base64Iv5678",
      lastMessageType: "text",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_ab").collection("messages").where("participantUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    eveDb.collection("chats").doc("chat_ab").collection("messages").get()
  );
  await assertFails(
    eveDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_future").set({
      chatId: "chat_ab",
      senderId: BOB_UID,
      senderName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      messageType: "text",
      ciphertext: "encryptedBase64Payload==",
      iv: "base64Iv1234",
      keyFingerprint: "DD44EE55FF66",
      createdAt: new Date(Date.now() + 60000),
    })
  );
});

test("Voice calls: signaling transitions and terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_call").set({
      title: "Voice Room",
      chatType: "direct",
      creatorId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      wrappedKeys: { [ALICE_UID]: "k1", [BOB_UID]: "k2" },
      lastMessageCiphertext: "c",
      lastMessageIv: "iv12345678",
      lastMessageType: "none",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      chatId: "chat_call",
      callerId: ALICE_UID,
      callerName: "Alice",
      participantUids: [ALICE_UID, BOB_UID],
      status: "ringing",
      sasCode: "482-915",
      durationSeconds: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "active",
      durationSeconds: 5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 18,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      status: "active",
      durationSeconds: 20,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(eveDb.collection("calls").doc("call_1").get());
});
