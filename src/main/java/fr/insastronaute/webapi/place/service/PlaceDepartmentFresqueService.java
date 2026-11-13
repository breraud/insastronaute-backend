package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.place.domain.PlaceConfig;
import fr.insastronaute.webapi.place.dto.PlaceDepartmentContributionsDto;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La fresque des départements : la fresque libre, relue par département.
 *
 * <p>Ce n'est pas un second canevas. Chaque case y reprend le pixel de la fresque
 * libre, peint de la couleur du département de celui qui l'a posé : on n'y pose
 * rien, on y regarde qui tient quoi.</p>
 *
 * <p>Une case n'est imputée que si sa dernière pose enregistrée a encore la couleur
 * que montre le canevas. Sinon, quelque chose l'a recouverte hors du jeu — une
 * censure — et l'attribuer au dernier poseur lui donnerait un dessin qui n'est plus
 * le sien. Même chose après un redimensionnement : le registre des poses est tenu
 * par génération de canevas, et la nouvelle n'en a encore aucune.</p>
 */
@Service
public class PlaceDepartmentFresqueService {

    /**
     * Une case peinte que rien ne rattache à un département. Grise plutôt que noire :
     * noire, elle se confondrait avec le fond et le dessin perdrait sa forme.
     */
    static final String UNATTRIBUTED_COLOR = "#52525b";

    private final PlaceCanvasStore canvasStore;
    private final PlaceConfigService configService;
    private final PlacePixelPlacementRepository placements;
    private final DepartmentRepository departments;

    /**
     * Le dernier calcul, valable tant que le canevas n'a pas bougé.
     *
     * <p>Tous les spectateurs de l'onglet relisent la même image : la recalculer à
     * chaque demande ferait une lecture du registre par joueur pour un résultat
     * identique. Chaque pixel posé change la révision, donc invalide le calcul.</p>
     */
    private volatile Fresque cached;

    public PlaceDepartmentFresqueService(
            PlaceCanvasStore canvasStore,
            PlaceConfigService configService,
            PlacePixelPlacementRepository placements,
            DepartmentRepository departments
    ) {
        this.canvasStore = canvasStore;
        this.configService = configService;
        this.placements = placements;
        this.departments = departments;
    }

    /** L'image, à la révision de la fresque libre : ses mises à jour en direct sont les siennes. */
    public PlaceCanvasSnapshot snapshot() {
        Fresque fresque = current();
        return new PlaceCanvasSnapshot(fresque.png(), fresque.descriptor());
    }

    public PlaceDepartmentContributionsDto contributions() {
        return current().contributions();
    }

    private Fresque current() {
        PlaceConfig config = configService.currentConfig();
        canvasStore.initialize(config.getCanvasWidth(), config.getCanvasHeight());
        PlaceCanvasStore.CanvasRead canvas = canvasStore.readCanvas();
        PlaceCanvasDescriptor descriptor = canvas.descriptor();
        Fresque known = cached;
        if (known != null
                && known.descriptor().revision() == descriptor.revision()
                && known.descriptor().generation().equals(descriptor.generation())) {
            return known;
        }
        Fresque computed = compute(canvas.rgb(), descriptor);
        cached = computed;
        return computed;
    }

    private Fresque compute(byte[] canvas, PlaceCanvasDescriptor descriptor) {
        int width = descriptor.width();
        int height = descriptor.height();
        int cells = Math.multiplyExact(width, height);
        byte[] fresque = new byte[Math.multiplyExact(cells, 3)];
        boolean[] attributed = new boolean[cells];

        Map<UUID, Department> byId = new HashMap<>();
        departments.findAll().forEach(department -> byId.put(department.getId(), department));
        Map<UUID, Long> pixels = new HashMap<>();

        for (PlacePixelPlacementRepository.LatestCellPlacement cell
                : placements.findLatestCellsWithDepartment(descriptor.generation())) {
            if (cell.getX() < 0 || cell.getY() < 0 || cell.getX() >= width || cell.getY() >= height) {
                continue;
            }
            Department department = cell.getDepartmentId() == null ? null : byId.get(cell.getDepartmentId());
            int index = cell.getY() * width + cell.getX();
            // Une case déjà comptée : deux poses au même instant la rendent deux fois.
            if (department == null || attributed[index] || !showsColor(canvas, index * 3, cell.getColor())) {
                continue;
            }
            write(fresque, index * 3, department.getColor());
            attributed[index] = true;
            pixels.merge(department.getId(), 1L, Long::sum);
        }

        long attributedPixels = 0;
        long unattributedPixels = 0;
        for (int index = 0; index < cells; index++) {
            if (attributed[index]) {
                attributedPixels++;
            } else if (painted(canvas, index * 3)) {
                // Le fond reste noir, comme sur la fresque libre : il est déjà à zéro.
                write(fresque, index * 3, UNATTRIBUTED_COLOR);
                unattributedPixels++;
            }
        }

        List<PlaceDepartmentContributionsDto.Entry> entries = byId.values().stream()
                .map(department -> new PlaceDepartmentContributionsDto.Entry(
                        department.getId(),
                        department.getShortName(),
                        department.getName(),
                        department.getColor(),
                        pixels.getOrDefault(department.getId(), 0L)))
                .sorted(Comparator.comparingLong(PlaceDepartmentContributionsDto.Entry::pixels).reversed()
                        .thenComparing(PlaceDepartmentContributionsDto.Entry::shortName,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new Fresque(
                descriptor,
                PlaceCanvasStore.renderPng(fresque, width, height),
                new PlaceDepartmentContributionsDto(
                        Long.toString(descriptor.revision()), attributedPixels, unattributedPixels, entries));
    }

    /** La case montre-t-elle encore la couleur de sa dernière pose ? */
    private static boolean showsColor(byte[] canvas, int offset, String color) {
        if (color == null || color.length() != 7 || offset + 2 >= canvas.length) {
            return false;
        }
        byte[] expected = PlaceCanvasStore.rgb(color);
        return canvas[offset] == expected[0] && canvas[offset + 1] == expected[1] && canvas[offset + 2] == expected[2];
    }

    private static boolean painted(byte[] canvas, int offset) {
        return offset + 2 < canvas.length
                && (canvas[offset] != 0 || canvas[offset + 1] != 0 || canvas[offset + 2] != 0);
    }

    private static void write(byte[] fresque, int offset, String color) {
        byte[] rgb = PlaceCanvasStore.rgb(color);
        fresque[offset] = rgb[0];
        fresque[offset + 1] = rgb[1];
        fresque[offset + 2] = rgb[2];
    }

    private record Fresque(PlaceCanvasDescriptor descriptor, byte[] png, PlaceDepartmentContributionsDto contributions) {
    }
}
