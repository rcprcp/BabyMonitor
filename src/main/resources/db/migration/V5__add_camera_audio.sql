create table babymonitor.camera_audio (
    camera_number integer not null,
    enabled boolean not null default false,
    device varchar(100),
    constraint camera_audio_pkey primary key (camera_number)
);
