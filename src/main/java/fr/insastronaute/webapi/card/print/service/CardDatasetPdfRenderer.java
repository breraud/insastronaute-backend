package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateSlot;
import fr.insastronaute.webapi.common.web.BadRequestException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CardDatasetPdfRenderer {

    public record DatasetCardInfo(String publicId, byte[] imageBytes) {}

    private static final int COLS = 3;
    private static final int ROWS = 13;
    private static final float MARGIN = 20f;
    private static final float GAP_X = 10f;
    private static final float GAP_Y = 10f;
    private static final float BOTTOM_MARGIN = 40f;

    /**
     * La grille d'une planche, et la bande qu'elle impose.
     *
     * <p>La dependance est volontairement dans ce sens. Auparavant la hauteur de bande valait
     * {@code codeHeightPt * 3.5}, donc la mise en page d'une planche dependait d'un champ de
     * calibration qui decrit tout autre chose : l'endroit ou le code est imprime sur une vraie
     * carte. Recalibrer faisait silencieusement deborder une planche sur une seconde page. Ici
     * la grille est fixe -- {@value #COLS} x {@value #ROWS}, soit une page pile -- et la bande
     * decoupee s'ajuste pour que l'image ne soit ni etiree ni ecrasee.
     */
    public static int cardsPerPage() {
        return COLS * ROWS;
    }

    private static float cardWidth() {
        return (PDRectangle.A4.getWidth() - (2 * MARGIN) - (COLS - 1) * GAP_X) / COLS;
    }

    private static float cardHeight() {
        return (PDRectangle.A4.getHeight() - MARGIN - BOTTOM_MARGIN + GAP_Y) / ROWS - GAP_Y;
    }

    /** La part de la hauteur de carte que la bande capture, pour remplir la case sans deformer. */
    private static float cropHeightRatio(CardPrintTemplate template) {
        CardPrintTemplateSlot slot = template.getSlots().get(0);
        float cropHeightPt = slot.getWidthPt().floatValue() * cardHeight() / cardWidth();
        return cropHeightPt / slot.getHeightPt().floatValue();
    }

    /** Une planche a rendre : son identifiant imprime, et les cartes qu'elle porte. */
    public record DatasetSheetRender(String documentId, List<DatasetCardInfo> cards) {}

    public byte[] render(List<DatasetCardInfo> cards, CardPrintTemplate template, String documentId) {
        return renderSheets(List.of(new DatasetSheetRender(documentId, cards == null ? List.of() : cards)), template);
    }

    /**
     * Rend plusieurs planches dans un seul document.
     *
     * <p>C'est le point de la manoeuvre : un seul {@link PDDocument} veut dire un seul jeu
     * d'objets images, donc une image par carte distincte pour tout le lot au lieu d'une par
     * emplacement. Concatener des PDF deja rendus ne permettrait pas ce partage.
     */
    public byte[] renderSheets(List<DatasetSheetRender> sheets, CardPrintTemplate template) {
        if (sheets == null || sheets.isEmpty()) {
            throw new BadRequestException("Aucune planche a rendre.");
        }
        if (sheets.stream().anyMatch(sheet -> sheet.cards() == null || sheet.cards().isEmpty())) {
            throw new BadRequestException("Aucune carte pour le dataset.");
        }
        if (template.getSlots().isEmpty()) {
            throw new BadRequestException("Le patron n'a aucun emplacement.");
        }

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            DatasetImages images = new DatasetImages(document);
            PDFont font = loadFont(document, template);

            for (DatasetSheetRender sheet : sheets) {
                appendSheet(document, sheet, template, font, images);
            }

            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Erreur de génération PDF du dataset.");
        }
    }

    private void appendSheet(
            PDDocument document,
            DatasetSheetRender sheet,
            CardPrintTemplate template,
            PDFont font,
            DatasetImages images
    ) throws IOException {
        PDRectangle mediaBox = PDRectangle.A4;
        int cardsPerPage = cardsPerPage();

        List<DatasetCardInfo> cards = sheet.cards();
        int totalCards = cards.size();
        int pages = (int) Math.ceil((double) totalCards / cardsPerPage);

        for (int p = 0; p < pages; p++) {
            PDPage page = new PDPage(mediaBox);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                int start = p * cardsPerPage;
                int end = Math.min(start + cardsPerPage, totalCards);
                List<DatasetCardInfo> pageCards = cards.subList(start, end);
                drawPage(document, content, page, pageCards, font, template, sheet.documentId(), p + 1, pages, images);
            }
        }
    }


    private void drawPage(PDDocument document, PDPageContentStream content, PDPage page, List<DatasetCardInfo> cards, PDFont font, CardPrintTemplate template, String documentId, int pageNum, int totalPages, DatasetImages images) throws IOException {
        float margin = MARGIN;
        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();

        int cols = COLS;
        float gapX = GAP_X;
        float gapY = GAP_Y;
        float cardWidth = cardWidth();
        float cardHeight = cardHeight();

        for (int i = 0; i < cards.size(); i++) {
            DatasetCardInfo cardInfo = cards.get(i);
            int col = i % cols;
            int row = i / cols;
            
            float x = margin + col * (cardWidth + gapX);
            float y = pageHeight - margin - (row + 1) * cardHeight - (row * gapY);

            drawDatasetCard(content, cardInfo, x, y, cardWidth, cardHeight, font, template, images);
        }
        
        // Draw page ID
        String text = "ID: " + documentId + " - Page " + pageNum + "/" + totalPages;
        float fontSize = 8f;
        float textWidth = font.getStringWidth(text) / 1000f * fontSize;
        content.beginText();
        content.setFont(font, fontSize);
        content.setNonStrokingColor(Color.DARK_GRAY);
        content.newLineAtOffset((pageWidth - textWidth) / 2f, 10f);
        content.showText(text);
        content.endText();
    }

    private void drawDatasetCard(PDPageContentStream content, DatasetCardInfo cardInfo, float x, float y, float width, float height, PDFont font, CardPrintTemplate template, DatasetImages images) throws IOException {
        byte[] imageBytes = cardInfo.imageBytes();
        
        CardPrintTemplateSlot slot = template.getSlots().get(0);
        float slotHeight = slot.getHeightPt().floatValue();
        float codeYPt = template.getCodeYPt().floatValue();
        float codeHeightPt = template.getCodeHeightPt().floatValue();
        
        float codeCenterYRatio = (codeYPt + codeHeightPt / 2f) / slotHeight;
        float cropHeightRatio = cropHeightRatio(template);
        
        float slotWidth = slot.getWidthPt().floatValue();
        
        ProcessedBand band = images.band(imageBytes, cropHeightRatio, codeCenterYRatio);
        float codeCenterYInCropRatio = band.codeCenterYInCropRatio();
        boolean isDark = band.dark();
        PDImageXObject image = images.image(imageBytes, band, cardInfo.publicId());
        
        content.saveGraphicsState();
        content.addRect(x, y, width, height);
        content.clip();
        content.drawImage(image, x, y, width, height);
        content.restoreGraphicsState();

        Color textColor = isDark ? Color.WHITE : Color.BLACK;

        // La bande est dessinee sans deformation : une seule echelle gouverne tout ce qui
        // vient du patron. La hauteur passait auparavant par codeHeightPt * 3.5, l'ancienne
        // hauteur de bande, ce qui faisait varier la taille du texte avec une calibration
        // qui n'a rien a y voir.
        float scale = width / slotWidth;
        float scaledCodeWidth = template.getCodeWidthPt().floatValue() * scale;
        float scaledCodeHeight = template.getCodeHeightPt().floatValue() * scale;
        float scaledCodeX = template.getCodeXPt().floatValue() * scale;
        
        float codeRenderX = x + scaledCodeX;
        float codeRenderY = y + height * (1f - codeCenterYInCropRatio) - scaledCodeHeight / 2f;

        if (template.getCodeBackgroundColor() != null && template.getCodeBackgroundOpacity() > 0D) {
            // ML dataset: we just draw the background solidly for simplicity if needed, but usually opacity helps text readability.
            // We can draw it if it's there.
            content.saveGraphicsState();
            content.setNonStrokingColor(parseColor(template.getCodeBackgroundColor()));
            content.addRect(codeRenderX, codeRenderY, scaledCodeWidth, scaledCodeHeight);
            content.fill();
            content.restoreGraphicsState();
        }

        float fontSize = template.getCodeFontSizePt().floatValue() * scale;
        String code = cardInfo.publicId();
        float textWidth = font.getStringWidth(code) / 1000f * fontSize;
        
        float textX = codeRenderX;
        if (template.getCodeAlign() != null) {
            switch (template.getCodeAlign()) {
                case CENTER -> textX = codeRenderX + (scaledCodeWidth - textWidth) / 2f;
                case RIGHT -> textX = codeRenderX + scaledCodeWidth - textWidth;
                default -> textX = codeRenderX;
            }
        }

        content.beginText();
        content.setFont(font, fontSize);
        content.setNonStrokingColor(textColor);
        content.newLineAtOffset(textX, codeRenderY + (scaledCodeHeight - fontSize) / 2f + fontSize * 0.22f);
        content.showText(code);
        content.endText();

    }
    
    private Color parseColor(String color) {
        if (color == null || !color.matches("^#[0-9A-Fa-f]{6}$")) {
            return Color.BLACK;
        }
        return new Color(
            Integer.parseInt(color.substring(1, 3), 16),
            Integer.parseInt(color.substring(3, 5), 16),
            Integer.parseInt(color.substring(5, 7), 16)
        );
    }

    private PDFont loadFont(PDDocument document, CardPrintTemplate template) throws IOException {
        fr.insastronaute.webapi.card.print.domain.CardPrintFontFamily family = template.getCodeFontFamily();
        fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight weight = template.getCodeFontWeight();
        
        fr.insastronaute.webapi.card.print.domain.CardPrintFontFamily resolvedFamily = family == null ? fr.insastronaute.webapi.card.print.domain.CardPrintFontFamily.HELVETICA : family;
        fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight resolvedWeight = weight == null ? fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight.BOLD : weight;
        
        boolean bold = resolvedWeight == fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight.BOLD;
        List<String> fontPaths = switch (resolvedFamily) {
            case TIMES -> List.of(bold
                    ? "fonts/DejaVuSerif-Bold.ttf"
                    : "fonts/DejaVuSerif.ttf");
            case COURIER -> List.of(bold
                    ? "fonts/DejaVuSansMono-Bold.ttf"
                    : "fonts/DejaVuSansMono.ttf");
            case HELVETICA -> List.of(bold
                    ? "fonts/DejaVuSans-Bold.ttf"
                    : "fonts/DejaVuSans.ttf");
        };
        
        for (String path : fontPaths) {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource(path);
            if (resource.exists()) {
                try (java.io.InputStream is = resource.getInputStream()) {
                    return PDType0Font.load(document, is);
                }
            }
        }
        
        Standard14Fonts.FontName fontName = switch (resolvedFamily) {
            case TIMES -> bold ? Standard14Fonts.FontName.TIMES_BOLD : Standard14Fonts.FontName.TIMES_ROMAN;
            case COURIER -> bold ? Standard14Fonts.FontName.COURIER_BOLD : Standard14Fonts.FontName.COURIER;
            case HELVETICA -> bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA;
        };
        return new PDType1Font(fontName);
    }

    /**
     * Les bandes de cartes du document, preparees une fois et partagees.
     *
     * <p>Le code imprime est dessine en texte par-dessus l'image : la bande ne depend donc que
     * de la carte, jamais du code. Deux emplacements montrant la meme carte referencent le meme
     * objet PDF. Sur une planche isolee cela ne change rien -- 39 emplacements pour un catalogue
     * plus large, donc pas de repetition -- mais sur un lot rendu d'un seul tenant, c'est la
     * difference entre une image par emplacement et une image par carte distincte.
     *
     * <p>Meme principe que {@code CardPrintPdfRenderer.ImageResolver}, la portee etant le
     * document : un {@link PDImageXObject} appartient au {@link PDDocument} qui l'a cree.
     */
    private static final class DatasetImages {

        private final PDDocument document;
        private final Map<String, ProcessedBand> bands = new HashMap<>();
        private final Map<String, PDImageXObject> images = new HashMap<>();

        private DatasetImages(PDDocument document) {
            this.document = document;
        }

        private ProcessedBand band(byte[] imageBytes, float cropHeightRatio, float codeCenterYRatio) {
            return bands.computeIfAbsent(
                    digest(imageBytes),
                    key -> process(imageBytes, cropHeightRatio, codeCenterYRatio)
            );
        }

        private PDImageXObject image(byte[] imageBytes, ProcessedBand band, String name) throws IOException {
            String key = digest(imageBytes);
            PDImageXObject existing = images.get(key);
            if (existing != null) {
                return existing;
            }
            PDImageXObject created = PDImageXObject.createFromByteArray(document, band.jpegBytes(), name);
            images.put(key, created);
            return created;
        }

        private static ProcessedBand process(byte[] imageBytes, float cropHeightRatio, float codeCenterYRatio) {
            float codeCenterYInCropRatio = 0.5f;
            boolean isDark = false;
            byte[] compressedBytes = imageBytes;
            try {
                BufferedImage bImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (bImage != null) {
                    // La bande est toujours le bas de la carte. La caler sur codeYPt demandait
                    // de convertir une ordonnee PDF, qui monte depuis le bas du slot, en ratio
                    // d'image, qui descend depuis le haut -- une inversion que ce code ne
                    // faisait pas, et que le garde-fou de debordement masquait en recollant la
                    // bande en bas. On prend le bas directement, sans conversion a se tromper.
                    int cropH = Math.max(1, Math.min(bImage.getHeight(), (int) (bImage.getHeight() * cropHeightRatio)));
                    int cropY = bImage.getHeight() - cropH;
                    codeCenterYInCropRatio = Math.max(0f, Math.min(1f,
                            ((bImage.getHeight() * codeCenterYRatio) - cropY) / (float) cropH));
                
                    BufferedImage croppedImage = bImage.getSubimage(0, cropY, bImage.getWidth(), cropH);
                
                    long rSum = 0, gSum = 0, bSum = 0;
                    int count = 0;
                    for (int cx = 0; cx < croppedImage.getWidth(); cx += 5) {
                        for (int cy = 0; cy < croppedImage.getHeight(); cy += 5) {
                            int rgb = croppedImage.getRGB(cx, cy);
                            rSum += (rgb >> 16) & 0xFF;
                            gSum += (rgb >> 8) & 0xFF;
                            bSum += rgb & 0xFF;
                            count++;
                        }
                    }
                    if (count > 0) {
                        double avgR = (double) rSum / count;
                        double avgG = (double) gSum / count;
                        double avgB = (double) bSum / count;
                        double luminance = 0.2126 * avgR + 0.7152 * avgG + 0.0722 * avgB;
                        isDark = luminance < 128;
                    }

                    BufferedImage rgbImage = new BufferedImage(croppedImage.getWidth(), croppedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = rgbImage.createGraphics();
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
                    g.drawImage(croppedImage, 0, 0, null);
                    g.dispose();
                
                    ByteArrayOutputStream jpgOut = new ByteArrayOutputStream();
                    ImageIO.write(rgbImage, "jpg", jpgOut);
                    compressedBytes = jpgOut.toByteArray();
                }
            } catch (Exception e) {
                // ignore
            }
            return new ProcessedBand(compressedBytes, isDark, codeCenterYInCropRatio);
        }

        private static String digest(byte[] bytes) {
            try {
                return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is not available.", exception);
            }
        }
    }

    private record ProcessedBand(byte[] jpegBytes, boolean dark, float codeCenterYInCropRatio) {
    }
}
