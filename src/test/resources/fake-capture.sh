#!/bin/sh
# Stand-in for sox/ffmpeg during tests.
#
# AudioService starts the capture binary and reads its stdout as raw little-
# endian s16le mono PCM. This stub never opens a microphone: it scans its
# arguments for the word "silent" and then streams either zeros (quiet) or
# random bytes (loud) until it is killed. Tests pass "silent" as the device to
# exercise the quiet path and anything else for the audible path.
case "$*" in
  *silent*) source=/dev/zero ;;
  *)        source=/dev/urandom ;;
esac
while :; do
  head -c 65536 "$source"
done
