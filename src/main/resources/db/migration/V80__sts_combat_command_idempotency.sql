-- A STOMP frame can be delivered more than once, so the same command can arrive twice.
-- Remembering the outcome of every command a run has accepted lets the second delivery be
-- answered with what the first one produced instead of resolving the command again.
create table sts_combat_commands (
    id uuid primary key,
    run_id uuid not null references sts_runs (id) on delete cascade,
    action_id varchar(128) not null,
    accepted boolean not null,
    resulting_revision bigint,
    rejection_code varchar(64),
    rejection_message text,
    created_at timestamptz not null default now(),
    constraint uk_sts_combat_commands_run_action unique (run_id, action_id)
);

create index ix_sts_combat_commands_run on sts_combat_commands (run_id);
