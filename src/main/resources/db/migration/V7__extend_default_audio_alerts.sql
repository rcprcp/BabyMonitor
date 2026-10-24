-- Extend the shipped default alert set from classes 0-23 to 0-61: the
-- human-sound classes that start the Singing & Music Genres group (singing,
-- humming, breathing, cough, heartbeat, cheering, ...). Only the newly
-- defaulted range is turned on, so a customized set elsewhere is left alone.
update babymonitor.audio_alert_classes
   set enabled = true
 where class_index between 24 and 61;
