#!/bin/sh
# BabyMonitor Motion event hook.
#
# Motion calls this from on_movie_end with the clip it just closed (its %f), so
# every recording shows up in the Notifications feed. The notification links the
# clip through the app's own recordings route
# (/api/cameras/<n>/recordings/<file>), which the Notifications page fetches with
# its bearer token and plays in the browser -- recordings are not public.
#
# The app starts Motion with the environment this reads:
#
#   BABYMONITOR_WEBHOOK_URL      the webhook to POST to
#                                (default http://localhost:8080/api/webhooks/videoNotification)
#   BABYMONITOR_CAMERA_NUMBER    the camera the clip belongs to, used to build the link
#   BABYMONITOR_WEBHOOK_SECRET   optional, sent as X-Webhook-Secret

clip="${1:-}"
[ -n "$clip" ] || exit 0

url="${BABYMONITOR_WEBHOOK_URL:-http://localhost:8080/api/webhooks/videoNotification}"
secret="${BABYMONITOR_WEBHOOK_SECRET:-}"
camera="${BABYMONITOR_CAMERA_NUMBER:-}"
name=$(basename "$clip")

# Fall back to <base>/<n>/recordings/<file> when the app did not tell us which
# camera this is (e.g. Motion started by hand).
case "$camera" in
    ''|*[!0-9]*)
        camera=$(basename "$(dirname "$(dirname "$clip")")")
        case "$camera" in
            ''|*[!0-9]*) camera="" ;;
        esac
        ;;
esac

if [ -n "$camera" ]; then
    videoLink="/api/cameras/$camera/recordings/$name"
else
    videoLink="$name"
fi
payload="{\"description\":\"Motion recorded a clip: $name\",\"videoLink\":\"$videoLink\"}"

if [ -n "$secret" ]; then
    curl -s -m 5 -X POST "$url" \
        -H 'Content-Type: application/json' \
        -H "X-Webhook-Secret: $secret" \
        -d "$payload" >/dev/null 2>&1 || true
else
    curl -s -m 5 -X POST "$url" \
        -H 'Content-Type: application/json' \
        -d "$payload" >/dev/null 2>&1 || true
fi
