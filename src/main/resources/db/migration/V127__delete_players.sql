-- Supprimer un joueur sans effacer qui il était.
--
-- Une suppression franche de la ligne est impossible sans détruire l'historique :
-- admin_action_events référence les joueurs en ON DELETE RESTRICT, aussi bien comme
-- auteur que comme cible. Cette contrainte est délibérée -- une trace de modération qui
-- disparaît avec son sujet ne trace rien -- et le registre doit continuer à nommer la
-- personne concernée.
--
-- La ligne reste donc, avec son nom, et porte la date de sa suppression. Ce qui part
-- réellement : le compte Keycloak, donc l'accès, et la présence du joueur dans l'état de
-- jeu des autres -- son équipe de cartes, ses amitiés, ses demandes en attente.

ALTER TABLE users
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- Les joueurs supprimés sont rares et les requêtes cherchent presque toujours les
-- vivants : un index partiel sur les seuls supprimés est petit et sert les deux sens.
CREATE INDEX ix_users_deleted ON users (deleted_at) WHERE deleted_at IS NOT NULL;
