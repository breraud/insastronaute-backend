package fr.insastronaute.webapi.card.print.domain;

import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * L'identifiant d'une planche de dataset, tel qu'il est imprime sur le papier.
 *
 * <p>Il est recopie a la main depuis la feuille, donc court : {@code PL-48213-9}, dix
 * caracteres. Deux proprietes le distinguent d'un code de carte
 * ({@code XXXXX-XXXXX-XXXXX}, quinze symboles alphanumeriques sans somme de controle) :
 *
 * <ul>
 *   <li>sa forme -- deux lettres fixes puis des chiffres -- ne peut pas etre celle d'un
 *       code de carte, donc une planche ne sera jamais prise pour quelque chose qui donne
 *       une carte ;</li>
 *   <li>son dernier chiffre est une somme de controle de Luhn : toute substitution d'un
 *       seul chiffre est refusee. Une faute de frappe ne peut donc pas designer une autre
 *       planche, ce qui etiquetterait toute une seance avec les codes du mauvais papier.</li>
 * </ul>
 */
public final class CardDatasetSheetId {

    private static final String PREFIX = "PL";
    private static final int BODY_LENGTH = 5;
    private static final Pattern CANONICAL = Pattern.compile("^PL-\\d{5}-\\d$");

    private CardDatasetSheetId() {
    }

    public static String generate(Random random) {
        StringBuilder body = new StringBuilder(BODY_LENGTH);
        for (int i = 0; i < BODY_LENGTH; i++) {
            body.append((char) ('0' + random.nextInt(10)));
        }
        return format(body.toString());
    }

    /**
     * Rend la forme canonique de ce qu'un humain a tape, ou rien si ce n'est pas un
     * identifiant de planche valide. Les tirets, les espaces et la casse sont sans
     * importance : ce qui compte est la suite de chiffres et sa somme de controle.
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (!compact.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String digits = compact.substring(PREFIX.length());
        if (digits.length() != BODY_LENGTH + 1 || !digits.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        String body = digits.substring(0, BODY_LENGTH);
        int checkDigit = digits.charAt(BODY_LENGTH) - '0';
        if (checkDigit != luhnCheckDigit(body)) {
            return Optional.empty();
        }
        return Optional.of(format(body));
    }

    public static boolean isValid(String candidate) {
        return normalize(candidate).filter(normalized -> CANONICAL.matcher(normalized).matches()).isPresent();
    }

    private static String format(String body) {
        return PREFIX + "-" + body + "-" + luhnCheckDigit(body);
    }

    private static int luhnCheckDigit(String body) {
        int sum = 0;
        boolean doubled = true;
        for (int i = body.length() - 1; i >= 0; i--) {
            int digit = body.charAt(i) - '0';
            if (doubled) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubled = !doubled;
        }
        return (10 - (sum % 10)) % 10;
    }
}
