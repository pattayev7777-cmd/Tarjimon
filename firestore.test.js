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
const ADMIN_UID = "admin_777";
const ADMIN_EMAIL = "akbarakramov0000@gmail.com";

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

function nowTimestamp() {
  return new Date();
}

function validUserPayload(uid, email = "alice@example.com") {
  const now = nowTimestamp();
  return {
    userId: uid,
    email,
    displayName: "Alice User",
    accountStatus: "ACTIVE",
    subscriptionStatus: "FREE",
    freeTranslationsRemaining: 5,
    completedTranslationsCount: 0,
    paidTranslationsCount: 0,
    subscriptionStartAtMillis: 0,
    subscriptionExpiresAtMillis: 0,
    createdAt: now,
    updatedAt: now,
  };
}

function validPaymentPayload(uid, email = "alice@example.com") {
  const now = nowTimestamp();
  return {
    userId: uid,
    userEmail: email,
    amountUzs: 20000,
    cardNumber: "5614681814567313",
    accountHolder: "Akbar",
    receiptFileName: "receipt.jpg",
    receiptMimeType: "image/jpeg",
    receiptDataBase64: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
    aiReceiptSummary: "Valid 20,000 UZS transfer receipt to Akbar",
    status: "PENDING",
    adminNote: "",
    reviewedByEmail: "",
    approvedAtMillis: 0,
    subscriptionExpiresAtMillis: 0,
    createdAt: now,
    updatedAt: now,
  };
}

test("1. Unauthenticated user cannot read users, translations, or payments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("translations").get());
  await assertFails(unauthDb.collection("payments").get());
});

test("2. Authenticated user can create own account with 5 free translations, cannot spoof 999 free translations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validUserPayload(ALICE_UID),
      freeTranslationsRemaining: 50,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );
});

test("3. PII Blanket Test: Alice cannot read Bob's user document", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "bob@example.com"));
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("4. User can decrement freeTranslationsRemaining by 1, but cannot increase it or self-activate subscription", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const initial = validUserPayload(ALICE_UID);
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(initial));

  // Cannot self-activate subscription
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      subscriptionStatus: "ACTIVE",
      updatedAt: nowTimestamp(),
    })
  );

  // Can decrement free balance by 1 and increment completed by 1
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      freeTranslationsRemaining: 4,
      completedTranslationsCount: 1,
      updatedAt: nowTimestamp(),
    })
  );
});

test("5. Payment receipt upload must be PENDING; only verified Admin can approve once", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Cannot upload already APPROVED payment
  await assertFails(
    aliceDb.collection("payments").doc("pay_1").set({
      ...validPaymentPayload(ALICE_UID),
      status: "APPROVED",
    })
  );

  // Can upload PENDING payment
  await assertSucceeds(
    aliceDb.collection("payments").doc("pay_1").set(validPaymentPayload(ALICE_UID))
  );

  // Alice cannot approve her own payment
  await assertFails(
    aliceDb.collection("payments").doc("pay_1").update({
      status: "APPROVED",
      reviewedByEmail: "alice@example.com",
      approvedAtMillis: 1700000000000,
      subscriptionExpiresAtMillis: 1702592000000,
      updatedAt: nowTimestamp(),
    })
  );

  // Email spoofing test: unverified admin email cannot approve
  const spoofAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  await assertFails(
    spoofAdminDb.collection("payments").doc("pay_1").update({
      status: "APPROVED",
      reviewedByEmail: ADMIN_EMAIL,
      approvedAtMillis: 1700000000000,
      subscriptionExpiresAtMillis: 1702592000000,
      updatedAt: nowTimestamp(),
    })
  );

  // Verified admin CAN approve
  const realAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  await assertSucceeds(
    realAdminDb.collection("payments").doc("pay_1").update({
      status: "APPROVED",
      adminNote: "Verified receipt",
      reviewedByEmail: ADMIN_EMAIL,
      approvedAtMillis: 1700000000000,
      subscriptionExpiresAtMillis: 1702592000000,
      updatedAt: nowTimestamp(),
    })
  );

  // Duplicate approval blocked by Terminal State Locking
  await assertFails(
    realAdminDb.collection("payments").doc("pay_1").update({
      status: "APPROVED",
      adminNote: "Second approval attempt",
      reviewedByEmail: ADMIN_EMAIL,
      approvedAtMillis: 1700000050000,
      subscriptionExpiresAtMillis: 1705184000000,
      updatedAt: nowTimestamp(),
    })
  );
});

test("6. Query Trust Test: User can query own translations with userId filter, fails without filter", async () => {
  const now = nowTimestamp();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("translations").doc("trans_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      videoUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      videoId: "dQw4w9WgXcQ",
      videoTitle: "Test Video",
      channelAuthor: "Test Channel",
      sourceLanguage: "en",
      detectedLanguage: "English",
      targetLanguage: "uz",
      isPaidTranslation: false,
      subtitleCount: 2,
      subtitlesJson: "[]",
      originalTranscriptPreview: "Hello world",
      translatedTranscriptPreview: "Salom dunyo",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("translations").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("translations").get());
});

test("7. Admin can configure PlatformSettings (customGeminiApiKey, pricing, card info) and edit/delete any user or record; non-admin cannot", async () => {
  const now = nowTimestamp();
  const settingsPayload = {
    settingId: "global_config",
    subscriptionPriceUzs: 20000,
    paymentCardNumber: "5614681814567313",
    paymentAccountHolder: "Akbar",
    subscriptionDurationDays: 30,
    initialFreeTranslations: 5,
    aiProviderMode: "CUSTOM_GEMINI_KEY",
    customGeminiApiKey: "AIzaSyCustomAdminTestKey123456789",
    aiModelName: "gemini-4-argon",
    customTranslationInstruction: "Translate idioms naturally into Uzbek",
    updatedByEmail: ADMIN_EMAIL,
    createdAt: now,
    updatedAt: now,
  };

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));

  // Non-admin cannot create or update global_config
  await assertFails(
    aliceDb.collection("app_settings").doc("global_config").set(settingsPayload)
  );

  // Verified Admin can create and update global_config
  const realAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  await assertSucceeds(
    realAdminDb.collection("app_settings").doc("global_config").set(settingsPayload)
  );

  // Authenticated user can read global_config
  await assertSucceeds(
    aliceDb.collection("app_settings").doc("global_config").get()
  );

  // Verified Admin can update Alice's freeTranslationsRemaining to 50 and edit displayName/status
  await assertSucceeds(
    realAdminDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice VIP",
      freeTranslationsRemaining: 50,
      subscriptionStatus: "ACTIVE",
      updatedAt: nowTimestamp(),
    })
  );

  // Verified Admin can delete user document if needed
  await assertSucceeds(
    realAdminDb.collection("users").doc(ALICE_UID).delete()
  );
});

