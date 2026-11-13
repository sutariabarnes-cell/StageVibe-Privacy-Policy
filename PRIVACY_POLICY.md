# Privacy Policy for VersePulse

**Effective Date:** November 13, 2026  
**Last Updated:** November 13, 2026  
**Application Name:** VersePulse (`com.aistudio.versepulse.kxmyp`)  
**Privacy Contact:** sutariabarnes@gmail.com *(replace with your public support email or web form URL before publishing on Google Play)*

---

## 1. Overview

VersePulse ("we," "our," or "the App") is a music and spoken-word performance arena for Creators and Spectators featuring audio-visual stages, creator portfolios, and real-time audience voting. This Privacy Policy explains what information VersePulse collects, how it is used and protected, whether it is shared, and how you can access or permanently delete your account and all associated content in compliance with **Google Play Developer Program User Data and Data Safety policies**.

By creating an account or using VersePulse, you acknowledge the practices described in this Privacy Policy. You can review this Privacy Policy at any time inside the App on the Sign-In / Registration screen (before signing in), in the Account menu, and on the Creator Profile screen.

---

## 2. Information We Collect and Why We Collect It

VersePulse adheres to data minimization and collects only the information necessary to provide authentication, performance publishing, live stage hosting, and spectator voting features:

### A. Account & Profile Information (Personal Info)
- **Data Collected:**
  - Display Name, Stage Name, and Stage Title / Moniker
  - Email address (when signing in with Google via Android Credential Manager, or if entered as a Standalone account identifier)
  - Unique User Identifier (`uid`)
  - Profile photo URL (only if provided by your Google Account during Google Sign-In)
  - Selected Arena Role (`Creator` or `Spectator`), primary category (`Music` or `Spoken Word`), optional artist biography, and optional external links you choose to enter (Spotify, Instagram, YouTube, Website)
- **Purpose:** Account creation, authentication, role-based navigation, displaying your public Creator stage profile (when using a Creator role), and preventing duplicate or unauthorized votes.

### B. Audio, Video & User-Generated Stage Content
- **Data Collected:**
  - **Voice Recordings:** Audio recorded directly inside Creator Studio when you initiate microphone capture.
  - **Uploaded Media:** Music audio files and performance video files (plus locally generated preview thumbnails) that you explicitly select through the Android system file/media picker.
  - **Performance & Stage Activity:** Performance titles, genres, lyrics or spoken-word scripts, hosted live sessions/events/stages, poll enrollments, spectator votes (`+1`, `+5`, `+10`), cheers, poll ballots, and 1–5 star audience ratings.
- **Purpose:** Publishing original music and spoken-word performances, rendering audio-visual playback, operating live performance rooms, and computing real-time poll and leaderboard standings.

### C. Authentication & Security Credentials
- **Google Sign-In:** Handled via Android Jetpack **Credential Manager** and **Google Firebase Authentication**. VersePulse never sees or stores your Google Account password.
- **Standalone Account Option:** If you register a Standalone Creator or Spectator account, your password is never stored in plain text; it is derived using **PBKDF2-HMAC-SHA256** with a cryptographic random salt and stored inside an **AES-256-GCM** Android Keystore–encrypted local vault on your device.

---

## 3. Information We Do NOT Collect

To protect your privacy, VersePulse **does NOT collect, request, or infer**:
- **Location Data:** VersePulse does **not** collect precise (`ACCESS_FINE_LOCATION`), approximate (`ACCESS_COARSE_LOCATION`), or background (`ACCESS_BACKGROUND_LOCATION`) device location while the App is in use or in the background, and does not bundle location tracking SDKs.
- **Advertising Identifiers or Cross-App Tracking:** VersePulse contains **no third-party advertising SDKs**, does not read the Android Advertising ID (`AD_ID`), and **never sells or rents personal data**.
- **Contacts, Call Logs, SMS, or Camera Feed:** VersePulse never accesses your address book, phone history, text messages, or live camera sensor.
- **Broad Device Storage:** VersePulse does not request `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, or `MANAGE_EXTERNAL_STORAGE`. Media uploads use Android's zero-permission system document/media pickers so only the specific file you select is accessed.

---

## 4. Device Permissions Used

| Permission | Type | Why VersePulse Uses It |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Install-Time (Normal) | Authenticating with Firebase Authentication and syncing live stages, performances, events, and real-time voting counters with Cloud Firestore over TLS. |
| `android.permission.RECORD_AUDIO` | Runtime (Optional, User-Initiated) | Capturing vocal or spoken-word audio **only** when a Creator actively taps Record inside Creator Studio. Recording never runs silently or in the background. You may deny or revoke microphone permission at any time in Android Settings without losing Spectator or file-upload functionality. |

---

## 5. Data Security & Encryption

VersePulse protects your data both **in transit** and **at rest**:
1. **Encryption in Transit (TLS/HTTPS):** All network communication with Firebase Authentication and Cloud Firestore is encrypted via Transport Layer Security (TLS). Cleartext HTTP traffic is blocked in the App's network security configuration.
2. **Encryption at Rest (AES-256-GCM):**
   - Local media uploads (music files, stage videos, extracted video thumbnails, and vocal studio recordings) are encrypted at rest inside app-private storage using hardware-backed Android Keystore **AES-256-GCM** (`VPENC1` authenticated chunked encryption).
   - Standalone account records and security preferences are encrypted at rest using Android Keystore **AES-256-GCM** and **PBKDF2-HMAC-SHA256**.
3. **Zero-Trust Database Security Rules:** Cloud Firestore security rules require authentication and enforce strict per-user ownership checks so only you can read, update, or delete your private `/users/{uid}` account document and modify or delete your published content.

---

## 6. Data Sharing, Visibility & Service Providers

We do **not** sell your personal information to third parties or share your data for behavioral advertising. Information is shared only in the following limited contexts:

1. **Community Stage Visibility (Signed-In Users):**
   - **Private Account Record:** Your `/users/{uid}` account record (including your email address) is strictly private to you and unreadable by other users.
   - **Public Stage Content:** If you publish a performance, create a Creator profile, host a live stage/event, or cast a counted stage ballot, your chosen Stage Name / Display Handle, performance metadata, and vote/rating tallies are visible to other authenticated VersePulse users so leaderboards, polls, and live stages function accurately.
2. **Cloud Infrastructure Service Providers:**
   - **Google Firebase (Firebase Authentication & Cloud Firestore):** Used strictly as our backend authentication and real-time database processor under [Google's Privacy & Security terms for Firebase](https://firebase.google.com/support/privacy).
3. **Legal Compliance & Safety:** We may disclose information if required to comply with applicable law, valid legal process, or to protect the safety and integrity of users and the service.

---

## 7. Data Retention & Permanent Account + Content Deletion

We retain your account and user-generated content only for as long as your account remains active. In accordance with **Google Play's Account Deletion requirement**, you can permanently delete your account and all associated content at any time:

### A. Instant In-App Account & Content Deletion
1. Open **VersePulse** and sign in to your Creator or Spectator account.
2. Tap your **Account chip** in the top app bar (or open the **Profile** tab).
3. Tap **Delete Account**, review the confirmation notice, and tap **Delete Permanently**.
4. *(If your Firebase session requires recent sign-in verification, you will be prompted once via Google Sign-In to confirm ownership before deletion completes.)*

### B. What Gets Deleted Immediately
Confirming deletion permanently purges:
- Your authentication account (Firebase Authentication user record and/or local Standalone encrypted vault credentials)
- Your private user record (`/users/{uid}`) and public performer/creator profiles (`/performers`, `/creator_profiles`)
- All performances, live sessions, and live events you created (`/performances`, `/live_sessions`, `/events`)
- All live performance stages you hosted (`/stages`), including their `voting_counters` and `spectator_votes` subcollections
- All votes and ballots associated with your account (`spectator_votes` across stages, `/votes`, `/poll_votes`, `/session_votes`) and local creator ratings
- All uploaded audio files, stage video files, video thumbnails, studio drafts, and voice recordings stored on your device

### C. Out-of-App / Web Deletion Requests
If you have already uninstalled VersePulse or cannot access the App, you may request permanent deletion of your cloud account and all associated Firestore data by emailing **sutariabarnes@gmail.com** from the email address associated with your account with the subject line **"VersePulse Account Deletion Request"**. Verified requests are processed within **7 business days** with no retention of personal user content (except where limited retention is strictly required by law).

---

## 8. Children's Privacy

VersePulse is intended for general audiences aged **13 and older** (or the minimum digital consent age in your jurisdiction) and is not directed to children under 13. We do not knowingly collect personal information from children under 13. If we learn that personal data has been collected from a child under 13 without verifiable parental consent, we will promptly delete the account and all associated data. Parents or guardians may contact us at **sutariabarnes@gmail.com**.

---

## 9. Your Privacy Rights & Choices

Depending on your region (including GDPR/EEA, UK GDPR, and CCPA/CPRA), you have the right to:
- **Access & Inspect:** View your active profile, role, account provider, and encryption vault status directly inside the App.
- **Update & Correct:** Edit your Stage Name, Stage Title, Bio, social links, or switch between Creator and Spectator roles at any time.
- **Control Permissions & Encryption:** Revoke microphone (`RECORD_AUDIO`) access in Android Settings and manage local vault encryption inside the Security Vault dialog.
- **Delete:** Permanently erase your account and all associated cloud and local content in-app or by contacting us.

---

## 10. Changes to This Privacy Policy

We may update this Privacy Policy to reflect changes in VersePulse features or legal requirements. When we make material changes, we will update the **"Last Updated"** date at the top of this policy and inside the in-app Privacy Policy viewer.

---

## 11. Contact Us

If you have questions, data access requests, or account deletion requests regarding this Privacy Policy, contact:

- **App:** VersePulse
- **Email:** sutariabarnes@gmail.com

---

## Appendix: Google Play Console "Data Safety" Form Summary (Reference for Publishing)

When completing the **App Content -> Data Safety** questionnaire in Google Play Console for VersePulse, use the following accurate disclosures matching the codebase:

- **Does your app collect or share any of the required user data types?** Yes
- **Is all of the user data collected by your app encrypted in transit?** Yes (TLS/HTTPS via Firebase SDK & Network Security Config)
- **Do you provide a way for users to request that their data is deleted?** Yes (In-app one-flow deletion + contact URL/email for web requests)
- **Location (Approximate / Precise):** **Not collected**
- **Personal Info:**
  - *Name* (Collected, Optional/Display handle, App functionality & Account management)
  - *Email address* (Collected when using Google Sign-In or email-based Standalone ID, App functionality & Account management, **not** shared publicly)
  - *User IDs* (Collected, App functionality, Account management & Fraud/duplicate-vote prevention)
- **Photos and Videos:**
  - *Videos* (Collected only when Creator uploads a stage video via system picker; encrypted at rest locally with AES-256-GCM; App functionality)
- **Audio Files:**
  - *Voice or sound recordings* & *Music files* (Collected only when Creator records audio or selects an audio file; encrypted at rest locally with AES-256-GCM; App functionality)
- **App Activity:**
  - *Other user-generated content* (Stage bios, lyrics/scripts, live stage titles, votes, ratings — Collected & displayed in-app to signed-in users for Arena functionality)
- **Device or Other IDs / Advertising ID:** **Not collected**
