-- Le choix du joueur : effacer ses preuves une fois le defi valide.
--
-- Une preuve de defi est une photo de soi, souvent avec d'autres, envoyee pour
-- qu'un moderateur la regarde. Cet examen fini et le defi valide, elle n'a plus
-- d'usage — mais elle reste, et rien ne permettait de demander qu'elle parte.
--
-- Le reglage est porte par le joueur, pas par la soumission : on ne veut pas
-- redemander a chaque envoi ce qui est une position de principe. Il vaut pour
-- les preuves a venir comme pour celles deja en attente.
--
-- Faux par defaut : c'est le comportement actuel, et la conservation reste ce
-- qui permet de revenir sur une validation contestee.
ALTER TABLE users
    ADD COLUMN purge_proofs_after_validation BOOLEAN NOT NULL DEFAULT FALSE;
