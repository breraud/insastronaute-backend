-- Les defis de promo : un defi que le departement releve une fois, et dont les
-- points vont au departement plutot qu'a celui qui a porte la preuve.
--
-- Pas de table d'attribution : la soumission validee fait foi, comme pour tout
-- autre defi. Un registre parallele demanderait d'etre defait a chaque refus,
-- chaque revocation et chaque ajustement de bonus, et finirait par diverger de
-- ce qu'il pretend resumer.
ALTER TABLE challenges ADD COLUMN promo BOOLEAN NOT NULL DEFAULT FALSE;

-- La lecture chaude : « ce departement a-t-il deja quelque chose sur ce defi ? »,
-- posee a chaque envoi de preuve et a chaque affichage de la liste des defis.
CREATE INDEX idx_challenge_submissions_promo_lookup
    ON challenge_submissions (challenge_id, user_id, status);
