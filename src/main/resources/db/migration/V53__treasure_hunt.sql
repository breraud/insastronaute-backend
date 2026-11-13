create table treasure_hunt_paths (
    id uuid primary key,
    department_id uuid not null unique references departments(id),
    position integer not null unique check (position between 1 and 6),
    name varchar(120) not null
);

create table treasure_hunt_checkpoints (
    id uuid primary key,
    path_id uuid not null references treasure_hunt_paths(id) on delete cascade,
    step_number integer not null check (step_number > 0),
    code_hash bytea not null unique,
    reward_type varchar(20) not null,
    reward_text text,
    reward_storage_path varchar(1024),
    unique(path_id, step_number)
);

create table player_checkpoint_progress (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    checkpoint_id uuid not null references treasure_hunt_checkpoints(id) on delete cascade,
    validated_at timestamp with time zone not null,
    unique(user_id, checkpoint_id)
);

create table player_vault_codes (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    path_id uuid not null references treasure_hunt_paths(id) on delete cascade,
    code varchar(64) not null unique,
    issued_at timestamp with time zone not null,
    unique(user_id, path_id)
);

create table treasure_hunt_vault (
    id boolean primary key default true check (id),
    final_pdf_storage_path varchar(1024) not null
);

create table player_vault_unlocks (
    id uuid primary key,
    user_id uuid not null unique references users(id) on delete cascade,
    unlocked_at timestamp with time zone not null
);

create index idx_treasure_hunt_checkpoints_path_step on treasure_hunt_checkpoints(path_id, step_number);
create index idx_player_checkpoint_progress_user_checkpoint on player_checkpoint_progress(user_id, checkpoint_id);
create index idx_player_vault_codes_user_path on player_vault_codes(user_id, path_id);
