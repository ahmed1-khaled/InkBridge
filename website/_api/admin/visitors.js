import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";

export default async function handler(req, res) {
  if (req.method !== "GET") return json(res, 405, { error: "Method not allowed." });
  try {
    const { auth, db } = firebaseServices();
    await requireAdmin(req, auth);
    const now = Date.now();
    const dayAgo = new Date(now - 86400000);
    const onlineCutoff = now - 90000;
    const [statsSnapshot, sessionsSnapshot] = await Promise.all([
      db.collection("siteStats").doc("global").get(),
      db.collection("siteSessions").where("lastSeen", ">=", dayAgo).orderBy("lastSeen", "desc").limit(500).get()
    ]);
    const stats = statsSnapshot.data() || {};
    const sessions = sessionsSnapshot.docs.map(doc => {
      const d = doc.data();
      const lastSeenMs = d.lastSeen?.toDate?.()?.getTime?.() || 0;
      return {
        id: "Visitor " + doc.id.slice(0, 8), path: d.lastPath || "/",
        lastSeen: d.lastSeen?.toDate?.()?.toISOString?.() || null,
        pageViews: Number(d.pageViews || 0),
        status: lastSeenMs >= onlineCutoff ? "online" : "away"
      };
    });
    const onlineVisitors = sessions.filter(session => session.status === "online").length;
    return json(res, 200, {
      pageViews: Number(stats.pageViews || 0), totalSessions: Number(stats.sessions || 0),
      onlineVisitors, awayVisitors: sessions.length - onlineVisitors,
      sessionsLast24Hours: sessions.length, truncated: sessions.length === 500,
      measuredAt: new Date().toISOString(), sessions
    });
  } catch (error) {
    return json(res, error.status || 500, { error: error.message || "Could not load website visitor activity." });
  }
}
