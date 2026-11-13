package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.dto.PlaceDepartmentContributionsDto;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository.LatestCellPlacement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fresque des départements n'est pas un second canevas : c'est la fresque libre,
 * chaque pixel repeint de la couleur du département de celui qui l'a posé.
 */
class PlaceDepartmentFresqueServiceTest {

    private static final int WIDTH = 4;
    private static final int HEIGHT = 2;

    private final PlaceCanvasStore canvasStore = mock(PlaceCanvasStore.class);
    private final PlaceConfigService configService = mock(PlaceConfigService.class);
    private final PlacePixelPlacementRepository placements = mock(PlacePixelPlacementRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final PlaceDepartmentFresqueService service =
            new PlaceDepartmentFresqueService(canvasStore, configService, placements, departments);

    /**
     * Les identifiants, tenus à part : les lire sur le mock pendant qu'on écrit le stub
     * d'un autre laisserait ce stub inachevé aux yeux de Mockito. Déclarés avant les
     * départements, qui les remplissent en s'initialisant.
     */
    private final Map<Department, UUID> ids = new IdentityHashMap<>();

    private final Department gm = department("GM", "#3b82f6");
    private final Department iti = department("ITI", "#38bdf8");
    private final Department mrie = department("MRIE", "#f97316");

    @BeforeEach
    void setUp() {
        PlaceConfig config = new PlaceConfig();
        config.setCanvasWidth(WIDTH);
        config.setCanvasHeight(HEIGHT);
        when(configService.currentConfig()).thenReturn(config);
        when(departments.findAll()).thenReturn(List.of(mrie, iti, gm));
    }

    @Test
    void eachPixelTakesTheColourOfItsAuthorsDepartment() throws IOException {
        byte[] canvas = canvas();
        paint(canvas, 0, 0, "#e50000");
        paint(canvas, 1, 0, "#02be01");
        paint(canvas, 2, 1, "#e50000");
        onCanvas(canvas, 7);
        when(placements.findLatestCellsWithDepartment("g1")).thenReturn(List.of(
                cell(0, 0, "#e50000", gm),
                cell(1, 0, "#02be01", iti),
                cell(2, 1, "#e50000", gm)));

        BufferedImage image = image(service.snapshot());

        assertThat(rgb(image, 0, 0)).isEqualTo(0x3b82f6);
        assertThat(rgb(image, 1, 0)).isEqualTo(0x38bdf8);
        assertThat(rgb(image, 2, 1)).isEqualTo(0x3b82f6);
        // Une case jamais peinte garde le fond de la fresque libre.
        assertThat(rgb(image, 3, 1)).isZero();
    }

    /**
     * Une censure recouvre la case sans s'inscrire au registre : la dernière pose a
     * alors une couleur que le canevas ne montre plus, et ce dessin n'est plus celui
     * de son auteur.
     */
    @Test
    void aPixelCoveredSinceItsLastPlacementIsNoLongerCountedForItsAuthor() throws IOException {
        byte[] canvas = canvas();
        paint(canvas, 0, 0, "#ffffff");
        onCanvas(canvas, 7);
        when(placements.findLatestCellsWithDepartment("g1")).thenReturn(List.of(cell(0, 0, "#e50000", gm)));

        assertThat(rgb(image(service.snapshot()), 0, 0)).isEqualTo(0x52525b);
        PlaceDepartmentContributionsDto contributions = service.contributions();
        assertThat(contributions.attributedPixels()).isZero();
        assertThat(contributions.unattributedPixels()).isEqualTo(1);
    }

    /** Grise, pas noire : noire, la case disparaîtrait dans le fond et le dessin perdrait sa forme. */
    @Test
    void aPaintedPixelThatNoDepartmentClaimsStaysVisibleInGrey() throws IOException {
        byte[] canvas = canvas();
        paint(canvas, 1, 1, "#e50000");
        paint(canvas, 2, 1, "#02be01");
        onCanvas(canvas, 7);
        // (1, 1) : un auteur sans département. (2, 1) : peint avant tout enregistrement.
        when(placements.findLatestCellsWithDepartment("g1")).thenReturn(List.of(cell(1, 1, "#e50000", null)));

        BufferedImage image = image(service.snapshot());

        assertThat(rgb(image, 1, 1)).isEqualTo(0x52525b);
        assertThat(rgb(image, 2, 1)).isEqualTo(0x52525b);
        assertThat(service.contributions().unattributedPixels()).isEqualTo(2);
    }

    @Test
    void contributionsListEveryDepartmentFromTheMostPresent() {
        byte[] canvas = canvas();
        paint(canvas, 0, 0, "#e50000");
        paint(canvas, 1, 0, "#e50000");
        paint(canvas, 2, 0, "#02be01");
        onCanvas(canvas, 7);
        when(placements.findLatestCellsWithDepartment("g1")).thenReturn(List.of(
                cell(0, 0, "#e50000", gm),
                cell(1, 0, "#e50000", gm),
                cell(2, 0, "#02be01", iti)));

        PlaceDepartmentContributionsDto contributions = service.contributions();

        assertThat(contributions.revision()).isEqualTo("7");
        assertThat(contributions.attributedPixels()).isEqualTo(3);
        assertThat(contributions.unattributedPixels()).isZero();
        assertThat(contributions.departments())
                .extracting(PlaceDepartmentContributionsDto.Entry::shortName, PlaceDepartmentContributionsDto.Entry::pixels)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("GM", 2L),
                        org.assertj.core.groups.Tuple.tuple("ITI", 1L),
                        org.assertj.core.groups.Tuple.tuple("MRIE", 0L));
    }

    /** Deux poses au même instant font sortir la case deux fois du registre : elle ne compte qu'une. */
    @Test
    void aCellReturnedTwiceIsCountedOnce() {
        byte[] canvas = canvas();
        paint(canvas, 0, 0, "#e50000");
        onCanvas(canvas, 7);
        when(placements.findLatestCellsWithDepartment("g1")).thenReturn(List.of(
                cell(0, 0, "#e50000", gm),
                cell(0, 0, "#e50000", iti)));

        PlaceDepartmentContributionsDto contributions = service.contributions();

        assertThat(contributions.attributedPixels()).isEqualTo(1);
        assertThat(contributions.departments())
                .filteredOn(entry -> entry.pixels() > 0)
                .hasSize(1);
    }

    /** Une pose d'avant un rétrécissement désigne une case qui n'existe plus. */
    @Test
    void aPlacementOutsideTheCanvasIsIgnored() {
        onCanvas(canvas(), 7);
        when(placements.findLatestCellsWithDepartment("g1")).thenReturn(List.of(cell(9, 9, "#000000", gm)));

        assertThat(service.contributions().attributedPixels()).isZero();
    }

    /** Tous les spectateurs relisent la même image : le registre n'est relu que quand le canevas bouge. */
    @Test
    void theRegistryIsReadOncePerCanvasRevision() {
        onCanvas(canvas(), 7);
        when(placements.findLatestCellsWithDepartment(anyString())).thenReturn(List.of());

        service.snapshot();
        service.contributions();
        service.snapshot();
        verify(placements, times(1)).findLatestCellsWithDepartment("g1");

        onCanvas(canvas(), 8);
        service.contributions();
        verify(placements, times(2)).findLatestCellsWithDepartment("g1");
    }

    /** L'image porte la révision de la fresque libre : c'est elle que suivent ses mises à jour. */
    @Test
    void theSnapshotCarriesTheFreeCanvasRevision() {
        onCanvas(canvas(), 42);
        when(placements.findLatestCellsWithDepartment("g1")).thenReturn(List.of());

        assertThat(service.snapshot().descriptor().revision()).isEqualTo(42);
    }

    private void onCanvas(byte[] rgb, long revision) {
        when(canvasStore.readCanvas()).thenReturn(
                new PlaceCanvasStore.CanvasRead(rgb, new PlaceCanvasDescriptor("g1", revision, WIDTH, HEIGHT)));
    }

    private static byte[] canvas() {
        return new byte[WIDTH * HEIGHT * 3];
    }

    private static void paint(byte[] canvas, int x, int y, String color) {
        byte[] rgb = PlaceCanvasStore.rgb(color);
        System.arraycopy(rgb, 0, canvas, (y * WIDTH + x) * 3, 3);
    }

    private static BufferedImage image(PlaceCanvasSnapshot snapshot) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(snapshot.png()));
    }

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFFFFFF;
    }

    private Department department(String shortName, String color) {
        Department department = mock(Department.class);
        UUID id = UUID.randomUUID();
        when(department.getId()).thenReturn(id);
        when(department.getShortName()).thenReturn(shortName);
        when(department.getName()).thenReturn("Département " + shortName);
        when(department.getColor()).thenReturn(color);
        ids.put(department, id);
        return department;
    }

    private LatestCellPlacement cell(int x, int y, String color, Department department) {
        UUID departmentId = department == null ? null : ids.get(department);
        return new LatestCellPlacement() {
            @Override public int getX() { return x; }
            @Override public int getY() { return y; }
            @Override public String getColor() { return color; }
            @Override public UUID getDepartmentId() { return departmentId; }
        };
    }
}
