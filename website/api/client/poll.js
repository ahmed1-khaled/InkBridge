import { createHash, timingSafeEqual } from "node:crypto";
import { firebaseServices, json } from "../../lib/firebase-admin.js";

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
    const { deviceId, deviceSecret, since } = req.body || {};
    if (typeof deviceId !== "string" || typeof deviceSecret !== "string") return json(res, 400, { error: "Device credentials are required." });
    const ref = db.collection("devices").doc(deviceId);
    const device = await ref.get();
    if (!device.exists || device.data().platform !== "windows" || !safeEqual(device.data().secretHash, hash(deviceSecret))) {
      return json(res, 401, { error: "Unknown or unauthorized Windows installation." });
    }

    const sinceTime = Number.isFinite(Date.parse(since)) ? Date.parse(since) : Date.now() - 24 * 60 * 60 * 1000;
    const snapshot = await db.collection("announcements").orderBy("createdAt", "desc").limit(60).get();
    const notifications = snapshot.docs
      .map(doc => ({ id: doc.id, ...doc.data() }))
      .filter(item => {
        const created = item.createdAt?.toDate?.()?.getTime?.() ?? 0;
        return created > sinceTime && (item.target === "all" || item.target === "windows");
      })
      .sort((a, b) => a.createdAt.toMillis() - b.createdAt.toMillis())
      .map(item => ({
        id: item.id,
        title: item.title,
        body: item.body,
        createdAt: item.createdAt.toDate().toISOString(),
      }));

    await ref.update({ lastSeen: FieldValue.serverTimestamp() });
    return json(res, 200, { notifications, serverTime: new Date().toISOString() });
  } catch (error) {
    return json(res, error.status || 500, { error: error.message || "Notification polling failed." });
  }
}
