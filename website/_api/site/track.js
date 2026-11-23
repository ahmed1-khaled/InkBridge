import { createHash } from "node:crypto";
import { firebaseServices, json } from "../../lib/firebase-admin.js";

const hash = value => createHash("sha256").update(value, "utf8").digest("hex");

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { error: "Method not allowed." });
  const { sessionId, path: pagePath, kind } = req.body || {};
  if (typeof sessionId !== "string" || !/^[a-f0-9-]{36}$/i.test(sessionId)) return json(res, 400, { error: "A valid anonymous session ID is required." });
  if (typeof pagePath !== "string" || !/^\/[A-Za-z0-9/_-]{0,159}$/.test(pagePath)) return json(res, 400, { error: "Invalid page path." });
  if (!["view", "heartbeat"].includes(kind)) return json(res, 400, { error: "Unsupported tracking event." });
  try {
    const { db, FieldValue } = firebaseServices();
    const sessionRef = db.collection("siteSessions").doc(hash(sessionId));
    const statsRef = db.collection("siteStats").doc("global");
    await db.runTransaction(async transaction => {
      const existing = await transaction.get(sessionRef);
      const now = FieldValue.serverTimestamp();
      const sessionData = {
        firstSeen: existing.exists ? (existing.data().firstSeen || now) : now,
        lastSeen: now, lastPath: pagePath,
        expiresAt: new Date(Date.now() + 30 * 86400000),
        pageViews: kind === "view" ? FieldValue.increment(1) : (existing.exists ? (existing.data().pageViews || 0) : 0)
      };
      transaction.set(sessionRef, sessionData, { merge: true });
      const counters = { updatedAt: now };
      if (kind === "view") counters.pageViews = FieldValue.increment(1);
      if (!existing.exists) counters.sessions = FieldValue.increment(1);
      transaction.set(statsRef, counters, { merge: true });
    });
    const stats = await statsRef.get();
    return json(res, 202, { tracked: true, pageViews: stats.data()?.pageViews || 0, sessions: stats.data()?.sessions || 0 });
  } catch (error) {
    return json(res, error.status || 503, { error: error.status ? error.message : "Site analytics are not configured." });
  }
}
