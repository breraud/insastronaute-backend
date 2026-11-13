package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.place.domain.PlaceTimelapseColouring;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.place.repository.PlaceTimelapsePixel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceTimelapseServiceTest {

    private final PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
    private final PlacePixelPlacementRepository placements = mock(PlacePixelPlacementRepository.class);

    private static final Instant FROM = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-23T10:00:00Z");

    private PlaceTimelapseService service;

    @BeforeEach
    void setUp() {
        service = new PlaceTimelapseService(canvasStore, placements);
        when(canvasStore.descriptor()).thenReturn(new PlaceCanvasDescriptor("gen-libre", 12, 500, 400));
        when(placements.findReplay(any(), any(), any(), any())).thenReturn(List.of());
        when(placements.findReplayByDepartment(any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    /** Une periode a l'envers donnerait une video vide : autant le dire tout de suite. */
    @Test
    void refusesABackwardsWindow() {
        Throwable refusal = catchThrowable(() -> service.prepare(PlaceTimelapseColouring.PLACED, TO, FROM));

        assertThat(((CodedBadRequestException) refusal).getCode()).isEqualTo("PLACE_TIMELAPSE_EMPTY_WINDOW");
    }

    @Test
    void refusesAnInstantWindow() {
        assertThat(catchThrowable(() -> service.prepare(PlaceTimelapseColouring.PLACED, FROM, FROM)))
                .isInstanceOf(CodedBadRequestException.class);
    }

    /** Ni sur un canevas qui n'existe pas dans Redis. */
    @Test
    void refusesACanvasRedisNeverInitialised() {
        when(canvasStore.descriptor()).thenThrow(new IllegalStateException("Le canevas Redis n'est pas initialisé."));

        assertThat(catchThrowable(() -> service.prepare(PlaceTimelapseColouring.PLACED, FROM, TO)))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * Les dimensions du flux sont celles du canevas, pas celles de la configuration.
     *
     * <p>Entre une demande de redimensionnement et sa fin, les deux different — et
     * c'est le canevas qui dit ou tombent les coordonnees de l'historique.</p>
     */
    @Test
    void theStreamCarriesTheCanvasDimensions() throws IOException {
        byte[] stream = write(PlaceTimelapseColouring.PLACED);

        assertThat(readUnsignedShort(stream, 6)).isEqualTo(500);
        assertThat(readUnsignedShort(stream, 8)).isEqualTo(400);
    }

    /**
     * Une seule generation : un redimensionnement recentre l'image, donc une
     * coordonnee d'une generation precedente ne designe plus la meme case.
     */
    @Test
    void readsOnlyTheActiveGeneration() throws IOException {
        write(PlaceTimelapseColouring.PLACED);

        verify(placements).findReplay(eq("gen-libre"), eq(FROM), eq(TO), any());
    }

    /**
     * Colore par departement, le rejeu passe par l'autre requete — celle qui remonte au
     * departement de l'auteur — et donne au sans-departement le gris de la vue.
     */
    @Test
    void theDepartmentColouringRepaintsEachPlacement() throws IOException {
        byte[] stream = write(PlaceTimelapseColouring.DEPARTMENT);

        verify(placements).findReplayByDepartment(eq("gen-libre"), eq(FROM), eq(TO),
                eq(PlaceDepartmentFresqueService.UNATTRIBUTED_COLOR), any());
        verify(placements, never()).findReplay(any(), any(), any(), any());
        // Le fichier porte sa coloration : on ne la redevine pas a la relecture.
        assertThat(stream[5]).isEqualTo((byte) 1);
    }

    /** Un bloc plein appelle le suivant ; un bloc incomplet arrete la lecture. */
    @Test
    void readsPageAfterPageUntilAShortOneEndsIt() throws IOException {
        when(placements.findReplay(any(), any(), any(), any()))
                .thenReturn(pixels(PlaceTimelapseService.PAGE_SIZE))
                .thenReturn(pixels(PlaceTimelapseService.PAGE_SIZE))
                .thenReturn(pixels(3));

        byte[] stream = write(PlaceTimelapseColouring.PLACED);

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(placements, times(3)).findReplay(any(), any(), any(), pages.capture());
        assertThat(pages.getAllValues()).extracting(Pageable::getPageNumber).containsExactly(0, 1, 2);
        assertThat(stream).hasSize(PlaceTimelapseEncoder.HEADER_BYTES
                + (2 * PlaceTimelapseService.PAGE_SIZE + 3) * PlaceTimelapseEncoder.PIXEL_BYTES);
    }

    /** Une periode sans une seule pose reste un fichier lisible : l'en-tete, et rien. */
    @Test
    void anEmptyPeriodStillProducesAReadableHeader() throws IOException {
        assertThat(write(PlaceTimelapseColouring.PLACED)).hasSize(PlaceTimelapseEncoder.HEADER_BYTES);
    }

    @Test
    void reportsHowManyPlacementsItWrote() throws IOException {
        when(placements.findReplay(any(), any(), any(), any())).thenReturn(pixels(7));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long written = service.writeTo(out, service.prepare(PlaceTimelapseColouring.PLACED, FROM, TO));

        assertThat(written).isEqualTo(7);
    }

    private byte[] write(PlaceTimelapseColouring colouring) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeTo(out, service.prepare(colouring, FROM, TO));
        return out.toByteArray();
    }

    private static List<PlaceTimelapsePixel> pixels(int count) {
        List<PlaceTimelapsePixel> pixels = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            pixels.add(new PlaceTimelapsePixel(index % 500, index % 400, "#e50000"));
        }
        return pixels;
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return (Byte.toUnsignedInt(bytes[offset]) << 8) | Byte.toUnsignedInt(bytes[offset + 1]);
    }
}
