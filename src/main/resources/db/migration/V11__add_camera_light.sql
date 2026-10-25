-- Camera-derived brightness: configuration, calibration and the last sample.
--
-- A single row (id 1) describes the light sensor. The six *_luma columns are
-- the luma values captured for each band during calibration; the band cut
-- points are their midpoints. last_* is the most recent sample. The camera's
-- device and stream port still come from babymonitor.cameras.
create table babymonitor.camera_light (
    id                integer primary key default 1,
    camera_number     integer not null default 1,
    exposure_ms       integer not null default 20,
    enabled           boolean not null default false,
    dark_luma         double precision,
    very_dim_luma     double precision,
    dim_luma          double precision,
    moderate_luma     double precision,
    bright_luma       double precision,
    very_bright_luma  double precision,
    last_luma         double precision,
    last_band         varchar(20),
    last_lux          integer,
    last_sample_at    timestamptz,
    last_alerted_band varchar(20),
    constraint camera_light_single_row check (id = 1)
);

insert into babymonitor.camera_light (id) values (1) on conflict do nothing;
