-- Un pixel bonus s'achète : il pose un pixel sans attendre le cooldown.
-- Le prix monte d'un jeton à chaque achat, sinon un gros solde effacerait
-- l'attente pour toute la durée de l'événement.
ALTER TABLE place_configs
    ADD COLUMN bonus_pixel_price BIGINT NOT NULL DEFAULT 30,
    ADD COLUMN bonus_pixel_price_increment BIGINT NOT NULL DEFAULT 1;

ALTER TABLE place_configs
    ADD CONSTRAINT ck_place_configs_bonus_price CHECK (bonus_pixel_price >= 0),
    ADD CONSTRAINT ck_place_configs_bonus_increment CHECK (bonus_pixel_price_increment >= 0);

-- Compté à part des pixels bonus gagnés aux défis : le prix ne doit monter que
-- pour ce qu'on a acheté, et les succès ne doivent pas s'acheter.
ALTER TABLE users
    ADD COLUMN place_purchased_bonus_pixels BIGINT NOT NULL DEFAULT 0,
    -- Deux achats d'affilée sont refusés : il faut un pixel de cooldown normal
    -- entre les deux, sinon le cooldown ne serait plus qu'un tarif.
    ADD COLUMN place_last_pixel_purchased BOOLEAN NOT NULL DEFAULT FALSE;
