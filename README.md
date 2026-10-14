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
  resource/                     AuthResource, SettingsResource,
                                NotificationsResource, WebhookResource
  service/                      NotificationService
  web/                          SpaFallbackFilter
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
  `streaming_url` = `http://192.168.0.2:8081`
- `babymonitor.users` (`id`, `name`, `password_hash`) — seeded with `Cindy`,
  `Marij`, `Stephen`, `Bob`, each with password equal to the name (bcrypt hash,
  `$2a$` prefix — the jbcrypt library rejects `$2b$`)
- `babymonitor.notifications` (`id`, `event_time`, `event_type`, `description`,
  `video_link`) — the feed behind the Notifications page. `event_type` is the
  PostgreSQL enum `babymonitor.event_type` with the values `Video` and `Audio`;
  a few demo rows are inserted only while the table is still empty

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
| GET         | `/api/notifications`                   | newest first                   |
| DELETE      | `/api/notifications/{id}`              | dismiss                        |
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

## SPA

React Router routes: `/login`, `/` (stream), `/notifications`, `/settings`. The
app boots into the live stream (from `streaming_url`); a blue title bar with a
hamburger menu (upper left) links the pages. `SpaFallbackFilter` serves
`index.html` for client-side routes so a deep link like `/settings` works on
refresh.
