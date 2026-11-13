-- La Mediatheque : parcourir les preuves pour monter l'after movie, en retenir
-- une selection commune et l'exporter. Voir
-- docs/superpowers/specs/2026-10-02-mediatheque-design.md.

-- Qui, en plus des administrateurs, peut ouvrir la Mediatheque. La ligne ne suffit
-- pas : il faut aussi un role de staff, verifie a chaque requete.
CREATE TABLE media_library_grants (
    user_id    UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    granted_by UUID NOT NULL REFERENCES users(id),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- La selection, unique et commune. Un media est un fichier d'une soumission,
-- designe par sa position dans proof_data.
CREATE TABLE media_library_selection (
    submission_id UUID NOT NULL REFERENCES challenge_submissions(id) ON DELETE CASCADE,
    media_index   INTEGER NOT NULL CHECK (media_index >= 0),
    selected_by   UUID NOT NULL REFERENCES users(id),
    selected_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (submission_id, media_index)
);

-- Un export fige la selection a un instant : ses parties restent coherentes entre
-- elles pendant que la selection continue de bouger.
CREATE TABLE media_library_exports (
    id          UUID PRIMARY KEY,
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    item_count  INTEGER NOT NULL,
    total_bytes BIGINT NOT NULL,
    part_count  INTEGER NOT NULL
);

CREATE TABLE media_library_export_items (
    export_id     UUID NOT NULL REFERENCES media_library_exports(id) ON DELETE CASCADE,
    item_position INTEGER NOT NULL,
    part_number   INTEGER NOT NULL,
    submission_id UUID NOT NULL REFERENCES challenge_submissions(id) ON DELETE CASCADE,
    media_index   INTEGER NOT NULL,
    size_bytes    BIGINT NOT NULL,
    selected_by   UUID NOT NULL REFERENCES users(id),
    selected_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (export_id, item_position)
);

-- La lecture d'une partie : un export, un numero de partie, dans l'ordre fige.
CREATE INDEX idx_media_library_export_items_part
    ON media_library_export_items (export_id, part_number, item_position);

-- Cinq gestes de plus au registre. Postgres ne sait pas etendre une contrainte CHECK
-- en place, d'ou les deux ordres pour chacune des deux.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'CHALLENGE_GRACE_GRANTED', 'CHALLENGE_BONUS_ADJUSTED',
    'CHALLENGE_PROOFS_PURGED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED',
    'COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED',
    'CHALLENGE_DELETED', 'CHALLENGE_RESTORED', 'PLAYER_DELETED',
    'PLACE_CANVAS_CENSORED', 'PLACE_BANNED', 'PLACE_UNBANNED',
    'PHYSICAL_CARDS_CREATED',
    'ONBOARDING_VALIDATED', 'ONBOARDING_REJECTED',
    'SHOP_ORDER_DELETED',
    'MEDIA_LIBRARY_ACCESS_GRANTED', 'MEDIA_LIBRARY_ACCESS_REVOKED',
    'MEDIA_LIBRARY_FILE_DOWNLOADED',
    'MEDIA_LIBRARY_EXPORT_CREATED', 'MEDIA_LIBRARY_EXPORT_DOWNLOADED'
));

ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_target_presence;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_target_presence CHECK (
    (action IN ('COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED', 'CHALLENGE_DELETED',
                'CHALLENGE_RESTORED', 'PLAYER_DELETED', 'PLACE_CANVAS_CENSORED',
                'PHYSICAL_CARDS_CREATED', 'CHALLENGE_PROOFS_PURGED',
                'MEDIA_LIBRARY_EXPORT_CREATED', 'MEDIA_LIBRARY_EXPORT_DOWNLOADED')) = (target_user_id IS NULL)
);
