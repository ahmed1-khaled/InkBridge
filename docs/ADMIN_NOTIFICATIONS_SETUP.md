# InkBridge Admin, notifications and device analytics

## What this adds

- A private `/admin` console with Google sign-in and server-side administrator allowlisting.
- Registered installation analytics: total devices, Android, Windows, and devices last seen in 30 days.
- An announcement composer that targets all platforms, Android, or Windows.
- Firebase Cloud Messaging delivery to Android installations that opt in and register a valid FCM token.
- A queued announcement feed that Windows Companion can poll while it is running.

**Important:** this repository does not yet contain an InkBridge Firebase project or registered client tokens. The dashboard and API are a foundation; real delivery stays disabled until the owner completes the external Firebase/Vercel setup and the clients are instrumented. Do not treat an empty dashboard as zero users before registration is shipped.

## External setup required

1. Create a dedicated Firebase project for InkBridge (do not reuse the unrelated Career Match Supabase project).
2. Enable Firebase Authentication and the Google provider. Add the production website domain to Authorized domains.
3. Create a Firestore database in production mode.
4. Enable the Firebase Cloud Messaging API (HTTP v1).
5. Register the Android app with package name `com.inkbridge.app` and download `google-services.json`. Keep the service-account key private; never commit it.
6. Create a Firebase service account with the minimum required Firebase Admin permissions.
7. Configure these Vercel Production environment variables, then redeploy:
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: full service-account JSON on one line
   - `FIREBASE_WEB_CONFIG`: public Firebase Web App config JSON (`apiKey`, `authDomain`, `projectId`, `appId`, `messagingSenderId`)
   - `ADMIN_EMAILS`: comma-separated verified Google email addresses allowed into the admin console
8. Open `/admin` and sign in with an allowlisted account.

## Client registration contract

Each installation must generate a random UUID and a cryptographically random secret of at least 32 characters, store both locally, and register over HTTPS at `POST /api/client/register`.

Example Android registration body (after Firebase Messaging returns an FCM token):

```json
{
  "deviceId": "generated-installation-uuid",
  "deviceSecret": "random-32-byte-or-stronger-secret",
  "platform": "android",
  "appVersion": "0.4.x",
  "fcmToken": "FirebaseMessaging.getToken()",
  "notificationsEnabled": true
}
```

For Windows, omit `fcmToken` and use `platform: "windows"`. The companion polls `POST /api/client/poll` with its installation ID, secret and last received timestamp. The API stores a SHA-256 hash of the secret, not the raw secret.

## Windows delivery semantics

Windows Companion does not receive Android FCM messages. Its channel is authenticated HTTPS polling, intended to deliver announcements while the companion is running (including while minimized). Native Windows toast notifications can be layered on after the in-app notification channel is verified. If the app is closed, polling stops.

## What counts as a user?

The dashboard reports **registered installations**, not human users. One person can have a tablet and laptop, reinstall the app, or use multiple profiles. A verified account system is required to report unique people accurately. The 30-day active number means a registered installation checked in within 30 days.

## Security

- Admin APIs verify Firebase ID tokens and compare the verified email to `ADMIN_EMAILS` on the server.
- Firebase service-account credentials are server-only; never place them in HTML, Android assets, Windows config, or public source.
- Clients do not receive direct Firestore access in this design; registration and polling go through HTTPS endpoints.
- Device registration uses per-installation secrets; the server stores their SHA-256 hashes.
- Add App Check and rate limiting before a public production rollout. Review Firestore access rules and data retention.
- Ask users for Android notification permission on Android 13+ and honor opt-out.

## Official references

- Firebase Android FCM setup: https://firebase.google.com/docs/cloud-messaging/android/get-started
- Firebase Admin SDK: https://firebase.google.com/docs/admin/setup
- Sending FCM messages: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
- Vercel environment variables: https://vercel.com/docs/environment-variables
