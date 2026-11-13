-- Rejouer la fresque sur une periode.
--
-- Les deux index existants servent « qui a pose cette case » et « qu'a pose ce
-- joueur ». Aucun ne sert « toutes les poses entre deux instants, dans l'ordre » :
-- l'export d'un timelapse lisait donc la table entiere, puis la triait.
--
-- L'identifiant en queue d'index departage deux poses de la meme milliseconde, pour
-- que deux exports identiques rendent la meme video.
CREATE INDEX place_pixel_placements_replay_idx
    ON place_pixel_placements (generation, placed_at, id);
