-- Une annonce peut renvoyer vers un onglet du site.
--
-- « Le jeu rouvre ce soir » sans lien laisse chercher l'onglet : le bouton évite
-- cette recherche, et c'est le clic qu'on attendait de toute façon.
--
-- Un chemin interne, jamais une adresse extérieure : une annonce est écrite en
-- administration et s'ouvre chez tous les joueurs, donc elle ne doit pas pouvoir
-- les emmener hors du site. La contrainte l'impose ici et pas seulement dans le
-- panneau, qui n'est qu'un des chemins d'écriture.
ALTER TABLE announcements
    ADD COLUMN link VARCHAR(120);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_link CHECK (
        link IS NULL OR link ~ '^/[A-Za-z0-9][A-Za-z0-9/_-]*$'
    );
