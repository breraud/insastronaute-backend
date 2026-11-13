create function treasure_hunt_random_vault_code()
returns varchar(6)
language plpgsql
volatile
as $$
declare
    alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    generated text := '';
begin
    for character_index in 1..6 loop
        generated := generated || substr(
                alphabet,
                floor(random() * length(alphabet))::integer + 1,
                1
        );
    end loop;
    return generated::varchar(6);
end
$$;

alter table player_vault_codes
    drop constraint player_vault_codes_code_key;

do $$
declare
    code_row record;
    candidate varchar(6);
begin
    for code_row in
        select id, user_id
        from player_vault_codes
        order by issued_at, id
    loop
        loop
            candidate := treasure_hunt_random_vault_code();
            exit when not exists (
                select 1
                from player_vault_codes
                where user_id = code_row.user_id
                  and code = candidate
                  and id <> code_row.id
            );
        end loop;

        update player_vault_codes
        set code = candidate
        where id = code_row.id;
    end loop;
end
$$;

alter table player_vault_codes
    add constraint uq_player_vault_codes_user_code unique (user_id, code);

drop function treasure_hunt_random_vault_code();
drop function treasure_hunt_vault_code(bigint);
drop sequence player_vault_code_sequence;
