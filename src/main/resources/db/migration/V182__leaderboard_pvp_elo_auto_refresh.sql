-- Le classement Elo se publie tout seul, comme le classement du jeu.
--
-- V177 a cree sa configuration sans preciser la publication automatique, et la colonne vaut
-- false par defaut (V10). Le classement restait donc fige sur le cliche a zero cree a la
-- premiere lecture, qui listait tout le monde a 0, tant qu'un administrateur ne le publiait pas
-- a la main.
--
-- On reprend le rythme du classement joueurs du jeu plutot que d'en inventer un. Seulement
-- s'il est encore coupe : un administrateur qui l'a deja regle garde son reglage.
UPDATE leaderboard_configs elo
SET auto_refresh_enabled = true,
    refresh_delay_minutes = COALESCE(game.refresh_delay_minutes, elo.refresh_delay_minutes)
FROM leaderboard_configs game
WHERE elo.scope = 'USER'
  AND elo.metric = 'PVP_ELO'
  AND elo.auto_refresh_enabled = false
  AND game.scope = 'USER'
  AND game.metric = 'GAME_POINTS';

-- Si le classement du jeu n'existe pas, l'Elo se publie quand meme, au rythme qu'il a deja.
UPDATE leaderboard_configs
SET auto_refresh_enabled = true
WHERE scope = 'USER'
  AND metric = 'PVP_ELO'
  AND auto_refresh_enabled = false
  AND NOT EXISTS (
      SELECT 1 FROM leaderboard_configs WHERE scope = 'USER' AND metric = 'GAME_POINTS'
  );
