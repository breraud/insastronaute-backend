package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.place.domain.PlaceTimelapseColouring;
import fr.insastronaute.webapi.place.repository.PlaceTimelapsePixel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Le format du flux, vu depuis celui qui devra le relire.
 *
 * <p>Un fichier qui ne porte pas ses propres dimensions oblige a les retrouver
 * ailleurs, et un export relu six mois plus tard n'a plus d'ailleurs.</p>
 */
class PlaceTimelapseEncoderTest {

    @Test
    void theHeaderCarriesEverythingNeededToReplayWithoutTheServer() throws IOException {
        byte[] stream = encode(PlaceTimelapseColouring.PLACED, 500, 400, List.of());

        assertThat(new String(stream, 0, 4)).isEqualTo("PLTL");
        assertThat(stream[4]).isEqualTo((byte) 1);          // version
        assertThat(stream[5]).isEqualTo((byte) 0);          // couleurs posees
        assertThat(readUnsignedShort(stream, 6)).isEqualTo(500);
        assertThat(readUnsignedShort(stream, 8)).isEqualTo(400);
        // Pas de compte dans l'en-tete : il se deduit de la taille du fichier, et un
        // compte ecrit d'avance mentirait sur une pose arrivee pendant l'export.
        assertThat(stream).hasSize(PlaceTimelapseEncoder.HEADER_BYTES);
    }

    /** Le fichier dit lui-meme s'il est colore par departement : on ne le redevine pas. */
    @Test
    void theHeaderNamesTheColouring() throws IOException {
        assertThat(encode(PlaceTimelapseColouring.DEPARTMENT, 10, 10, List.of())[5]).isEqualTo((byte) 1);
    }

    /** Sept octets par pose : deux pour x, deux pour y, trois pour la couleur. */
    @Test
    void eachPlacementIsSevenBytesOfCoordinatesAndColour() throws IOException {
        byte[] stream = encode(PlaceTimelapseColouring.PLACED, 500, 400, List.of(
                new PlaceTimelapsePixel(3, 7, "#e50000"),
                new PlaceTimelapsePixel(499, 399, "#00d3dd")));

        assertThat(stream).hasSize(PlaceTimelapseEncoder.HEADER_BYTES + 14);

        int first = PlaceTimelapseEncoder.HEADER_BYTES;
        assertThat(readUnsignedShort(stream, first)).isEqualTo(3);
        assertThat(readUnsignedShort(stream, first + 2)).isEqualTo(7);
        assertThat(Byte.toUnsignedInt(stream[first + 4])).isEqualTo(0xE5);
        assertThat(Byte.toUnsignedInt(stream[first + 5])).isEqualTo(0x00);
        assertThat(Byte.toUnsignedInt(stream[first + 6])).isEqualTo(0x00);

        int second = first + 7;
        assertThat(readUnsignedShort(stream, second)).isEqualTo(499);
        assertThat(readUnsignedShort(stream, second + 2)).isEqualTo(399);
        assertThat(Byte.toUnsignedInt(stream[second + 4])).isEqualTo(0x00);
        assertThat(Byte.toUnsignedInt(stream[second + 6])).isEqualTo(0xDD);
    }

    /** L'ordre du flux est l'ordre du rejeu : le tri vient de la requete, on n'y touche pas. */
    @Test
    void placementsKeepTheOrderTheyArriveIn() throws IOException {
        byte[] stream = encode(PlaceTimelapseColouring.PLACED, 10, 10, List.of(
                new PlaceTimelapsePixel(1, 0, "#010101"),
                new PlaceTimelapsePixel(2, 0, "#020202"),
                new PlaceTimelapsePixel(3, 0, "#030303")));

        int base = PlaceTimelapseEncoder.HEADER_BYTES;
        assertThat(readUnsignedShort(stream, base)).isEqualTo(1);
        assertThat(readUnsignedShort(stream, base + 7)).isEqualTo(2);
        assertThat(readUnsignedShort(stream, base + 14)).isEqualTo(3);
    }

    /**
     * Le flux s'ecrit par blocs : la requete pagine pour que la memoire ne depende
     * pas de la duree de l'evenement. Le fichier, lui, doit rester d'un seul tenant.
     */
    @Test
    void chunkedWritesProduceOneContinuousStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlaceTimelapseEncoder encoder = new PlaceTimelapseEncoder(out, PlaceTimelapseColouring.PLACED, 10, 10);
        encoder.writeHeader();
        encoder.write(List.of(new PlaceTimelapsePixel(1, 1, "#ffffff")));
        encoder.write(List.of(new PlaceTimelapsePixel(2, 2, "#ffffff"), new PlaceTimelapsePixel(3, 3, "#ffffff")));
        encoder.finish();

        byte[] stream = out.toByteArray();
        assertThat(stream).hasSize(PlaceTimelapseEncoder.HEADER_BYTES + 21);
        assertThat(readUnsignedShort(stream, PlaceTimelapseEncoder.HEADER_BYTES + 14)).isEqualTo(3);
    }

    /**
     * Une couleur qui n'est pas un code hexadecimal ne peut pas s'ecrire sur trois
     * octets. Le flux s'arrete plutot que de livrer une image fausse.
     */
    @Test
    void refusesAColourItCannotWrite() {
        Throwable refusal = catchThrowable(() -> encode(PlaceTimelapseColouring.PLACED, 10, 10,
                List.of(new PlaceTimelapsePixel(1, 1, "rouge"))));

        assertThat(refusal).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] encode(PlaceTimelapseColouring colouring, int width, int height, List<PlaceTimelapsePixel> pixels)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlaceTimelapseEncoder encoder = new PlaceTimelapseEncoder(out, colouring, width, height);
        encoder.writeHeader();
        encoder.write(pixels);
        encoder.finish();
        return out.toByteArray();
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return (Byte.toUnsignedInt(bytes[offset]) << 8) | Byte.toUnsignedInt(bytes[offset + 1]);
    }
}
