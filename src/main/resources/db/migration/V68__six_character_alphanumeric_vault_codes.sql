do $$
begin
    if (select count(*) from player_vault_codes) > 1073741824 then
        raise exception 'Cannot migrate more than 1073741824 vault codes';
    end if;
end
$$;

alter table player_vault_codes
    drop constraint chk_player_vault_codes_six_digits;

alter sequence player_vault_code_sequence
    maxvalue 1073741823
    no cycle;

create or replace function treasure_hunt_vault_code(sequence_value bigint)
returns varchar(6)
language plpgsql
immutable
strict
as $$
declare
    alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    remaining bigint := sequence_value;
    encoded text := '';
begin
    if sequence_value < 0 or sequence_value > 1073741823 then
        raise exception 'Vault code sequence value % is outside the six-character range', sequence_value;
    end if;

    for digit_index in 1..6 loop
        encoded := substr(alphabet, ((remaining % 32) + 1)::integer, 1) || encoded;
        remaining := remaining / 32;
    end loop;

    return encoded::varchar(6);
end
$$;

with numbered_codes as (
    select id, row_number() over (order by issued_at, id) - 1 as code_number
    from player_vault_codes
)
update player_vault_codes as codes
set code = treasure_hunt_vault_code(numbered_codes.code_number)
from numbered_codes
where codes.id = numbered_codes.id;

select setval(
    'player_vault_code_sequence',
    case
        when count(*) = 1073741824 then 1073741823
        else count(*)
    end,
    count(*) = 1073741824
)
from player_vault_codes;

alter table player_vault_codes
    add constraint chk_player_vault_codes_six_alphanumeric
    check (code ~ '^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$');
