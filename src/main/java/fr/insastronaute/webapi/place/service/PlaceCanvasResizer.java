package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.place.domain.PlaceResizeMode;
import org.springframework.stereotype.Component;

@Component
public class PlaceCanvasResizer {

    private static final int BYTES_PER_PIXEL = 3;

    public byte[] resize(byte[] source, int sourceWidth, int sourceHeight,
                         int targetWidth, int targetHeight, PlaceResizeMode mode) {
        int targetPixels = Math.multiplyExact(targetWidth, targetHeight);
        byte[] target = new byte[Math.multiplyExact(targetPixels, BYTES_PER_PIXEL)];
        if (mode == PlaceResizeMode.RESET) {
            return target;
        }

        int copyWidth = Math.min(sourceWidth, targetWidth);
        int copyHeight = Math.min(sourceHeight, targetHeight);
        int destinationX = Math.max(Math.subtractExact(targetWidth, sourceWidth) / 2, 0);
        int destinationY = Math.max(Math.subtractExact(targetHeight, sourceHeight) / 2, 0);
        int sourceX = Math.max(Math.subtractExact(sourceWidth, targetWidth) / 2, 0);
        int sourceY = Math.max(Math.subtractExact(sourceHeight, targetHeight) / 2, 0);
        int sourceStride = Math.multiplyExact(sourceWidth, BYTES_PER_PIXEL);
        int targetStride = Math.multiplyExact(targetWidth, BYTES_PER_PIXEL);
        int rowLength = Math.multiplyExact(copyWidth, BYTES_PER_PIXEL);

        for (int row = 0; row < copyHeight; row++) {
            int sourceOffset = Math.addExact(
                    Math.multiplyExact(Math.addExact(row, sourceY), sourceStride),
                    Math.multiplyExact(sourceX, BYTES_PER_PIXEL));
            int targetOffset = Math.addExact(
                    Math.multiplyExact(Math.addExact(row, destinationY), targetStride),
                    Math.multiplyExact(destinationX, BYTES_PER_PIXEL));
            System.arraycopy(source, sourceOffset, target, targetOffset, rowLength);
        }
        return target;
    }
}
