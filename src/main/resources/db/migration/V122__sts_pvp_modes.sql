-- Les modes multijoueur : le 1v1 existant, le 2v2, et le raid (deux joueurs contre un boss
-- que le serveur pilote).
--
-- Le défaut 'ONE_V_ONE' vaut pour tout ce qui existe déjà, batailles comme entrées de file :
-- avant cette colonne, il n'y avait qu'un mode et rien ne le disait. Une bataille en cours
-- pendant le déploiement reprend donc exactement dans le mode où elle a commencé.
alter table sts_pvp_battles
    add column mode varchar(32) not null default 'ONE_V_ONE';

alter table sts_pvp_matchmaking_queue
    add column mode varchar(32) not null default 'ONE_V_ONE';

-- On ne s'apparie qu'à l'intérieur d'un mode : un joueur qui cherche un raid ne doit jamais
-- être tiré dans un duel. C'est l'index que consulte la recherche d'adversaires.
create index ix_sts_pvp_queue_mode_friendly
    on sts_pvp_matchmaking_queue (mode, friendly, queued_at);
