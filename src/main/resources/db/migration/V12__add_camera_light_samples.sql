-- Camera light history: one row per calibrated probe, used by the Environment
-- page to show brightness as a band instead of a lux reading. The single-row
-- camera_light table still holds the latest sample; this table is the history.
create table babymonitor.camera_light_samples (
    id         bigserial primary key,
    sampled_at timestamptz not null,
    luma       double precision not null,
    band       varchar(20) not null,
    proxy_lux  integer not null
);

create index camera_light_samples_sampled_at_idx
    on babymonitor.camera_light_samples (sampled_at);
