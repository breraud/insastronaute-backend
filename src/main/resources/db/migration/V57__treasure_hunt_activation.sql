create table treasure_hunt_settings (
    id boolean primary key default true check (id),
    active boolean not null default false
);

insert into treasure_hunt_settings (id, active) values (true, false);
