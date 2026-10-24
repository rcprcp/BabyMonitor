-- Which YAMNet classes are allowed to raise an Audio notification.
--
-- The class names and their category ranges live in the bundled class map
-- (src/main/resources/yamnet/yamnet_class_map.csv); this table stores only the
-- per-class switch, so the alert set can be edited from the Audio Notification
-- Config page without a rebuild. A fresh install starts with the Speech & Human
-- Sounds group (indices 0-23) on and everything else off.
create table babymonitor.audio_alert_classes (
    class_index integer primary key,
    enabled     boolean not null default false
);

insert into babymonitor.audio_alert_classes (class_index, enabled)
select sample.index, sample.index between 0 and 23
  from generate_series(0, 520) as sample(index);
