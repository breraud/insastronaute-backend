create table sts_runs (
    id uuid primary key,
    owner_id uuid not null references users(id) on delete cascade,
    status varchar(32) not null,
    data_version varchar(80) not null,
    selected_character varchar(80) not null,
    act integer not null default 0,
    current_floor integer not null default 0,
    current_node_id integer not null default 0,
    entered_node_id integer,
    player_state jsonb not null,
    run_inventory jsonb not null,
    map_state jsonb not null,
    pending_rewards jsonb not null,
    active_encounter jsonb,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_sts_runs_owner_status_updated_at on sts_runs(owner_id, status, updated_at desc);
