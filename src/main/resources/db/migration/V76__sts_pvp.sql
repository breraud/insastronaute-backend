create table sts_pvp_profiles (
    id uuid primary key,
    user_id uuid not null unique references users(id) on delete cascade,
    selected_character varchar(80) not null,
    elo integer not null default 1000,
    wins_ranked integer not null default 0,
    losses_ranked integer not null default 0,
    wins_friendly integer not null default 0,
    losses_friendly integer not null default 0,
    character_exp jsonb not null default '{}'::jsonb,
    character_stats jsonb not null default '{}'::jsonb,
    preferred_friendly boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table sts_pvp_decks (
    id uuid primary key,
    owner_id uuid not null references users(id) on delete cascade,
    name varchar(120) not null,
    selected_character varchar(80) not null,
    is_active boolean not null default false,
    card_ids jsonb not null default '[]'::jsonb,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_sts_pvp_decks_owner_updated_at on sts_pvp_decks(owner_id, updated_at desc);
create index idx_sts_pvp_decks_owner_character on sts_pvp_decks(owner_id, selected_character);

create table sts_pvp_matchmaking_queue (
    id uuid primary key,
    user_id uuid not null unique references users(id) on delete cascade,
    elo_snapshot integer not null,
    friendly boolean not null,
    queued_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_sts_pvp_queue_friendly_queued_at on sts_pvp_matchmaking_queue(friendly, queued_at);

create table sts_pvp_battles (
    id uuid primary key,
    status varchar(32) not null,
    friendly boolean not null,
    teams jsonb not null,
    state jsonb not null,
    heartbeat jsonb not null,
    turn_deadline timestamp with time zone,
    winner_team integer,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table sts_pvp_challenges (
    id uuid primary key,
    challenger_id uuid not null references users(id) on delete cascade,
    challenged_id uuid not null references users(id) on delete cascade,
    friendly boolean not null,
    status varchar(32) not null,
    battle_id uuid references sts_pvp_battles(id) on delete set null,
    expires_at timestamp with time zone not null,
    responded_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_sts_pvp_challenges_target_status_created on sts_pvp_challenges(challenged_id, status, created_at desc);

create table sts_pvp_notifications (
    id uuid primary key,
    recipient_id uuid not null references users(id) on delete cascade,
    actor_id uuid references users(id) on delete set null,
    type varchar(64) not null,
    title varchar(180) not null,
    body text not null,
    payload jsonb not null default '{}'::jsonb,
    read_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create index idx_sts_pvp_notifications_recipient_created on sts_pvp_notifications(recipient_id, created_at desc);
