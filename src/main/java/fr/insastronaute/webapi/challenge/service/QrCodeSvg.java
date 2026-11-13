package fr.insastronaute.webapi.challenge.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

/**
 * Un QR-Code en SVG, rendu par le serveur.
 *
 * <p>En SVG et non en image : il voyage dans la reponse JSON qui le demande, se
 * dessine net a toutes les tailles, et evite au navigateur d'aller chercher une
 * image sur une route qui demanderait sa propre authentification.</p>
 */
public final class QrCodeSvg {

    private static final int MODULE_MARGIN = 2;

    private QrCodeSvg() {
    }

    public static String of(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    0,
                    0,
                    Map.of(
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, MODULE_MARGIN
                    )
            );

            StringBuilder path = new StringBuilder();
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        path.append("M").append(x).append(" ").append(y).append("h1v1h-1z");
                    }
                }
            }

            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "
                    + matrix.getWidth() + " " + matrix.getHeight() + "\" shape-rendering=\"crispEdges\">"
                    + "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>"
                    + "<path fill=\"#000000\" d=\"" + path + "\"/>"
                    + "</svg>";
        } catch (WriterException exception) {
            throw new IllegalStateException("QR-Code illisible a l'encodage", exception);
        }
    }
}
