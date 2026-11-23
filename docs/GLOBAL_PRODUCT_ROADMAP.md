# InkBridge Global Product Roadmap

This roadmap is intentionally local-first. A cloud database should extend the product, not become a dependency for a basic tablet-to-PC writing session.

## Product principles

- Keep live pen input on the fastest available path: direct LAN first, optional secure internet relay later.
- Never require an account just to write locally.
- Store notebooks as durable user-owned documents, not as opaque cloud-only records.
- Make sync, backup and remote access explicit opt-ins.
- Treat pairing codes as a convenience gate, not as encryption.

## Phase 0 — Make the current core dependable

1. Ship the detached Windows live-preview window and verify its full minimize/maximize/close lifecycle.
2. Make connection onboarding reliable: recommended Wi-Fi IPv4 address, copy/QR pairing, explicit firewall setup, useful error stages, and a one-tap reconnect.
3. Add network discovery on the LAN (mDNS/NSD) so users can select a laptop instead of typing an IP.
4. Add a connection diagnostics screen: network name/type, selected interface, reachable host, TCP port status, pairing result and protocol version. Do not expose the session code in logs.
5. Make file transfers robust: progress, per-chunk acknowledgements, retry, resume, SHA-256 verification, duplicate-name handling, cancellation and cleanup of partial files.
6. Add protocol capability negotiation and backwards-compatible versioning.
7. Run a real-device test matrix for Windows 10/11, Android phones/tablets, Wi-Fi routers, personal hotspots, guest networks, sleep/resume and Windows Firewall profiles.

## Phase 1 — Local-first document storage

### Android
- Use Room (SQLite) for notebooks, pages, document metadata, tool preferences, recent files and recovery state.
- Keep large PDFs and image assets in app-managed document storage; store their paths/URIs and hashes in Room.
- Persist strokes as structured vector data with pressure, color, width and tool type. Do not store every point as a separate relational row unless profiling proves it is needed; use compact document files or batched records.
- Add autosave, crash recovery, document version snapshots and user-controlled export.

### Windows
- Use SQLite for preferences, known devices, recent transfers and session history.
- Keep received files in user-selected folders. Do not silently upload documents.
- Use a shared document format and a versioned import/export layer so Android and Windows can exchange editable notes later.

### Why no cloud database yet?
A local TCP session does not need a database. Putting live pen points into Postgres or a cloud database would add latency, cost and privacy risk without improving local writing.

## Phase 2 — Optional accounts, backup and sync

A practical MVP stack is Supabase:
- **Auth:** email/password plus Google sign-in; add passkeys later.
- **Postgres:** accounts, devices, workspaces, document metadata, sharing permissions, sync versions and billing entitlements. Keep row-level security enabled on every user-owned table.
- **Object Storage:** PDFs, images, thumbnails and encrypted backup bundles. Use short-lived signed URLs and upload limits.
- **Edge Functions:** invitation links, device authorization, share-link creation and scheduled cleanup.
- **Realtime:** presence and document-change notifications, not a substitute for the LAN drawing stream.

Suggested relational entities:
- profiles
- devices
- workspaces
- workspace_members
- documents
- document_versions
- document_assets
- sync_cursors
- share_links
- transfer_history
- subscription_entitlements

Sync design:
- Every document has a stable ID, owner/workspace ID, revision number, updated timestamp and content hash.
- Upload a new immutable version, then atomically advance the document's current revision.
- Download deltas where possible; use full snapshots for recovery.
- Detect simultaneous edits. Start with explicit conflict copies; only adopt CRDT/OT when true multi-user co-editing is a committed feature.
- Offer user-controlled retention, export and account deletion.

Global deployment:
- Select the primary database region based on the first real user base, not guesswork.
- Add backups, point-in-time recovery, storage lifecycle policies and a tested restore procedure.
- Measure latency from supported regions before adding replicas. Do not add multi-region write complexity prematurely.
- Keep metadata and document payloads separate so storage can move between providers without redesigning the app.

## Phase 3 — Secure remote connection

Current InkBridge is LAN-only. Do not expose TCP 45832 directly to the public internet.

For remote sessions:
1. Add authenticated device identity and short-lived pairing grants.
2. Prefer WebRTC DataChannels for direct peer-to-peer transport.
3. Use STUN for NAT discovery and a TURN relay only when direct connectivity fails.
4. Encrypt sessions end-to-end; the relay should not be able to read ink or files.
5. Keep remote access opt-in, revocable per device, rate-limited and visible in the UI.
6. Keep LAN mode working without sign-in or cloud availability.

## Phase 4 — Professional writing and teaching workflow

- Pressure and tilt calibration, stylus hover, palm rejection and configurable pen buttons.
- Zoom, pan, lasso selection, shapes, text, image insertion and layers.
- Editable PDF annotations and export that preserves vector strokes where practical.
- Notebook library, page thumbnails, templates, tags, search and favorites.
- Floating/dockable toolbars, portrait/landscape layouts, keyboard shortcuts and presentation mode.
- A true second-monitor mode only if virtual-display drivers and OS support are implemented; the current live page preview is not a virtual monitor.
- Undo history and autosave across multiple pages and documents.
- Optional audio recording synced with notes for lectures.

## Phase 5 — Global release quality

- Google Play distribution, signed Android releases and in-app update verification.
- Signed Windows installer, code signing certificate, automatic updates, winget and Microsoft Store packaging.
- Arabic/English first, then a translation workflow for additional languages; complete RTL, dates, number formats and accessibility labels.
- Accessibility: keyboard navigation, screen-reader labels, high-contrast mode, scalable UI and reduced-motion support.
- Privacy policy, terms, security contact, data export/delete controls and clear retention rules.
- Opt-in crash reporting and diagnostics with document contents and pairing codes excluded.
- Public compatibility matrix, release notes, support channel, bug-report template and reproducible release checks.

## Recommended order

1. Stabilize LAN pairing and detached preview.
2. Complete reliable file transfer and local document persistence.
3. Add optional account + backup/sync with Supabase.
4. Add secure remote sessions.
5. Expand collaboration, advanced pen features and platform distribution.

This order avoids paying for cloud infrastructure before the core writing experience is trustworthy.
