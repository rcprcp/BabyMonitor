# Deploying BabyMonitor

This is the operator's runbook: what to install, how to point it at cameras and
microphones, how to run it under systemd, and what to check when something is
silent. It assumes a single Linux host on a trusted network — the app is a
monitor, not a multi-tenant service.

- Application code and behaviour: [`README.md`](README.md)
- Known gaps and planned work: [`TODO.md`](TODO.md), [`USABILITY.md`](USABILITY.md)

> **Tested platform.** Linux `x86_64` with a JVM. YAMNet runs in-process through
> DJL's TFLite engine, and the pinned native library is published for
> `linux-x86_64` only (`pom.xml`, `djl.platform`). ARM hosts would need another
> `tflite-native-cpu` classifier; the app builds, but audio classification would
> not.

---

## 1. What runs where

```
browser ──HTTP──> BabyMonitor (Quarkus, :8080)
                     │  serves the SPA + the JSON API
                     │
                     ├── PostgreSQL (:5432)          accounts, settings, feed, readings
                     ├── motion, one per camera     MJPEG :8081/:8082, recordings
                     └── capture tool, one per camera
                            sox | ffmpeg | arecord  → PCM → YAMNet (in the JVM)
```

- **One Java process** serves both the API and the SPA. The Vite bundle is built
  into the jar (`target/quarkus-app/`).
- **The app owns `motion` and the capture processes.** There is no systemd unit
  per camera: `MotionService` writes `motion.config.<n>` and starts
  `motion -n -c <file>`; `AudioService` starts the capture tool. The supervisors
  restart either when it dies while it should be running.
- **Motion serves the video itself** on each camera's `stream_port`; the browser
  loads it directly from `stream_url` + port, not through the app. Those ports
  must be reachable from the phones/laptops that watch.
- **The app serves the recordings** it finds in each camera's `target_dir`, with
  the bearer token, at `/api/cameras/<n>/recordings/<file>`.

Ports used by default:

| Port | Owner | Purpose |
| --- | --- | --- |
| 8080 | app | SPA, API, webhooks, notification stream |
| 8081 / 8082 | motion | each camera's MJPEG stream (from its `stream_port` row) |
| 8091 / 8092 | motion | web control (from `webcontrol_port`); keep it on localhost |
| 5432 | PostgreSQL | database |

---

## 2. Host prerequisites

| Component | Version / notes |
| --- | --- |
| JDK | **25** (`maven.compiler.release=25`) |
| Maven | 3.9+ — build host only |
| Node.js / npm | 20 or 22 (CI uses 22) — build host only |
| PostgreSQL | 16 recommended (tests pin `postgres:16-alpine`); any version Quarkus/Hibernate supports |
| motion | 4.x from the distribution (`apt install motion`). Needs `-n`, `-c`, `on_movie_end` and SIGHUP reload |
| SoX | `sox` with ALSA support (`libsox-fmt-alsa` on Debian/Ubuntu). `ffmpeg` and `arecord` are supported alternatives |
| alsa-utils | `arecord -l` to find capture devices |
| v4l-utils + ffmpeg | only for the UVC camera-light sensor (`v4l2-ctl`, one-frame probe) |
| curl | the Motion notify hook posts with it |
| Docker | **only for the test suite** (Dev Services PostgreSQL); not needed at runtime |

The app user needs:

- membership of `video` (open `/dev/video*`) and `audio` (open the ALSA device);
- write access to the Motion base directory (`motion.config.<n>`, per-camera
  recordings/logs, pid files) and the audio base directory (pids, stderr);
- permission to execute `motion`, `sox` and (for the light probe) `v4l2-ctl` and
  `ffmpeg`.

---

## 3. Create the service account and state directories

The defaults in `application.properties` point at `/home/bob/...` and are
development paths. Override them in production.

```bash
sudo useradd --system --home /var/lib/babymonitor --shell /usr/sbin/nologin babymonitor
sudo usermod -aG video babymonitor
sudo usermod -aG audio babymonitor   # if the distribution defines an audio group

sudo install -d -o babymonitor -g babymonitor -m 0750 /var/lib/babymonitor
sudo install -d -o babymonitor -g babymonitor -m 0750 /var/lib/babymonitor/motion
sudo install -d -o babymonitor -g babymonitor -m 0750 /var/lib/babymonitor/audio
sudo install -d -o babymonitor -g babymonitor -m 0755 /opt/babymonitor
sudo install -d -o root -g babymonitor -m 0750 /etc/babymonitor

# One recordings/log directory per camera, matching the target_dir/log_file rows.
for n in 1 2; do
  sudo install -d -o babymonitor -g babymonitor -m 0750 \
    /var/lib/babymonitor/motion/$n/recordings /var/lib/babymonitor/motion/$n/logs
done
```

Create the recording and log directories up front: Motion writes clips into
`target_dir` and its log into `log_file`, and a missing parent directory is the
easiest way to end up with a camera that runs but never records. (Motion may
create a missing `target_dir`, but the log path is not worth gambling on.)

---

## 4. PostgreSQL

```bash
# The app creates the schema, so the role must own the database.
sudo -u postgres createuser --pwprompt babymonitor
sudo -u postgres createdb -O babymonitor babymonitor
```

The app connects with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`. Flyway runs the
migrations in `src/main/resources/db/migration/` at startup and creates the
`babymonitor` schema; there is nothing to run by hand.

```bash
psql -h 127.0.0.1 -U babymonitor -d babymonitor -c 'table babymonitor.flyway_schema_history'
```

### The seed migration is not dev-only

`V2__seed_data.sql` is ordinary migration data, so **it runs on every fresh
database**, including production. A first start therefore gives you:

- two demo cameras (`Nursery` `/dev/video0`, `Hallway` `/dev/video4`) whose device
  paths, ports and directories are examples;
- four demo accounts whose password is their name (`Cindy`, `Marij`, `Stephen`,
  `Bob`);
- a week of synthetic environment readings and twenty test notifications;
- `systemName = Cindy & Stephen's Baby Monitor` and `stream_url = http://localhost`.

Delete or edit all of it before the monitor is trusted (section 10).

### Backups

The database is the source of truth for accounts, settings, the camera rows, the
notification feed and the environment history. Recordings live on disk.

```bash
# nightly
pg_dump -Fc -h 127.0.0.1 -U babymonitor babymonitor > /var/backups/babymonitor-$(date +%F).dump

# restore into an empty database
pg_restore -h 127.0.0.1 -U babymonitor -d babymonitor --clean --if-exists backup.dump
```

Back up the Motion base directory too if the clips matter (`rsync -a
/var/lib/babymonitor/motion/`), and remember that the app never deletes
recordings or notifications — see `TODO.md` P3.

---

## 5. Build and install the artifact

Build on a machine with JDK 25, Maven and Node (the `frontend` Maven profile runs
`npm install` then `npm run build`):

```bash
mvn -B -DskipTests package        # SPA + backend, no test run
# or, with the suites:
mvn -B test                       # backend; needs Docker
(cd frontend && npm ci && npm test && npm run lint && npm run format:check)
```

The deployable unit is the **whole `target/quarkus-app` directory** — the jar
needs its neighbours:

```bash
sudo rsync -a --delete target/quarkus-app/ /opt/babymonitor/quarkus-app/
sudo chown -R root:root /opt/babymonitor/quarkus-app
```

`java -jar /opt/babymonitor/quarkus-app/quarkus-run.jar` is the entry point.
Run it as the `babymonitor` user, never as root.

A `native` Maven profile exists, but the in-process TFLite path has not been
validated as a GraalVM native image. Use the JVM artifact.

---

## 6. Configuration

Every setting can be supplied as an environment variable: Quarkus maps
`babymonitor.motion.base-dir` to `BABYMONITOR_MOTION_BASE_DIR`, and so on. Put
them in `/etc/babymonitor/babymonitor.env`, owned by root and readable only by
the service group:

```bash
sudo install -o root -g babymonitor -m 0640 /dev/null /etc/babymonitor/babymonitor.env
sudo editor /etc/babymonitor/babymonitor.env
```

Minimal production file:

```ini
DB_URL=jdbc:postgresql://127.0.0.1:5432/babymonitor
DB_USERNAME=babymonitor
DB_PASSWORD=change-me

# Where Motion and the capture tool keep configs, clips and pids.
BABYMONITOR_MOTION_BASE_DIR=/var/lib/babymonitor/motion
BABYMONITOR_AUDIO_BASE_DIR=/var/lib/babymonitor/audio

# The capture device used when a camera has no camera_audio.device row.
BABYMONITOR_AUDIO_DEVICE=default

# Require webhooks to present this secret (Motion gets it from the app).
BABYMONITOR_WEBHOOKS_SECRET=change-me-too

# Keep Swagger UI off a production deployment.
QUARKUS_SWAGGER_UI_ALWAYS_INCLUDE=false
```

Frequently used keys (defaults in brackets):

| Key | Default | Effect |
| --- | --- | --- |
| `QUARKUS_HTTP_PORT` | `8080` | SPA/API port |
| `QUARKUS_HTTP_HOST` | `0.0.0.0` | bind address; set `127.0.0.1` behind a proxy |
| `BABYMONITOR_START_CAMERAS_ON_BOOT` | `true` | boot starts every camera **and its audio** (see section 8) |
| `BABYMONITOR_MOTION_BINARY` | `/usr/bin/motion` | Motion executable |
| `BABYMONITOR_MOTION_BASE_DIR` | `/home/bob/motion` | generated configs, template, notify script, camera dirs |
| `BABYMONITOR_MOTION_WEBHOOK_URL` | `http://localhost:8080/api/webhooks/videoNotification` | what the notify script posts to |
| `BABYMONITOR_MOTION_CHECK_INTERVAL` | `15s` | how often dead cameras are restarted |
| `BABYMONITOR_MOTION_RESET_ON_START` | `true` | kill leftover `motion` processes at boot |
| `BABYMONITOR_AUDIO_BINARY` | `sox` | `sox`, `ffmpeg` or `arecord`; flags are chosen from the name |
| `BABYMONITOR_AUDIO_DEVICE` | `default` | ALSA device when a camera has none |
| `BABYMONITOR_AUDIO_BASE_DIR` | `/home/bob/babymonitor/audio` | pid/stderr files |
| `BABYMONITOR_AUDIO_RESET_ON_START` | `true` | kill leftover captures at boot |
| `BABYMONITOR_AUDIO_MIN_CONFIDENCE` | `0.35` | YAMNet score a class needs |
| `BABYMONITOR_AUDIO_WINDOWS_TO_ALERT` | `2` | consecutive windows above the threshold |
| `BABYMONITOR_AUDIO_COOLDOWN_SECONDS` | `60` | quiet time per camera after an alert |
| `BABYMONITOR_ENVIRONMENT_CHECK_INTERVAL` | `60s` | threshold check cadence |
| `BABYMONITOR_LIGHT_V4L2_CTL` | `/usr/bin/v4l2-ctl` | camera-light probe |
| `BABYMONITOR_LIGHT_FFMPEG` | `/usr/bin/ffmpeg` | camera-light probe |
| `BABYMONITOR_WEBHOOKS_SECRET` | unset | when set, webhooks need `X-Webhook-Secret` |
| `BABYMONITOR_VIDEO_SYSFS_DIR` | `/sys/class/video4linux` | where the setup wizard lists camera nodes |
| `BABYMONITOR_VIDEO_DEV_DIR` | `/dev` | where it checks the nodes exist |
| `BABYMONITOR_AUTH_SESSION_TTL` | `PT12H` | token lifetime; tokens are in memory, so a restart signs everyone out |
| `TZ` | system | timestamps are stored in UTC and rendered in the browser's zone |

Values that belong to one camera (device, resolution, stream port, `target_dir`,
`log_file`, Motion options) live in `babymonitor.cameras` and are edited on that
camera's page, not in this file. Audio's "Listen for cries" preference lives in
`babymonitor.camera_audio.enabled` and is edited on the same page.

---

## 7. Motion

### Install and test the binary

```bash
sudo apt install motion
motion -h | head

# The distribution may ship its own service on the same ports; the app owns
# motion processes, so disable it:
sudo systemctl disable --now motion
```

List the capture nodes (a UVC camera exposes several; pick the capture one):

```bash
v4l2-ctl --list-devices
ffmpeg -f v4l2 -i /dev/video0 -frames:v 1 /tmp/frame.jpg   # sanity check
```

### What the app writes

On the first start/restart of a camera, the app copies two support files into
`BABYMONITOR_MOTION_BASE_DIR` if they are missing:

- `motion.template` — shared defaults; local edits survive, it is never
  overwritten;
- `motion-notify.sh` — the event hook, made executable.

It then writes `motion.config.<n>` from the template plus one `key value` line
per camera row and runs `motion -n -c <base>/motion.config.<n>`. `Restart camera`
sends SIGHUP instead of spawning a second process.

**Absolute paths in the template.** `motion.template` ships with

```
on_movie_end /home/bob/motion/motion-notify.sh %f
```

If you changed the base directory, edit `<base>/motion.template` after the first
start (or pre-create it) so that line points at the real script, then restart the
camera. The same applies to the seeded `target_dir` and `log_file` rows, which
are `/home/bob/motion/...`: change them on the Camera pages or with SQL.

The notify hook needs `curl`; the app passes it
`BABYMONITOR_WEBHOOK_URL`, `BABYMONITOR_CAMERA_NUMBER` and, when configured,
`BABYMONITOR_WEBHOOK_SECRET`.

### Ports and exposure

`stream_port` and `webcontrol_port` come from the camera rows (the seed uses
8081/8091 and 8082/8092). The seed sets `webcontrol_localhost off`, which puts
Motion's **web control** on the LAN without authentication. Turn it back on:

```sql
update babymonitor.cameras set value = 'on' where key = 'webcontrol_localhost';
```

Then restart the cameras (the generated config is rebuilt on start).

The MJPEG stream has to stay reachable from the browser: the app does not proxy
video. `stream_localhost off` is therefore intentional, but it means anyone who
can reach 8081/8082 can watch. Keep the host on a trusted VLAN, or put a proxy in
front (section 9).

### Files worth knowing

| Path | What it is |
| --- | --- |
| `<motion base>/motion.config.<n>` | generated per-camera config |
| `<motion base>/motion.template` | shared defaults, editable |
| `<motion base>/motion-notify.sh` | event hook (curl) |
| `<motion base>/<n>/recordings/` | clips; `target_dir` from the camera row |
| `<motion base>/<n>/logs/motion.log` | Motion's log; `log_file` from the row |
| `<motion base>/<n>/motion.pid` | pid file the app reads after a restart |

---

## 8. Audio capture (SoX)

### Install and test the device

```bash
sudo apt install sox libsox-fmt-alsa alsa-utils

arecord -l                              # card/device numbers
arecord -D plughw:1,0 -f S16_LE -r 16000 -c 1 -d 3 /tmp/test.wav
aplay /tmp/test.wav

# The exact SoX shape the app uses:
timeout 3 sox -q -t alsa default -c 1 -r 16000 -e signed -b 16 -t raw - > /dev/null
```

No error and three seconds of quiet output means the app can capture. `default`
resolves through ALSA/PulseAudio/PipeWire; if that is ambiguous, use an explicit
`plughw:<card>,<device>` for `BABYMONITOR_AUDIO_DEVICE` or in the camera's
`camera_audio.device` row.

`ffmpeg` and `arecord` work too — `CaptureCommand` picks the flag shape from the
binary's name, so `BABYMONITOR_AUDIO_BINARY=/usr/bin/ffmpeg` is enough.

### How capture and detection work

- The tool streams raw s16le mono 16 kHz PCM on stdout; `PcmFramer` slices it into
  15600-sample windows every 7680 samples.
- Windows below `BABYMONITOR_AUDIO_SILENCE_RMS` (0.005) are dropped; the rest are
  scored by the bundled YAMNet model in the same JVM.
- Two consecutive windows above `MIN_CONFIDENCE` (0.35) for an enabled class
  raise an `Audio` notification; the camera then stays quiet for
  `COOLDOWN_SECONDS`.
- Which classes may alert is chosen on the **Audio Configuration** page
  (presets plus the collapsible sound sections), stored in
  `babymonitor.audio_alert_classes`.
- **Listen for cries** per camera is a preference in
  `babymonitor.camera_audio.enabled`. It survives a Stop; boot forces every
  camera and its audio back on, so a muted monitor cannot come back deaf.
- `AudioSupervisor` restarts a capture only while the camera itself is meant to
  be running, so audio never resurrects a stopped camera.

### Boot behaviour

With `BABYMONITOR_START_CAMERAS_ON_BOOT=true` (the default) a reboot starts every
camera **and** its audio capture, regardless of what was running before. A Stop
in the UI lasts until the next restart, and the page says so. Set the flag to
`false` only if you want a cold start where nothing runs until someone presses
**Monitor this camera**.

### Files and diagnostics

| Path | What it is |
| --- | --- |
| `<audio base>/<n>/audio.pid` | capture pid |
| `<audio base>/<n>/audio.err` | capture tool's stderr |
| `/api/cameras/<n>/audio` | running/enabled/pid/device, RMS and last classification |

The Camera page's **Diagnostics** disclosure can start the capture without the
camera, which is the quickest way to prove the microphone works.

---

## 9. Run it under systemd

`/etc/systemd/system/babymonitor.service`:

```ini
[Unit]
Description=BabyMonitor
After=network-online.target postgresql.service
Wants=network-online.target

[Service]
Type=simple
User=babymonitor
Group=babymonitor
SupplementaryGroups=video audio
WorkingDirectory=/opt/babymonitor
EnvironmentFile=/etc/babymonitor/babymonitor.env
ExecStart=/usr/bin/java -Xms256m -Xmx1g -jar /opt/babymonitor/quarkus-app/quarkus-run.jar
# Adjust /usr/bin/java if your JDK 25 lives elsewhere (e.g. /usr/lib/jvm/...).
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
# Keep the default KillMode=control-group: on stop, systemd also terminates the
# motion/sox children the app spawned.

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now babymonitor
curl -fsS http://127.0.0.1:8080/q/health
journalctl -u babymonitor -f
```

Notes:

- The default `Restart=on-failure` covers crashes; a clean `systemctl stop` stays
  stopped.
- On boot the app first kills leftover `motion`/capture processes
  (`*-reset-on-start`), then starts every camera and its audio.
- Health: `/q/health` (aggregate, includes the database), `/q/health/live`,
  `/q/health/ready`. Wire `/q/health/ready` into your uptime check.
- The API schema is at `/q/openapi`; Swagger UI at `/q/swagger-ui` unless
  `QUARKUS_SWAGGER_UI_ALWAYS_INCLUDE=false`.
- Logs are the journal plus the per-camera Motion log and `audio.err` files.

---

## 10. First run and hardening

On a fresh database, signing in opens **Set up your monitor** (`/welcome`): name
the monitor, point each camera at one of the host's `/dev/video*` nodes, then
preview the stream and play the alert tone. The wizard writes the same settings
and camera rows as the normal pages; **Skip for now** marks it done, and
**Run setup again** on the Settings page reopens it. If it finds no devices, the
container or sysfs setup is the first thing to check (section 11).

1. Open `http://<host>:8080` and sign in with a demo account (`Bob` / `Bob`).
2. **Create a real account before deleting the demo ones.** There is no signup
   or password-change endpoint; accounts are rows. Generate a bcrypt hash with a
   `$2a$` prefix (jbcrypt rejects `$2b$`/`$2y$`):

   ```bash
   # One of these, then copy the hash into the INSERT:
   htpasswd -bnBC 12 "" 'a-real-password' | tr -d ':\n' | sed 's/^\$2y\$/\$2a\$/'
   python3 -c 'import bcrypt; print(bcrypt.hashpw(b"a-real-password", bcrypt.gensalt(12)).decode().replace("$2b$", "$2a$"))'
   ```

   `htpasswd` comes from `apache2-utils`; the Python variant needs
   `python3-bcrypt`. Either way the stored hash must start with `$2a$`.

   ```sql
   insert into babymonitor.users (name, password_hash)
   values ('Your Name', '$2a$12$...');
   ```

3. Delete the demo accounts (their passwords are their names):

   ```sql
   delete from babymonitor.notification_dismissals;
   delete from babymonitor.notifications;
   delete from babymonitor.environment;
   delete from babymonitor.users where name in ('Cindy', 'Marij', 'Stephen', 'Bob');
   ```

4. In **Settings**, set `systemName` and `stream_url` (the host base, no port;
   the SPA appends each camera's `stream_port`). In **Cameras**, set each camera's
   name, video device, resolution, frame rate and stream port in the friendly
   form; `webcontrol_port`, `target_dir`, `log_file` and every other Motion
   option are under that camera's **Advanced**.
5. On the **Camera** pages, press **Monitor this camera**; on **Audio
   notifications**, pick a preset; on **Environment Configuration**, set the alert
   bounds and calibrate the camera light if you use it (needs `v4l2-ctl` and
   `ffmpeg`).
6. Set `BABYMONITOR_WEBHOOKS_SECRET` and restart so webhook callers must present
   it. Motion's notify script receives the same secret from the app.
7. Turn Swagger UI off, put the host on a trusted network, and terminate TLS at a
   proxy if the browser will use HTTPS.

### TLS and the MJPEG exception

The app speaks plain HTTP. A reverse proxy can terminate TLS for `:8080`:

```nginx
server {
  listen 443 ssl;
  server_name monitor.example.lan;
  # ssl_certificate / ssl_certificate_key ...
  client_max_body_size 16m;

  # Server-sent events must not be buffered.
  location /api/notifications/stream {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_read_timeout 24h;
  }

  location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
  }
}
```

The video is different: the browser loads `<stream_url>:<stream_port>` directly
from Motion. An HTTPS page cannot load an HTTP MJPEG feed (mixed content), so
either keep the UI on HTTP on the LAN, or terminate TLS for the stream ports too
and set `stream_url` to the HTTPS host. Relaying the stream through the
authenticated app is tracked in `TODO.md` P1.

---

## 11. Upgrades, rollback and operations

```bash
sudo systemctl stop babymonitor
pg_dump -Fc -h 127.0.0.1 -U babymonitor babymonitor > /var/backups/pre-upgrade.dump
sudo rsync -a --delete target/quarkus-app/ /opt/babymonitor/quarkus-app/
sudo systemctl start babymonitor
curl -fsS http://127.0.0.1:8080/q/health/ready
```

- Flyway applies pending migrations at startup. It never rolls back, so a
  rollback means the previous jar **and** the pre-upgrade database dump.
- The session store is in memory: every restart signs everyone out.
- Recordings and notifications are never pruned automatically; watch the disk
  (`df -h /var/lib/babymonitor`) and the notification table's growth.
- If you edit `<base>/motion.template`, generated configs pick it up on the next
  **Monitor this camera** / restart.

### Troubleshooting

| Symptom | Check |
| --- | --- |
| SPA loads but API calls 401 | token expired (`BABYMONITOR_AUTH_SESSION_TTL`, 12 h) or the app restarted; sign in again |
| App will not start | `journalctl -u babymonitor`; database reachable with the `DB_*` values; `flyway_schema_history` exists |
| **Camera unavailable** | the `video_device` row exists and is readable by the `video` group; `ffmpeg -f v4l2 -i <dev> -frames:v 1` works; the device is not held by another process |
| Stream does not play | `stream_localhost`/`stream_port`; `stream_url` in Settings is the host the *browser* resolves; the port is open; `curl <url>:<port>` returns MJPEG |
| Motion does not start | `BABYMONITOR_MOTION_BINARY`, `<motion base>/<n>/logs/motion.log`, generated config not empty, `target_dir` exists |
| **Audio down** | `<audio base>/<n>/audio.err`; `sox` works from the shell **as the `babymonitor` user**; the user is in `audio`; the ALSA device name is right |
| No audio alerts | **Listen for cries** on, a preset selected on **Audio Configuration**, YAMNet class enabled for the sound; `/api/cameras/<n>/audio` shows `lastClass`/`lastConfidence` |
| Clips never appear in the feed | Motion's `on_movie_end` points at the real notify script (section 7); `curl` installed; `BABYMONITOR_MOTION_WEBHOOK_URL` reachable; webhook secret matches |
| Recordings link 404 | `target_dir` row matches where Motion actually writes; the app user can read it |
| Temperature/brightness alerts missing | alert bounds on **Environment Configuration**; `BABYMONITOR_ENVIRONMENT_CHECK_INTERVAL`; for the light sensor, the camera is running and `v4l2-ctl`/`ffmpeg` paths are right |
| Wizard lists no video devices | `/sys/class/video4linux` is readable by the service user; in a container it and `/dev` are bind-mounted (or point `BABYMONITOR_VIDEO_SYSFS_DIR`/`_DEV_DIR` at them); `v4l2-ctl --list-devices` as the same user |
| Database fills up | notifications and environment readings are never pruned; `TODO.md` P3 tracks retention |

---

## 12. Running the checks

On a build host (not the deployment):

```bash
mvn -B test                      # backend suite + SPA build (Node + Docker/Podman)
mvn -B -DskipFrontend test       # backend suite only (Docker/Podman)
cd frontend && npm ci && npm test && npm run lint && npm run format:check
cd frontend && npm run build     # production bundle; also runs inside `mvn package`
```

CI runs the same checks on every push and pull request
(`.github/workflows/ci.yml`).
