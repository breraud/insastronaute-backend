-- Numérotée après V118 : les versions déjà appliquées en base ne se doublent
-- pas, et Flyway refuse par défaut une migration qui arriverait en dessous du
-- maximum atteint.
--
-- Construire un deck PvP demande une collection déjà fournie. Le temps d'un test
-- d'équilibrage, ou d'une démonstration avant que les cartes circulent, on veut
-- pouvoir ouvrir toutes les cartes à tout le monde — et refermer aussitôt après.
CREATE TABLE sts_pvp_settings (
    id BOOLEAN PRIMARY KEY DEFAULT TRUE,
    all_cards_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_sts_pvp_settings_singleton CHECK (id)
);

INSERT INTO sts_pvp_settings (id, all_cards_unlocked) VALUES (TRUE, FALSE);
