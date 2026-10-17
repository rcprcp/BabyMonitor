-- Store each account's most-recently viewed camera so the Live Stream page can
-- restore it after a full browser restart or on any device.  Nullable because
-- old installs will fill it in via the new API; we default to "1" for them.
ALTER TABLE babymonitor.users ADD COLUMN IF NOT EXISTS active_camera INTEGER DEFAULT 1;
