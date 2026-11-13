package fr.insastronaute.webapi.medialibrary.repository;

/**
 * Ce que la Mediatheque a le droit de montrer, ecrit une seule fois.
 *
 * <p>Toutes les requetes de la Mediatheque y ajoutent leurs propres conditions : la
 * navigation, la lecture d'un fichier, la selection et l'export. Une soumission en sort
 * des qu'un de ses membres a demande l'effacement de ses preuves ou a supprime son
 * compte, ou que le defi ou la preuve sont sensibles. La regle se relit a chaque
 * requete, jamais depuis un cache.</p>
 *
 * <p>Alias attendus : {@code s} pour la soumission, {@code c} pour son defi, {@code u}
 * pour son auteur.</p>
 */
public final class MediaLibraryEligibility {

    public static final String JPQL = """
            s.proofType in ('MEDIA', 'IMAGE', 'VIDEO')
            and s.proofMediaPurgedAt is null
            and s.proofData <> '' and s.proofData <> '[]'
            and c.deletedAt is null
            and c.sensitive = false
            and s.sensitive = false
            and u.deletedAt is null
            and u.purgeProofsAfterValidation = false
            and not exists (
                select 1 from ChallengeSubmission sx join sx.participants px
                where sx = s and (px.deletedAt is not null or px.purgeProofsAfterValidation = true))
            """;

    private MediaLibraryEligibility() {
    }
}
