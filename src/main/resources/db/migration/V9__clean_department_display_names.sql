UPDATE departments
SET name = btrim(regexp_replace(name, '\s*\([^)]*\)', '', 'g'))
WHERE name ~ '\([^)]*\)';

UPDATE departments
SET short_name = 'CFI'
WHERE keycloak_group_name = 'CGC';

UPDATE departments
SET short_name = 'MRIE'
WHERE keycloak_group_name = 'GPGR';
