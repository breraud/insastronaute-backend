package fr.insastronaute.webapi.push;

import com.interaso.webpush.VapidKeys;

/**
 * Genere la paire de cles VAPID, une fois pour toutes.
 *
 * <p>Se lance par {@code ./gradlew vapidKeys}. Ce n'est pas une commande a rejouer :
 * <strong>changer la cle publique invalide d'un coup tous les abonnements existants</strong>.
 * Chaque appareil deja abonne l'a memorisee ; le service de push refusera ensuite les
 * envois signes par une autre, et il faudra que chaque joueur se reabonne - ce que
 * personne ne pense a faire, puisque rien ne le lui dit.</p>
 *
 * <p>La cle privee ne quitte jamais le serveur. Elle ne sert qu'a signer le jeton qui
 * prouve au service de push que l'envoi vient bien de nous : elle ne chiffre pas le
 * contenu, qui l'est avec les cles de l'appareil. La perdre n'expose donc aucune
 * notification passee, mais oblige a tout reabonner - autant la traiter comme un
 * secret de production ordinaire.</p>
 */
public final class VapidKeyGenerator {

    private VapidKeyGenerator() {}

    public static void main(String[] args) {
        VapidKeys keys = VapidKeys.generate();
        System.out.println();
        System.out.println("# A coller dans le .env du serveur, une seule fois.");
        System.out.println("# Regenerer ces cles plus tard desabonnerait silencieusement tout le monde.");
        System.out.println("APP_PUSH_ENABLED=true");
        System.out.println("APP_PUSH_SUBJECT=mailto:contact@example.com");
        System.out.println("APP_PUSH_VAPID_PUBLIC_KEY=" + keys.getX509PublicKey());
        System.out.println("APP_PUSH_VAPID_PRIVATE_KEY=" + keys.getPkcs8PrivateKey());
        System.out.println();
        System.out.println("# Pour verification seulement : ce que le navigateur recevra");
        System.out.println("# depuis GET /api/push/public-key. Rien a copier.");
        System.out.println("# applicationServerKey = " + java.util.Base64.getUrlEncoder()
                .withoutPadding().encodeToString(keys.getApplicationServerKey()));
        System.out.println();
    }
}
