import { cert, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

export function firebaseServices() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    const error = new Error("Firebase server credentials are not configured.");
    error.status = 503;
    throw error;
  }

  let serviceAccount;
  try {
    serviceAccount = JSON.parse(raw);
  } catch {
    const error = new Error("FIREBASE_SERVICE_ACCOUNT_JSON must contain valid JSON.");
    error.status = 503;
    throw error;
  }

  const app = getApps()[0] ?? initializeApp({
    credential: cert(serviceAccount),
    projectId: serviceAccount.project_id,
  });

  return {
    app,
    auth: getAuth(app),
    db: getFirestore(app),
    messaging: getMessaging(app),
    FieldValue,
  };
}

export async function requireAdmin(req, auth) {
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) {
    const error = new Error("Sign in with an authorized administrator account.");
    error.status = 401;
    throw error;
  }

  const decoded = await auth.verifyIdToken(token);
  const allowed = new Set(
    (process.env.ADMIN_EMAILS || "")
      .split(",")
      .map(value => value.trim().toLowerCase())
      .filter(Boolean)
  );

  if (!decoded.email || !decoded.email_verified || !allowed.has(decoded.email.toLowerCase())) {
    const error = new Error("This account is not allowed to access the InkBridge admin console.");
    error.status = 403;
    throw error;
  }
  return decoded;
}

export function json(res, status, payload) {
  res.status(status).setHeader("Cache-Control", "no-store");
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.end(JSON.stringify(payload));
}
