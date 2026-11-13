-- Les filieres par apprentissage, en tant qu'audience et pas en tant que departement.
--
-- Un apprenti reste rattache a sa filiere parente : `users.department_id` ne change
-- pas, et c'est ce qui le fait compter dans Place, dans les defis et dans le
-- classement de son departement comme n'importe quel autre etudiant.
--
-- La ligne PERF n'est le departement de personne. Elle existe pour etre ciblee par
-- un evenement, et pour cela seulement. Son `keycloak_group_name` est nul a dessein :
-- les deux seuls endroits qui ecrivent `users.department_id` — la connexion et le
-- balayage de resynchronisation — cherchent un departement par son nom de groupe,
-- apres avoir ecarte les valeurs vides. Aucune reponse de l'annuaire ne peut donc
-- designer PERF, meme si quelqu'un cree un groupe de ce nom dans Keycloak demain.
ALTER TABLE departments ADD COLUMN virtual BOOLEAN NOT NULL DEFAULT FALSE;

-- La coche est posee a la main par un administrateur. L'annuaire ne la connait pas.
ALTER TABLE users ADD COLUMN perf BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO departments (id, name, short_name, keycloak_group_name, color, virtual)
VALUES (gen_random_uuid(), 'Filieres par apprentissage', 'PERF', NULL, '#A855F7', TRUE);
