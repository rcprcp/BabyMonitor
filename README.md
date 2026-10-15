# BabyMonitor

Quarkus 3.40.1 (Java 25) backend with a React + Vite single-page app, serving a
baby-monitor live stream, a notification feed and settings.

- **Backend:** Quarkus REST + Hibernate ORM with Panache + PostgreSQL
- **Frontend:** React 18 + Vite + React Router (`frontend/`)
- **API schema:** OpenAPI at `/q/openapi`, Swagger UI at `/q/swagger-ui`

## Layout

```
db/init.sql                     schema `babymonitor`, tables, seed rows
src/main/java/com/babymonitor/
  auth/                         PasswordHasher, Session, SessionStore, AuthFilter
  motion/                       MotionConfig, MotionConfigBuilder,
                                MotionProcessManager, MotionService
  resource/                     AuthResource, SettingsResource,
                                NotificationsResource, WebhookResource,
                                CamerasResource
  service/                      NotificationService, AfterCommit
  web/                          SpaFallbackFilter
src/main/resources/motion/      motion.template, motion-notify.sh
frontend/                       React/Vite SPA (built into the jar)
```

## Database

Uses the running PostgreSQL instance, schema `babymonitor` inside database `bob`.
The defaults match the local setup; override with `DB_URL` / `DB_USERNAME` /
`DB_PASSWORD`.

```bash
PGPASSWORD=bob psql -h 127.0.0.1 -U bob -d bob -f db/init.sql
```

`db/init.sql` is re-runnable (idempotent). It creates:

- `babymonitor.settings` (`tag`/`value`) — seeded with
  `systemName` = `Cindy & Stephen's Baby Monitor` (the title shown by the SPA)
  and `stream_url` = `http://localhost`, the host base of the box running
  Motion. The Live stream page plays `<stream_url>:<camera's stream_port>` (see
  *Motion* below); a run of this script drops the older `streaming_url` row
- `babymonitor.users` (`id`, `name`, `password_hash`, `theme`, `theme_color`) —
  seeded with `Cindy`, `Marij`, `Stephen`, `Bob`, each with password equal to
  the name (bcrypt hash, `$2a$` prefix — the jbcrypt library rejects `$2b$`).
  `theme` holds the account's UI preference (`light`, `dark` or `auto`,
  defaulting to `auto`) and `theme_color` its accent color, a Mantine palette
  name (defaulting to `blue`)
- `babymonitor.notifications` (`id`, `event_time`, `event_type`, `description`,
  `video_link`) — the feed behind the Notifications page. `event_type` is the
  PostgreSQL enum `babymonitor.event_type` with the values `Video` and `Audio`.
  The script seeds twenty test notifications (eleven audio, nine video with a
  clip link) spread over the previous four days, one row at a time and only
  while its description is missing, so reloading the database brings any that
  were removed back without ever duplicating them. Delete that statement in
  `db/init.sql` for a feed of real events only.
- `babymonitor.notification_dismissals` (`notification_id`, `user_id`,
  `dismissed_at`) — one row per notification an account has dismissed. The pair
  is the primary key, so dismissing twice is a no-op, and the feed lists the
  notifications that have no row for the account asking. Notifications
  themselves are never deleted, so dismissing hides an event for one account
  only and leaves it on record for everybody else
- `babymonitor.environment` (`id`, `event_time`, `temperature`, `humidity`,
  `brightness`) — the ambient time series behind the Environment page.
  `temperature` is Celsius and `humidity` a percentage, both `NUMERIC(5,2)`;
  `brightness` is lux. The script seeds one sample per hour for the previous
  seven days, following a daily rhythm (coolest before dawn, brightest around
  noon), only while the table is empty so re-running does not stack another week
  on top. A camera or sensor writes real rows; delete the seed statement for
  real readings only
- `babymonitor.cameras` (`camera_number`, `key`, `value`, `enabled`) — camera
  parameters, keyed by `(camera_number, key)`. There is no separate camera row,
  so `enabled` (the camera's on/off flag) is repeated on every parameter of that
  camera and switching it updates them all. The keys *are* Motion option names:
  the app writes them, almost verbatim, into the camera's generated
  `motion.config.<n>` (see *Motion* below). Both cameras are USB cameras, so
  they are addressed by `video_device` (`/dev/video0`, `/dev/video2`), not a
  network URL. The script seeds twelve parameters for each of the two cameras
  the menu links to, inserted only while a `(camera, parameter)` pair is
  missing, so edits made on the Camera pages survive a re-run; it also drops the
  pre-Motion parameter names (`stream_url`, `resolution`, `frame_rate`, …) from
  any older database. Camera 2 ships switched off so both states are visible

## Build & run

```bash
mvn package                 # builds the SPA then the Quarkus app
java -jar target/quarkus-app/quarkus-run.jar
# -> http://localhost:8080
```

Useful flags / modes:

```bash
mvn -DskipFrontend test     # backend tests only (no npm)
mvn quarkus:dev             # dev mode
cd frontend && npm run dev  # SPA on :5173, proxies /api -> :8080
```

The Vite dev server answers any hostname (`server.allowedHosts: true` in
`frontend/vite.config.js`, so its DNS-rebinding guard does not reject an IP
address or a reverse proxy). It still listens on localhost only — add `--host`
(or `host: true`) to serve it to the rest of the network.

## API

All `/api` calls need `Authorization: Bearer <token>` except login and the
webhooks.

| Method      | Path                                   | Notes                          |
| ----------- | -------------------------------------- | ------------------------------ |
| POST        | `/api/auth/login`                      | `{ "name", "password" }` → token |
| POST        | `/api/auth/logout`                     | invalidates the token          |
| GET         | `/api/auth/me`                         | current session                |
| GET         | `/api/settings`                        | list all settings              |
| GET/PUT     | `/api/settings/{tag}`                  | read / update one setting      |
| GET         | `/api/users/me`                        | the signed-in user's record    |
| PUT         | `/api/users/me/theme`                  | `{ "theme" }`: light/dark/auto |
| PUT         | `/api/users/me/theme-color`            | `{ "themeColor" }`: palette    |
| GET         | `/api/notifications`                   | newest first, minus my dismissals |
| DELETE      | `/api/notifications/{id}`              | dismiss for my account only    |
| GET         | `/api/environment`                     | ambient readings, newest first (`?limit=`, 1–2000, default 168) |
| GET         | `/api/cameras`                         | every camera parameter         |
| GET         | `/api/cameras/{n}`                     | one camera's parameters        |
| PUT         | `/api/cameras/{n}/parameters/{key}`    | `{ "value" }`: edit a parameter |
| PUT         | `/api/cameras/{n}/enabled`             | `{ "enabled" }`: switch a camera on/off (starts/kills Motion) |
| GET         | `/api/cameras/{n}/motion`              | Motion's state for the camera  |
| POST        | `/api/cameras/{n}/motion/apply`        | rebuild the config and (re)start Motion |
| POST        | `/api/cameras/{n}/motion/stop`         | stop the camera's Motion       |
| POST        | `/api/webhooks/audioNotification`      | device audio alert (see below) |
| POST        | `/api/webhooks/videoNotification`      | device video/motion alert      |

### Webhooks

`POST /api/webhooks/audioNotification` and
`POST /api/webhooks/videoNotification` receive alerts from the camera / motion
detector and store them in `babymonitor.notifications`, where the Notifications
page lists them. They are exempt from the bearer-token gate (the sender is a
device, not a browser).

Payload schema (`NotificationRequest`):

| Field | Type | Notes |
| --- | --- | --- |
| `eventTime` | ISO-8601 instant | Optional; defaults to when the webhook was received |
| `description` | string | Required |
| `videoLink` | string | Required for `videoNotification`; omit for audio |

The stored `eventType` is taken from the endpoint — `videoNotification` records
`Video`, `audioNotification` records `Audio` — so it can never disagree with the
URL that was called.

```bash
# video alert with a clip
curl -X POST http://localhost:8080/api/webhooks/videoNotification \
  -H 'Content-Type: application/json' \
  -d '{"description":"Motion detected in the crib",
       "videoLink":"http://192.168.0.2:8081/recordings/motion.mp4"}'

# audio alert (no video)
curl -X POST http://localhost:8080/api/webhooks/audioNotification \
  -H 'Content-Type: application/json' \
  -d '{"description":"Baby cried"}'
# -> 202 {"status":"accepted","id":4,"eventTime":"..."}
```

A missing `description` (or a missing `videoLink` on the video endpoint) returns
`400`. Set `BABYMONITOR_WEBHOOKS_SECRET` to require a matching
`X-Webhook-Secret` header; leave it unset to accept any caller (dev default).
The `GET` variants return `{"status":"ready"}` so a provider can verify the URL
first.

### Dismissing a notification

`DELETE /api/notifications/{id}` dismisses the notification **for the calling
account only**. It writes a row to `babymonitor.notification_dismissals` and
leaves the notification itself alone, so the event stays on record and stays
visible to every other account. `GET /api/notifications` therefore returns the
notifications that have no dismissal row for the account asking.

Dismissing is idempotent — a second call still returns `204` — and an id that is
not a notification at all returns `404`. There is no way to dismiss an event for
the household through the API: nothing deletes notification rows any more.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"name":"Bob","password":"Bob"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

curl -X DELETE http://localhost:8080/api/notifications/4 \
  -H "Authorization: Bearer $TOKEN"   # -> 204, hidden for Bob only
```

A freshly initialized database has the twenty seeded test notifications to try
this on: dismiss one as Bob and it stays in Cindy's feed.

## Motion

The app owns one [`motion`](https://motion-project.github.io/) process per
camera; systemd is not involved. Everything is driven by the rows in
`babymonitor.cameras`, so the Camera pages are the control panel.

### Generated configuration

Motion has no `include` directive, so each camera's file must be self-contained.
On every **Apply** the app reads the shared template
`src/main/resources/motion/motion.template`, puts it at the top, and appends one
`key value` line per camera row to form
`$BABYMONITOR_MOTION_BASE_DIR/motion.config.<n>` (default
`/home/bob/motion/motion.config.<n>`). The template is copied to the base
directory on first use and never overwritten, so local edits survive.

Motion applies settings in file order and the last one wins, so the camera rows
override the template's defaults. The rows are validated before they are
written — no newlines or control characters, and the numeric options
(`width`, `height`, `framerate`, `threshold`, `stream_port`, …) must be in
range — because a stray newline in a hand-edited value would otherwise inject a
whole new option.

The base directory ends up looking like this:

```
/home/bob/motion/
  motion.template                   shared base config (editable, never overwritten)
  motion-notify.sh                  on_movie_end hook (copied from the jar)
  motion.config.1  motion.config.2  generated, one per camera
  1/logs  1/recordings              Camera 1 data (its target_dir / log_file rows)
  2/logs  2/recordings              Camera 2 data
```

### Processes

Apply starts `motion -n -c /home/bob/motion/motion.config.<n>` (non-daemon, a
plain child of the app) if it is not already running, or sends it `SIGHUP` to
reload when it is. That `-c …motion.config.<n>` path is what distinguishes the
two processes in `ps -ef`.

| Action | Effect |
| --- | --- |
| Save a camera parameter | stored only; Motion is untouched until Apply |
| **Apply** | rebuild the config, then start Motion or `SIGHUP` it |
| **Enabled** on | start Motion (build the config first if needed) |
| **Enabled** off | kill Motion (`SIGTERM`, then `SIGKILL` if it lingers) |

Each camera's pid is written to `<base>/<n>/motion.pid`. When the app restarts
(dev mode recompiles constantly) it reads that file and adopts a process it did
not start rather than launching a second one, then stops it if the camera is
disabled — see `MotionService.reconcile`.

`babymonitor.motion.enabled` is a master switch and defaults to **false**, so
development and tests never spawn a real `motion`. Set
`BABYMONITOR_MOTION_ENABLED=true` (and, if needed,
`BABYMONITOR_MOTION_BASE_DIR` or `BABYMONITOR_MOTION_BINARY`) to let the app
drive it; the rest of the knobs are in `application.properties`.

### Live stream and recordings

The Live stream page plays Motion's own published stream, built as
`<stream_url>:<stream_port>`, so what you watch is what Motion is recording.
`stream_localhost`/`webcontrol_localhost` default to `off` so both the stream
and the web control are reachable from other machines on the network; set them
to `on` to keep the ports on the box itself.

The template's `on_movie_end` runs `motion-notify.sh` with each finished clip,
which POSTs a Video notification to `/api/webhooks/videoNotification`; the
webhook URL and any secret are passed in Motion's environment. Recordings are
not served over HTTP yet, so the notification currently links the clip by its
file name — serving `<base>/<n>/recordings` is the next step.

## SPA

React Router routes: `/login`, `/` (stream), `/notifications`, `/environment`,
`/cameras/1` and `/cameras/2` (the shared Camera page, one route per camera),
`/settings`, `/profile` (Personal settings). The app boots into the Live stream
page, which has a camera drop-down: both cameras are always selectable, and it
plays Motion's stream for the chosen one, built from the `stream_url` setting
and that camera's `stream_port` row. It defaults to Camera 1 when
that is enabled, otherwise to Camera 2 when that one is enabled, and shows no
stream when both are disabled. An Enabled switch beside the drop-down turns the
selected camera on or off — the same flag the Camera page edits, and the server
starts or kills Motion to match — so a
switched-off camera can be turned back on right there. A blue title bar with a hamburger menu (upper left)
links the pages. The title bar text — and the browser tab title — come from the
`systemName` setting, so renaming the system is just a setting edit.
`SpaFallbackFilter` serves `index.html` for client-side routes so a deep link
like `/settings` works on refresh.

The Environment page charts the last 24 hours / 3 / 7 / 14 days of readings and
lists the raw rows under it. Temperature and humidity share the left axis;
brightness gets its own right axis. The Camera pages show one camera's
parameters and let each value be edited, plus a switch for the camera's
`enabled` flag and an **Apply** button that rebuilds and (re)starts its Motion
process.

The UI is built with [Mantine](https://mantine.dev/) 8 (`@mantine/core`,
`@mantine/hooks` and `@mantine/charts` on top of `recharts`, wired up in
`frontend/src/main.jsx` with the PostCSS preset in
`frontend/postcss.config.cjs`). Only the blue brand bar and the camera frame
keep hand-written CSS (`frontend/src/styles.css`).

### Theme

Each account carries its own preferences in `babymonitor.users`, and the
Personal settings page writes them straight to the account:

- **Theme Selection** — Mantine's color palette
  (https://mantine.dev/theming/colors/) as clickable swatches. The chosen name
  (`blue`, `grape`, `teal`, …) is stored in `theme_color` with
  `PUT /api/users/me/theme-color` and becomes the Mantine primary color, so
  buttons, links and highlights follow it. `gray` is offered too; Mantine's
  `dark` is not, as it is a surface color rather than an accent.
- **Light / dark** — `light`, `dark` or `auto` (follow the OS), stored in
  `theme` with `PUT /api/users/me/theme`.

The database is the single source of truth — Mantine is configured with a
non-persisting color-scheme manager (`frontend/src/theme.js`) and its theme is
built from the account's color in `frontend/src/App.jsx`, where `AuthProvider`
sits *above* `MantineProvider` for exactly that reason. The login response plus
`GET /api/users/me` carry both values so the app paints in the right colors. A
`babymonitor.prefs` localStorage entry is kept purely as a hint to avoid
flashing the wrong theme while the profile request is in flight.

The blue title bar is part of this: it is painted with Mantine's
`--mantine-primary-color-filled` / `-contrast` variables, so it takes the
account's palette color, and the initial in its top-right circle is the palette
color on the contrasting color. `createAppTheme` enables Mantine's
`autoContrast`, without which Mantine answers *white* for every contrast color
and a pale palette (yellow, lime, gray) would put white text on its own light
shade.

## License

Released under the Apache License, Version 2.0 — the full text is in
[`LICENSE`](LICENSE), and the copyright and attribution notices are in
[`NOTICE`](NOTICE).

Copyright 2026 Bob Plotts. Licensed under the Apache License, Version 2.0 (the
"License"); you may not use this project except in compliance with the License.
You may obtain a copy of the License at
<https://www.apache.org/licenses/LICENSE-2.0>. Unless required by applicable law
or agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing permissions and
limitations under the License.
