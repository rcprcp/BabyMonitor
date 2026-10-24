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

- [ ] **Supervise the `motion` processes.** `MotionProcessManager` only tracks a
      process at `start`/`stop`; if `motion` crashes (camera unplugged, USB
      reset, OOM) nothing restarts it and nothing tells anyone. It can also
      misreport: `pidOf` falls back to the pid file and only checks that the pid
      is alive, so a reused pid makes a dead camera appear alive. Add a periodic
      liveness check, bounded restart with backoff, and a notification when a
      camera goes down. This is the single biggest trust gap.
- [ ] **Detect a frozen stream.** MJPEG in an `<img>`
      (`frontend/src/components/StreamPlayer.jsx`) can stop updating without
      firing `onError`, so the parent sees a still image that *looks* live. Show
      a last-frame timestamp / stall indicator and reconnect automatically.
      "Looks live but is frozen" is the worst possible failure here.
- [ ] **Reconnect the stream instead of dying.** There is no retry on the HLS
      path and no way back on the MJPEG path short of reloading the page
      (`StreamPlayer.jsx`). Add bounded exponential backoff and a manual retry.
- [ ] **Environment threshold alerting.** Readings are charted
      (`EnvironmentPage.jsx`, `EnvironmentResource`) but nothing ever fires when
      the nursery is too hot, too cold, or too humid. For a baby monitor this is
      a safety feature, not a chart. Add configurable thresholds + notifications.
- [x] **Fix the failing test.** `ApiTest.cameraReturnsItsParametersInNameOrder`
      used to assert seeded values (`width=1280`) that the UI is designed to
      change, so it failed against any database a user had used. It now checks
      the invariants (camera number, name ordering, the Motion option names are
      present, the rows share one enabled flag) instead of live, user-editable
      values, so `mvn package` passes against an edited database.
- [ ] **Alert on Video/motion events too.** Alerts are sound-only for
      `eventType === 'Audio'` (`frontend/src/useNotificationAlerts.js`). A
      motion/clip event — arguably the more important one — is silent.

## P1 — Security

- [ ] **Serve over TLS.** Everything is plain HTTP: webhooks, the SPA, and the
      Motion streams. If the SPA is ever served over HTTPS the browser will
      block the `http` MJPEG feed as mixed content (`StreamPage.jsx` builds the
      URL directly from `stream_base`). Document and support a TLS terminator,
      or relay the stream through the app.
- [ ] **The stream ports are unauthenticated and on the network.**
      `stream_localhost off` / `webcontrol_localhost off` (`db/init.sql`) expose
      8081/8091 to the LAN, bypassing the bearer gate entirely — including
      Motion's **web control** interface. Default them to localhost and relay
      through the authenticated app instead.
- [ ] **Rate-limit login.** `/api/auth/login` has no throttling or lockout
      (`AuthResource.java`), so bcrypt is the only thing slowing a brute force.
- [ ] **Kill the default credentials.** DB defaults to `bob/bob/bob` and the
      seeded accounts use `password == username` (`db/init.sql`). Fine for a
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

- [ ] **Add CI.** There is no `.github/workflows` (or any pipeline): the test
      suite, the frontend build and the failing test above are all unchecked on
      every push.
- [ ] **Package it.** No `Dockerfile`, no compose, no systemd unit. Deployment
      is documented as `mvn package && java -jar`. Provide a container image and
      a service unit, including what owns the `motion` children on restart.
- [ ] **Health and readiness endpoints.** No `quarkus-smallrye-health`, so there
      is nothing for a supervisor or load balancer to probe — and nowhere to
      surface "camera 1's motion is down".
- [ ] **Metrics and structured logging.** No metrics at all; logging is ad-hoc
      `org.jboss.logging` in two classes (`MotionService`,
      `MotionProcessManager`). Add Micrometer/Prometheus and JSON logs with
      request correlation.
- [ ] **Introduce real migrations.** The schema is hand-maintained idempotent
      DDL in `db/init.sql`, applied manually, with Hibernate generation off
      (`application.properties`). This works until it doesn't. Add
      Flyway/Liquibase and a documented upgrade path.
- [ ] **Move the session store out of memory.** Tokens vanish on restart, so
      every redeploy logs everyone out (`SessionStore.java`). Move to the DB or
      Redis, add rotation, and support more than one instance.
- [ ] **Backups and restore.** Not mentioned anywhere. Document a DB dump/restore
      procedure and what happens to recordings.
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
      notifications (see *Audio and cry detection* in the README). Audio now
      follows the camera: a camera's Start/Stop drives it, the Camera page has
      an Audio Start/Stop button and status chip, a per-camera device lives in
      `camera_audio` (falling back to `babymonitor.audio.device`), and
      `AudioSupervisor` restarts a dead capture with backoff and a notification.
- [ ] **Snapshots and a timeline.** There is no thumbnail gallery, no scrub
      across a night, and no "jump to the moment" from a notification.
- [ ] **Roles and caregiver accounts.** All accounts are equal; there is no
      owner/member split, no audit of who changed what (e.g. who switched a
      camera off), and no session/activity log.
- [ ] **Onboarding.** First run drops you on a Live stream page with both
      cameras off and no guidance on pointing them at a device.

## P5 — Frontend experience

- [ ] **Fix the SSE reconnect gap.** `stream.onerror` closes the stream and
      clears the ref but never schedules a reconnect
      (`useNotificationAlerts.js`), so push silently degrades to polling until a
      visibility/online event.
- [ ] **Alert sound preference is global, not per-account.** `ENABLED_KEY` in
      `frontend/src/alerting.js` is one localStorage key while the watermark is
      namespaced by user — the comment claims per-account behaviour it does not
      have.
- [ ] **Add a global error boundary.** `components/DebugWrapper.jsx` is a
      render-crash boundary that is imported nowhere, so one render error blanks
      the app.
- [ ] **Add fetch timeouts and cancellation.** `frontend/src/api.js` uses bare
      `fetch` with no `AbortController`, so a hung backend hangs the UI.
- [ ] **Parse status before JSON.** `api.js` calls `JSON.parse` before checking
      `response.ok`, so an HTML error page throws `SyntaxError` and hides the
      real status.
- [ ] **Give every error a retry.** Only the Notifications page has a Refresh
      button; everywhere else an error is a dead end.
- [ ] **Harden the title bar for phones.** It packs burger, title, Test alert,
      Play sound and avatar with `wrap="nowrap"` at a fixed 60px
      (`App.jsx`, `TitleBar.jsx`).
- [ ] **Handle `theme-color` and safe areas.** Static `theme-color` that ignores
      the chosen palette, and no `viewport-fit=cover` / `env(safe-area-inset-*)`
      for notched phones.
- [ ] **Catch dismissal failures.** `NotificationsPage` calls `dismiss` with no
      try/catch, producing an unhandled rejection.
- [ ] **Offline/reconnecting indicator.** The app already listens for `online`
      but shows the user nothing.

## P6 — Code quality, tests, docs

- [ ] **Frontend tests.** There are none, and no runner configured
      (`frontend/package.json` has only dev/build/preview).
- [ ] **Lint and format the frontend.** `eslint-disable` comments exist but
      ESLint is not installed; no Prettier config either.
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
- [ ] **Write the missing docs.** No `CONTRIBUTING`, `CHANGELOG` or
      `SECURITY.md`; no deployment/TLS runbook, no troubleshooting guide
      (orphaned `motion`, frozen stream), no complete config reference.
- [ ] **Track the open-items list.** `docs/` is git-ignored, so the existing
      `docs/agent-sessions/todos.md` is invisible to contributors — this file
      supersedes it and is tracked.
- [ ] **Plan the Mantine 9 / React 19 upgrade.** Nothing blocks it; it is just
      pending.

---

## Quick wins

Small, self-contained, worth doing first:

- Reconnect the stream and show a stall indicator (P0).
- Sound alerts for Video events too, not just Audio (P0).
- Default `stream_localhost`/`webcontrol_localhost` to `on` (P1).
- Require the webhook secret by default (P1).
- `AbortController` + status-before-JSON in `api.js` (P5).
- Wire up the existing but unused `DebugWrapper` error boundary (P5).
- Namespace the alerts-enabled flag per account (P5).
- Delete the unused `api.me()` (P6).
- Fix or re-scope the failing test so `mvn package` passes (P0) — done.
