#!/bin/sh
# Stand-in for `motion` during tests.
#
# MotionService starts `motion -n -c <config>` and records the child's pid, then
# reloads it with a SIGHUP and stops it with a SIGTERM. This stub ignores those
# arguments and stays alive until it is signalled, so the tests can assert that
# a process was started, reloaded and stopped without ever opening a camera,
# binding a stream port or writing to /home/bob/motion.
#
# Real motion re-reads its config on SIGHUP rather than exiting, so the stub
# has to survive it too -- otherwise "reload" would look like "died" and Apply
# would appear to spawn a second process. The shell traps HUP and keeps itself
# alive; the short sleep is just a wakeup, and the pid the app records is the
# shell's, which answers SIGTERM directly on stop.
trap '' HUP
while :; do
  sleep 1
done
