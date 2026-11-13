package fr.insastronaute.webapi.card.print.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCrop;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropStatus;
import fr.insastronaute.webapi.card.print.domain.CardDatasetSheetCode;
import fr.insastronaute.webapi.card.print.repository.CardDatasetCropRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * L'export du jeu de donnees reel, decoupe <strong>par bande physique</strong>.
 *
 * <h2>Pourquoi par bande, et jamais par image</h2>
 * Une bande physique est un bout de papier unique : une position d'une planche imprimee. Elle
 * sera photographiee plusieurs fois -- angles, distances, lumieres, appareils differents. Si
 * deux photos d'une meme bande tombent de part et d'autre de la separation, la validation note
 * le modele sur du papier qu'il a deja vu. Elle mesure alors de la memorisation, et annonce un
 * score que le terrain dementira. La separation porte donc sur l'identite de la bande.
 *
 * <h2>Le jeu de test tenu a l'ecart</h2>
 * L'appartenance au jeu de test est une <strong>fonction du code que la bande porte</strong> :
 * SHA-256 d'un sel fixe et de ce code, compare a un seuil fixe. Elle n'est
 * donc pas retiree au sort a chaque export -- une part retiree au sort a chaque export n'est pas
 * mise de cote, puisque la bande qui etait en test hier entraine aujourd'hui. Elle ne depend ni
 * du nombre de photos, ni de leur ordre, ni de la date : elle est arretee a l'impression de la
 * planche, avant qu'une seule photo n'existe, donc personne ne peut regarder les donnees puis
 * choisir son jeu de test. Les fractions sont des constantes de ce fichier et non des parametres
 * de requete, pour la meme raison.
 *
 * <h2>Ce qui attend un arbitrage</h2>
 * Une decoupe {@code PENDING_ARBITRATION} n'a pas d'etiquette de confiance : aucun code de la
 * planche n'explique sa lecture. L'exporter sous le code attendu de sa position, ce serait
 * entrainer sur une etiquette fausse. Elle sort donc des splits etiquetes -- et n'est jamais
 * jetee, car c'est l'exemple que le modele ne sait pas lire, le plus utile du lot. Elle part
 * dans {@code arbitration/}, sous un manifeste qui n'a pas de colonne {@code code} et ne
 * s'appelle pas {@code labels.csv} : un chargeur d'entrainement pointe dessus echoue bruyamment
 * au lieu d'apprendre du bruit.
 *
 * <h2>Ce que l'operateur a ecarte</h2>
 * Une decoupe {@code DISCARDED} est une capture qu'un humain a regardee et jugee inutilisable :
 * floue, mal cadree, la mauvaise bande. Elle ne sort par aucune porte de cet export -- ni
 * {@code labels.csv}, ni le jeu tenu a l'ecart, ni {@code arbitration/}. L'arbitrage serait le
 * detour le plus facile a manquer : une decoupe ecartee et sans etiquette y tomberait par la
 * seule absence de code, et ce qu'un humain a jete reviendrait dans le circuit. Elle reste en
 * base et peut revenir ; {@code report.json} en donne le compte, pour qu'un jeu de donnees qui
 * retrecit ait une raison visible.
 *
 * <h2>La forme de l'archive</h2>
 * <pre>
 * labels.csv              image,code,split,source_photo,slot,...,framing (split = train | val)
 * train/&lt;fichier&gt;
 * val/&lt;fichier&gt;
 * held-out/crops.csv      les memes colonnes, split = test, chemins relatifs a held-out/
 * held-out/test/&lt;fichier&gt;
 * arbitration/crops.csv   expected_code au lieu de code, chemins relatifs a arbitration/
 * arbitration/&lt;fichier&gt;
 * report.json
 * </pre>
 * {@code labels.csv} et l'arborescence {@code train/} + {@code val/} sont exactement ce que
 * produit {@code ml/extract_printed_sheets.py} et ce que lit {@code RealCodeStore} dans
 * {@code ml/finetune_crnn_real.py}. {@code held-out/crops.csv} porte les memes colonnes mais pas
 * le nom {@code labels.csv} : {@code --data held-out} echoue, et rendre ce jeu chargeable demande
 * de renommer le fichier, ce qui est un geste delibere et pas une distraction.
 *
 * <h2>La colonne {@code framing}</h2>
 * Les trois manifestes la portent, en derniere position pour ne rien deplacer de ce que les
 * scripts lisent deja. Elle vaut {@code AUTOMATIC} ou {@code MANUAL}. Une decoupe {@code MANUAL}
 * est une decoupe que la segmentation automatique n'a pas su traiter : l'operateur l'a cadree a
 * la main, et ce geste seul designe un cas difficile, sans qu'aucun modele soit necessaire pour
 * le trouver. Sans cette colonne le signal existe en base et n'atteint jamais l'entrainement.
 */
@Service
public class CardDatasetExportService {

    /** La part des bandes tenue a l'ecart de l'entrainement, pour toujours. */
    static final double HELD_OUT_FRACTION = 0.15;
    /** La part des bandes restantes qui sert de validation. */
    static final double VALIDATION_FRACTION = 0.20;

    /**
     * Les sels sont versionnes. Les changer redistribue les bandes, donc rend les mesures
     * d'avant et d'apres incomparables et fait entrer en entrainement des bandes qui etaient
     * tenues a l'ecart. C'est une decision, pas un reglage.
     */
    static final String HELD_OUT_SALT = "card-dataset:held-out:v1";
    static final String VALIDATION_SALT = "card-dataset:validation:v1";

    private static final String LABELS_ENTRY = "labels.csv";
    private static final String HELD_OUT_PREFIX = "held-out/";
    private static final String HELD_OUT_ENTRY = HELD_OUT_PREFIX + "crops.csv";
    private static final String ARBITRATION_PREFIX = "arbitration/";
    private static final String ARBITRATION_ENTRY = ARBITRATION_PREFIX + "crops.csv";
    private static final String REPORT_ENTRY = "report.json";

    private static final List<String> LABELLED_COLUMNS = List.of(
            "image", "code", "split", "source_photo", "slot",
            "sheet_id", "position", "strip_id", "crop_id",
            "capture_device", "capture_width_px", "capture_height_px",
            "image_width_px", "image_height_px", "created_at", "framing",
            "frame_object_key", "region_x_px", "region_y_px", "region_width_px",
            "region_height_px", "region_angle_deg");
    private static final List<String> ARBITRATION_COLUMNS = List.of(
            "image", "expected_code", "split", "source_photo", "slot",
            "sheet_id", "position", "strip_id", "crop_id",
            "capture_device", "capture_width_px", "capture_height_px",
            "image_width_px", "image_height_px", "created_at", "framing",
            "frame_object_key", "region_x_px", "region_y_px", "region_width_px",
            "region_height_px", "region_angle_deg");

    private static final long MAX_EXPORT_IMAGE_BYTES = 512L * 1024L * 1024L;

    private final CardDatasetCropRepository cropRepository;
    private final CardDatasetCropStorageService storageService;
    private final ObjectMapper objectMapper;

    public CardDatasetExportService(
            CardDatasetCropRepository cropRepository,
            CardDatasetCropStorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.cropRepository = cropRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    /** Les trois destinations d'une bande. Une bande en a une, et une seule, pour toujours. */
    public enum Split {
        TRAIN("train", "train"),
        VALIDATION("val", "val"),
        HELD_OUT("test", "test");

        private final String label;
        private final String directory;

        Split(String label, String directory) {
            this.label = label;
            this.directory = directory;
        }

        public String label() {
            return label;
        }

        public String directory() {
            return directory;
        }
    }

    /**
     * La destination d'une bande physique, deduite de sa seule identite.
     *
     * <p>Deux tirages independants : le premier decide du jeu de test, le second de la
     * validation parmi ce qui reste. Deux sels distincts, sinon une bande proche du seuil du
     * premier tirage le serait aussi du second et les deux parts se recouvriraient.
     */
    public static Split splitForStrip(CardDatasetSheetCode strip) {
        return splitFor(splitKey(strip));
    }

    /**
     * La cle de separation : le code imprime, pas l'identifiant de la bande.
     *
     * <p>Une bande est bien l'unite -- elle n'est jamais coupee en deux -- mais deux bandes
     * peuvent porter le meme code. {@code generateBatchDatasetPdf} enregistre une planche
     * neuve a chaque appel : regenerer la planche d'un lot imprime les memes codes sur un
     * second papier. Deux bouts de papier differents, la meme suite de dix-sept caracteres.
     * Les separer laisserait la validation reconnaitre une chaine vue a l'entrainement --
     * un autre papier, mais le meme mot, sorti de la meme imprimante. Grouper par code est
     * strictement plus prudent que grouper par bande : aucune bande n'est coupee, et aucune
     * chaine ne traverse la separation.
     *
     * <p>Le code est fixe a l'impression, comme l'identifiant de la bande : le jeu de test
     * reste arrete avant qu'une photo n'existe.
     */
    static String splitKey(CardDatasetSheetCode strip) {
        String code = strip.getCode();
        return code == null || code.isBlank()
                ? "strip:" + strip.getId()
                : "code:" + code.trim().toUpperCase(Locale.ROOT);
    }

    public static Split splitFor(String splitKey) {
        if (bucket(HELD_OUT_SALT, splitKey) < HELD_OUT_FRACTION) {
            return Split.HELD_OUT;
        }
        return bucket(VALIDATION_SALT, splitKey) < VALIDATION_FRACTION ? Split.VALIDATION : Split.TRAIN;
    }

    private static double bucket(String salt, String splitKey) {
        byte[] digest = sha256(salt + ":" + splitKey);
        long value = 0L;
        for (int index = 0; index < 8; index++) {
            value = (value << 8) | (digest[index] & 0xFFL);
        }
        return (double) (value >>> 1) / (double) (1L << 62) / 2.0;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 est absent de cette machine virtuelle.", exception);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportArchive() {
        return exportArchive(false);
    }

    /**
     * @param withFrames joint les photos entieres, celles d'ou les decoupes ont ete tirees.
     *
     * <p>Sans elles, l'archive ne permet pas d'entrainer un detecteur : les colonnes
     * {@code region_*} disent ou se trouve la bande, mais l'image dans laquelle elles la
     * situent n'est nulle part. Elles restent optionnelles parce qu'elles pesent bien plus
     * que les decoupes, et qu'un entrainement de lecture n'en a aucun besoin.</p>
     *
     * <p>Chaque photo est ecrite sous sa propre cle de stockage, celle que
     * {@code frame_object_key} porte deja dans les manifestes : rien de nouveau a resoudre
     * pour retrouver l'une depuis l'autre. Une photo partagee par plusieurs decoupes n'est
     * ecrite qu'une fois.</p>
     */
    public byte[] exportArchive(boolean withFrames) {
        List<CardDatasetCrop> crops = cropRepository.findAllForExport();

        List<Row> labelled = new ArrayList<>();
        List<Row> heldOut = new ArrayList<>();
        List<Row> arbitration = new ArrayList<>();
        Map<Split, Set<String>> stripsBySplit = new LinkedHashMap<>();
        Set<String> allStrips = new LinkedHashSet<>();
        for (Split split : Split.values()) {
            stripsBySplit.put(split, new LinkedHashSet<>());
        }

        int discarded = 0;
        for (CardDatasetCrop crop : crops) {
            CardDatasetSheetCode strip = crop.getStrip();
            String slot = slotOf(crop);
            // Ecartee par l'operateur : elle ne sort par aucune porte. Pas labels.csv, pas le
            // jeu tenu a l'ecart, et pas davantage l'arbitrage -- une decoupe sans etiquette y
            // tomberait par la seule absence de code, et ce qu'un humain a juge inutilisable
            // reviendrait dans le circuit par le detour d'un arbitrage. Elle ne compte pas non
            // plus parmi les bandes du jeu : une bande dont toutes les photos sont ecartees
            // n'est plus couverte, et la dire couverte serait mentir sur le jeu de donnees.
            if (crop.getStatus() == CardDatasetCropStatus.DISCARDED) {
                discarded++;
                continue;
            }
            allStrips.add(slot);
            if (crop.getStatus() == CardDatasetCropStatus.PENDING_ARBITRATION || crop.getLabel() == null) {
                arbitration.add(new Row(crop, null, ARBITRATION_PREFIX, fileNameOf(crop), "arbitration", slot));
                continue;
            }
            Split split = splitForStrip(strip);
            stripsBySplit.get(split).add(slot);
            String fileName = split.directory() + "/" + fileNameOf(crop);
            if (split == Split.HELD_OUT) {
                heldOut.add(new Row(crop, split, HELD_OUT_PREFIX, fileName, split.label(), slot));
            } else {
                labelled.add(new Row(crop, split, "", fileName, split.label(), slot));
            }
        }

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            writeCsv(zip, LABELS_ENTRY, LABELLED_COLUMNS, labelled);
            writeCsv(zip, HELD_OUT_ENTRY, LABELLED_COLUMNS, heldOut);
            writeCsv(zip, ARBITRATION_ENTRY, ARBITRATION_COLUMNS, arbitration);

            long written = 0L;
            for (Row row : concat(labelled, heldOut, arbitration)) {
                byte[] image = storageService.read(row.crop().getImageObjectKey());
                written += image.length;
                if (written > MAX_EXPORT_IMAGE_BYTES) {
                    throw new BadRequestException(
                            "Le jeu de donnees depasse 512 Mo d'images : exporter par planche.");
                }
                zip.putNextEntry(new ZipEntry(row.prefix() + row.imagePath()));
                zip.write(image);
                zip.closeEntry();
            }

            if (withFrames) {
                Set<String> writtenFrames = new LinkedHashSet<>();
                for (Row row : concat(labelled, heldOut, arbitration)) {
                    String frameKey = row.crop().getFrameObjectKey();
                    if (frameKey == null || frameKey.isBlank() || !writtenFrames.add(frameKey)) {
                        continue;
                    }
                    byte[] frame = storageService.read(frameKey);
                    written += frame.length;
                    if (written > MAX_EXPORT_IMAGE_BYTES) {
                        throw new BadRequestException(
                                "Le jeu de donnees depasse 512 Mo d'images : exporter sans les photos entieres, "
                                        + "ou par planche.");
                    }
                    zip.putNextEntry(new ZipEntry(frameKey));
                    zip.write(frame);
                    zip.closeEntry();
                }
            }

            zip.putNextEntry(new ZipEntry(REPORT_ENTRY));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                    report(crops.size() - discarded, discarded, allStrips, stripsBySplit,
                            labelled, heldOut, arbitration)));
            zip.closeEntry();

            zip.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de generer l'archive du jeu de donnees.");
        }
    }

    private Map<String, Object> report(
            int cropCount,
            int discardedCount,
            Set<String> allStrips,
            Map<Split, Set<String>> stripsBySplit,
            List<Row> labelled,
            List<Row> heldOut,
            List<Row> arbitration
    ) {
        Map<String, Object> stripCounts = new LinkedHashMap<>();
        Map<String, Object> cropCounts = new LinkedHashMap<>();
        stripsBySplit.forEach((split, strips) -> stripCounts.put(split.label(), strips.size()));
        cropCounts.put(Split.TRAIN.label(), labelled.stream().filter(row -> row.split() == Split.TRAIN).count());
        cropCounts.put(Split.VALIDATION.label(), labelled.stream().filter(row -> row.split() == Split.VALIDATION).count());
        cropCounts.put(Split.HELD_OUT.label(), (long) heldOut.size());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("format", "insastronaute-card-dataset");
        report.put("version", 1);
        report.put("split_unit", "physical_strip");
        report.put("split_key", "printed_code");
        report.put("split_rule", "sha256(salt:code:<code imprime>), premiers 64 bits, ramenes dans [0,1)");
        report.put("held_out_salt", HELD_OUT_SALT);
        report.put("held_out_fraction", HELD_OUT_FRACTION);
        report.put("validation_salt", VALIDATION_SALT);
        report.put("validation_fraction", VALIDATION_FRACTION);
        // Le total est celui de ce que l'archive porte. Les decoupes ecartees n'y sont pas, et
        // crops_discarded dit ou elles sont passees : un jeu de donnees qui retrecit doit avoir
        // une raison visible, sans quoi la seule trace de dix captures jetees serait un total
        // qui a baisse sans explication.
        report.put("crops_total", cropCount);
        report.put("crops_pending_arbitration", arbitration.size());
        report.put("crops_discarded", discardedCount);
        report.put("crops_by_split", cropCounts);
        // Le total compte aussi les bandes dont toutes les photos attendent un arbitrage : elles
        // existent, elles ne sont dans aucun split. La somme de strips_by_split vaut donc
        // strips_labelled, pas strips_total.
        report.put("strips_total", allStrips.size());
        report.put("strips_labelled", stripsBySplit.values().stream().mapToInt(Set::size).sum());
        report.put("strips_by_split", stripCounts);
        report.put("labels_entry", LABELS_ENTRY);
        report.put("held_out_entry", HELD_OUT_ENTRY);
        report.put("arbitration_entry", ARBITRATION_ENTRY);
        report.put("notes", List.of(
                "La separation se fait par bande physique, jamais par image : la meme bande est "
                        + "photographiee plusieurs fois et deux photos d'une bande de part et d'autre "
                        + "feraient mesurer de la memorisation a la validation.",
                "Le jeu de test est une fonction du code imprime, arretee a l'impression de la "
                        + "planche. Il n'est pas retire au sort a chaque export, sinon il ne serait "
                        + "pas tenu a l'ecart.",
                "La cle est le code imprime et non l'identifiant de la bande : regenerer la planche "
                        + "d'un lot imprime les memes codes sur un second papier, et separer ces deux "
                        + "bandes laisserait la validation reconnaitre une chaine vue a l'entrainement. "
                        + "Aucune bande n'est pour autant coupee en deux.",
                "held-out/crops.csv ne s'appelle pas labels.csv pour que --data held-out echoue. Le "
                        + "renommer est un geste delibere.",
                "arbitration/crops.csv n'a pas de colonne code : ces decoupes n'ont pas d'etiquette "
                        + "de confiance. Elles sont conservees et jamais entrainees telles quelles.",
                "Les decoupes ecartees par l'operateur ne sont dans aucun manifeste et leurs "
                        + "images ne sont pas dans l'archive : ce qu'un humain a juge inutilisable "
                        + "n'atteint aucun entrainement. Elles restent en base et peuvent revenir, "
                        + "et crops_discarded dit combien ont ete retirees."
        ));
        return report;
    }

    private List<Row> concat(List<Row> first, List<Row> second, List<Row> third) {
        List<Row> all = new ArrayList<>(first.size() + second.size() + third.size());
        all.addAll(first);
        all.addAll(second);
        all.addAll(third);
        return all;
    }

    private void writeCsv(ZipOutputStream zip, String entry, List<String> columns, List<Row> rows) throws IOException {
        StringBuilder content = new StringBuilder(String.join(",", columns)).append('\n');
        for (Row row : rows) {
            List<String> values = new ArrayList<>(columns.size());
            for (String column : columns) {
                values.add(escape(row.value(column)));
            }
            content.append(String.join(",", values)).append('\n');
        }
        zip.putNextEntry(new ZipEntry(entry));
        zip.write(content.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** Le nom de la bande physique, tel que la colonne {@code slot} l'a toujours porte. */
    private static String slotOf(CardDatasetCrop crop) {
        return crop.getSheet().getSheetId() + "-P" + String.format(Locale.ROOT, "%02d", crop.getStrip().getPosition());
    }

    private static String fileNameOf(CardDatasetCrop crop) {
        return slotOf(crop) + "__" + crop.getId() + "." + extensionOf(crop);
    }

    private static String extensionOf(CardDatasetCrop crop) {
        String objectKey = crop.getImageObjectKey();
        if (objectKey != null) {
            int dot = objectKey.lastIndexOf('.');
            if (dot >= 0 && dot < objectKey.length() - 1) {
                String candidate = objectKey.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (candidate.matches("[a-z0-9]{1,5}")) {
                    return candidate;
                }
            }
        }
        String contentType = crop.getImageContentType() == null ? "" : crop.getImageContentType().toLowerCase(Locale.ROOT);
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }

    /**
     * Une ligne du manifeste, et l'entree d'archive qui va avec.
     *
     * @param prefix    le prefixe de l'entree zip ; les chemins du manifeste restent relatifs a
     *                  la racine du manifeste, pour que chaque dossier soit lisible seul.
     * @param imagePath la valeur de la colonne {@code image}
     */
    private record Row(CardDatasetCrop crop, Split split, String prefix, String imagePath,
                       String splitLabel, String slot) {

        String value(String column) {
            return switch (column) {
                case "image" -> imagePath;
                case "code" -> crop.getLabel();
                case "expected_code" -> crop.getStrip().getCode();
                case "split" -> splitLabel;
                // Chaque decoupe est deposee seule : sa photo source, c'est elle-meme. La colonne
                // reste, parce que le format la porte, mais elle ne designe pas une planche
                // entiere photographiee d'un coup.
                case "source_photo" -> crop.getSheet().getSheetId() + "/" + crop.getId() + "."
                        + extensionOf(crop);
                case "slot" -> slot;
                case "sheet_id" -> crop.getSheet().getSheetId();
                case "position" -> String.valueOf(crop.getStrip().getPosition());
                case "strip_id" -> String.valueOf(crop.getStrip().getId());
                case "crop_id" -> String.valueOf(crop.getId());
                case "capture_device" -> crop.getCaptureDevice();
                case "capture_width_px" -> text(crop.getCaptureWidthPx());
                case "capture_height_px" -> text(crop.getCaptureHeightPx());
                case "image_width_px" -> text(crop.getImageWidthPx());
                case "image_height_px" -> text(crop.getImageHeightPx());
                case "created_at" -> crop.getCreatedAt() == null ? "" : crop.getCreatedAt().toString();
                // AUTOMATIC ou MANUAL. Une decoupe MANUAL est une decoupe que la segmentation
                // automatique n'a pas su traiter : le cas difficile du lot. La colonne existe
                // pour que ce signal atteigne l'entrainement au lieu de rester en base.
                case "framing" -> crop.getFraming() == null ? "" : crop.getFraming().name();
                // Ou se trouvait cette decoupe dans la photo entiere, et laquelle. Les images
                // elles-memes ne sont pas dans cette archive : elle sert a entrainer le lecteur,
                // et y verser des photos dix fois plus lourdes que les decoupes alourdirait
                // chaque export pour un chantier qui n'est pas le sien. La cle suffit a les
                // retrouver, et les colonnes suffisent a mesurer le cadrage automatique.
                //
                // Une seule bande est decrite par photo. Les autres bandes visibles ne sont pas
                // du fond : elles sont inconnues. Les compter comme du fond apprendrait a un
                // detecteur a ignorer de vraies bandes.
                case "frame_object_key" -> crop.getFrameObjectKey() == null ? "" : crop.getFrameObjectKey();
                case "region_x_px" -> text(crop.getRegionXPx());
                case "region_y_px" -> text(crop.getRegionYPx());
                case "region_width_px" -> text(crop.getRegionWidthPx());
                case "region_height_px" -> text(crop.getRegionHeightPx());
                case "region_angle_deg" -> crop.getRegionAngleDeg() == null ? "" : String.valueOf(crop.getRegionAngleDeg());
                default -> "";
            };
        }

        private String text(Integer value) {
            return value == null ? "" : String.valueOf(value);
        }
    }
}
