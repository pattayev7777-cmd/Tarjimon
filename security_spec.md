# Security Specification (Phase 0: Payload-First Security TDD)

## 1. Data Invariants
1. **User Account Isolation & Anti-Privilege Escalation (`/users/{userId}`)**:
   - Every user account document ID must match `request.auth.uid` on creation.
   - Upon creation, `freeTranslationsRemaining` MUST be `5`, `completedTranslationsCount` MUST be `0`, `paidTranslationsCount` MUST be `0`, `subscriptionStatus` MUST be `'FREE'`, and `accountStatus` MUST be `'ACTIVE'`.
   - Non-admin users can NEVER increase `freeTranslationsRemaining`, modify `subscriptionStatus` to `'ACTIVE'`, or alter `subscriptionExpiresAtMillis`.
   - Non-admin users can only decrement `freeTranslationsRemaining` by exactly 1 per completed translation, or increment `paidTranslationsCount` by 1 when `subscriptionStatus == 'ACTIVE'` and `subscriptionExpiresAtMillis > request.time.toMillis()`.
2. **Payment Receipt Approval Gate (`/payments/{paymentId}`)**:
   - Users can only create payment submissions with `status == 'PENDING'`, `amountUzs == 20000`, `approvedAtMillis == 0`, and `subscriptionExpiresAtMillis == 0`.
   - Only verified administrators (`isAdmin()`) can transition a payment from `'PENDING'` to `'APPROVED'` or `'REJECTED'`.
   - Terminal State Locking: Once a payment is `'APPROVED'` or `'REJECTED'`, it cannot be updated again, preventing duplicate approvals.
3. **Translation History Ownership (`/translations/{translationId}`)**:
   - Users can only read, list, create, or edit subtitle segments on translations where `userId == request.auth.uid`. Admins can inspect all translations.
4. **Support Messages (`/support_messages/{messageId}`)**:
   - Users can create support requests with `status == 'OPEN'` and `adminReply == ''`. Only admins can set `adminReply` or mark `status == 'RESOLVED'`.
5. **Notifications (`/notifications/{notificationId}`)**:
   - Users can only read notifications where `recipientUserId == request.auth.uid`. Admins can read all notifications (including `recipientUserId == 'ADMIN'`).

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("users").get()` -> Reject.
2. **Cross-User PII Leak**: Alice reads `users/bob_456` -> Reject.
3. **Self-Granted Free Translations on Signup**: Alice creates `users/alice_123` with `freeTranslationsRemaining: 999` -> Reject.
4. **Self-Activated Subscription Update**: Alice updates `users/alice_123` with `subscriptionStatus: 'ACTIVE'` -> Reject.
5. **Free Balance Inflation Update**: Alice updates `users/alice_123` with `freeTranslationsRemaining: 10` -> Reject.
6. **Auto-Approved Receipt Upload**: Alice creates `payments/pay_1` with `status: 'APPROVED'` -> Reject.
7. **Non-Admin Payment Approval**: Alice updates `payments/pay_1` from `'PENDING'` to `'APPROVED'` -> Reject.
8. **Duplicate Admin Payment Approval (Terminal State Bypass)**: Admin updates an already `'APPROVED'` payment -> Reject.
9. **Email Spoofing Attack**: User with `email: 'akbarakramov0000@gmail.com'` but `email_verified: false` attempts admin action -> Reject.
10. **Shadow Field Injection**: Alice updates `users/alice_123` with extra ghost field `isAdmin: true` -> Reject.
11. **Unfiltered List Query Scraping**: Alice runs `db.collection("translations").get()` without `.where("userId", "==", "alice_123")` -> Reject.
12. **Value Poisoning**: Alice creates a translation with a 1MB `videoId` or invalid `targetLanguage: 'fr'` -> Reject.
