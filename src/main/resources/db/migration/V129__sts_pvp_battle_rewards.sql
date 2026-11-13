-- Ce qu'un duel a rapporte ou coute a chacun de ses joueurs.
--
-- Ecrit au reglement de la bataille, et garde : sans cela, un joueur qui gagne n'a aucun moyen
-- de savoir ce que sa victoire lui a valu. Son classement a bouge entre deux passages au menu,
-- et rien ne dit de combien ni pourquoi. Le detail est donc range avec la bataille qui l'a
-- produit plutot que recalcule apres coup -- il ne serait de toute facon plus recalculable, les
-- classements ayant deja bouge.
--
-- Sert aussi de trace : un classement qui a bouge de travers se relit ici, bataille par bataille.
--
-- Renumerotee de V125 en V129 : V125 etait deja pris par la purge des notifications de succes,
-- deja appliquee en production. Flyway refuse de demarrer sur deux migrations de meme version,
-- donc celle qui n'avait ete deployee nulle part cede la place.
--
-- « if not exists » pour la meme raison : si une base de test a deja recu cette colonne sous
-- l'ancien numero, Flyway la rejouerait sous le nouveau et echouerait sur une colonne existante.
alter table sts_pvp_battles
    add column if not exists rewards jsonb not null default '{}'::jsonb;
