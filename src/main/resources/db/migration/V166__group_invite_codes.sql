-- L'invitation par QR : le chef montre un code, son voisin le scanne, et le voila
-- dans l'equipe -- deja d'accord.
--
-- Le parcours d'avant demandait de chercher chaque personne par son nom, de lui
-- envoyer une invitation, puis d'attendre qu'elle l'accepte. Trois gestes et deux
-- ecrans pour quelqu'un qui est assis a cote.
CREATE TABLE challenge_group_invites (
    id UUID PRIMARY KEY,
    token UUID NOT NULL,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    submitter_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uk_challenge_group_invites_token UNIQUE (token),
    -- Un seul code vivant par personne et par defi : le regenerer prolonge le meme,
    -- sinon un code montre hier resterait valable a cote de celui d'aujourd'hui.
    CONSTRAINT uk_challenge_group_invites_owner UNIQUE (challenge_id, submitter_user_id)
);

-- Ceux qui ont rejoint en scannant, avant meme que la preuve parte.
--
-- Ils ont dit oui en scannant : quand la preuve part, ils comptent comme ayant
-- accepte, et ne recoivent pas une invitation a confirmer ce qu'ils viennent de
-- faire. C'est toute la redondance que ce code existe pour supprimer.
CREATE TABLE challenge_group_invite_joins (
    invite_id UUID NOT NULL REFERENCES challenge_group_invites(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    PRIMARY KEY (invite_id, user_id)
);

CREATE INDEX idx_challenge_group_invite_joins_user
    ON challenge_group_invite_joins (user_id);
