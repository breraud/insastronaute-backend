-- Fermer les inscriptions aux events pour le staff.
--
-- Le staff joue sur le même site que les étudiants et prend donc des places sur des
-- événements qu'il organise. L'interrupteur est un geste d'administration, pas une
-- modification des événements : le rouvrir rend exactement ce qu'il y avait, sans avoir
-- à se souvenir de ce qui était ouvert.
--
-- Une table à un seul enregistrement, sur le modèle de challenge_visibility_settings :
-- la clé primaire booléenne contrainte à TRUE interdit qu'il en existe deux, ce qu'aucun
-- code n'aurait alors à départager.
CREATE TABLE event_settings (
    id BOOLEAN PRIMARY KEY DEFAULT TRUE,
    staff_registration_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_event_settings_singleton CHECK (id)
);

INSERT INTO event_settings (id, staff_registration_blocked) VALUES (TRUE, FALSE);

-- Rien n'est retiré rétroactivement : les inscriptions déjà prises par du staff restent.
-- Les effacer d'un clic retirerait des places sans que personne l'ait décidé cas par cas,
-- et sans repêcher la liste d'attente au bon moment. C'est à quoi sert la suppression
-- manuelle d'une inscription, ajoutée en même temps.
