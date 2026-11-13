package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.place.domain.PlaceTimelapseColouring;
import fr.insastronaute.webapi.place.repository.PlaceTimelapsePixel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Le flux d'un timelapse : un en-tete, puis les poses dans l'ordre.
 *
 * <p>Sept octets par pose la ou JSON en demanderait une quarantaine. Sur un million
 * de poses, c'est sept megaoctets contre quarante : la difference entre un export
 * qu'on telecharge et un export qu'on abandonne.</p>
 *
 * <p>Le nombre de poses n'est pas ecrit : il se deduit de la taille du fichier.
 * L'ecrire supposerait de le connaitre avant de lire la base, et une pose arrivee
 * entre le comptage et la lecture rendrait l'en-tete menteur.</p>
 *
 * <pre>
 * octets 0-3   "PLTL"
 * octet  4     version du format (1)
 * octet  5     coloration : 0 couleurs posees, 1 couleurs de departement
 * octets 6-7   largeur  (uint16, gros-boutiste)
 * octets 8-9   hauteur  (uint16, gros-boutiste)
 * puis, par pose : x (uint16), y (uint16), r, g, b
 * </pre>
 */
public class PlaceTimelapseEncoder {

    public static final int HEADER_BYTES = 10;
    public static final int PIXEL_BYTES = 7;
    static final byte VERSION = 1;

    private final OutputStream out;
    private final PlaceTimelapseColouring colouring;
    private final int width;
    private final int height;
    private long written;

    public PlaceTimelapseEncoder(OutputStream out, PlaceTimelapseColouring colouring, int width, int height) {
        this.out = out;
        this.colouring = colouring;
        this.width = width;
        this.height = height;
    }

    public void writeHeader() throws IOException {
        byte[] header = new byte[HEADER_BYTES];
        byte[] magic = "PLTL".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 0, magic.length);
        header[4] = VERSION;
        header[5] = (byte) (colouring == PlaceTimelapseColouring.DEPARTMENT ? 1 : 0);
        putUnsignedShort(header, 6, width);
        putUnsignedShort(header, 8, height);
        out.write(header);
    }

    /** Un bloc de poses. La pagination de la requete decide de leur taille, pas ce format. */
    public void write(List<PlaceTimelapsePixel> pixels) throws IOException {
        byte[] chunk = new byte[Math.multiplyExact(pixels.size(), PIXEL_BYTES)];
        int offset = 0;
        for (PlaceTimelapsePixel pixel : pixels) {
            putUnsignedShort(chunk, offset, pixel.x());
            putUnsignedShort(chunk, offset + 2, pixel.y());
            putColour(chunk, offset + 4, pixel.color());
            offset += PIXEL_BYTES;
        }
        out.write(chunk);
        written += pixels.size();
    }

    public void finish() throws IOException {
        out.flush();
    }

    /** Ce qui a ete ecrit, pour la trace cote serveur. Le fichier, lui, n'en a pas besoin. */
    public long count() {
        return written;
    }

    private static void putUnsignedShort(byte[] target, int offset, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Coordonnée hors du format du flux : " + value);
        }
        target[offset] = (byte) (value >>> 8);
        target[offset + 1] = (byte) value;
    }

    private static void putColour(byte[] target, int offset, String color) {
        if (color == null || color.length() != 7 || color.charAt(0) != '#') {
            throw new IllegalArgumentException("Couleur illisible dans l'historique : " + color);
        }
        for (int component = 0; component < 3; component++) {
            int index = 1 + component * 2;
            int high = Character.digit(color.charAt(index), 16);
            int low = Character.digit(color.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Couleur illisible dans l'historique : " + color);
            }
            target[offset + component] = (byte) ((high << 4) | low);
        }
    }
}
