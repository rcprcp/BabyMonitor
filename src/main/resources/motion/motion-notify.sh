#!/bin/sh
# BabyMonitor Motion event hook.
#
# Motion calls this from on_movie_end with the clip it just closed (its %f), so
# every recording shows up in the Notifications feed. The app starts Motion with
# the environment this reads:
#
#   BABYMONITOR_WEBHOOK_URL     the webhook to POST to
#                               (default http://localhost:8080/api/webhooks/videoNotification)
#   BABYMONITOR_WEBHOOK_SECRET  optional, sent as X-Webhook-Secret
#
# Recordings are not served over HTTP yet, so the notification links the clip by
# its file name; see the README.

clip="${1:-}"
[ -n "$clip" ] || exit 0

url="${BABYMONITOR_WEBHOOK_URL:-http://localhost:8080/api/webhooks/videoNotification}"
secret="${BABYMONITOR_WEBHOOK_SECRET:-}"
name=$(basename "$clip")
payload="{\"description\":\"Motion recorded a clip: $name\",\"videoLink\":\"$name\"}"

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
