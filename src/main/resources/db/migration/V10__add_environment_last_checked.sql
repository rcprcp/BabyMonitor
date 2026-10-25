-- Where EnvironmentSupervisor has read up to: the newest environment reading id
-- it has already checked. A new sample is checked exactly once, and a restart
-- does not re-alert the sample already seen.
ALTER TABLE babymonitor.environment_thresholds
    ADD COLUMN last_checked_id BIGINT NOT NULL DEFAULT 0;
