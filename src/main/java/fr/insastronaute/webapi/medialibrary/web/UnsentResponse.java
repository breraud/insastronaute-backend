package fr.insastronaute.webapi.medialibrary.web;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Une reponse dont rien n'est encore parti, rendue vierge avant l'erreur.
 *
 * <p>Le fichier a deja pose ses en-tetes : taille, plage, nom de piece jointe, type. Une
 * panne avant que le tampon ne se vide (S3 qui lache a la premiere lecture) laisserait
 * l'erreur JSON partir sous ces en-tetes, et un Content-Length de fichier ferait attendre
 * au client des octets qui ne viendront jamais. Une fois le premier octet parti, il n'y a
 * plus rien a effacer : ApiExceptionHandler coupe alors la connexion.</p>
 */
final class UnsentResponse {

    private UnsentResponse() {
    }

    static void discard(HttpServletResponse response) {
        if (!response.isCommitted()) {
            response.reset();
        }
    }
}
