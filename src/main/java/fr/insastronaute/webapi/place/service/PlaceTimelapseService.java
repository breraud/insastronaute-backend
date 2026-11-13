package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.place.domain.PlaceTimelapseColouring;
import fr.insastronaute.webapi.place.repository.PlacePixelPlacementRepository;
import fr.insastronaute.webapi.place.repository.PlaceTimelapsePixel;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;

/**
 * Rejouer la fresque, sous forme de flux.
 *
 * <p>Le canevas ne garde que son etat courant : c'est l'historique des poses, tenu
 * pour la moderation, qui permet de remonter le temps. Il est en ajout seul, donc
 * rejouer ses lignes par-dessus un canevas vide reconstruit n'importe quel instant.</p>
 *
 * <p>Rien n'est rendu ici : le serveur envoie les poses, le navigateur en fait une
 * video. Encoder une video cote serveur demanderait ffmpeg, un repertoire de travail
 * et une file d'attente, pour un export que les organisateurs lancent trois fois
 * dans l'annee.</p>
 */
@Service
public class PlaceTimelapseService {

    /**
     * La taille d'un bloc lu en base.
     *
     * <p>Assez grand pour que le nombre d'allers-retours reste petit, assez petit pour
     * que la memoire ne depende pas de la duree de l'evenement : un bloc pese environ
     * 350 Ko de projections, quel que soit le nombre total de poses.</p>
     */
    static final int PAGE_SIZE = 50_000;

    private final PlaceCanvasStore canvasStore;
    private final PlacePixelPlacementRepository placements;

    public PlaceTimelapseService(PlaceCanvasStore canvasStore,
                                 PlacePixelPlacementRepository placements) {
        this.canvasStore = canvasStore;
        this.placements = placements;
    }

    /**
     * Verifie ce qui peut l'etre avant que le telechargement commence.
     *
     * <p>Separe de l'ecriture pour une raison de protocole : le corps d'une reponse en
     * flux s'ecrit apres que le statut est parti. Une periode a l'envers decouverte a
     * ce moment-la ne produirait pas une erreur, mais un fichier tronque.</p>
     *
     * @throws CodedBadRequestException si la periode est vide ou a l'envers.
     * @throws NotFoundException si la fresque demandee n'a pas de canevas.
     */
    public Replay prepare(PlaceTimelapseColouring colouring, Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new CodedBadRequestException("PLACE_TIMELAPSE_EMPTY_WINDOW",
                    "La fin de la période doit être postérieure à son début.");
        }
        PlaceCanvasDescriptor descriptor = descriptor();
        return new Replay(colouring, descriptor.generation(), descriptor.width(), descriptor.height(), from, to);
    }

    /** Ce qu'il faut pour ecrire le flux, une fois la demande jugee valable. */
    public record Replay(PlaceTimelapseColouring colouring, String generation, int width, int height,
                         Instant from, Instant to) {
    }

    /** Ecrit le flux. Tout ce qui pouvait etre refuse l'a ete par {@link #prepare}. */
    public long writeTo(OutputStream out, Replay replay) throws IOException {
        String generation = replay.generation();
        Instant from = replay.from();
        Instant to = replay.to();

        PlaceTimelapseEncoder encoder =
                new PlaceTimelapseEncoder(out, replay.colouring(), replay.width(), replay.height());
        encoder.writeHeader();

        // Pagination par rang plutot que par curseur : a l'echelle d'un evenement —
        // quelques centaines de milliers de poses, soit une poignee de pages — le saut
        // se fait dans l'index, et un curseur obligerait a transporter l'instant et
        // l'identifiant de chaque ligne pour ne rien gagner de visible.
        int page = 0;
        List<PlaceTimelapsePixel> chunk;
        do {
            PageRequest block = PageRequest.of(page, PAGE_SIZE);
            chunk = replay.colouring() == PlaceTimelapseColouring.DEPARTMENT
                    ? placements.findReplayByDepartment(generation, from, to,
                            PlaceDepartmentFresqueService.UNATTRIBUTED_COLOR, block)
                    : placements.findReplay(generation, from, to, block);
            encoder.write(chunk);
            page++;
        } while (chunk.size() == PAGE_SIZE);

        encoder.finish();
        return encoder.count();
    }

    /**
     * Les dimensions viennent du canevas, pas de la configuration.
     *
     * <p>La configuration dit ce qui a ete demande ; le descripteur dit ce qui existe.
     * Les deux different entre une demande de redimensionnement et sa fin.</p>
     */
    private PlaceCanvasDescriptor descriptor() {
        try {
            return canvasStore.descriptor();
        } catch (IllegalStateException neverOpened) {
            throw new NotFoundException("La fresque n'a pas de canevas à rejouer.");
        }
    }
}
