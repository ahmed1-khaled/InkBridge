import { createHash, timingSafeEqual } from "node:crypto";
import { firebaseServices, json } from "../_lib/firebase.js";

const hash = value => createHash("sha256").update(value, "utf8").digest("hex");
const safeEqual = (a, b) => {
  const left = Buffer.from(a || "");
  const right = Buffer.from(b || "");
  return left.length === right.length && timingSafeEqual(left, right);
};

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { error: "Method not allowed." });
  try {
    const { db, FieldValue } = firebaseServices();
    const { deviceId, deviceSecret, platform, appVersion, fcmToken, notificationsEnabled } = req.body || {};
    if (typeof deviceId !== "string" || !/^[a-f0-9-]{36}$/i.test(deviceId)) return json(res, 400, { error: "Invalid device ID." });
    if (typeof deviceSecret !== "string" || deviceSecret.length < 32) return json(res, 400, { error: "Invalid device secret." });
    if (!["android", "windows"].includes(platform)) return json(res, 400, { error: "Unsupported platform." });
    if (fcmToken && (platform !== "android" || typeof fcmToken !== "string" || fcmToken.length > 4096)) return json(res, 400, { error: "Invalid push token." });

    const ref = db.collection("devices").doc(deviceId);
    const existing = await ref.get();
    const secretHash = hash(deviceSecret);
    if (existing.exists && !safeEqual(existing.data().secretHash, secretHash)) {
      return json(res, 401, { error: "This installation ID is already registered to another device." });
    }
    await ref.set({
      platform,
      appVersion: typeof appVersion === "string" ? appVersion.slice(0, 32) : "unknown",
      fcmToken: fcmToken || existing.data()?.fcmToken || null,
      notificationsEnabled: platform === "android" ? Boolean(notificationsEnabled && (fcmToken || existing.data()?.fcmToken)) : true,
      secretHash,
      lastSeen: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: existing.data()?.createdAt || FieldValue.serverTimestamp(),
    }, { merge: true });
    return json(res, 200, { success: true, registered: true });
  } catch (error) {
    return json(res, error.status || 500, { error: error.message || "Device registration failed." });
  }
}
