-- Le registre des gestes d'administration. En ajout seul : aucun code n'y fait
-- d'UPDATE ni de DELETE, et aucun endpoint ne l'expose en ecriture.
CREATE TABLE admin_action_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    target_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(40) NOT NULL,
    details_json TEXT NOT NULL,
    backfilled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_admin_action_events_action CHECK (action IN (
        'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
        'TOKENS_GRANTED', 'TOKENS_REVOKED',
        'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
        'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED'
    ))
);

-- Les trois axes de lecture de la page : la chronologie, l'auteur, le joueur.
CREATE INDEX ix_admin_action_events_occurred_at ON admin_action_events (occurred_at DESC);
CREATE INDEX ix_admin_action_events_actor ON admin_action_events (actor_user_id, occurred_at DESC);
CREATE INDEX ix_admin_action_events_target ON admin_action_events (target_user_id, occurred_at DESC);
