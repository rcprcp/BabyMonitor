-- First-run marker for the setup wizard.
--
-- A fresh install starts at 'false', so the wizard appears on first login. An
-- existing deployment is only marked complete when something already looks
-- configured -- a real account, a changed camera device, or an edited name or
-- stream host -- so upgrading does not interrupt a working monitor with a
-- wizard it does not need. The wizard itself can be re-run from Settings, which
-- flips this back to 'false'.
INSERT INTO babymonitor.settings (tag, value)
SELECT 'onboardingComplete',
       CASE WHEN
           EXISTS (SELECT 1 FROM babymonitor.users
                    WHERE name NOT IN ('Cindy', 'Marij', 'Stephen', 'Bob'))
           OR EXISTS (SELECT 1 FROM babymonitor.cameras
                       WHERE key = 'video_device'
                         AND value NOT IN ('/dev/video0', '/dev/video4'))
           OR EXISTS (SELECT 1 FROM babymonitor.settings
                       WHERE tag = 'systemName'
                         AND value <> 'Cindy & Stephen''s Baby Monitor')
           OR EXISTS (SELECT 1 FROM babymonitor.settings
                       WHERE tag = 'stream_url'
                         AND value <> 'http://localhost')
       THEN 'true' ELSE 'false' END
ON CONFLICT (tag) DO NOTHING;
