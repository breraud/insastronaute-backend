package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.place.domain.PlaceResizeMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceCanvasResizerTest {

    private final PlaceCanvasResizer resizer = new PlaceCanvasResizer();

    @Test
    void enlargesCanvasWithSourceCenteredInsideBlackBorder() {
        byte[] source = pixels(1, 2, 3, 4);

        byte[] resized = resizer.resize(source, 2, 2, 4, 4, PlaceResizeMode.PRESERVE_CENTERED);

        assertThat(resized).isEqualTo(pixels(
                0, 0, 0, 0,
                0, 1, 2, 0,
                0, 3, 4, 0,
                0, 0, 0, 0));
    }

    @Test
    void resetCreatesAnEmptyTargetCanvas() {
        byte[] source = pixels(1, 2, 3, 4);

        byte[] resized = resizer.resize(source, 2, 2, 4, 4, PlaceResizeMode.RESET);

        assertThat(resized).containsOnly((byte) 0).hasSize(4 * 4 * 3);
    }

    @Test
    void reducesCanvasToCenteredSourceRegion() {
        byte[] source = pixels(
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16);

        byte[] resized = resizer.resize(source, 4, 4, 2, 2, PlaceResizeMode.PRESERVE_CENTERED);

        assertThat(resized).isEqualTo(pixels(6, 7, 10, 11));
    }

    @Test
    void oddSurplusRemainsOnRightAndBottomWhenEnlarging() {
        byte[] source = pixels(1, 2, 3, 4);

        byte[] resized = resizer.resize(source, 2, 2, 5, 3, PlaceResizeMode.PRESERVE_CENTERED);

        assertThat(resized).isEqualTo(pixels(
                0, 1, 2, 0, 0,
                0, 3, 4, 0, 0,
                0, 0, 0, 0, 0));
    }

    @Test
    void oddDeficitIsCroppedFromRightAndBottomWhenReducing() {
        byte[] source = pixels(
                1, 2, 3, 4, 5,
                6, 7, 8, 9, 10,
                11, 12, 13, 14, 15);

        byte[] resized = resizer.resize(source, 5, 3, 2, 2, PlaceResizeMode.PRESERVE_CENTERED);

        assertThat(resized).isEqualTo(pixels(2, 3, 7, 8));
    }

    private static byte[] pixels(int... values) {
        byte[] pixels = new byte[Math.multiplyExact(values.length, 3)];
        for (int index = 0; index < values.length; index++) {
            byte value = (byte) values[index];
            int offset = index * 3;
            pixels[offset] = value;
            pixels[offset + 1] = value;
            pixels[offset + 2] = value;
        }
        return pixels;
    }
}
