create table treasure_hunt_contents (
    id uuid primary key,
    checkpoint_id uuid references treasure_hunt_checkpoints(id) on delete cascade,
    vault_id boolean references treasure_hunt_vault(id) on delete cascade,
    position integer not null check (position > 0),
    type varchar(20) not null,
    content_text text,
    storage_path varchar(1024),
    article_id uuid references articles(id),
    check ((checkpoint_id is null) <> (vault_id is null)),
    unique (checkpoint_id, position),
    unique (vault_id, position)
);

insert into treasure_hunt_contents (id, checkpoint_id, position, type, content_text, storage_path)
select gen_random_uuid(), id, 1,
       case when reward_type in ('IMAGE', 'VIDEO', 'PDF') then 'MEDIA' else reward_type end,
       reward_text, reward_storage_path
from treasure_hunt_checkpoints;

insert into treasure_hunt_contents (id, vault_id, position, type, storage_path)
select gen_random_uuid(), id, 1, 'MEDIA', final_pdf_storage_path
from treasure_hunt_vault;

alter table treasure_hunt_checkpoints drop column reward_type;
alter table treasure_hunt_checkpoints drop column reward_text;
alter table treasure_hunt_checkpoints drop column reward_storage_path;
alter table treasure_hunt_vault drop column final_pdf_storage_path;
alter table treasure_hunt_paths drop constraint treasure_hunt_paths_position_check;
alter table treasure_hunt_paths add constraint treasure_hunt_paths_position_check check (position between 1 and 12);
