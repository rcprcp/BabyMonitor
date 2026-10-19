# BabyMonitor

Quarkus 3.40.1 (Java 25) backend with a React + Vite single-page app, serving a
baby-monitor live stream, a notification feed and settings.

- **Backend:** Quarkus REST + Hibernate ORM with Panache + PostgreSQL
- **Frontend:** React 18 + Vite + React Router (`frontend/`)
- **API schema:** OpenAPI at `/q/openapi`, Swagger UI at `/q/swagger-ui`

## Layout

```
src/main/java/com/babymonitor/
  audio/                        AudioConfig, CaptureCommand, PcmFramer,
                                AudioProcessManager, AudioService,
                                AudioClassifier, YamNetClassifier,
                                YamNetLabels, CryDetector
  auth/                         PasswordHasher, Session, SessionStore, AuthFilter
  dto/                          request/response records
  entity/                       Camera, Notification, User, Setting, … (Panache)
  motion/                       MotionConfig, MotionConfigBuilder,
                                MotionProcessManager, MotionService,
                                MotionSupervisor
  resource/                     AuthResource, UserResource, SettingsResource,
                                NotificationsResource, WebhookResource,
                                CamerasResource, RecordingsResource,
                                EnvironmentResource
  service/                      NotificationService, NotificationBroadcaster,
                                AfterCommit, RecordingsService
  web/                          SpaFallbackFilter
src/main/resources/db/migration/  Flyway migrations (schema, seed data)
src/main/resources/motion/      motion.template, motion-notify.sh
src/main/resources/yamnet/      yamnet.tflite, yamnet_class_map.csv
frontend/                       React/Vite SPA (built into the jar)
```

## Database

Uses the running PostgreSQL instance, schema `babymonitor` inside database `bob`.
The defaults match the local setup; override with `DB_URL` / `DB_USERNAME` /
`DB_PASSWORD`.

Flyway owns the schema: the migrations in `src/main/resources/db/migration/` run
automatically at startup (`quarkus.flyway.migrate-at-start`), so there is
nothing to run by hand — point the app at an empty database and it builds
itself. A fresh database runs them in order; an existing one is baselined at V1
(the state the retired `db/init.sql` produced) and brought forward by the later
migrations.

- `V1__initial_schema.sql` — schema only:
  - `babymonitor.settings` (`tag`/`value`) — application settings, including
    `systemName` (the title shown by the SPA) and `stream_url`, the host base of
    the box running Motion. The Live stream page plays
    `<stream_url>:<camera's stream_port>` (see *Motion* below)
  - `babymonitor.users` (`id`, `name`, `password_hash`, `theme`, `theme_color`,
    `prompt_sound`) — login accounts. `theme` holds the account's UI preference
    (`light`, `dark` or `auto`, defaulting to `auto`), `theme_color` its accent
    color, a Mantine palette name (defaulting to `blue`), and `prompt_sound` the
    alert-tone prefix it plays (defaulting to `chime`)
  - `babymonitor.notifications` (`id`, `event_time`, `event_type`,
    `description`, `video_link`) — the feed behind the Notifications page.
    `event_type` is the PostgreSQL enum `babymonitor.event_type` with the values
    `Video`, `Audio` and `System` (the last is written by the Motion supervisor;
    see *Motion* below)
  - `babymonitor.notification_dismissals` (`notification_id`, `user_id`,
    `dismissed_at`) — one row per notification an account has dismissed. The
    pair is the primary key, so dismissing twice is a no-op, and the feed lists
    the notifications that have no row for the account asking. Notifications
    themselves are never deleted, so dismissing hides an event for one account
    only and leaves it on record for everybody else
  - `babymonitor.environment` (`id`, `event_time`, `temperature`, `humidity`,
    `brightness`) — the ambient time series behind the Environment page.
    `temperature` is Celsius and `humidity` a percentage, both `NUMERIC(5,2)`;
    `brightness` is lux
  - `babymonitor.cameras` (`camera_number`, `key`, `value`, `enabled`) — camera
    parameters, keyed by `(camera_number, key)`. There is no separate camera
    row, so `enabled` (the camera's on/off flag) is repeated on every parameter
    of that camera: Start/Apply writes it to all of them and Stop clears them.
    The keys *are* Motion option names: the app writes them, almost verbatim,
    into the camera's generated `motion.config.<n>` (see *Motion* below)
- `V2__seed_data.sql` — development sample data; a production database would not
  run it. Every statement is guarded, so applying it on top of existing data
  neither duplicates a row nor clobbers an edit:
  - the `systemName` = `Cindy & Stephen's Baby Monitor` and `stream_url` =
    `http://localhost` settings
  - the four sample accounts `Cindy`, `Marij`, `Stephen`, `Bob`, each with
    password equal to the name (bcrypt hash, `$2a$` prefix — the jbcrypt
    library rejects `$2b$`)
  - one environment sample per hour for the previous seven days, following a
    daily rhythm (coolest before dawn, brightest around noon), seeded only while
    the table is empty so it never stacks another week on top. A camera or
    sensor writes real rows; delete that statement for real readings only
  - twelve parameters for each of the two demo cameras, inserted only while a
    `(camera, parameter)` pair is missing so edits made on the Camera pages
    survive. Both cameras are USB cameras, addressed by `video_device`
    (`/dev/video0`, `/dev/video4`) rather than a network URL, and both ship with
    `enabled` false, so startup is clean and a camera only comes up when the
    user starts it from its own page
  - twenty test notifications (eleven audio, nine video with a clip link)
    spread over the previous four days, one row at a time and only while its
    description is missing, so reloading brings back any that were removed
    without ever duplicating them. Delete that statement for a feed of real
    events only
- `V3__add_system_event_type.sql` — adds `System` to the `event_type` enum
- `V4__add_active_camera.sql` — adds `users.active_camera`, the camera the
  account last viewed, so the Live stream page can restore it

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
| PUT         | `/api/users/me/prompt-sound`           | `{ "promptSound" }`: alert tone |
| PUT         | `/api/users/me/active-camera`          | `{ "activeCamera" }`: last camera |
| GET         | `/api/notifications`                   | newest first, minus my dismissals |
| GET         | `/api/notifications/stream`            | server-sent events: live push of new notifications (`?token=`, `?lastId=`) |
| DELETE      | `/api/notifications/{id}`              | dismiss for my account only    |
| GET         | `/api/environment`                     | ambient readings, newest first (`?limit=`, 1–2000, default 168) |
| GET         | `/api/cameras`                         | every camera parameter         |
| GET         | `/api/cameras/{n}`                     | one camera's parameters        |
| POST        | `/api/cameras/{n}/parameters`          | `{ "key", "value" }`: add a parameter |
| PUT         | `/api/cameras/{n}/parameters/{key}`    | `{ "value" }`: edit a parameter |
| DELETE      | `/api/cameras/{n}/parameters/{key}`    | remove a parameter row         |
| GET         | `/api/cameras/{n}/motion`              | Motion's state for the camera  |
| POST        | `/api/cameras/{n}/motion/apply`        | rebuild the config and (re)start Motion |
| POST        | `/api/cameras/{n}/motion/stop`         | stop the camera's Motion       |
| GET         | `/api/cameras/{n}/recordings`          | the camera's clips, newest first |
| GET         | `/api/cameras/{n}/recordings/{file}`   | one clip; honours a byte `Range` |
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

### Live alerts

`GET /api/notifications/stream` is a [server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
stream: every new notification is pushed as a `notification` event the moment
the webhook that recorded it commits, so the Notifications page updates and the
parent is alerted without waiting for a refresh.

A reconnecting client passes the last event id it saw, via the standard
`Last-Event-ID` header (set automatically by the browser's `EventSource`) or a
`lastId` query parameter, and is sent everything newer, in id order. `eventTime`
is deliberately not used for this: a webhook may back-date an event, while the
id is always monotonic.

Because `EventSource` cannot set an `Authorization` header, this one endpoint
also accepts the session token as a `token` query parameter. That is the only
path where a URL-borne token is accepted; every other request must use the
`Bearer` header, so the token does not leak into URLs or logs elsewhere.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"name":"Bob","password":"Bob"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

curl -N "http://localhost:8080/api/notifications/stream?token=$TOKEN&lastId=0"
```

The SPA consumes the stream from any page while signed in, with short polling
as a fallback when the stream is unavailable. Alerting needs no opt-in: every
new event raises an in-app toast and plays the account's chosen tone. `System`
notices are the one exception — they stay silent, because the supervisor has
already recovered the camera and a chime would only startle a sleeping parent.
Personal settings → **Notification sound** picks the tone (`chime`, a small
two-tone file at `/sounds/notification-chime.mp3`, by default; it is saved on
the account so it follows the user across browsers). A browser that has not yet
seen a user interaction may block the sound; a blocked play is ignored, so the
toast still appears.

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
| Add, save or delete a parameter | stored only; Motion is untouched until Apply |
| **Start/Apply** | set `enabled`, rebuild the config, then start Motion or `SIGHUP` it |
| **Stop** | clear `enabled`, then kill Motion (`SIGTERM`, then `SIGKILL` if it lingers) |

Once a camera is marked running, the `MotionSupervisor` keeps it alive: a
background check restarts a camera whose process has died (with backoff) and
records a silent `System` notification so the feed shows the recovery.

### Startup is cold

Nothing runs when the app starts. With `babymonitor.motion.reset-on-start` on
(the default), boot kills any `motion` left over from a previous run and marks
every camera not enabled — see `MotionService.resetOnStartup`. Both cameras
therefore ship switched off in the seed, and a camera only comes up when the
user starts it from its own page.

The kill matters because a started child outlives the JVM that spawned it: a
dev-mode recompile would otherwise leave an orphaned `motion` that nothing
tracks, and the next Apply would start a second one. Each camera's pid is
written to `<base>/<n>/motion.pid`, which is how boot finds the leftovers.

There is no master switch: a camera may have no process running, but the app can
always write its config and start or stop its process. Development therefore
runs a real `motion` — set `BABYMONITOR_MOTION_BASE_DIR` or
`BABYMONITOR_MOTION_BINARY` if you want it somewhere other than the defaults in
`application.properties`. The tests never spawn a real one: `MotionTestResource`
points `binary` at a stub that just sleeps and `base-dir` at a scratch
directory.

### Live stream and recordings

The Live stream page plays Motion's own published stream, built as
`<stream_url>:<stream_port>`, so what you watch is what Motion is recording.
`stream_localhost`/`webcontrol_localhost` default to `off` so both the stream
and the web control are reachable from other machines on the network; set them
to `on` to keep the ports on the box itself.

The template's `on_movie_end` runs `motion-notify.sh` with each finished clip,
which POSTs a Video notification to `/api/webhooks/videoNotification`; the
webhook URL, any secret and the camera number are passed in Motion's
environment. The hook points the notification at the clip through the app's own
route, `/api/cameras/<n>/recordings/<file>`, derived from the clip's path so it
still works when Motion is started by hand. Like the template, the script is
copied into the base directory on first use and never overwritten — delete
`<base>/motion-notify.sh` to pick up a change to it.

`GET /api/cameras/{n}/recordings` lists a camera's clips and
`…/recordings/{file}` streams one, from the file's own `target_dir` (either
absolute or the default `<base>/<n>/recordings`). The name must be a plain file
name, so a request cannot read outside that directory, and the response honours
a byte `Range` so a player can seek. Both sit behind the same bearer gate as the
rest of `/api`; that is why the Notifications page fetches a clip with its token
and plays it in an in-app player rather than opening the URL in a new tab.

## Audio and cry detection

Motion's own sound detection is not used. Audio is a second, independent
pipeline: `AudioService` owns a capture process per camera, scores its windows
with [YAMNet](https://www.kaggle.com/models/google/yamnet) and turns a
sustained cry into an `Audio` notification — the same row the
`/api/webhooks/audioNotification` webhook writes, so the feed, the live event
stream, the dismissals and the chime all work unchanged.

### Capture

The capture binary is a child process, never a linked library: `sox` by
default, with `ffmpeg` and `arecord` supported because the flag shape is chosen
from the binary's own name (`CaptureCommand`). Running SoX as an external tool
is also what keeps its GPL away from this Apache-2.0 application. The child's
stdout *is* the PCM feed, so unlike Motion its diagnostics go to
`<base>/<n>/audio.err` and its pid to `<base>/<n>/audio.pid`.

Each camera needs its own ALSA device, because a camera's microphone is a
property of the camera: the `FHD Camera` on the development box, for instance,
has no microphone at all, so audio has to come from the machine's internal
`hw:1,7`. A device line is asked for 16 kHz mono signed 16-bit PCM;
`PcmFramer` slices that into overlapping windows of 15600 samples (0.975 s)
every 7680 (0.48 s), which is exactly YAMNet's input length and hop.

### Classification

YAMNet runs **in this JVM** through [DJL](https://djl.ai/)'s TFLite engine — no
Python sidecar and no ONNX conversion. The model
(`src/main/resources/yamnet/yamnet.tflite`, Apache-2.0) and its class map are
bundled as classpath resources and loaded on the first camera that needs them,
so an install that captures no audio never pays for them. The model is shared;
each camera gets its own predictor and `NDManager`, because a DJL predictor is
not thread-safe and each capture has its own reader thread.

YAMNet scores all 521 classes, and the whole ranked list is kept: which of them
mean "cry" is configuration (`babymonitor.audio.alert-class-indices`, `19` and
`20` by default), not code.

### When an alert fires

A single window is a poor trigger — YAMNet's score for a real cry ramps over
several windows, and a lone window over the line is as likely to be a cough. So
`CryDetector` wants `windows-to-alert` (2) consecutive windows whose best
alert-class score clears `min-confidence` (0.35), and then keeps that camera
quiet for `cooldown-seconds` (60) before it may fire again. A run already long
enough keeps counting through the cooldown, so a baby who does not pause is not
asked to prove himself again the moment it expires.

Tuning notes from validating the model against ESC-50 clips: `19 Crying,
sobbing` is the most reliable single class (`20 Baby cry, infant cry` tracks it
at roughly half the confidence), a genuine cry peaked at 0.50 while ambient
noise, a cat, a dog and a vacuum cleaner all stayed at 0.00, and laughter —
`13`, which must never be in the alert list — can outscore crying on a
mixed clip.

### Status

`AudioStatusDto` reports, per camera, whether the capture is running, its pid
and device, how many windows it has seen, the last window's RMS and the best
class YAMNet gave it, and how many alerts it has raised. Nothing is exposed
over HTTP yet: audio is started from `AudioService` and the capture is not yet
driven by a camera's Start/Stop button.

The tests never open a microphone or load the model: `AudioTestResource` points
`binary` at `fake-capture.sh` and selects `StubClassifier`, and only
`YamNetClassifierTest` injects the real model, to check that the bundled class
map still lines up with the model's output.

## SPA

React Router routes: `/login`, `/` (stream), `/notifications`, `/environment`,
`/cameras/1` and `/cameras/2` (the shared Camera page, one route per camera),
`/settings`, `/profile` (Personal settings). The app boots into the Live stream
page, which has a camera drop-down: both cameras are always selectable, and it
plays Motion's stream for the chosen one, built from the `stream_url` setting
and that camera's `stream_port` row. It restores the account's last-viewed
camera from the profile (`users.active_camera`, saved whenever the drop-down
changes) and otherwise defaults to the first enabled camera — Camera 1 when it
is running, else Camera 2. It shows no stream when the chosen camera is stopped,
which is how a cold start looks, since both cameras come up switched off;
starting one is done from its own Camera page. A blue title bar with a hamburger menu (upper left)
links the pages. The title bar text — and the browser tab title — come from the
`systemName` setting, so renaming the system is just a setting edit.
`SpaFallbackFilter` serves `index.html` for client-side routes so a deep link
like `/settings` works on refresh.

The Environment page charts the last 24 hours / 3 / 7 / 14 days of readings and
lists the raw rows under it. Temperature and humidity share the left axis;
brightness gets its own right axis. The Camera pages show one camera's
parameters: each value can be edited in place, a row can be added or deleted,
and **Start/Apply** rebuilds the config and (re)starts its Motion process while
**Stop** takes it down.

The Notifications page lists the feed the webhooks write. A video event links the
clip through the app's recordings route; because that route needs the bearer
token, a plain link could not fetch it, so the page pulls the clip with the
token and plays it in an in-app player. A link the app does not serve (an older
or an external URL) still opens in a new tab.

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
