-- High/low bounds for the nursery environment, edited on the Environment page.
--
-- Temperature is stored in Fahrenheit because that is how the page's bounds are
-- presented; the readings in babymonitor.environment remain Celsius. One row
-- (id 1) holds the pair for each metric.
create table babymonitor.environment_thresholds (
    id               integer      primary key default 1,
    humidity_low     numeric(5,2) not null default 30,
    humidity_high    numeric(5,2) not null default 75,
    temperature_low  numeric(5,2) not null default 66,
    temperature_high numeric(5,2) not null default 76,
    brightness_low   integer      not null default 200,
    brightness_high  integer      not null default 2000,
    constraint environment_thresholds_single_row check (id = 1)
);

insert into babymonitor.environment_thresholds (id) values (1) on conflict do nothing;
