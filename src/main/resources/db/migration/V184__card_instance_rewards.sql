-- Un exemplaire physique ne paie qu'une fois un meme joueur pour une meme carte.
--
-- Un joueur peut desormais liberer un doublon papier pour le donner. Le meme
-- exemplaire peut donc revenir a son ancien proprietaire, ou faire la navette
-- entre deux joueurs : chaque scan compte la carte dans la collection, mais les
-- jetons de collection ne se touchent qu'au premier passage. La carte fait partie
-- de la cle : un exemplaire repointe vers une autre carte par l'administration est
-- une acquisition nouvelle pour son proprietaire.
CREATE TABLE card_instance_rewards (
    id UUID PRIMARY KEY,
    card_instance_id UUID NOT NULL REFERENCES card_instances(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id UUID NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    rewarded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_card_instance_rewards_instance_user_card UNIQUE (card_instance_id, user_id, card_id)
);

-- Les exemplaires deja detenus ont deja paye leur proprietaire actuel : sans cette
-- reprise, le liberer puis le rescanner le paierait une seconde fois.
INSERT INTO card_instance_rewards (id, card_instance_id, user_id, card_id, rewarded_at)
SELECT gen_random_uuid(), id, owner_user_id, card_id, assigned_at
FROM card_instances
WHERE owner_user_id IS NOT NULL;
