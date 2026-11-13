package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.web.BadRequestException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Le format d'un plan : une palette, et une case par caractere.
 *
 * <p>Ranger un plan en couleurs repetees couterait sept octets par case — un
 * plan de 128x128 pesant alors plus de cent kilo-octets, transportes a chaque
 * ouverture de la page. En palette et indices, une case tient en un caractere
 * et le meme plan tient en seize.</p>
 *
 * <p>Le premier caractere de l'alphabet marque le vide. C'est le cas le plus
 * frequent, et il doit exister : un plan n'est presque jamais un rectangle
 * plein, et une case vide ne recouvre rien du canevas.</p>
 */
public final class BlueprintCells {

    /** Soixante-quatre caracteres sans ambiguite, tous surs dans une URL comme dans du JSON. */
    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";

    /** Le vide, et non une couleur : il ne recouvre rien. */
    public static final char EMPTY = ALPHABET.charAt(0);

    /** Une case pour le vide, le reste pour les couleurs. */
    public static final int MAX_COLORS = ALPHABET.length() - 1;

    private static final Pattern COLOR = Pattern.compile("^#[0-9A-F]{6}$");

    private BlueprintCells() {
    }

    /**
     * Verifie qu'un plan se tient : dimensions, palette, et un indice par case.
     *
     * <p>Un plan mal forme n'a pas de moitie acceptable. Rendu tel quel a
     * l'application, il donnerait des cases hors palette — donc des couleurs
     * inventees a poser sur une fresque commune.</p>
     */
    public static void validate(int width, int height, List<String> palette, String cells) {
        if (width < 1 || height < 1) {
            throw new BadRequestException("Un plan doit avoir au moins une case.");
        }
        if (width > fr.insastronaute.webapi.place.domain.PlaceBlueprint.MAX_SIDE
                || height > fr.insastronaute.webapi.place.domain.PlaceBlueprint.MAX_SIDE) {
            throw new BadRequestException("Un plan ne peut pas dépasser 128 cases de côté.");
        }
        if (palette == null || palette.size() > MAX_COLORS) {
            throw new BadRequestException("Un plan ne peut pas employer plus de " + MAX_COLORS + " couleurs.");
        }
        for (String color : palette) {
            if (color == null || !COLOR.matcher(color).matches()) {
                throw new BadRequestException("Les couleurs d'un plan doivent être au format #RRGGBB.");
            }
        }
        if (cells == null || cells.length() != width * height) {
            throw new BadRequestException("Le plan ne décrit pas exactement ses cases.");
        }

        for (int i = 0; i < cells.length(); i++) {
            int index = ALPHABET.indexOf(cells.charAt(i));
            // Un caractere hors alphabet, ou qui vise une couleur absente de la
            // palette : dans les deux cas la case n'a pas de couleur connue.
            if (index < 0 || index > palette.size()) {
                throw new BadRequestException("Le plan vise une couleur qu'il ne déclare pas.");
            }
        }
    }

    /** La palette telle qu'on la range : une seule colonne, virgules entre les couleurs. */
    public static String joinPalette(List<String> palette) {
        return String.join(",", palette);
    }

    public static List<String> splitPalette(String palette) {
        return palette == null || palette.isBlank() ? List.of() : List.of(palette.split(","));
    }

    /** Le nombre de cases reellement dessinees : c'est ce qui reste a poser. */
    public static int filledCells(String cells) {
        if (cells == null) {
            return 0;
        }
        int filled = 0;
        for (int i = 0; i < cells.length(); i++) {
            if (cells.charAt(i) != EMPTY) {
                filled++;
            }
        }
        return filled;
    }
}
