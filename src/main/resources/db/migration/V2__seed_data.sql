-- BabyMonitor development seed data.
--
-- Everything here is sample data for a fresh development database: the title
-- and stream host, the four sample accounts, a week of ambient readings, the two
-- demo cameras and a feed of test notifications. A production database would
-- simply not run this migration.
--
-- Each insert is guarded, so applying it to a database that already holds the
-- data (the baseline path for an existing install, where V1 is skipped) neither
-- duplicates a row nor clobbers an edit.
--
-- Delete the environment and notification statements for real readings and a
-- feed of real events only.

-- ---------------------------------------------------------------------------
-- Settings
-- ---------------------------------------------------------------------------

-- The host base of the box running Motion, with no port: the Live stream page
-- builds `<stream_url>:<camera's stream_port>`.
INSERT INTO babymonitor.settings (tag, value)
VALUES ('stream_url', 'http://localhost')
ON CONFLICT (tag) DO NOTHING;

-- The name shown in the UI title bar and the browser tab.
INSERT INTO babymonitor.settings (tag, value)
VALUES ('systemName', 'Cindy & Stephen''s Baby Monitor')
ON CONFLICT (tag) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------

-- Each password is the bcrypt hash of the user's name (demo only). The $2a$
-- prefix is deliberate: the jbcrypt library on the backend rejects $2b$ hashes
-- with "Invalid salt revision".
INSERT INTO babymonitor.users (name, password_hash) VALUES
    ('Cindy',   '$2a$12$hsnq39oWWSK7hshYdnV2/OF0d8wXC2aRTtiiV2WPLUU9e87B3nROy'),
    ('Marij',   '$2a$12$0CRAmFNA/WAhFJRcFevKnO20hY3rGLyKOzu/lLvvWZl79mszdlU86'),
    ('Stephen', '$2a$12$3k0h6ObMfwGbaogVof.pP.08sotmXT6UU3cJihjA5qD.VWplqiiFu'),
    ('Bob',     '$2a$12$vRZy6FF.VMqt/TAWXyDPJ.Jr9VaVeCNWx4slYvUBFOxB39JS1JNxq')
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Environment readings
-- ---------------------------------------------------------------------------

-- One sample per hour for the last seven days, following a daily rhythm
-- (coolest just before dawn, warmest mid-afternoon, brightness peaking around
-- noon) plus a little deterministic jitter so the chart is not a pure sine.
-- Seeded only while the table is empty: the timestamps are relative to now(),
-- so re-running must not stack another week on top.
INSERT INTO babymonitor.environment (event_time, temperature, humidity, brightness)
SELECT sample.taken_at,
       round((21.5
              + 2.5 * sin(2 * pi() * (extract(hour FROM sample.taken_at) - 9) / 24)
              + (sample.n % 5 - 2) * 0.1)::numeric, 2),
       round((45.0
              - 8.0 * sin(2 * pi() * (extract(hour FROM sample.taken_at) - 9) / 24)
              + (sample.n % 7 - 3) * 0.3)::numeric, 2),
       (greatest(0, round(40000 * sin(pi() * (extract(hour FROM sample.taken_at) - 6) / 12)
              + (sample.n % 11) * 150)))::integer
  FROM (SELECT g AS n, now() - (g || ' hours')::interval AS taken_at
          FROM generate_series(0, 167) AS g) AS sample
 WHERE NOT EXISTS (SELECT 1 FROM babymonitor.environment);

-- ---------------------------------------------------------------------------
-- Cameras
-- ---------------------------------------------------------------------------

-- The parameter names are Motion option names: the app writes them, almost
-- verbatim, into the camera's generated motion.config.<n>. Both cameras are USB
-- cameras, so they are addressed by `video_device` (video4linux), not a network
-- URL. The devices below are examples -- set them to your box's (list them with
-- `v4l2-ctl --list-devices`, remembering a UVC camera exposes several nodes).
--
-- Both cameras ship with enabled = FALSE: startup is cold, so nothing runs
-- until a camera is started from its own page. Inserted only while a
-- (camera, parameter) pair is missing, so edits made on the Camera pages
-- survive.
INSERT INTO babymonitor.cameras (camera_number, key, value, enabled) VALUES
    (1, 'camera_name',         'Nursery',                          FALSE),
    (1, 'video_device',        '/dev/video0',                      FALSE),
    (1, 'width',               '1280',                             FALSE),
    (1, 'height',              '720',                              FALSE),
    (1, 'framerate',           '15',                               FALSE),
    (1, 'threshold',           '1500',                             FALSE),
    (1, 'stream_port',         '8081',                             FALSE),
    (1, 'webcontrol_port',     '8091',                             FALSE),
    (1, 'stream_localhost',    'off',                              FALSE),
    (1, 'webcontrol_localhost','off',                              FALSE),
    (1, 'target_dir',          '/home/bob/motion/1/recordings',    FALSE),
    (1, 'log_file',            '/home/bob/motion/1/logs/motion.log', FALSE),
    (2, 'camera_name',         'Hallway',                          FALSE),
    (2, 'video_device',        '/dev/video4',                      FALSE),
    (2, 'width',               '1920',                             FALSE),
    (2, 'height',              '1080',                             FALSE),
    (2, 'framerate',           '10',                               FALSE),
    (2, 'threshold',           '2500',                             FALSE),
    (2, 'stream_port',         '8082',                             FALSE),
    (2, 'webcontrol_port',     '8092',                             FALSE),
    (2, 'stream_localhost',    'off',                              FALSE),
    (2, 'webcontrol_localhost','off',                              FALSE),
    (2, 'target_dir',          '/home/bob/motion/2/recordings',    FALSE),
    (2, 'log_file',            '/home/bob/motion/2/logs/motion.log', FALSE)
ON CONFLICT (camera_number, key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------

-- Twenty test events spread over the last four days, nine of them video alerts
-- with a clip link, so the feed has a realistic list to read, refresh, link
-- from and dismiss. Each row is inserted only while its description is missing,
-- so re-running never duplicates a row. Delete this statement for a feed of
-- real events only.
INSERT INTO babymonitor.notifications (event_time, event_type, description, video_link)
SELECT seed.event_time, seed.event_type, seed.description, seed.video_link
  FROM (VALUES
    (now() - interval '3 minutes',
     'Audio'::babymonitor.event_type,
     'Baby cried for more than 30 seconds.',
     NULL::varchar),
    (now() - interval '12 minutes',
     'Audio'::babymonitor.event_type,
     'Loud noise detected in the nursery.',
     NULL),
    (now() - interval '40 minutes',
     'Video'::babymonitor.event_type,
     'Motion detected near the crib.',
     'http://192.168.0.2:8081/recordings/motion-2026-10-14.mp4'::varchar),
    (now() - interval '55 minutes',
     'Video'::babymonitor.event_type,
     'Baby rolled over in the crib.',
     'http://192.168.0.2:8081/recordings/rolling-over.mp4'),
    (now() - interval '1 hour 20 minutes',
     'Audio'::babymonitor.event_type,
     'Crying stopped after a few seconds.',
     NULL),
    (now() - interval '2 hours',
     'Audio'::babymonitor.event_type,
     'Baby babbled happily for a few minutes.',
     NULL),
    (now() - interval '3 hours',
     'Video'::babymonitor.event_type,
     'Motion detected at the nursery door.',
     'http://192.168.0.2:8081/recordings/door-motion.mp4'),
    (now() - interval '5 hours',
     'Audio'::babymonitor.event_type,
     'Baby woke up and started talking.',
     NULL),
    (now() - interval '8 hours',
     'Video'::babymonitor.event_type,
     'Bedtime recording saved to the archive.',
     'http://192.168.0.2:8081/recordings/night-2026-10-13.mp4'),
    (now() - interval '9 hours',
     'Audio'::babymonitor.event_type,
     'Crying detected for more than 2 minutes.',
     NULL),
    (now() - interval '11 hours',
     'Video'::babymonitor.event_type,
     'Motion detected on the changing table.',
     'http://192.168.0.2:8081/recordings/changing-table.mp4'),
    (now() - interval '14 hours',
     'Audio'::babymonitor.event_type,
     'Baby sneezed three times.',
     NULL),
    (now() - interval '20 hours',
     'Video'::babymonitor.event_type,
     'Nap recording saved to the archive.',
     'http://192.168.0.2:8081/recordings/nap.mp4'),
    (now() - interval '26 hours',
     'Audio'::babymonitor.event_type,
     'Baby cried briefly and settled.',
     NULL),
    (now() - interval '30 hours',
     'Video'::babymonitor.event_type,
     'Motion detected: blanket over the crib rail.',
     'http://192.168.0.2:8081/recordings/blanket.mp4'),
    (now() - interval '34 hours',
     'Audio'::babymonitor.event_type,
     'Coughing detected in the nursery.',
     NULL),
    (now() - interval '2 days',
     'Audio'::babymonitor.event_type,
     'Baby laughed out loud.',
     NULL),
    (now() - interval '2 days 6 hours',
     'Video'::babymonitor.event_type,
     'Motion detected: a parent entered the nursery.',
     'http://192.168.0.2:8081/recordings/visit.mp4'),
    (now() - interval '3 days',
     'Audio'::babymonitor.event_type,
     'Baby slept through the night for the first time.',
     NULL),
    (now() - interval '4 days',
     'Video'::babymonitor.event_type,
     'Evening recording saved to the archive.',
     'http://192.168.0.2:8081/recordings/evening.mp4')
) AS seed(event_time, event_type, description, video_link)
 WHERE NOT EXISTS (SELECT 1
                     FROM babymonitor.notifications existing
                    WHERE existing.description = seed.description);
