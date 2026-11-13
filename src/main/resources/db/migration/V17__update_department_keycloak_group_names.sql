UPDATE departments
SET keycloak_group_name = 'CFI'
WHERE short_name = 'CFI'
  AND keycloak_group_name = 'CGC';

UPDATE departments
SET keycloak_group_name = 'MRIE'
WHERE short_name = 'MRIE'
  AND keycloak_group_name = 'GPGR';
