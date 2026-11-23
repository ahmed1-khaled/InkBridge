import { firebaseServices, requireAdmin, json } from "../../lib/firebase-admin.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { error: "Method not allowed." });
  try {
    const { auth, db, messaging, FieldValue } = firebaseServices();
    const admin = await requireAdmin(req, auth);
    const { title, body, target } = req.body || {};
    if (typeof title !== "string" || !title.trim() || title.length > 80) {
      return json(res, 400, { error: "Title is required and must be 80 characters or fewer." });
    }
    if (typeof body !== "string" || !body.trim() || body.length > 500) {
      return json(res, 400, { error: "Message is required and must be 500 characters or fewer." });
    }
    if (!["all", "android", "windows"].includes(target)) {
      return json(res, 400, { error: "Choose all, Android, or Windows." });
    }

    const announcement = await db.collection("announcements").add({
      title: title.trim(),
      body: body.trim(),
      target,
      createdAt: FieldValue.serverTimestamp(),
      createdBy: admin.email,
    });

    let sent = 0;
    let failed = 0;
    if (target === "all" || target === "android") {
      const devices = await db.collection("devices")
        .where("platform", "==", "android")
        .where("notificationsEnabled", "==", true)
        .get();
      const recipients = devices.docs.filter(doc => typeof doc.data().fcmToken === "string" && doc.data().fcmToken.length > 20);
      for (let offset = 0; offset < recipients.length; offset += 500) {
        const batch = recipients.slice(offset, offset + 500);
        const messages = batch.map(doc => ({
          token: doc.data().fcmToken,
          notification: { title: title.trim(), body: body.trim() },
          data: { announcementId: announcement.id, target },
        }));
        const result = await messaging.sendEach(messages);
        sent += result.successCount;
        failed += result.failureCount;
        const cleanup = db.batch();
        result.responses.forEach((response, index) => {
          const code = response.error?.code || "";
          if (code.includes("registration-token-not-registered") || code.includes("invalid-registration-token")) {
            cleanup.update(batch[index].ref, { fcmToken: FieldValue.delete(), notificationsEnabled: false });
          }
        });
        await cleanup.commit();
      }
    }

    const windowsSnapshot = (target === "all" || target === "windows")
      ? await db.collection("devices").where("platform", "==", "windows").get()
      : null;

    return json(res, 200, {
      success: true,
      announcementId: announcement.id,
      androidAccepted: sent,
      androidFailed: failed,
      windowsQueued: windowsSnapshot?.size || 0,
      windowsDelivery: "Queued for Windows installations after the Companion polling client is installed."
    });
  } catch (error) {
    return json(res, error.status || 500, { error: error.message || "Notification could not be sent." });
  }
}
