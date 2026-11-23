import { json } from "../lib/firebase-admin.js";
import route_config from "../_api/config.js";
import route_admin_activity from "../_api/admin/activity.js";
import route_admin_announcements from "../_api/admin/announcements.js";
import route_admin_devices from "../_api/admin/devices.js";
import route_admin_feedback from "../_api/admin/feedback.js";
import route_admin_flags from "../_api/admin/flags.js";
import route_admin_health from "../_api/admin/health.js";
import route_admin_notify from "../_api/admin/notify.js";
import route_admin_overview from "../_api/admin/overview.js";
import route_admin_release from "../_api/admin/release.js";
import route_admin_visitors from "../_api/admin/visitors.js";
import route_client_config from "../_api/client/config.js";
import route_client_feedback from "../_api/client/feedback.js";
import route_client_poll from "../_api/client/poll.js";
import route_client_register from "../_api/client/register.js";
import route_client_telemetry from "../_api/client/telemetry.js";
import route_site_track from "../_api/site/track.js";

const routes = new Map([
  ["config", route_config],
  ["admin/activity", route_admin_activity],
  ["admin/announcements", route_admin_announcements],
  ["admin/devices", route_admin_devices],
  ["admin/feedback", route_admin_feedback],
  ["admin/flags", route_admin_flags],
  ["admin/health", route_admin_health],
  ["admin/notify", route_admin_notify],
  ["admin/overview", route_admin_overview],
  ["admin/release", route_admin_release],
  ["admin/visitors", route_admin_visitors],
  ["client/config", route_client_config],
  ["client/feedback", route_client_feedback],
  ["client/poll", route_client_poll],
  ["client/register", route_client_register],
  ["client/telemetry", route_client_telemetry],
  ["site/track", route_site_track],
]);

export default async function handler(req, res) {
  const rewrittenPath = req.query?.path ?? new URL(req.url || "/", "https://inkbridge.local").searchParams.get("path");
  const route = Array.isArray(rewrittenPath) ? rewrittenPath.join("/") : String(rewrittenPath || "").replace(/^\/+|\/+$/g, "");
  const selected = routes.get(route);
  if (!selected) return json(res, 404, { error: "API route not found." });
  try { return await selected(req, res); }
  catch { return json(res, 500, { error: "The requested API service failed." }); }
}
