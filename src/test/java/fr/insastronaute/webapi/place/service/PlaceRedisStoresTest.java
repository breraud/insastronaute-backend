package fr.insastronaute.webapi.place.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceRedisStoresTest {

    @Test
    void rgbConversionPreservesUnsignedColorBytes() {
        assertThat(PlaceCanvasStore.rgb("#00A5FF"))
                .containsExactly(0, (byte) 0xA5, (byte) 0xFF);
    }
}
