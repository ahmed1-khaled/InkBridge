import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";

export default async function handler(req, res) {
  if (req.method !== "GET") return json(res, 405, { error: "Method not allowed." });
  try {
    const { auth, db } = firebaseServices();
    await requireAdmin(req, auth);
    const [devices, events, announcements, feedback] = await Promise.all([
      db.collection("devices").get(),
      db.collection("events").orderBy("createdAt", "desc").limit(1000).get(),
      db.collection("announcements").orderBy("createdAt", "desc").limit(8).get(),
      db.collection("feedback").orderBy("createdAt", "desc").limit(8).get()
    ]);

    const now = Date.now();
    const activeCutoff = now - 30 * 86400000;
    const weekCutoff = now - 7 * 86400000;
    let android = 0, windows = 0, active30 = 0, push = 0;
    const versions = {};
    for (const doc of devices.docs) {
      const d = doc.data();
      if (d.platform === "android") android++;
      if (d.platform === "windows") windows++;
      const seen = d.lastSeen?.toDate?.()?.getTime?.() || 0;
      if (seen >= activeCutoff) active30++;
      if (d.platform === "android" && d.notificationsEnabled && d.fcmToken) push++;
      const version = String(d.appVersion || "unknown").slice(0, 32);
      versions[version] = (versions[version] || 0) + 1;
    }

    const utcToday = new Date();
    utcToday.setUTCHours(0, 0, 0, 0);
    const days = Array.from({ length: 7 }, (_, index) => {
      const date = new Date(utcToday);
      date.setUTCDate(date.getUTCDate() - (6 - index));
      return { date: date.toISOString().slice(0, 10), label: date.toLocaleDateString("en", { weekday: "short", timeZone: "UTC" }), events: 0 };
    });
    const dayMap = new Map(days.map(day => [day.date, day]));
    const eventCounts = {};
    const recentEvents = [];
    let weekEvents = 0, errors7d = 0, pdfExports7d = 0, fileTransfers7d = 0;
    let connectionSuccess7d = 0, connectionFailed7d = 0;

    for (const doc of events.docs) {
      const event = doc.data();
      const timestamp = event.createdAt?.toDate?.()?.getTime?.() || 0;
      if (recentEvents.length < 12) {
        recentEvents.push({
          id: doc.id,
          event: event.event || "event",
          platform: event.platform || "unknown",
          appVersion: event.appVersion || "unknown",
          createdAt: event.createdAt?.toDate?.()?.toISOString?.() || null,
          deviceId: event.deviceId || null
        });
      }
      if (timestamp < weekCutoff || timestamp > now) continue;
      weekEvents++;
      const name = String(event.event || "event");
      eventCounts[name] = (eventCounts[name] || 0) + 1;
      const day = dayMap.get(new Date(timestamp).toISOString().slice(0, 10));
      if (day) day.events++;
      if (name === "app_error" || name === "app_crash") errors7d++;
      if (name === "pdf_exported") pdfExports7d++;
      if (name === "file_transfer_completed") fileTransfers7d++;
      if (name === "connection_succeeded") connectionSuccess7d++;
      if (name === "connection_failed") connectionFailed7d++;
    }

    const recentAnnouncements = announcements.docs.map(doc => {
      const item = doc.data();
      return {
        id: doc.id, title: item.title || "", body: item.body || "", target: item.target || "all",
        createdAt: item.createdAt?.toDate?.()?.toISOString?.() || null,
        androidAccepted: item.androidAccepted ?? null, androidFailed: item.androidFailed ?? null,
        windowsQueued: item.windowsQueued ?? null
      };
    });
    const recentFeedback = feedback.docs.map(doc => {
      const item = doc.data();
      return {
        id: doc.id, category: item.category || "other", message: (item.message || "").slice(0, 180),
        platform: item.platform || "unknown", status: item.status || "open",
        createdAt: item.createdAt?.toDate?.()?.toISOString?.() || null
      };
    });
    const connectionAttempts = connectionSuccess7d + connectionFailed7d;
    const topEvents7d = Object.entries(eventCounts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 6)
      .map(([event, count]) => ({ event, count }));

    return json(res, 200, {
      registeredDevices: devices.size, activeDevices30d: active30,
      androidDevices: android, windowsDevices: windows, androidPushOptIns: push,
      versions, telemetryEvents7d: weekEvents, errors7d, pdfExports7d, fileTransfers7d,
      connectionAttempts7d: connectionAttempts,
      connectionSuccessRate7d: connectionAttempts ? Math.round(connectionSuccess7d / connectionAttempts * 100) : null,
      eventsByType: eventCounts, topEvents7d, activityByDay: days,
      recentEvents, recentAnnouncements, recentFeedback,
      measuredAt: new Date().toISOString(),
      instrumentationStatus: events.size ? "receiving" : "waiting_for_client_instrumentation",
      note: "Installation counts are not unique human users. Analytics include only registered devices that have sent telemetry."
    });
  } catch (error) {
    return json(res, error.status || 500, { error: error.message || "Could not load product analytics." });
  }
}