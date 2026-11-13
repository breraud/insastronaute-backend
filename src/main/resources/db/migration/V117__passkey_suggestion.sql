-- La suggestion d'enregistrer une passkey, et son sort.
--
-- Ces deux colonnes ne disent pas si le compte possede une passkey : Keycloak seul le
-- sait, et le lui demander mettrait un appel reseau externe sur le chemin du profil,
-- qui est charge a chaque ouverture de l'application. Elles disent seulement si on a
-- deja pose la question, ce qui est tout ce dont l'affichage a besoin.
--
-- La consequence assumee : quelqu'un qui enregistre une passkey depuis la console
-- Keycloak, ou qui supprime la sienne, ne sera pas suivi ici. Une suggestion affichee
-- en trop n'est rien, et l'entree permanente dans les parametres rattrape l'autre sens.
ALTER TABLE users
    ADD COLUMN passkey_prompt_answered_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN passkey_prompt_snoozed_until TIMESTAMP WITH TIME ZONE;
