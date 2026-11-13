-- Les coequipiers choisis pour un defi de groupe vivaient dans l'etat de la page :
-- un rafraichissement les perdait, alors que les fichiers deja deposes, eux,
-- survivaient cote serveur. La partie visible disparaissait donc, et la partie
-- invisible restait.
CREATE TABLE proof_draft_participants (
    draft_id UUID NOT NULL REFERENCES proof_drafts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (draft_id, user_id)
);

CREATE INDEX idx_proof_draft_participants_user
    ON proof_draft_participants (user_id);
