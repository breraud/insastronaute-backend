package fr.insastronaute.webapi.card.print.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import fr.insastronaute.webapi.card.print.domain.CardPrintQrCorner;

import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateBackSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateSlot;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardPrintPdfRendererTest {

    private final CardPrintPdfRenderer renderer = new CardPrintPdfRenderer();

    @Test
    void rendersFrontAndBackPagesFromTemplateWithCardAndCode() throws Exception {
        CardPrintTemplate template = template();
        byte[] renderedPdf = renderer.render(
                template,
                blankTemplatePdf(),
                List.of(new CardPrintPdfRenderer.PrintSheet(List.of(
                        new CardPrintPdfRenderer.PrintableCard(0, "Carte", jpegImage(), "ABCDE-FGHJK-MNPQR")
                )))
        );

        assertThat(renderedPdf).isNotEmpty();
        try (PDDocument document = Loader.loadPDF(renderedPdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
        }
    }

    @Test
    void rendersPageStampsOnFrontAndBackPages() throws Exception {
        CardPrintTemplate template = template();
        byte[] renderedPdf = renderer.render(
                template,
                blankTemplatePdf(),
                List.of(new CardPrintPdfRenderer.PrintSheet(
                        List.of(new CardPrintPdfRenderer.PrintableCard(0, "Carte", jpegImage(), "ABCDE-FGHJK-MNPQR")),
                        "TEST-1",
                        "TEST-2"
                ))
        );

        try (PDDocument document = Loader.loadPDF(renderedPdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("TEST-1", "TEST-2");
        }
    }

    @Test
    void marksPdfAsRenderedWithCurrentCompression() throws Exception {
        byte[] renderedPdf = renderer.render(
                template(),
                blankTemplatePdf(),
                List.of(new CardPrintPdfRenderer.PrintSheet(List.of(
                        new CardPrintPdfRenderer.PrintableCard(0, "Carte", jpegImage(), "ABCDE-FGHJK-MNPQR")
                )))
        );

        assertThat(renderer.isRenderedWithCurrentCompression(renderedPdf)).isTrue();
        assertThat(renderer.isRenderedWithCurrentCompression(blankTemplatePdf())).isFalse();
    }

    @Test
    void rendersPublicIdAsDecodableQrCodeOnMatchingBackSlot() throws Exception {
        CardPrintTemplate template = template();
        CardPrintTemplateBackSlot backSlot = new CardPrintTemplateBackSlot();
        backSlot.setSlotIndex(0);
        backSlot.setXPt(36D);
        backSlot.setYPt(30D);
        backSlot.setWidthPt(178D);
        backSlot.setHeightPt(250D);
        template.addBackSlot(backSlot);
        template.setQrEnabled(true);
        template.setQrCorner(CardPrintQrCorner.TOP_LEFT);
        template.setQrSizePt(72D);
        template.setQrMarginModules(2);
        template.setQrInsetPt(12D);

        byte[] renderedPdf = renderer.render(
                template,
                blankTemplatePdf(),
                List.of(new CardPrintPdfRenderer.PrintSheet(List.of(
                        new CardPrintPdfRenderer.PrintableCard(0, "Carte", jpegImage(), "ABCDE-FGHJK-MNPQR")
                )))
        );

        try (PDDocument document = Loader.loadPDF(renderedPdf)) {
            BufferedImage backPage = new PDFRenderer(document).renderImageWithDPI(1, 200);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(backPage)));
            assertThat(new MultiFormatReader().decode(bitmap).getText()).isEqualTo("ABCDE-FGHJK-MNPQR");
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
        template.addSlot(slot());
        return template;
    }

    private CardPrintTemplateSlot slot() {
        CardPrintTemplateSlot slot = new CardPrintTemplateSlot();
        slot.setSlotIndex(0);
        slot.setXPt(36D);
        slot.setYPt(30D);
        slot.setWidthPt(178D);
        slot.setHeightPt(250D);
        return slot;
    }

    private byte[] blankTemplatePdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] jpegImage() throws Exception {
        BufferedImage image = new BufferedImage(200, 280, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLUE);
        graphics.fillRect(10, 10, image.getWidth() - 20, image.getHeight() - 20);
        graphics.dispose();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", output);
            return output.toByteArray();
        }
    }
}
