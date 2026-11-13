-- A STOMP frame can be delivered more than once, so the same PvP command can arrive twice.
-- The PvE ledger cannot answer for it: its run_id points at sts_runs, and a battle is not a run.
-- Two different players also sit in one battle and each numbers its own actions, so the same
-- action_id can legitimately come from both; the acting player is therefore part of the key.
create table sts_pvp_battle_commands (
    id uuid primary key,
    battle_id uuid not null references sts_pvp_battles (id) on delete cascade,
    acting_user_id uuid not null references users (id) on delete cascade,
    action_id varchar(128) not null,
    resulting_revision bigint not null,
    created_at timestamptz not null default now(),
    constraint uk_sts_pvp_battle_commands_battle_user_action
        unique (battle_id, acting_user_id, action_id)
);

create index ix_sts_pvp_battle_commands_battle on sts_pvp_battle_commands (battle_id);
