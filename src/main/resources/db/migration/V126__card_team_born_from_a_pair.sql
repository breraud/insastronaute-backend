-- Une équipe naît à deux, jamais seule.
--
-- Jusqu'ici une équipe se créait seul puis se remplissait. Une équipe d'une personne
-- n'apporte pourtant rien -- rien n'est mutualisé -- et elle réservait un nom que
-- personne ne pouvait plus prendre. L'état « pas encore d'équipe » la remplace : il est
-- désormais légitime, et la collection s'ouvre sans elle.
--
-- Conséquence sur les invitations : celui qui invite peut ne pas encore avoir d'équipe.
-- Son invitation ne pointe alors vers rien et porte le nom qu'aura l'équipe si l'autre
-- accepte. L'équipe est créée à cet instant-là, avec ses deux membres.

ALTER TABLE card_team_invitations
    ALTER COLUMN team_id DROP NOT NULL;

ALTER TABLE card_team_invitations
    ADD COLUMN proposed_team_name VARCHAR(60);

-- Exactement l'un des deux, jamais les deux ni aucun : soit l'invitation rejoint une
-- équipe existante, soit elle en propose une. Sans cette contrainte, une invitation sans
-- équipe et sans nom serait acceptable en base et n'aboutirait à rien au moment de
-- l'accepter.
ALTER TABLE card_team_invitations
    ADD CONSTRAINT ck_card_team_invitations_team_or_name
        CHECK ((team_id IS NULL) <> (proposed_team_name IS NULL));

-- Les invitations en attente qui pointaient vers une équipe restent valables telles
-- quelles : elles ont une équipe, donc pas de nom proposé, ce que la contrainte accepte.
