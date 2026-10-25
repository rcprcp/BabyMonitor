-- Add the Environment event type.
--
-- EnvironmentSupervisor records a notification when a nursery reading is outside
-- the high/low bounds on the Environment page. A separate migration so a
-- database baselined at V1 still picks the new value up.
--
-- PostgreSQL 12+ allows ADD VALUE inside a transaction as long as the new value
-- is not used until after the commit, which is the case here.
ALTER TYPE babymonitor.event_type ADD VALUE IF NOT EXISTS 'Environment';
