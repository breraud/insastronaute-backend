-- Les notifications existantes, poussees jusqu'a l'appareil.
--
-- Deux tables et pas une : l'abonnement decrit un appareil, la file decrit un envoi
-- a faire. Les separer permet a l'ecriture d'une notification de ne jamais toucher au
-- reseau -- elle depose une ligne, un planificateur la reprend -- et c'est ce qui
-- garantit qu'un service de push injoignable ne peut pas faire echouer la transaction
-- qui vient d'accorder un succes ou de valider un defi.

CREATE TABLE push_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- L'unicite porte sur l'endpoint, jamais sur l'utilisateur : une meme personne a
    -- un abonnement par appareil, et se reabonner depuis le meme navigateur doit
    -- remplacer l'ancien plutot que d'en empiler un second.
    endpoint TEXT NOT NULL UNIQUE,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    user_agent VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_success_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_push_subscriptions_user ON push_subscriptions (user_id);

CREATE TABLE push_outbox (
    id BIGSERIAL PRIMARY KEY,
    -- La file porte la notification, pas la liste des abonnements : un appareil abonne
    -- entre l'ecriture et l'envoi recoit quand meme, et un appareil disparu ne laisse
    -- pas de ligne morte derriere lui.
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX ix_push_outbox_created_at ON push_outbox (created_at);
