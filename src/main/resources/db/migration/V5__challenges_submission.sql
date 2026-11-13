CREATE TABLE challenge_submissions (
                                       id UUID PRIMARY KEY,
                                       challenge_id UUID NOT NULL,
                                       user_id UUID NOT NULL,
                                       status VARCHAR(20) NOT NULL, -- 'PENDING' (en attente), 'VALIDATED' (validé), 'REJECTED' (refusé)
                                       proof_text TEXT, -- URL d'une image ou texte envoyé par le joueur

                                       created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                       validated_at TIMESTAMP WITH TIME ZONE, -- Date à laquelle un admin a dit "OK"

                                       CONSTRAINT fk_submission_challenge FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE,
                                       CONSTRAINT fk_submission_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);