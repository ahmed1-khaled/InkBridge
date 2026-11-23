import { json } from "./_lib/firebase.js";

export default function handler(req, res) {
  if (req.method !== "GET") return json(res, 405, { error: "Method not allowed." });
  try {
    const config = process.env.FIREBASE_WEB_CONFIG
      ? JSON.parse(process.env.FIREBASE_WEB_CONFIG)
      : null;
    if (!config?.apiKey || !config?.projectId || !config?.appId) {
      return json(res, 503, { configured: false, error: "Firebase web configuration is not set yet." });
    }
    return json(res, 200, { configured: true, config });
  } catch {
    return json(res, 503, { configured: false, error: "FIREBASE_WEB_CONFIG must be valid JSON." });
  }
}
