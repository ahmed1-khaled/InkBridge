import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";

export default async function handler(req, res) {
  if (req.method !== "GET") return json(res, 405, { error: "Method not allowed." });
  try {
    const webConfigured = Boolean(process.env.FIREBASE_WEB_CONFIG);
    const { auth, db } = firebaseServices();
    await requireAdmin(req, auth);
    const probe = await db.collection("devices").limit(1).get();
    return json(res, 200, {
      overall: "operational",
      services: {
        firebaseAdmin: "connected",
        firestore: "connected",
        firebaseWeb: webConfigured ? "configured" : "missing",
        androidPush: "awaiting_android_fcm_tokens",
        windowsNotifications: "awaiting_companion_polling_client"
      },
      checkedAt: new Date().toISOString(),
      registeredDeviceCollectionReachable: true,
      note: "A successful Firestore probe confirms server access only. Push and desktop delivery still depend on client registration and instrumentation."
    });
  } catch (error) {
    const status = error.status || 500;
    return json(res, status, {
      overall: status === 503 ? "configuration_required" : "degraded",
      error: status === 401 || status === 403 ? error.message : "A required backend service could not be reached.",
      checkedAt: new Date().toISOString()
    });
  }
}