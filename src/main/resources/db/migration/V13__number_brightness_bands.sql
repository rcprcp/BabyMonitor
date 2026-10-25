-- Store brightness bands as their stable numbers (1 = dark .. 6 = very bright)
-- instead of names. The captured luma calibration only maps a new probe to one
-- of these numbers; stored bands are never renumbered by a recalibration.
alter table babymonitor.camera_light_samples
    alter column band type integer using (
        case band
            when 'dark' then 1
            when 'very-dim' then 2
            when 'dim' then 3
            when 'moderate' then 4
            when 'bright' then 5
            when 'very-bright' then 6
        end
    );

alter table babymonitor.camera_light
    alter column last_band type integer using (
        case last_band
            when 'dark' then 1
            when 'very-dim' then 2
            when 'dim' then 3
            when 'moderate' then 4
            when 'bright' then 5
            when 'very-bright' then 6
        end
    ),
    alter column last_alerted_band type integer using (
        case last_alerted_band
            when 'dark' then 1
            when 'very-dim' then 2
            when 'dim' then 3
            when 'moderate' then 4
            when 'bright' then 5
            when 'very-bright' then 6
        end
    );
