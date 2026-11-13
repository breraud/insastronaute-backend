package fr.insastronaute.webapi.medialibrary.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaThumbnailRendererTest {

    private final MediaThumbnailRenderer renderer = new MediaThumbnailRenderer();

    @Test
    void aLargePhotoShrinksTo480OnItsLongEdge() throws IOException {
        BufferedImage result = decode(renderer.render(encode(3072, 2048, "jpg", BufferedImage.TYPE_INT_RGB)));

        assertThat(Math.max(result.getWidth(), result.getHeight())).isEqualTo(480);
        assertThat(result.getWidth()).isGreaterThan(result.getHeight());
    }

    @Test
    void aSmallPhotoIsNeverEnlarged() throws IOException {
        BufferedImage result = decode(renderer.render(encode(200, 100, "jpg", BufferedImage.TYPE_INT_RGB)));

        assertThat(result.getWidth()).isEqualTo(200);
    }

    /** Une photo de telephone tenue droite porte l'orientation 6 : sans elle, la vignette est couchee. */
    @Test
    void theExifOrientationIsApplied() throws IOException {
        byte[] landscapeOnDisk = encode(100, 50, "jpg", BufferedImage.TYPE_INT_RGB);

        BufferedImage result = decode(renderer.render(withExifOrientation(landscapeOnDisk, 6)));

        assertThat(result.getHeight()).isGreaterThan(result.getWidth());
    }

    @Test
    void aTransparentPngBecomesAReadableJpeg() throws IOException {
        BufferedImage result = decode(renderer.render(encode(300, 300, "png", BufferedImage.TYPE_INT_ARGB)));

        assertThat(result).isNotNull();
    }

    @Test
    void anUnreadableFormatIsRefused() {
        byte[] fakeWebp = "RIFF\0\0\0\0WEBPVP8 ".getBytes();

        assertThatThrownBy(() -> renderer.render(fakeWebp)).isInstanceOf(UnsupportedThumbnailException.class);
    }

    /**
     * Le sous-echantillonnage borne la memoire, pas le calcul : un petit fichier qui
     * declare une image immense couterait des dizaines de secondes a chaque affichage.
     * Le plafond se lit dans l'en-tete, avant de decoder.
     */
    @Test
    void anImageDeclaringMorePixelsThanTheCapIsRefused() throws IOException {
        byte[] photo = encode(200, 100, "jpg", BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> new MediaThumbnailRenderer(200 * 100 - 1).render(photo))
                .isInstanceOf(RefusedThumbnailException.class);
        assertThat(decode(new MediaThumbnailRenderer(200 * 100).render(photo)).getWidth()).isEqualTo(200);
    }

    /** Coupee avant ses donnees, une photo ne se lit meme pas jusqu'a ses dimensions. */
    @Test
    void aJpegCutInItsHeaderIsRefused() throws IOException {
        byte[] photo = encode(3072, 2048, "jpg", BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> renderer.render(Arrays.copyOf(photo, 300)))
                .isInstanceOf(RefusedThumbnailException.class);
    }

    @Test
    void aCorruptPngIsRefused() throws IOException {
        byte[] png = encode(300, 300, "png", BufferedImage.TYPE_INT_ARGB);

        assertThatThrownBy(() -> renderer.render(Arrays.copyOf(png, png.length / 2)))
                .isInstanceOf(RefusedThumbnailException.class);
    }

    private static byte[] encode(int width, int height, String format, int type) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    /** Insere un segment APP1 Exif minimal (une seule entree : Orientation) apres le JFIF. */
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
                0x00, (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        byte[] header = {'E', 'x', 'i', 'f', 0, 0};
        int segmentLength = 2 + header.length + tiff.length;
        int app0Length = ((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF);
        int insertAt = 4 + app0Length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, insertAt);
        out.write(0xFF);
        out.write(0xE1);
        out.write(segmentLength >> 8);
        out.write(segmentLength & 0xFF);
        out.writeBytes(header);
        out.writeBytes(tiff);
        out.write(jpeg, insertAt, jpeg.length - insertAt);
        return out.toByteArray();
    }
}
