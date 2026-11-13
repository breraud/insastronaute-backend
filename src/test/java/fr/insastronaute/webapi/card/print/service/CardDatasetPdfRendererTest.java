package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateSlot;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CardDatasetPdfRendererTest {

    private final CardDatasetPdfRenderer renderer = new CardDatasetPdfRenderer();

    @Test
    void aCardDrawnManyTimesAcrossManySheetsIsEmbeddedOnce() throws IOException {
        // Sans partage, chaque emplacement embarque sa propre copie de l'image : douze copies
        // ici, alors que trois cartes distinctes sont tirees. C'est ce que coute la
        // concatenation de PDF deja rendus, et c'est precisement ce qu'un rendu d'un seul
        // tenant evite.
        byte[] first = image(Color.RED);
        byte[] second = image(Color.GREEN);
        byte[] third = image(Color.BLUE);

        List<CardDatasetPdfRenderer.DatasetSheetRender> sheets = List.of(
                sheet("AAAA-1111-BBBB-2", first, second, third, first, second, third),
                sheet("CCCC-3333-DDDD-4", first, second, third, first, second, third)
        );

        byte[] pdf = renderer.renderSheets(sheets, template());

        assertThat(distinctImageCount(pdf)).isEqualTo(3);
    }

    @Test
    void twoDistinctCardsStayTwoDistinctImages() throws IOException {
        // Le partage se fait sur le contenu de l'image, pas sur le code imprime a cote :
        // il ne doit pas confondre deux cartes differentes.
        byte[] pdf = renderer.renderSheets(
                List.of(sheet("AAAA-1111-BBBB-2", image(Color.RED), image(Color.GREEN))),
                template()
        );

        assertThat(distinctImageCount(pdf)).isEqualTo(2);
    }

    @Test
    void aSheetAlwaysFitsOnExactlyOnePageWhateverTheCalibration() throws IOException {
        // La mise en page ne doit plus dependre de codeHeightPt. Avec l'ancienne formule
        // -- hauteur de bande = codeHeightPt x 3.5 -- une calibration a 20 ne laissait que
        // 27 cartes par page et faisait deborder la planche sur une seconde feuille.
        byte[] card = image(Color.RED);

        for (double codeHeightPt : new double[] {8D, 14D, 20D, 32D}) {
            CardPrintTemplate template = template();
            template.setCodeHeightPt(codeHeightPt);

            byte[] pdf = renderer.renderSheets(List.of(fullSheet("PL-48213-3", card)), template);

            try (PDDocument document = Loader.loadPDF(pdf)) {
                assertThat(document.getNumberOfPages())
                        .as("codeHeightPt = %s", codeHeightPt)
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void theCodeIsDrawnAtTheSizeThePatternAsksFor() throws IOException {
        // La bande est dessinee sans deformation, donc une seule echelle gouverne tout :
        // largeur dessinee / largeur du slot. La taille du texte ne doit donc dependre ni de
        // codeHeightPt ni de la hauteur de bande -- elle en dependait encore apres le passage
        // a la grille fixe, et le code ressortait a une taille sans rapport avec le patron.
        List<Float> sizes = new ArrayList<>();
        for (double codeHeightPt : new double[] {8D, 20D, 32D}) {
            CardPrintTemplate template = template();
            template.setCodeFontSizePt(7D);
            template.setCodeHeightPt(codeHeightPt);

            sizes.add(drawnFontSize(renderer.renderSheets(
                    List.of(sheet("PL-48213-3", image(Color.RED))), template)));
        }

        // Meme taille quelle que soit la calibration verticale...
        assertThat(sizes).allSatisfy(size -> assertThat(size).isCloseTo(sizes.get(0), within(0.01f)));
        // ...et c'est bien celle du patron, a l'echelle de dessin pres (178,43 / 178).
        assertThat(sizes.get(0)).isCloseTo(7f * (595.28f - 60f) / 3f / 178f, within(0.05f));
    }

    private float drawnFontSize(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<Float> sizes = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    for (TextPosition position : positions) {
                        if (text.contains("-")) {
                            sizes.add(position.getFontSizeInPt());
                        }
                    }
                }
            };
            stripper.getText(document);
            assertThat(sizes).isNotEmpty();
            return sizes.get(0);
        }
    }

    @Test
    void theGridHoldsThirtyNineCards() {
        assertThat(CardDatasetPdfRenderer.cardsPerPage()).isEqualTo(39);
    }

    private CardDatasetPdfRenderer.DatasetSheetRender fullSheet(String sheetId, byte[] imageBytes) {
        List<CardDatasetPdfRenderer.DatasetCardInfo> cards = new ArrayList<>();
        for (int index = 0; index < CardDatasetPdfRenderer.cardsPerPage(); index++) {
            cards.add(card(sheetId + "-" + index, imageBytes));
        }
        return new CardDatasetPdfRenderer.DatasetSheetRender(sheetId, cards);
    }

    @Test
    void theSingleSheetEntryPointStillRendersItsPage() throws IOException {
        byte[] pdf = renderer.render(
                List.of(card("AAAA-1111-BBBB-2", image(Color.RED))),
                template(),
                "AAAA-1111-BBBB-2"
        );

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    private int distinctImageCount(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            Set<COSBase> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                for (COSName name : resources.getXObjectNames()) {
                    PDXObject xobject = resources.getXObject(name);
                    if (xobject instanceof PDImageXObject) {
                        distinct.add(xobject.getCOSObject());
                    }
                }
            }
            return distinct.size();
        }
    }

    private CardDatasetPdfRenderer.DatasetSheetRender sheet(String sheetId, byte[]... images) {
        List<CardDatasetPdfRenderer.DatasetCardInfo> cards = new ArrayList<>();
        for (int index = 0; index < images.length; index++) {
            cards.add(card(sheetId + "-" + index, images[index]));
        }
        return new CardDatasetPdfRenderer.DatasetSheetRender(sheetId, cards);
    }

    private CardDatasetPdfRenderer.DatasetCardInfo card(String code, byte[] imageBytes) {
        return new CardDatasetPdfRenderer.DatasetCardInfo(code, imageBytes);
    }

    private byte[] image(Color color) {
        BufferedImage image = new BufferedImage(120, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 120, 180);
        graphics.dispose();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private CardPrintTemplate template() {
        CardPrintTemplate template = new CardPrintTemplate();
        template.setPageWidthPt((double) PDRectangle.A4.getWidth());
        template.setPageHeightPt((double) PDRectangle.A4.getHeight());
        template.setCodeXPt(10D);
        template.setCodeYPt(220D);
        template.setCodeWidthPt(150D);
        template.setCodeHeightPt(16D);
        template.setCodeFontSizePt(9D);
        template.setCodeColor("#111111");
        template.setCodeBackgroundColor("#FFFFFF");
        template.setCodeBackgroundOpacity(0.75D);

        CardPrintTemplateSlot slot = new CardPrintTemplateSlot();
        slot.setSlotIndex(0);
        slot.setXPt(36D);
        slot.setYPt(36D);
        slot.setWidthPt(178D);
        slot.setHeightPt(250D);
        template.addSlot(slot);
        return template;
    }
}
