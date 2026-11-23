# InkBridge Operations Console

## Admin modules

The private /admin console follows patterns from product analytics, device management and customer-support tools:

- Overview: registered installations, 30-day active devices, connection success rate, app errors, PDF exports, file transfers, seven-day activity, platform mix, latest feedback and product signals.
- Devices: searchable Android/Windows inventory with app version, model, OS, locale, push opt-in and last-seen timestamp.
- Announcements: immediate platform-targeted broadcasts, message history, FCM provider-accepted Android counts and Windows queue counts.
- Feedback inbox: bug reports, feature requests, questions and status workflow (open, in progress, resolved, closed).
- Releases: latest GitHub release, notes, assets, public download counts and installed-version distribution.
- Feature controls: remote flags for staged rollout. A flag only affects clients after client-side checks are implemented and released.
- Audit trail: administrator actions and recent client telemetry.
- System health: authenticated Firebase Admin and Firestore checks, with Android/Windows delivery readiness called out separately.
- Website visitors: real page views, anonymous tab sessions, recent activity and approximate online/away state based on a heartbeat.
- Settings: integration readiness and security guidance.

## Website analytics and privacy

- Page views count actual page loads and refreshes. The counter starts at zero; it is not seeded with a fabricated baseline.
- A visitor is an anonymous browser-tab session, not a named person or a unique human. The browser generates a random session ID in sessionStorage; the server stores only its SHA-256 hash.
- The tracker stores the last page path, first/last seen timestamps and page-view count. It does not store visitor IP addresses, names, email addresses, full referrers or browser fingerprints.
- A session is labelled online after a heartbeat in the last 90 seconds. The site sends a heartbeat every 30 seconds while the tab is visible. Away means no recent heartbeat, not proof that the browser is closed.
- Configure a Firestore TTL policy for siteSessions.expiresAt (30 days) to remove old session records. Aggregate counts in siteStats/global are lifetime totals and remain separate.

## Metric definitions

- Registered devices are app installations, not unique human users.
- Active · 30 days means last check-in within 30 days.
- Connection success rate uses connection_succeeded and connection_failed events from the last seven days.
- PDF exports and file transfers count only successful events received in the last seven days.
- App errors count app_error and app_crash events received in the last seven days.
- FCM provider acceptance does not prove that a notification was shown or opened.
- Windows announcements are queued for compatible desktop clients; they are not delivered until a future acknowledgement mechanism exists.

Empty charts mean client telemetry has not been instrumented or no events have arrived. They must not be presented as zero usage.

## Vercel environment

Before deployment, set the Vercel project's Root Directory to website. The project serves public assets from website/public (configured as outputDirectory) and builds the single catch-all API function from website/api/index.js. Keep the website/_api and website/lib directories outside the public output.

Use a Node.js 22-or-newer runtime for firebase-admin 14.5.1. The package manifest and lockfile pin this dependency and the minimum runtime.

Configure Production (and Preview, if needed) environment variables:

- FIREBASE_SERVICE_ACCOUNT_JSON: private Firebase service-account JSON, stored as an encrypted/sensitive variable.
- FIREBASE_WEB_CONFIG: Firebase Web App config JSON (public client config, not a service-account key).
- ADMIN_EMAILS: comma-separated verified Google email addresses allowed into the console.

The API is consolidated into one catch-all Vercel function under website/api/index.js; the original handlers live under website/_api and website/vercel.json rewrites /api/* to this router. This keeps the project within the Hobby plan 12-function deployment limit.

No Firebase project or Vercel environment variables are provisioned by this repository. Create a dedicated Firebase project, register inkbridge-tool.vercel.app under Firebase Authentication > Settings > Authorized domains, enable Google Authentication, Firestore and Firebase Cloud Messaging, then configure these values before login or delivery works.

After creating Firestore, enable a 30-day TTL policy on the siteSessions collection's expiresAt field. The tracker creates siteSessions and siteStats/global automatically on first use.

## Firestore security

Deploy website/firestore.rules to deny direct client reads and writes. All application data access is mediated by Vercel APIs and Firebase Admin SDK. Deploy website/firestore.indexes.json for device inventory and notification queries.

## Client integration still required

Android and Windows clients still need to:
1. Generate and securely persist a random installation UUID and at least 32 bytes of per-installation secret.
2. Call POST /api/client/register at startup and periodically refresh lastSeen.
3. Send allowlisted events to POST /api/client/telemetry for app opens, connection success/failure, PDF exports, file transfer completion and handled errors.
4. Send user feedback to POST /api/client/feedback.
5. Windows Companion must poll POST /api/client/poll while running and acknowledge received announcements if delivery receipts are later added.
6. Android must request notification permission (Android 13+) and register an FCM token before push is available. Configure Firebase Android SDK and App Check.

Registration metadata may include device model, OS version and locale. Avoid sending machine names, contact lists, file contents, drawing data or other unnecessary personal information.

## Security

- Admin APIs verify Firebase ID tokens and require a verified email in ADMIN_EMAILS.
- Device endpoints validate per-installation secrets and store only SHA-256 hashes.
- Service-account credentials are server-only. Never commit them or place them in HTML, APK assets or Windows config.
- Add Firebase App Check enforcement and rate limiting before broad public release.
- Define a retention policy for telemetry, feedback and stale device records.
- Provide users clear opt-out for analytics and push notifications.

## Research references

- Explain Everything: mixed-media whiteboard, collaboration, projects and export options.
- OpenBoard: desktop whiteboard, library pane, screencast and dual-screen use.
- PostHog: event analytics, dashboards, error tracking, feature flags and surveys.
- Device-management consoles: searchable inventory, version distribution, device detail and audit history.

Official references:
- https://help.explaineverything.com/hc/en-us/articles/360014138733-What-is-Explain-Everything
- https://github.com/OpenBoard-org/OpenBoard/wiki
- https://posthog.com/docs/product-analytics/dashboards
- https://posthog.com/docs/product-analytics
- https://firebase.google.com/docs/cloud-messaging/understand-delivery
- https://firebase.google.com/docs/cloud-messaging/android/get-started
- https://firebase.google.com/docs/admin/setup
- https://vercel.com/docs/environment-variables
