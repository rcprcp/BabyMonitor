# BabyMonitor — Roadmap

A working list of what needs fixing, hardening or building to make this a
world-class baby monitor. Ordered by how much it matters, not by effort.

The bar for "world-class" here is a specific one: **a parent must be able to
trust it while asleep.** Everything below is judged against that — a feature
that is nice to have ranks below anything that can fail silently, lose a clip,
or leave a nursery unmonitored without anyone noticing.

---

## P0 — Silent failure and trust

These are the ones where the app looks fine while being wrong.

- [x] **Supervise the `motion` processes.** `MotionSupervisor` now checks every
      camera whose persisted state says running, restarts a dead process with
      exponential backoff (15 s → 5 min) and records a throttled `System`
      notification. The pid-reuse caveat is tracked separately below.
- [ ] **Harden Motion pid identity.** `MotionProcessManager.pidOf` still falls
      back to the pid file and only checks that the pid is alive, so a reused pid
      can make a dead camera look alive. Verify the process's command line (or
      config path) before treating it as ours.
- [ ] **Detect a frozen stream.** MJPEG in an `<img>`
      (`frontend/src/components/StreamPlayer.jsx`) can stop updating without
      firing `onError`, so the parent sees a still image that *looks* live. The
      `<img>` retries on an error, but a silent stall still looks live: show a
      last-frame timestamp / stall indicator and reconnect on it. "Looks live but
      is frozen" is the worst possible failure here.
- [~] **Reconnect the stream instead of dying.** MJPEG now retries with bounded
      backoff (0/1/2/3/5 s) before showing the error, but the HLS path still has
      no retry and neither path has a manual Retry button
      (`StreamPlayer.jsx`). Add the HLS retry and a Retry control.
- [x] **Environment threshold alerting.** The Environment page stores high/low
      bounds for humidity (0-100%, default 30-75), temperature (32-100°F, default
      66-76) and brightness (0-3000 lux, default 200-2000); readings outside them
      are red in the table. `EnvironmentSupervisor` checks the newest reading
      every 60 s and records an `Environment` notification (toast + feed) when a
      value is outside its bounds.
- [x] **Fix the failing test.** `ApiTest.cameraReturnsItsParametersInNameOrder`
      used to assert seeded values (`width=1280`) that the UI is designed to
      change, so it failed against any database a user had used. It now checks
      the invariants (camera number, name ordering, the Motion option names are
      present, the rows share one enabled flag) instead of live, user-editable
      values, so `mvn package` passes against an edited database.
- [x] **Alert on Video/motion events too.** Every non-`System` event now plays
      the chime and shows a toast, so a motion/clip alert is no longer silent
      (`useNotificationAlerts.js`, `alerting.js`).

## P1 — Security

- [~] **Serve over TLS.** Everything is plain HTTP: webhooks, the SPA, and the
      Motion streams. `DEVOPS.md` now has an nginx terminator recipe, but the
      MJPEG ports still need their own TLS or a relay: an HTTPS page blocks the
      `http` feed as mixed content (`StreamPage.jsx` builds the URL directly from
      `stream_url`). Relaying video through the authenticated app is still open.
- [ ] **The stream ports are unauthenticated and on the network.**
      `stream_localhost off` / `webcontrol_localhost off` (seed `V2`) expose
      8081/8091 to the LAN, bypassing the bearer gate entirely — including
      Motion's **web control** interface. Default them to localhost and relay
      through the authenticated app instead.
- [ ] **Rate-limit login.** `/api/auth/login` has no throttling or lockout
      (`AuthResource.java`), so bcrypt is the only thing slowing a brute force.
- [ ] **Kill the default credentials.** DB defaults to `bob/bob/bob` and the
      seeded accounts use `password == username` (`V2__seed_data.sql`). Fine for a
      demo, unacceptable for a real deployment. Add a first-run setup step that
      forces a real admin password, and refuse to start on the sample accounts
      outside dev.
- [ ] **Require the webhook secret by default.** With
      `babymonitor.webhooks.secret` unset, *anyone* who can reach the host can
      inject notifications (`WebhookResource.java`). Make it mandatory (or bind
      webhooks to localhost) rather than opt-in.
- [ ] **Get the SSE token out of the URL.** The notification stream accepts
      `?token=` because `EventSource` cannot set headers (`AuthFilter.java`).
      Tokens in query strings land in access logs and `Referer`. Issue a
      short-lived stream token, or use a cookie for that one endpoint.
- [ ] **Turn off Swagger UI in production.** `quarkus.swagger-ui.always-include=true`
      (`application.properties`) advertises the full API surface on any
      deployment.
- [ ] **Add a `SECURITY.md` and a short threat model.** Right now the security
      posture lives only in README prose.

## P2 — Reliability and operations

- [x] **Add CI.** `.github/workflows/ci.yml` runs the backend suite and the
      frontend tests/lint/format check/build on every push and pull request
      (T11 in `USABILITY.md`).
- [~] **Package it.** `DEVOPS.md` documents a systemd unit, the state
      directories, PostgreSQL, Motion, SoX, upgrades and troubleshooting. There
      is still no `Dockerfile` or compose file, and no packaged release artifact.
- [x] **Health and readiness endpoints.** `quarkus-smallrye-health` is in the
      build and `/q/health` (liveness + readiness, with a database check) is
      available. Camera-specific trouble is surfaced on the Camera page/Motion
      status, not the readiness probe.
- [ ] **Metrics and structured logging.** No metrics at all; logging is ad-hoc
      `org.jboss.logging` in two classes (`MotionService`,
      `MotionProcessManager`). Add Micrometer/Prometheus and JSON logs with
      request correlation.
- [x] **Introduce real migrations.** Flyway owns the schema: the migrations in
      `src/main/resources/db/migration` (V1-V13) run at startup, with the baseline
      path for pre-Flyway databases documented. `db/init.sql` is gone.
- [ ] **Move the session store out of memory.** Tokens vanish on restart, so
      every redeploy logs everyone out (`SessionStore.java`). Move to the DB or
      Redis, add rotation, and support more than one instance.
- [x] **Backups and restore.** `DEVOPS.md` documents `pg_dump`/`pg_restore`, the
      Motion recording directory, and the pre-upgrade snapshot. Automatic
      rotation is still left to the operator.
- [ ] **Move `start()` out of the request transaction.** `CamerasResource` is
      class-level `@Transactional`, so `MotionProcessManager.start()` spawns a
      process before the request commits. Harmless today, fragile later.

## P3 — Data lifecycle

24/7 recording fills disks, and nothing here prunes or warns.

- [ ] **Recording retention and quotas.** No age limit, no size cap, no
      per-camera quota, no cleanup, and no disk-space check before Motion writes
      (`RecordingsService`). Add a retention policy and a low-disk warning —
      a full disk silently stops recording.
- [ ] **Paginate the recordings listing.** `RecordingsService.list()` reads and
      materialises the entire directory on every call.
- [ ] **Prune notifications and dismissals.** Neither is ever deleted (only
      per-account dismissal rows are written). Add a retention rule.
- [ ] **Prune environment readings.** The table grows unbounded (response
      `limit` only caps reads).

## P4 — The features a world-class baby monitor needs

- [~] **Camera-derived brightness.** The Environment page has a UVC camera
      light sensor: pick a running camera, capture a luma for each of six numbered
      bands (1 = dark .. 6 = very bright), then enable it. A probe runs every
      minute, is kept as history, and raises an `Environment` alert when the
      band's proxy lux is outside the brightness bounds; the chart and table show
      the hourly band.
      It is UVC/V4L2-specific (manual exposure + the camera's MJPEG stream) and
      needs the camera running; ONVIF and libcamera need capability adapters and
      their own calibration before this is truly plug-and-play.
- [x] **Audio.** The HLS player was hardcoded `muted` and MJPEG carried no
      audio, so you could watch your baby but not *hear* them. The Live stream
      page now has a Listen control and volume slider: the selected camera's
      captured PCM is streamed to the browser and played through the Web Audio
      API, with a hybrid level meter (server RMS when idle, analyser while
      listening).
- [ ] **Push notifications.** Alerts only exist inside an open, authenticated
      tab; a backgrounded phone gets nothing (`useNotificationAlerts.js`). Add
      Web Push (VAPID) with a service worker, plus OS notifications and
      vibration. This is table stakes for a monitor.
- [ ] **Make it a PWA.** No manifest, no service worker, no icons, and
      `theme-color` is a hardcoded blue (`frontend/index.html`). It cannot be
      installed to a home screen. Add manifest + icons + offline shell, then
      test it properly on a phone.
- [ ] **Two-way audio / talkback.** Nothing uses `getUserMedia`. Being able to
      soothe a baby from the phone is a headline feature.
- [ ] **Quiet hours and schedules.** Alerts fire 24/7 with no way to silence
      them for a nap or a night shift, and no way to arm only at night.
- [x] **Cry / sound detection.** `AudioService` captures an ALSA device,
      `YamNetClassifier` scores it and `CryDetector` raises `Audio`
      notifications (see *Audio and cry detection* in the README). One
      **Monitor this camera** switch drives the camera and its audio; **Listen
      for cries** is a per-camera preference in `camera_audio.enabled` (with a
      per-camera device falling back to `babymonitor.audio.device`), boot forces
      both back on, and `AudioSupervisor` restarts a dead capture with backoff
      and a notification while the camera is meant to run.
      Which classes may alert is chosen with presets (or per class under
      Custom) on the Audio notifications page, stored in `audio_alert_classes`.
- [ ] **Snapshots and a timeline.** There is no thumbnail gallery, no scrub
      across a night, and no "jump to the moment" from a notification.
- [ ] **Roles and caregiver accounts.** All accounts are equal; there is no
      owner/member split, no audit of who changed what (e.g. who switched a
      camera off), and no session/activity log.
- [ ] **Onboarding.** First run lands on a running Live stream of the two seeded
      demo cameras, whose `video_device` values almost certainly are not the
      host's cameras, with no guidance on naming them, pointing them at a device
      or calibrating the light sensor (`USABILITY.md` T10).

## P5 — Frontend experience

- [x] **Fix the SSE reconnect gap.** `stream.onerror` used to close the stream
      and clear the ref without scheduling a reconnect
      (`useNotificationAlerts.js`), so push silently degraded to polling until a
      visibility/online event. It now reconnects with bounded backoff (1 s → 30 s)
      and catches up with `lastId`.
- [x] **Alert sound preference is global, not per-account.** The opt-out flag
      (`ENABLED_KEY`) was removed; alerts are unconditional, so there is no
      per-account flag left to namespace.
- [ ] **Add a global error boundary.** `components/DebugWrapper.jsx` is a
      render-crash boundary that is imported nowhere, so one render error blanks
      the app.
- [ ] **Add fetch timeouts and cancellation.** `frontend/src/api.js` uses bare
      `fetch` with no `AbortController`, so a hung backend hangs the UI.
- [ ] **Parse status before JSON.** `api.js` calls `JSON.parse` before checking
      `response.ok`, so an HTML error page throws `SyntaxError` and hides the
      real status.
- [ ] **Give every error a retry.** Only the Alerts page has a Refresh button;
      everywhere else an error is a dead end.
- [x] **Harden the title bar for phones.** The Test alert and Play sound buttons
      were removed; the bar is title/avatar (navigation moved to a sidebar on
      desktop and a tab bar on phones) and the title ellipsises (`.bm-title`).
      Safe-area handling remains in the separate item below.
- [ ] **Handle `theme-color` and safe areas.** Static `theme-color` that ignores
      the chosen palette, and no `viewport-fit=cover` / `env(safe-area-inset-*)`
      for notched phones.
- [x] **Catch dismissal failures.** `NotificationsPage` now catches `dismiss`
      and **Clear my notifications** errors and shows them in the page alert.
- [ ] **Offline/reconnecting indicator.** The app already listens for `online`
      but shows the user nothing.

## P6 — Code quality, tests, docs

- [x] **Frontend tests.** Vitest + Testing Library, one smoke test per page
      (`frontend/src/pages/*.test.jsx`): `cd frontend && npm test`.
- [x] **Lint and format the frontend.** ESLint 9 flat config and Prettier:
      `npm run lint`, `npm run format:check`, enforced in CI.
- [x] **Make the backend tests hermetic.** They used to run against the live
      seeded DB, log in as `Bob/Bob` and mutate camera rows, so they depended on
      machine state — the root cause of the P0 test failure above. `%test` now
      leaves the datasource URL unset, so Dev Services starts a disposable
      PostgreSQL container, Flyway migrates and seeds it, and it is destroyed
      after the run. The suite no longer touches the developer's database.
- [ ] **Turn integration tests on.** `skipITs` defaults to true (`pom.xml`), so
      failsafe never runs anything.
- [ ] **De-duplicate the palette list.** It is maintained by hand in both
      `frontend/src/theme.js` and `UserResource`.
- [ ] **Remove dead code.** `api.me()` in `frontend/src/api.js` is unused.
- [~] **Write the missing docs.** `DEVOPS.md` now covers deployment, TLS, the
      config reference, backups/upgrades and a troubleshooting table. Still
      missing: `CONTRIBUTING`, `CHANGELOG` and `SECURITY.md` (threat model).
- [x] **Track the open-items list.** This `TODO.md` is tracked; it supersedes
      the git-ignored `docs/agent-sessions/todos.md`.
- [ ] **Plan the Mantine 9 / React 19 upgrade.** Nothing blocks it; it is just
      pending.

---

## Quick wins

Small, self-contained, worth doing first:

- MJPEG reconnect is done; the stall indicator and HLS/manual retry remain (P0).
- Sound alerts for Video events too, not just Audio (P0) — done.
- Default `stream_localhost`/`webcontrol_localhost` to `on` (P1).
- Require the webhook secret by default (P1).
- `AbortController` + status-before-JSON in `api.js` (P5).
- Wire up the existing but unused `DebugWrapper` error boundary (P5).
- Namespace the alerts-enabled flag per account (P5) — moot; the flag was removed.
- Delete the unused `api.me()` (P6).
- Fix or re-scope the failing test so `mvn package` passes (P0) — done.
