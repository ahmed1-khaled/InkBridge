# InkBridge Product Operations Dashboard

The private `/admin` workspace is designed around product operations rather than vanity analytics. It includes Overview, Devices, Announcements, Feedback Inbox, Releases, Feature Controls, Audit Trail, and Settings.

## Dashboard capabilities

| Area | What it does | Data source |
|---|---|---|
| Overview | Registered installations, active in 30 days, 7-day events, reported errors, platform split and activity | Firestore `devices` and `events` |
| Devices | Searchable inventory with platform, app version, last seen and Android push opt-in | Firestore `devices` |
| Announcements | Compose a platform-targeted broadcast and review send/queue receipts | FCM + Firestore `announcements` |
| Feedback Inbox | Review user feedback and move it through open, in progress, resolved and closed | Firestore `feedback` |
| Releases | Latest public GitHub release, notes, assets and download counts; installed-version adoption | GitHub Releases API + device records |
| Feature Controls | Server-side remote feature switches with an audit trail | Firestore `remoteConfig` |
| Audit Trail | Admin changes and client telemetry timeline | Firestore `adminAudit` and `events` |
| Settings | Integration readiness without exposing credentials | Vercel environment + Firebase config endpoint |

The dashboard intentionally distinguishes **registered devices** from **unique people**. One person may use multiple devices or reinstall the app. Unique human-user counts require an account/identity system.

## External setup still required

The repository does not contain a Firebase project or service-account credentials. The admin UI and server endpoints are implemented, but Firebase-backed functions remain unavailable until the owner configures the project.

1. Create a dedicated Firebase project for InkBridge.
2. Enable Firebase Authentication and Google as a sign-in provider. Add the production website domain to Authorized domains.
3. Create a Firestore database in production mode.
4. Enable Firebase Cloud Messaging API (HTTP v1).
5. Register the Android application using its actual Gradle application ID. Do not assume the package name: inspect `android-app/build.gradle` before registering it.
6. Create a Firebase service account with minimum required Firebase Admin permissions.
7. Add Vercel Production (and Preview if desired) environment variables:
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: full service-account JSON, server-only, encrypted/sensitive.
   - `FIREBASE_WEB_CONFIG`: public Firebase Web App configuration JSON with `apiKey`, `authDomain`, `projectId`, `appId`, and `messagingSenderId`.
   - `ADMIN_EMAILS`: comma-separated verified Google email addresses allowed to administer InkBridge.
8. Redeploy the existing Vercel project after adding environment variables.
9. Visit `/admin`, sign in with a verified allowlisted Google account.

Do not commit service-account JSON, FCM private keys, Android signing keystores or private signing credentials.

## Client registration and telemetry

Each installation must generate a UUID and a cryptographically random secret (at least 32 random bytes), store both securely on that device, and register over HTTPS at `POST /api/client/register`.

Example Android registration payload after Firebase Messaging returns a token:

```json
{
  "deviceId": "generated-installation-uuid",
  "deviceSecret": "random-secret-of-at-least-32-characters",
  "platform": "android",
  "appVersion": "current-app-version",
  "fcmToken": "FirebaseMessaging.getToken()",
  "notificationsEnabled": true
}
```

For Windows, use `platform: "windows"` and omit `fcmToken`. Registration stores a SHA-256 hash of the installation secret, not the raw secret.

### Telemetry contract

`POST /api/client/telemetry` accepts installation credentials and one of:

- `app_opened`
- `connection_succeeded`
- `connection_failed`
- `pdf_opened`
- `pdf_exported`
- `file_transfer_completed`
- `update_available`
- `update_installed`
- `app_error`
- `app_crash`

Only a small allowlist of non-content metadata fields is stored (for example duration, error code, feature, file type and update version). Do not send document content, ink strokes, filenames, IP addresses, passwords or personal information as analytics metadata.

### Feedback contract

`POST /api/client/feedback` accepts installation credentials, category (`bug`, `feature`, `question`, `other`) and a message of 5–3000 characters. Email is optional and should only be sent when the user chooses to provide it.

## Announcements

- Android delivery uses Firebase Cloud Messaging to registered, opted-in devices.
- Windows announcements are queued and retrieved by `POST /api/client/poll`. The Windows Companion must call this endpoint periodically with its installation ID and secret, then display a native Windows toast or in-app notice.
- Provider acceptance is not proof that a notification was displayed or read. Do not label these counts as delivered/read receipts.
- Android 13+ notification permission must be requested and opt-out respected.

## Remote feature controls

Admin toggles are stored in `remoteConfig`. Compatible clients can fetch them from `POST /api/client/config` using installation credentials. Current control keys include:

- `tablet_pdf_vector_export`
- `windows_overlay`
- `local_file_transfer`
- `beta_features`
- `maintenance_banner`

The server-side controls do not affect an app until the corresponding client fetch and enforcement logic ships. Keep dangerous operations and security-sensitive behavior server-enforced; feature flags are not an authorization boundary.

## Security and operations

- Admin APIs verify Firebase ID tokens and compare verified emails against `ADMIN_EMAILS` on the server.
- Installation secrets are hashed at rest and compared using constant-time comparison.
- Clients do not receive direct Firestore access; they use HTTPS endpoints.
- Add Firebase App Check and rate limiting before public rollout. The current registration and telemetry endpoints are not a substitute for abuse controls.
- Define Firestore retention and deletion policies. Give users a privacy notice before collecting telemetry.
- Use minimal telemetry and avoid content capture. Keep a documented incident response and credential-rotation process.
- Use a separate Firebase project for InkBridge; do not reuse another unrelated app's database or credentials.

## Product operations ideas reviewed

The dashboard borrows established operational patterns from device-management consoles (inventory, last-seen, version distribution, rollout visibility and audit trails) and product analytics platforms (events, errors, release health and feature flags). It intentionally avoids adding billing, CRM or user-account management until InkBridge has subscriptions or real user identities.

References:
- Open-source MDM operations dashboard patterns: https://github.com/jinee-s/mdm-operations-dashboard
- Open-source admin layout and working data tables: https://github.com/Kiranism/next-shadcn-dashboard-starter
- Product analytics, error tracking, session replay and feature flags concepts: https://posthog.com/products
- Firebase Android FCM: https://firebase.google.com/docs/cloud-messaging/android/get-started
- Firebase Admin SDK: https://firebase.google.com/docs/admin/setup
- Firebase message sending: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
- Vercel environment variables: https://vercel.com/docs/environment-variables
