package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntInputTool;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * La forme canonique d'un secret, selon l'outil qui l'a produit.
 *
 * Elle vit ici, cote serveur, et nulle part ailleurs : deux fronts distincts -- celui du
 * joueur et celui de l'administration -- ecrivent la meme melodie, et doivent produire le
 * meme hash sans avoir a s'accorder sur une convention d'ecriture.
 *
 * Un secret inexploitable rend un Optional vide plutot qu'une exception : il doit suivre
 * exactement le chemin d'un secret inconnu. Une erreur de validation distincte designerait
 * l'outil attendu aussi surement qu'un message explicite.
 */
public final class TreasureHuntSecretCodec {
    private static final List<String> NOTES = List.of(
            "DO", "DO#", "RE", "RE#", "MI", "FA", "FA#", "SOL", "SOL#", "LA", "LA#", "SI");
    // Les cinq bemols usuels. DOb, FAb, MI# et SI# ne sont pas des noms qu'on publie :
    // ils sont refuses comme n'importe quel jeton inconnu.
    private static final Map<String, String> FLAT_TO_SHARP = Map.of(
            "REB", "DO#", "MIB", "RE#", "SOLB", "FA#", "LAB", "SOL#", "SIB", "LA#");

    /**
     * Le tiret, l'espace et la virgule, et rien d'autre.
     *
     * C'est la liste que la specification enumere, et celle que le panneau d'administration
     * applique pour compter les notes d'une melodie et signaler un jeton fautif. Un
     * separateur de plus ici -- un point-virgule, une barre oblique -- et le panneau
     * annoncerait un nombre de notes que le serveur ne lit pas.
     */
    private static final String SEPARATORS = "[-\\s,]+";

    private TreasureHuntSecretCodec() { }

    public static Optional<String> normalize(TreasureHuntInputTool tool, String secret) {
        if (tool == null) return Optional.empty();
        if (secret == null || secret.isBlank()) return Optional.empty();
        if (tool != TreasureHuntInputTool.PIANO) {
            return Optional.of(secret.trim().toUpperCase(Locale.ROOT));
        }
        StringBuilder canonical = new StringBuilder();
        for (String token : secret.trim().split(SEPARATORS)) {
            if (token.isEmpty()) continue;
            Optional<String> note = note(token);
            if (note.isEmpty()) return Optional.empty();
            if (!canonical.isEmpty()) canonical.append('-');
            canonical.append(note.get());
        }
        return canonical.isEmpty() ? Optional.empty() : Optional.of(canonical.toString());
    }

    private static Optional<String> note(String token) {
        String folded = Normalizer.normalize(token.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('♯', '#')
                .replace('♭', 'B');
        folded = FLAT_TO_SHARP.getOrDefault(folded, folded);
        return NOTES.contains(folded) ? Optional.of(folded) : Optional.empty();
    }
}
