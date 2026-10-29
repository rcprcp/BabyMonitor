# Usability review — simplification backlog

A working document for reducing the number of user-facing switches and controls
without hiding anything that matters for trust.

- **Status:** research complete, no product code changed
- **Reviewed at:** commit `2394e39` (`master`), 2026-10-28
- **Scope:** the React SPA, the resources behind each control, seed data, README
  and TODO
- **Purpose:** work through the topics below one at a time, recording decisions

> This is an analysis document, not a session transcript. Keep it up to date as
> topics are decided and shipped.

## How we will work

1. Take **one topic (T1–T12)** per branch/PR. Keep the topic number in the branch
   name (`agent/t3-camera-power`).
2. Record the decision in the **Decision log** before implementing.
3. Update the **Status** table and the topic's acceptance criteria when done.
4. Run the checks required by `AGENTS.md` (backend tests, frontend build) plus
   `npm test`, `npm run lint` and `npm run format:check`.
5. Update `README.md` wherever the UI description changes; it is the de-facto
   manual.
6. Never delete the expert capability — move it behind an **Advanced** surface.

### Status legend

| Status | Meaning |
| --- | --- |
| `todo` | Not started |
| `decision` | Needs a product decision first |
| `doing` | In progress |
| `done` | Shipped and verified |

## Status overview

| # | Topic | Priority | Size | Status |
| --- | --- | --- | --- | --- |
| T1 | Audio-alert presets instead of 521 checkboxes | High | S | todo |
| T2 | Camera page: simple form + advanced Motion options | High | M | todo |
| T3 | One camera power control; audio as a preference | High | S–M | todo |
| T4 | Make "Stop" honest about boot behaviour | High | S | decision |
| T5 | One Settings area; label and validate raw tags | High | S | todo |
| T6 | Environment: monitoring first, setup behind a disclosure | Medium | M | todo |
| T7 | Navigation: 4 destinations, camera names | Medium | S | todo |
| T8 | One save action per page, less badge noise | Medium | S–M | decision |
| T9 | Live stream polish (stopped label, volume, mute, stall) | Medium | S | todo |
| T10 | First-run onboarding wizard | Medium | L | todo |
| T11 | Frontend test + lint baseline (enabling work) | High | M | done |
| T12 | Copy and naming cleanups | Low | S | todo |

**Suggested sequence**

1. **Phase 0:** T11 (safety net) and T12 (cheap copy fixes)
2. **Phase 1:** T1, T3, T7 (structural wins with little backend work)
3. **Phase 2:** T2, T5, T8
4. **Phase 3:** T6, T9, T10 (T4 decided before T3 ships)

## Snapshot: what the user sees today

| Screen | Interactive controls | Audience today |
| --- | --- | --- |
| Live stream (`StreamPage`, `AudioListener`) | camera select, listen toggle, volume, level meter | Parent, daily |
| Notifications | Refresh, Dismiss All, per-row Dismiss / Watch video | Parent, daily |
| Environment (`EnvironmentPage`) | range selector, 6 bound inputs, camera picker, exposure, "sample every minute" switch, Sample now, **6 Capture buttons**, 2 Save/Reset pairs | Parent + installer, mixed |
| Camera 1 / Camera 2 | ~40 each: 12 raw Motion rows × (input + Save + delete), Add Row, Start/Apply, Start/Stop audio, Stop, 3 status badges | Installer |
| Audio notifications | **521 checkboxes**, filter, 12 group buttons, Reset | Installer / power user |
| Settings | 2 raw tag/value rows with per-row Save | Installer |
| Personal settings | 13 palette swatches, light/dark/auto, sound popover (7 sounds) | Parent, occasional |
| Menu (`TitleBar.jsx:65-92`) | 9 destinations | Everyone |

Roughly **640 interactive controls**, of which ~535 are on the Audio Notification
Config page.

### Control-count targets

| Surface | Today | Target |
| --- | --- | --- |
| Camera page | ~40 | ~8 primary + advanced drawer |
| Audio alerts | 535 | 4 preset choices + optional custom list |
| Environment | 21 | ~3 primary controls + collapsed settings |
| Navigation | 9 destinations | 4 |
| Saves | 1 per row (~14) | 1 per page |

## Guiding principles

- **Separate daily use from installation.** A parent should never meet a Motion
  option name, a pid, or a luma value.
- **One control, one job.** "Start/Apply", "Stop" and "Start audio" currently
  overlap; collapse them.
- **Status stays visible.** Simplify controls, never the "camera down" warnings.
- **Honest labels.** A Stop that is undone at boot is not a Stop.
- **Defaults are a control too.** 62 audio classes on by default is a design
  decision with a usability cost.
- **Advanced, not hidden.** Expert surfaces live behind a disclosure with a
  stable deep link, so support can still reach them.

---

## T1 — Audio-alert presets instead of 521 checkboxes

**Why.** Selecting which sounds may wake you is a daily-trust decision, but the
page presents every YAMNet class as an equal checkbox. The README already states
the useful cry-only set.

**Evidence**

- `frontend/src/pages/AudioAlertsPage.jsx:199-209` — one checkbox per class.
- `src/main/java/com/babymonitor/audio/AudioAlertSettings.java:44` — default is
  classes 0–61 (62 on).
- `README.md:539-540` — "for cry-only alerts keep just 11 Screaming, 19 Crying,
  20 Baby cry and 21 Whimper checked".
- Backend already accepts a whole set:
  `PUT /api/audio/alert-classes` with `{ "indices": [...] }`
  (`AudioAlertConfigResource.java`, `api.js:207-209`).

**Proposal**

- Show preset cards/buttons: **Cry only**, **Voices and cries**, **Everything**,
  plus **Custom**.
- Selecting a preset applies immediately (same optimistic save as today) and
  shows "Custom" when the set no longer matches a preset.
- Keep the filter, the per-group macros and the per-class list under **Custom**.
- Keep **Reset to default** available but not prominent.

**Suggested preset contents (needs a decision)**

| Preset | Indices | Notes |
| --- | --- | --- |
| Cry only | 11, 19, 20, 21 | README's validated set |
| Voices and cries | 0–23 | Speech & Human Sounds |
| Everything | 0–520 | Loud, likely noisy |
| Custom | current selection | Opens the existing UI |

**Files likely involved.** `AudioAlertsPage.jsx`, `api.js` (no change), maybe a
shared constants module. `README.md`.

**Backend impact.** None; reuse the existing whole-set endpoint.

**Acceptance criteria**

- The page opens with the current selection expressed as a preset or "Custom".
- Choosing a preset writes the set and survives a reload.
- Every one of the 521 classes is still reachable under Custom.
- The page no longer renders 521 checkboxes by default.

**Open questions**

- Is "Voices and cries" (0–23) the right middle preset, or should the default
  simply become "Cry only"?
- Should the chosen preset name be stored, or derived from the index set?

---

## T2 — Camera page: simple form + advanced Motion options

**Why.** The camera page is a raw `motion.config` editor. Twelve Motion option
names per camera, each with its own Save button, are not usable by a parent and
are easy to break.

**Evidence**

- `frontend/src/pages/CameraPage.jsx:403-506` — editable key/value table.
- `src/main/resources/db/migration/V2__seed_data.sql:81-106` — the 12 seeded
  parameters (`webcontrol_localhost`, `threshold`, `log_file`, `target_dir`, …).
- `README.md:348-352` — "the Camera pages are the control panel".
- The page also exposes `pid` in the status badge
  (`CameraPage.jsx:321`, `336`).

**Proposal**

- Primary card with only: **Name**, **Camera device**, **Resolution preset**
  (720p / 1080p), **Frame rate**, **Stream port**, **On/off** (see T3).
- Status line: one summary — e.g. "Recording · Audio on" — with a warning when
  degraded.
- **Advanced → Motion options** disclosure containing today's table, Add Row,
  delete, config-written badge and pids.
- After editing, offer **Restart camera** only when a restart is required; do not
  make "Apply" the save button.

**Files likely involved.** `CameraPage.jsx`, possibly a new
`CameraEssentials` component, `StreamPage`/`EnvironmentPage` helpers that read
`camera_name`.

**Backend impact.** Minimal. Resolution writes `width` and `height`; a small
batch-parameter endpoint would make that atomic, otherwise two PUTs. No schema
change.

**Acceptance criteria**

- The default camera view has ≤ 8 interactive controls.
- No Motion option name, pid, or file path is visible without opening Advanced.
- Editing name/device/resolution/port works through the friendly form.
- Every existing parameter remains editable under Advanced.
- A destructive row delete still confirms, and the table still validates values
  server-side.

**Open questions**

- Which resolution presets should be offered (and does the device support them)?
- Should the friendly form write immediately or behind one save (see T8)?

---

## T3 — One camera power control; audio as a preference

**Why.** "Start/Apply", "Stop" and "Start/Stop audio" are three power affordances
for two processes. Audio is described as following the camera, yet it can be
started and stopped independently, so the UI can show "Audio processing" while
the camera is stopped.

**Evidence**

- `CameraPage.jsx:357-377` — Start/Apply, Start/Stop audio, Stop.
- `CamerasResource.java:190-199` — starting Motion also enables audio.
- `CamerasResource.java:250-285` — audio start/stop is independent.
- `README.md:482-484` — "Audio follows the camera."

**Proposal**

- One primary toggle/switch: **Monitor this camera** (on/off).
- One preference: **Listen for cries** (checkbox), stored in
  `camera_audio.enabled`, applied when the camera starts and honoured when it is
  stopped.
- Move the manual audio start/stop into Advanced/diagnostics.
- If the camera is off, the audio control is unavailable rather than contradictory.

**Files likely involved.** `CameraPage.jsx`, `api.js` (existing calls).

**Backend impact.** None required. A future tidy-up could make `audio/start`
refuse when Motion is stopped, but the UI should not need it.

**Acceptance criteria**

- Exactly one on/off control governs the camera and its audio.
- A camera that is on but has audio deliberately off says so in one place.
- No reachable UI state shows audio running for a stopped camera.
- Advanced still allows forcing audio alone for diagnostics.

**Open questions**

- Do we ever need video without audio? (Yes: privacy/bandwidth. The preference
  covers it, but confirm the default.)

---

## T4 — Make "Stop" honest about boot behaviour

**Why.** `babymonitor.start-cameras-on-boot=true` re-starts every camera at boot,
discarding the user's Stop. The button implies persistence it does not have.

**Evidence**

- `src/main/resources/application.properties:115`
- `README.md:410-424` — the reset and start-at-boot behaviour.
- `CameraPage.jsx:370-377` — the button is labelled "Stop".

**Proposal (choose one)**

- **A. Persist the choice.** Stop survives a restart; boot starts only cameras
  that were not explicitly stopped. Safest UX, but a monitor can then boot
  watching nothing, which the current design deliberately avoids.
- **B. Keep auto-start, change the promise.** Label it **Stop until restart** and
  explain it in one sentence. No behaviour change, no new schema.

**Recommendation.** B now (honest, cheap), revisit A if users report surprise.
A needs a durable "stopped by user" flag plus an explicit resume affordance.

**Files likely involved.** `CameraPage.jsx`; for A also `CameraStartup.java`,
`schema`/migration, and README.

**Acceptance criteria**

- The UI never claims a stop persists when it does not.
- The behaviour matches `README.md` and the label exactly.

---

## T5 — One Settings area; label and validate raw tags

**Why.** There are two settings destinations with overlapping names, and the
global one exposes database tags (`stream_url`, `systemName`) with no explanation
or validation. A malformed `stream_url` silently breaks the live stream.

**Evidence**

- `TitleBar.jsx:80-88` — "Settings" and "Personal settings".
- `SettingsPage.jsx:78-105` — label is the raw tag; per-row Save; no help text.
- `V2__seed_data.sql:21-28` — only two settings exist.
- `StreamPage.jsx:59-62` — the value is used as a host base, trailing slashes
  stripped; a port in the value produces a broken URL.
- `ProfilePage.jsx:204-243` — "Theme Selection" and "Light / dark" both called
  theme.

**Proposal**

- One **Settings** page with sections:
  - **System:** Monitor name, Stream host (with placeholder and validation)
  - **Alerts:** alert sound, audio presets link, environment bounds link
  - **Appearance:** accent color, light/dark
  - **Account:** name, sign out
- Friendly labels and help text in the frontend; keep the tag as secondary text.
- Validate `stream_url` client-side (absolute `http(s)` URL, no port, no path) and
  show an inline error before save.
- Remove the second "Personal settings" menu entry; the avatar still opens the
  same page.

**Files likely involved.** `SettingsPage.jsx`, `ProfilePage.jsx`, `App.jsx`,
`TitleBar.jsx`, `README.md`.

**Backend impact.** None required. Later, a settings metadata endpoint would let
the backend own labels/validation.

**Acceptance criteria**

- Only one settings destination is reachable from the menu.
- Each setting has a human label and one line of help.
- An invalid stream host cannot be saved, and the live stream is unaffected.
- Personalization controls live in the same area and still persist per account.

---

## T6 — Environment: monitoring first, setup behind a disclosure

**Why.** The page a parent opens to check the nursery also contains the complete
camera-light calibration workflow and six alert bounds. Brightness is configured
in proxy lux but displayed as bands, which is hard to reason about.

**Evidence**

- `EnvironmentPage.jsx:463-560` — alert bounds (6 inputs).
- `EnvironmentPage.jsx:562-731` — camera light: picker, exposure, 6 captures,
  switch, sample now.
- `README.md:608-638` — proxy-lux bounds versus band display.
- Three independent polls: 10 s (`:207`), 15 s (`:248`), 60 s (`:256`).

**Proposal**

- Top: four read-only summary tiles (temperature, humidity, brightness band,
  last update) and the chart/table.
- **Alert settings** accordion containing the bounds.
- **Camera light** moved to the Camera page (or an **Advanced → Set up
  brightness** wizard), collapsed by default.
- Express brightness bounds as bands: "alert when darker than X / brighter than
  Y", keeping proxy lux internal.
- Consolidate the three timers behind one refresh cadence.

**Files likely involved.** `EnvironmentPage.jsx`, `CameraPage.jsx` (if moved),
`EnvironmentThresholds`/DTO if bounds become band-based, `README.md`.

**Backend impact.** Band-based bounds would need a small model/DTO decision;
keeping lux under the hood with a band UI is frontend-only.

**Acceptance criteria**

- The default Environment view is read-only and answers "is the nursery okay?"
- Calibration is not visible without opening Advanced/setup.
- A user can set a brightness alert without knowing what a lux is.
- No duplicate polling of the same data.

**Open questions**

- Should brightness alerts be band-based, lux-based, or both?
- Is Environment global or per-room? (Today it is global.)

---

## T7 — Navigation: 4 destinations, camera names

**Why.** The primary action — watching and hearing the baby — is two taps away
behind a hamburger, and camera entries are numbered even though they have names.

**Evidence**

- `TitleBar.jsx:65-92` — 9 menu items, no active indicator.
- `StreamPage.jsx:102-105` — options use "Camera N (name)".
- `EnvironmentPage.jsx:362` — already labels stopped cameras.
- `README.md:590-606` — route list and hamburger description.

**Proposal**

- Persistent navigation: **Monitor**, **Alerts**, **Cameras**, **More**.
  - Monitor = Live stream
  - Alerts = Notifications feed + alert-sound entry
  - Cameras = camera list (named), not one menu item per camera
  - More = Environment, Audio notification settings, System settings, Sign out
- Mobile: bottom tab bar; desktop: AppShell sidebar. Keep the title bar for the
  system name and avatar.
- Show the active destination.
- Use `camera_name` in labels and headings.

**Files likely involved.** `App.jsx`, `TitleBar.jsx`, new nav component,
`StreamPage.jsx`, `CameraPage.jsx`, `styles.css`, `README.md`.

**Backend impact.** None.

**Acceptance criteria**

- Monitoring is one tap from any page.
- Camera destinations use the camera's name, falling back to its number.
- The current page is visibly selected.
- A deep link to every page still works on refresh (`SpaFallbackFilter`).

---

## T8 — One save action per page, less badge noise

**Why.** Saving is per row, and every save can raise its own "Saved" badge. This
produces ~14 Save buttons and ~14 transient badges, and makes the user wonder
whether anything else needs saving.

**Evidence**

- `CameraPage.jsx:477-488` — Save + "Saved" per parameter.
- `SettingsPage.jsx:93-104` — Save + badge per setting.
- `ProfilePage.jsx:210-219, 233-242` — separate saved badges per preference.

**Proposal**

- Choose one model per page:
  - **Autosave on blur/change** with a single toast, or
  - **Dirty tracking + one sticky Save bar** ("2 unsaved changes · Save · Discard").
- Keep an explicit **Restart camera** for changes that need Motion to reload; do
  not conflate save with apply.
- Warn on navigation when there are unsaved changes.

**Acceptance criteria**

- At most one Save control and one save confirmation per page.
- A failed save identifies the field and leaves the draft intact.
- No destructive action becomes implicit through autosave.

**Open questions**

- Autosave or explicit save? (Autosave is friendlier; explicit is safer for the
  camera config.)

---

## T9 — Live stream polish

**Why.** The core screen has a few avoidable frictions, and it does not warn when
the picture has silently frozen — the worst failure mode for a monitor.

**Evidence**

- `StreamPage.jsx:102-105` — stopped cameras look identical to running ones.
- `AudioListener.jsx:250-258` — volume disabled until listening.
- `StreamPlayer.jsx:120-132` — error state, but no stall detection.
- `TODO.md:25-34` — frozen-stream and manual retry are known P0 items.

**Proposal**

- Mark stopped cameras in the picker and link to the camera page.
- Enable the volume slider before listening; persist it (already done).
- One sound control: **Sound on/off** plus volume, instead of a listen/stop pair.
- Show the camera name in the page heading.
- Add a last-frame/stall indicator and a manual **Retry**, then reconnect
  automatically (aligns with TODO P0).

**Acceptance criteria**

- A stopped camera is identifiable from the picker alone.
- Volume can be set before playback starts.
- A frozen stream becomes visibly stale and recoverable within a bounded time.
- Sound state is obvious at a glance.

---

## T10 — First-run onboarding wizard

**Why.** A new install drops the user on a stream page for cameras that may not
exist, with no guidance. This is already acknowledged in the roadmap, and it is
the natural home for the controls T2 hides.

**Evidence**

- `TODO.md:169-170` — "Onboarding" missing.
- `SEED` data uses example devices `/dev/video0`, `/dev/video4`
  (`V2__seed_data.sql:81-106`).
- `README.md:412-424` — cameras start at boot, so a missing device surfaces as
  "Camera unavailable".

**Proposal**

- Three steps: **Name the monitor** → **Choose camera device(s)** → **Test
  stream and sound**.
- Writes the same `babymonitor.cameras` rows through existing endpoints.
- Offer a "list available video devices" endpoint (new) or a manual picker with
  `v4l2-ctl --list-devices` instructions.
- Mark completion with a settings row (e.g. `onboardingComplete`), so it only
  appears on a fresh install.
- Surface the default-credentials warning here (relates to `TODO.md:65-69`).

**Acceptance criteria**

- A fresh install reaches a working stream without visiting the raw parameter
  table.
- The wizard is skippable and re-runnable from Settings.
- No sample data is duplicated by re-running it.

---

## T11 — Frontend test + lint baseline (enabling work)

**Why.** There are no frontend tests, no linter and no formatter, so every change
above would be unverified. Backend tests are hermetic and should stay the model.

**Evidence**

- `frontend/package.json:7-11` — only `dev`, `build`, `preview`.
- `TODO.md:205-208` — frontend tests and lint are open.
- `TODO.md:86-87` — no CI.

**Proposal**

- Add Vitest + React Testing Library and a smoke test per page (renders, primary
  action, error state).
- Add ESLint (the repo already contains `eslint-disable` comments) and Prettier.
- Wire both into `npm test` / `npm run lint` and, once CI exists, into the
  pipeline.
- Keep `mvn -DskipFrontend test` working.

**Acceptance criteria**

- `npm test` and `npm run lint` exist and pass.
- Each page has at least a render smoke test.
- CI runs backend and frontend checks on every push.

**Done (2026-10-28).** All three criteria met.

- **Runner.** Vitest 3 with jsdom, configured in `frontend/vite.config.js`;
  `src/test/setup.js` stubs the browser APIs Mantine, recharts and `LevelMeter`
  need, `src/test/render.jsx` supplies Mantine + a `MemoryRouter` with the v7
  future flags. Each page mocks `../api` and asserts render, one primary action
  and the error state: 8 files, 24 tests, all eight pages covered.
- **Lint.** ESLint 9 flat config (`frontend/eslint.config.js`) with `react`,
  `react-hooks` and a deliberately small `jsx-a11y` subset. Formatting is not
  argued by ESLint (`eslint-config-prettier`); Prettier owns layout. The
  baseline reports 0 errors and 2 pre-existing `exhaustive-deps` warnings
  (`auth.jsx`, `AudioAlertsPage.jsx`), which are left for the topics that touch
  those files rather than papered over with disables.
- **Format.** Prettier 3 (`frontend/.prettierrc.json`, `semi: false`,
  `singleQuote: true`, `printWidth: 100`) applied to the SPA once, as part of
  this topic, so `npm run format:check` is a real gate from here on. The
  alternative — shipping the config and ignoring the 14 unformatted files — was
  rejected: a check that never passes is worse than no check.
- **CI.** `.github/workflows/ci.yml` runs `mvn -B -DskipFrontend test` and, in a
  second job, `npm ci && test && lint && format:check && build` on every push
  and pull request. This closes the "checks are unchecked on every push" part of
  `TODO.md` P2; packaging (container image, systemd unit) stays open there.
- **Kept working.** `mvn -DskipFrontend test` still skips npm entirely, and
  `mvn package` still builds the SPA through the existing frontend profile.

---

## T12 — Copy and naming cleanups

**Why.** Several labels describe implementation, not intent.

**Evidence / proposed change**

| Where | Today | Change |
| --- | --- | --- |
| `ProfilePage.jsx:248-258` | "Refresh" beside Notification sound | Remove; it re-reads the value the page just wrote |
| `ProfilePage.jsx:204` | "Theme Selection" (palette) | "Accent color" |
| `ProfilePage.jsx:223` | "Light / dark" | "Appearance" |
| `NotificationsPage.jsx:301-312` | "Dismiss All" (red) | "Clear my notifications" (per-account semantics) |
| `CameraPage.jsx:358` | "Start/Apply" | "Restart camera" / "Turn on" (see T3) |
| `CameraPage.jsx:321,336` | "Motion running (pid 1234)" | "Recording"; pid under Advanced |
| `CameraPage.jsx:310-312` | "Config written" badge | Advanced only |
| `SettingsPage.jsx:82` | Raw tag as label | Friendly label + tag as helper |
| `TitleBar.jsx:74-79` | "Camera1"/"Camera2" | Camera name (T7) |

**Acceptance criteria**

- No user-facing label names an implementation detail (Motion, pid, luma, tag)
  on a daily-use screen.
- Terminology is consistent between nav, page titles and README.

---

## Cross-cutting concerns

### What not to simplify away

- **Camera unavailable**, **Motion down — restarting**, **Audio down** and
  **stream frozen** must remain prominent and specific.
- Per-class alert tuning must stay reachable (under Custom/Advanced).
- Both cameras stay selectable and the last-viewed camera still persists.
- Dismissal stays per account; do not turn it into a global delete.
- Alerts must not become silent by accident; any preset change shows what is on.

### Risks

- **Hiding advanced too far** increases support load. Give Advanced a stable URL
  and keep it searchable/linkable.
- **Presets can change alert behaviour** overnight. Default to an explicit choice;
  show the class count ("4 sounds on").
- **Persisting Stop** (T4-A) can leave a monitor watching nothing.
- **Frontend checks exist** (T11): `npm test`, `npm run lint` and
  `npm run format:check` cover every page and run in CI.
- **Docs drift**: `README.md` is the manual and describes the current UI in detail.

### Measurements

Track these before/after per topic:

| Metric | Baseline |
| --- | --- |
| Interactive controls on the camera page | ~40 |
| Interactive controls on the audio page | 535 |
| Interactive controls on the Environment page | 21 |
| Menu destinations | 9 |
| Save buttons per page | 1 per row |
| Taps from login to a live stream | 2 (hamburger + item) |
| Distinct polling timers on Environment | 3 |

### Related existing roadmap items

Do not duplicate these; link to them instead:

- `TODO.md` P0 — frozen stream / manual retry (T9)
- `TODO.md` P4 — onboarding (T10)
- `TODO.md` P5 — error boundary, fetch timeouts, retry everywhere
- `TODO.md` P6 — frontend tests, lint, CI (T11)

## Decision log

Fill in as topics are worked. One row per decision.

| Date | Topic | Decision | Rationale | By |
| --- | --- | --- | --- | --- |
| 2026-10-28 | T11 | Vitest + Testing Library, ESLint 9 flat config, Prettier 3 | Fits Vite with no second bundler; RTL is the house style for React; ESLint+Prettier split keeps lint about correctness | agent |
| 2026-10-28 | T11 | Reformat the SPA once instead of dropping `format:check` | T11 lands first, so this is the cheapest moment for a mechanical diff; a check that cannot pass is dead weight | agent |
| 2026-10-28 | T11 | Two CI jobs (backend, frontend), not one | A frontend failure must not hide a backend one, and the jobs need different toolchains | agent |
| | T1 | | | |
| | T3 | | | |
| | T4 | | | |
| | T6 | | | |
| | T8 | | | |

## Open decisions (blocking)

1. **T4** — Does Stop persist across restarts, or is it relabelled?
2. **T1** — Which presets ship, and which is the default?
3. **T6** — Are brightness bounds expressed in bands or proxy lux?
4. **T8** — Autosave, or an explicit save bar?
5. **T7** — Four destinations as proposed, or three?
6. **T10** — How is "first run" detected, and is scanning `/dev/video*` in scope?

## Changelog

| Date | Change |
| --- | --- |
| 2026-10-28 | Initial review recorded; no product code changed. |
| 2026-10-28 | T11 shipped: frontend tests, lint, format and CI baseline. |
