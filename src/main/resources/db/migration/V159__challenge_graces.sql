-- La grace : rendre a un joueur le droit de reposter une preuve apres un refus.
--
-- Un refus n'empeche pas de recommencer — ce n'est pas lui qui bloque. Ce qui
-- bloque, c'est la fenetre du defi : une preuve envoyee la veille de la
-- fermeture, lue par un moderateur le lendemain, est refusee alors qu'il n'y a
-- plus une minute pour la refaire. Le joueur a fait le defi, il a juste mal
-- cadre sa photo, et il n'a aucun recours.
--
-- La grace rouvre la fenetre pour ce joueur et ce defi, le temps qu'on lui
-- accorde. Elle ne rouvre rien d'autre : ni le defi pour les autres, ni un defi
-- qui n'a pas encore commence.
CREATE TABLE challenge_graces (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL,
    user_id UUID NOT NULL,
    -- La soumission refusee a l'origine de la grace. Elle peut disparaitre sans
    -- que la grace perde son sens : ce qui compte, c'est le delai accorde.
    submission_id UUID,
    granted_by_user_id UUID,
    reason TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_challenge_graces_challenge
        FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_graces_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_graces_submission
        FOREIGN KEY (submission_id) REFERENCES challenge_submissions(id) ON DELETE SET NULL,
    CONSTRAINT fk_challenge_graces_granted_by
        FOREIGN KEY (granted_by_user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- La lecture chaude : « ce joueur a-t-il une grace en cours sur ce defi ? », et
-- « toutes ses graces en cours », une fois par ouverture de la page des defis.
CREATE INDEX idx_challenge_graces_user_expiry
    ON challenge_graces(user_id, expires_at DESC);

CREATE INDEX idx_challenge_graces_challenge
    ON challenge_graces(challenge_id, user_id, expires_at DESC);

-- La contrainte se reecrit en entier : elle enumere les gestes, et en ajouter un
-- sans reprendre la liste effacerait ceux ajoutes entre-temps.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'CHALLENGE_GRACE_GRANTED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED',
    'COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED',
    'CHALLENGE_DELETED', 'PLAYER_DELETED',
    'PLACE_CANVAS_CENSORED', 'PLACE_BANNED', 'PLACE_UNBANNED',
    'PHYSICAL_CARDS_CREATED',
    'ONBOARDING_VALIDATED', 'ONBOARDING_REJECTED',
    'SHOP_ORDER_DELETED'
));
