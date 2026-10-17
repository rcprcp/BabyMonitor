#!/bin/sh
# Stand-in for `motion` during tests.
#
# MotionService starts `motion -n -c <config>` and records the child's pid. This
# stub ignores those arguments and simply stays alive until it is signalled, so
# the tests can assert that a process was started, adopted and stopped without
# ever opening a camera, binding a stream port or writing to /home/bob/motion.
#
# `exec` replaces this shell, so the pid the app records is the one that answers
# SIGTERM directly and no orphan is left behind.
exec sleep 3600
