import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";

export default async function handler(req, res) {
  if (req.method !== "GET") return json(res, 405, { error: "Method not allowed." });
  try {
    const { auth, db } = firebaseServices();
    await requireAdmin(req, auth);
    const snapshot = await db.collection("devices").get();
    const cutoff = Date.now() - 30 * 24 * 60 * 60 * 1000;
    let android = 0, windows = 0, active30d = 0, pushEnabled = 0;
    const versions = {};
    for (const doc of snapshot.docs) {
      const device = doc.data();
      if (device.platform === "android") android++;
      if (device.platform === "windows") windows++;
      const lastSeen = device.lastSeen?.toDate?.()?.getTime?.() ?? 0;
      if (lastSeen >= cutoff) active30d++;
      if (device.notificationsEnabled && device.fcmToken) pushEnabled++;
      if (device.appVersion) versions[device.appVersion] = (versions[device.appVersion] || 0) + 1;
    }
    return json(res, 200, {
      registeredDevices: snapshot.size,
      activeDevices30d: active30d,
      androidDevices: android,
      windowsDevices: windows,
      androidPushOptIns: pushEnabled,
      versions,
      measuredAt: new Date().toISOString(),
      note: "These are registered installations/devices, not verified human accounts."
    });
  } catch (error) {
    return json(res, error.status || 500, { error: error.message || "Could not load device analytics." });
  }
}
