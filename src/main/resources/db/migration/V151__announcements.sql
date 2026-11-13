-- Les annonces affichées aux joueurs : correctifs, informations, incidents.
--
-- Une annonce vit dans une fenêtre plutôt que d'être « allumée » puis oubliée :
-- une panne annoncée reste sinon à l'écran une semaine après sa résolution.
-- `active` permet de la couper avant terme, sans perdre son texte.
CREATE TABLE announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    -- INFO, PATCH ou INCIDENT : le ton de l'affichage en dépend, pas le contenu.
    kind VARCHAR(20) NOT NULL DEFAULT 'INFO',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_announcements_kind CHECK (kind IN ('INFO', 'PATCH', 'INCIDENT')),
    CONSTRAINT ck_announcements_window CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

-- « Compris » suit le joueur et non l'appareil : refermer sur son téléphone doit
-- valoir aussi sur l'ordinateur du lendemain. D'où une table plutôt qu'un
-- stockage local.
CREATE TABLE announcement_dismissals (
    announcement_id UUID NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dismissed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (announcement_id, user_id)
);

CREATE INDEX ix_announcement_dismissals_user ON announcement_dismissals (user_id);
