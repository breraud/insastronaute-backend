do $$
begin
    if (select count(*) from player_vault_codes) > 1000000 then
        raise exception 'Cannot migrate more than 1000000 vault codes';
    end if;
end
$$;

create sequence player_vault_code_sequence
    as bigint
    minvalue 0
    maxvalue 999999
    start with 0
    no cycle;

with numbered_codes as (
    select id, row_number() over (order by issued_at, id) - 1 as code_number
    from player_vault_codes
)
update player_vault_codes as codes
set code = lpad(numbered_codes.code_number::text, 6, '0')
from numbered_codes
where codes.id = numbered_codes.id;

select setval(
    'player_vault_code_sequence',
    (select count(*) from player_vault_codes),
    false
);

alter table player_vault_codes
    alter column code type varchar(6);

alter table player_vault_codes
    add constraint chk_player_vault_codes_six_digits
    check (code ~ '^[0-9]{6}$');
