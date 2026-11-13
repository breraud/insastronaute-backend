package fr.insastronaute.webapi.medialibrary.service;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.util.exif.ExifFilterUtils;
import net.coobird.thumbnailator.util.exif.ExifUtils;
import net.coobird.thumbnailator.util.exif.Orientation;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Une vignette JPEG de 480 px au plus, jamais agrandie, dans le bon sens.
 *
 * <p>Le decodage se fait directement a resolution reduite (sous-echantillonnage) : une
 * photo de 3072 px se lit a environ 1000 px, ce qui coute trois a dix fois moins de
 * memoire et de temps sur les deux coeurs du serveur. Tout reste en memoire : ni
 * l'entree ni la sortie ne passent par les fichiers de cache d'ImageIO.</p>
 */
@Component
public class MediaThumbnailRenderer {

    static final int EDGE = 480;
    private static final float QUALITY = 0.8f;
    private static final long MAX_PIXELS = 50_000_000L;

    private final long maxPixels;

    public MediaThumbnailRenderer() {
        this(MAX_PIXELS);
    }

    MediaThumbnailRenderer(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public byte[] render(byte[] source) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new UnsupportedThumbnailException();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, false);
                BufferedImage image = decode(reader);
                Orientation orientation = orientation(reader);

                var builder = Thumbnails.of(image).imageType(BufferedImage.TYPE_INT_RGB);
                if (Math.max(image.getWidth(), image.getHeight()) > EDGE) {
                    builder.size(EDGE, EDGE);
                } else {
                    builder.scale(1.0);
                }
                if (orientation != null && orientation != Orientation.TOP_LEFT) {
                    builder.addFilter(ExifFilterUtils.getFilterForOrientation(orientation));
                }
                return jpeg(builder.asBufferedImage());
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Lire l'image a resolution reduite, ou la refuser.
     *
     * <p>Le sous-echantillonnage borne la memoire, pas le calcul : le decodeur parcourt
     * quand meme toute l'image. Un petit fichier envoye directement a l'API peut declarer
     * des dimensions immenses et couter des dizaines de secondes a chaque affichage, sans
     * jamais entrer dans le cache. Les dimensions de l'en-tete sont donc plafonnees avant
     * de decoder.</p>
     *
     * <p>Un fichier corrompu echoue ici, a l'en-tete (un JPEG coupe avant ses donnees)
     * ou au decodage (un PNG tronque) : il est refuse aussi. Un JPEG coupe au milieu de
     * ses donnees, lui, se lit avec un simple avertissement et donne une vignette
     * partielle.</p>
     */
    private BufferedImage decode(ImageReader reader) {
        try {
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if ((long) width * height > maxPixels) {
                throw new RefusedThumbnailException("Image trop grande pour une vignette : " + width + " x " + height + ".");
            }
            int step = Math.max(1, Math.max(width, height) / (EDGE * 2));
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(step, step, 0, 0);
            return reader.read(0, param);
        } catch (IOException corrupt) {
            throw new RefusedThumbnailException(corrupt);
        }
    }

    private static Orientation orientation(ImageReader reader) {
        try {
            return ExifUtils.getExifOrientation(reader, 0);
        } catch (Exception exception) {
            return null;
        }
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
