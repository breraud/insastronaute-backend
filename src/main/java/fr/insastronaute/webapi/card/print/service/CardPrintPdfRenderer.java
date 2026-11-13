package fr.insastronaute.webapi.card.print.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import fr.insastronaute.webapi.card.print.domain.CardPrintFitMode;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontFamily;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplate;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateBackSlot;
import fr.insastronaute.webapi.card.print.domain.CardPrintTemplateSlot;
import fr.insastronaute.webapi.common.web.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CardPrintPdfRenderer {

    private static final String RENDERER_METADATA_KEY = "INSAstronauteCardPrintRenderer";
    private static final String RENDERER_METADATA_VALUE = "template-form-xobject-v2-back-qr";

    public byte[] render(CardPrintTemplate template, byte[] templatePdfBytes, List<PrintSheet> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            throw new BadRequestException("Au moins une feuille est requise.");
        }

        try (
                PDDocument templateDocument = Loader.loadPDF(templatePdfBytes);
                PDDocument outputDocument = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            if (templateDocument.getNumberOfPages() < 2) {
                throw new BadRequestException("Le PDF patron doit contenir au moins deux pages: recto puis verso.");
            }

            FontResolver fontResolver = new FontResolver(outputDocument);
            ImageResolver imageResolver = new ImageResolver(outputDocument);
            LayerUtility layerUtility = new LayerUtility(outputDocument);
            PDFormXObject frontTemplateForm = layerUtility.importPageAsForm(templateDocument, 0);
            PDFormXObject backTemplateForm = layerUtility.importPageAsForm(templateDocument, 1);
            PDRectangle frontMediaBox = templateDocument.getPage(0).getMediaBox();
            PDRectangle backMediaBox = templateDocument.getPage(1).getMediaBox();
            for (PrintSheet sheet : sheets) {
                PDPage frontPage = addPageFromTemplate(outputDocument, frontMediaBox, frontTemplateForm);
                drawSheet(outputDocument, frontPage, template, sheet, fontResolver, imageResolver);
                drawPageStamp(outputDocument, frontPage, sheet.frontPageStamp(), fontResolver);
                PDPage backPage = addPageFromTemplate(outputDocument, backMediaBox, backTemplateForm);
                drawBackSheet(outputDocument, backPage, template, sheet);
                drawPageStamp(outputDocument, backPage, sheet.backPageStamp(), fontResolver);
            }

            markCompressedRenderer(outputDocument);
            outputDocument.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de générer le PDF d'impression.");
        }
    }

    public boolean isRenderedWithCurrentCompression(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return false;
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDDocumentInformation information = document.getDocumentInformation();
            return RENDERER_METADATA_VALUE.equals(information.getCustomMetadataValue(RENDERER_METADATA_KEY));
        } catch (IOException exception) {
            return false;
        }
    }

    private void markCompressedRenderer(PDDocument document) {
        PDDocumentInformation information = document.getDocumentInformation();
        information.setCustomMetadataValue(RENDERER_METADATA_KEY, RENDERER_METADATA_VALUE);
        document.setDocumentInformation(information);
    }

    private PDPage addPageFromTemplate(PDDocument document, PDRectangle mediaBox, PDFormXObject templateForm) throws IOException {
        PDPage page = new PDPage(mediaBox);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.drawForm(templateForm);
        }
        return page;
    }

    private void drawSheet(
            PDDocument document,
            PDPage page,
            CardPrintTemplate template,
            PrintSheet sheet,
            FontResolver fontResolver,
            ImageResolver imageResolver
    ) throws IOException {
        Map<Integer, CardPrintTemplateSlot> slotsByIndex = template.getSlots().stream()
                .collect(Collectors.toMap(CardPrintTemplateSlot::getSlotIndex, slot -> slot));
        PDRectangle mediaBox = page.getMediaBox();
        float pageHeight = mediaBox.getHeight();

        try (PDPageContentStream content = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
        )) {
            for (PrintableCard printableCard : sheet.cards().stream()
                    .sorted(Comparator.comparing(PrintableCard::slotIndex))
                    .toList()) {
                CardPrintTemplateSlot slot = slotsByIndex.get(printableCard.slotIndex());
                if (slot == null) {
                    throw new BadRequestException("Le patron ne contient pas tous les emplacements requis.");
                }
                drawCard(content, pageHeight, template, slot, printableCard, imageResolver);
                drawCode(content, pageHeight, template, slot, printableCard.publicId(), fontResolver);
            }
        }
    }

    private void drawPageStamp(PDDocument document, PDPage page, String stamp, FontResolver fontResolver) throws IOException {
        if (stamp == null || stamp.isBlank()) {
            return;
        }

        try (PDPageContentStream content = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
        )) {
            content.beginText();
            content.setFont(fontResolver.resolve(CardPrintFontFamily.HELVETICA, CardPrintFontWeight.BOLD), 8F);
            content.setNonStrokingColor(new Color(68, 68, 68));
            content.newLineAtOffset(12F, 12F);
            content.showText(stamp);
            content.endText();
        }
    }

    private void drawBackSheet(
            PDDocument document,
            PDPage page,
            CardPrintTemplate template,
            PrintSheet sheet
    ) throws IOException {
        if (!template.isQrEnabled()) return;
        Map<Integer, CardPrintTemplateBackSlot> slotsByIndex = template.getBackSlots().stream()
                .collect(Collectors.toMap(CardPrintTemplateBackSlot::getSlotIndex, slot -> slot));
        float pageHeight = page.getMediaBox().getHeight();
        Map<String, PDImageXObject> qrImages = new HashMap<>();
        try (PDPageContentStream content = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true
        )) {
            for (PrintableCard card : sheet.cards()) {
                CardPrintTemplateBackSlot slot = slotsByIndex.get(card.slotIndex());
                if (slot == null) {
                    throw new BadRequestException("Le patron ne contient pas tous les emplacements verso requis.");
                }
                PDImageXObject qrImage = qrImages.computeIfAbsent(
                        card.publicId(),
                        publicId -> createQrImage(document, publicId, template.getQrMarginModules())
                );
                float size = template.getQrSizePt().floatValue();
                float inset = template.getQrInsetPt().floatValue();
                boolean right = template.getQrCorner().name().endsWith("RIGHT");
                boolean bottom = template.getQrCorner().name().startsWith("BOTTOM");
                float x = slot.getXPt().floatValue() + (right
                        ? slot.getWidthPt().floatValue() - inset - size
                        : inset);
                float topY = slot.getYPt().floatValue() + (bottom
                        ? slot.getHeightPt().floatValue() - inset - size
                        : inset);
                float y = pageHeight - topY - size;
                content.drawImage(qrImage, x, y, size, size);
            }
        }
    }

    private PDImageXObject createQrImage(PDDocument document, String publicId, int marginModules) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    publicId,
                    BarcodeFormat.QR_CODE,
                    256,
                    256,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, marginModules)
            );
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
                }
            }
            return LosslessFactory.createFromImage(document, image);
        } catch (WriterException | IOException exception) {
            throw new BadRequestException("Impossible de générer le QR code d'impression.");
        }
    }

    private void drawCard(
            PDPageContentStream content,
            float pageHeight,
            CardPrintTemplate template,
            CardPrintTemplateSlot slot,
            PrintableCard printableCard,
            ImageResolver imageResolver
    ) throws IOException {
        PDImageXObject image = imageResolver.resolve(printableCard);
        float slotX = slot.getXPt().floatValue();
        float slotY = pageHeight - slot.getYPt().floatValue() - slot.getHeightPt().floatValue();
        float bleed = template.getBleedPt().floatValue();
        float slotWidth = slot.getWidthPt().floatValue();
        float slotHeight = slot.getHeightPt().floatValue();
        float renderX = slotX - bleed;
        float renderY = slotY - bleed;
        float renderWidth = slotWidth + 2F * bleed;
        float renderHeight = slotHeight + 2F * bleed;

        float imageRatio = (float) image.getWidth() / (float) image.getHeight();
        float slotRatio = renderWidth / renderHeight;
        float drawWidth;
        float drawHeight;
        if (template.getFitMode() == CardPrintFitMode.CONTAIN) {
            if (imageRatio > slotRatio) {
                drawWidth = renderWidth;
                drawHeight = renderWidth / imageRatio;
            } else {
                drawHeight = renderHeight;
                drawWidth = renderHeight * imageRatio;
            }
        } else {
            if (imageRatio > slotRatio) {
                drawHeight = renderHeight;
                drawWidth = renderHeight * imageRatio;
            } else {
                drawWidth = renderWidth;
                drawHeight = renderWidth / imageRatio;
            }
        }

        float drawX = renderX + (renderWidth - drawWidth) / 2F;
        float drawY = renderY + (renderHeight - drawHeight) / 2F;

        content.saveGraphicsState();
        content.addRect(renderX, renderY, renderWidth, renderHeight);
        content.clip();
        content.drawImage(image, drawX, drawY, drawWidth, drawHeight);
        content.restoreGraphicsState();
    }

    private void drawCode(
            PDPageContentStream content,
            float pageHeight,
            CardPrintTemplate template,
            CardPrintTemplateSlot slot,
            String publicId,
            FontResolver fontResolver
    ) throws IOException {
        float x = slot.getXPt().floatValue() + template.getCodeXPt().floatValue();
        float y = pageHeight - slot.getYPt().floatValue() - template.getCodeYPt().floatValue() - template.getCodeHeightPt().floatValue();
        float width = template.getCodeWidthPt().floatValue();
        float height = template.getCodeHeightPt().floatValue();

        if (template.getCodeBackgroundColor() != null && template.getCodeBackgroundOpacity() > 0D) {
            content.saveGraphicsState();
            PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
            graphicsState.setNonStrokingAlphaConstant(template.getCodeBackgroundOpacity().floatValue());
            content.setGraphicsStateParameters(graphicsState);
            content.setNonStrokingColor(parseColor(template.getCodeBackgroundColor()));
            content.addRect(x, y, width, height);
            content.fill();
            content.restoreGraphicsState();
        }

        PDFont font = fontResolver.resolve(template.getCodeFontFamily(), template.getCodeFontWeight());
        float fontSize = template.getCodeFontSizePt().floatValue();
        float textWidth = font.getStringWidth(publicId) / 1000F * fontSize;
        float textX = switch (template.getCodeAlign()) {
            case LEFT -> x;
            case CENTER -> x + (width - textWidth) / 2F;
            case RIGHT -> x + width - textWidth;
        };
        float textY = y + (height - fontSize) / 2F + fontSize * 0.22F;

        content.beginText();
        content.setFont(font, fontSize);
        content.setNonStrokingColor(parseColor(template.getCodeColor()));
        content.newLineAtOffset(textX, textY);
        content.showText(publicId);
        content.endText();
    }

    private PDFont fallbackStandardFont(CardPrintFontFamily family, CardPrintFontWeight weight) {
        boolean bold = weight == CardPrintFontWeight.BOLD;
        Standard14Fonts.FontName fontName = switch (family) {
            case TIMES -> bold ? Standard14Fonts.FontName.TIMES_BOLD : Standard14Fonts.FontName.TIMES_ROMAN;
            case COURIER -> bold ? Standard14Fonts.FontName.COURIER_BOLD : Standard14Fonts.FontName.COURIER;
            case HELVETICA -> bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA;
        };
        return new PDType1Font(fontName);
    }

    private List<String> fontPaths(CardPrintFontFamily family, CardPrintFontWeight weight) {
        boolean bold = weight == CardPrintFontWeight.BOLD;
        return switch (family) {
            case TIMES -> List.of(bold
                    ? "/fonts/DejaVuSerif-Bold.ttf"
                    : "/fonts/DejaVuSerif.ttf");
            case COURIER -> List.of(bold
                    ? "/fonts/DejaVuSansMono-Bold.ttf"
                    : "/fonts/DejaVuSansMono.ttf");
            case HELVETICA -> List.of(bold
                    ? "/fonts/DejaVuSans-Bold.ttf"
                    : "/fonts/DejaVuSans.ttf");
        };
    }

    private static final class ImageResolver {
        private final PDDocument document;
        private final Map<ImageKey, PDImageXObject> images = new HashMap<>();

        private ImageResolver(PDDocument document) {
            this.document = document;
        }

        private PDImageXObject resolve(PrintableCard printableCard) throws IOException {
            if (printableCard.imageBytes() == null || printableCard.imageBytes().length == 0) {
                throw new BadRequestException("L'image de la carte est requise.");
            }
            ImageKey key = new ImageKey(printableCard.cardName(), digest(printableCard.imageBytes()));
            PDImageXObject existing = images.get(key);
            if (existing != null) {
                return existing;
            }

            PDImageXObject image = PDImageXObject.createFromByteArray(
                    document,
                    printableCard.imageBytes(),
                    printableCard.cardName()
            );
            images.put(key, image);
            return image;
        }

        private String digest(byte[] bytes) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return Base64.getEncoder().encodeToString(digest.digest(bytes));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is not available.", exception);
            }
        }
    }

    private record ImageKey(String cardName, String contentHash) {
    }

    private final class FontResolver {
        private final PDDocument document;
        private final Map<FontKey, PDFont> fonts = new HashMap<>();

        private FontResolver(PDDocument document) {
            this.document = document;
        }

        private PDFont resolve(CardPrintFontFamily family, CardPrintFontWeight weight) throws IOException {
            CardPrintFontFamily resolvedFamily = family == null ? CardPrintFontFamily.HELVETICA : family;
            CardPrintFontWeight resolvedWeight = weight == null ? CardPrintFontWeight.BOLD : weight;
            FontKey key = new FontKey(resolvedFamily, resolvedWeight);
            PDFont existing = fonts.get(key);
            if (existing != null) {
                return existing;
            }

            for (String path : fontPaths(resolvedFamily, resolvedWeight)) {
                org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource(path.startsWith("/") ? path.substring(1) : path);
                if (resource.exists()) {
                    try (java.io.InputStream is = resource.getInputStream()) {
                        PDFont font = PDType0Font.load(document, is);
                        fonts.put(key, font);
                        return font;
                    }
                }
            }

            PDFont font = fallbackStandardFont(resolvedFamily, resolvedWeight);
            fonts.put(key, font);
            return font;
        }
    }

    private record FontKey(CardPrintFontFamily family, CardPrintFontWeight weight) {
    }

    private Color parseColor(String color) {
        if (color == null || !color.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new BadRequestException("La couleur du code est invalide.");
        }
        int red = Integer.parseInt(color.substring(1, 3), 16);
        int green = Integer.parseInt(color.substring(3, 5), 16);
        int blue = Integer.parseInt(color.substring(5, 7), 16);
        return new Color(red, green, blue);
    }

    public record PrintSheet(List<PrintableCard> cards, String frontPageStamp, String backPageStamp) {
        public PrintSheet(List<PrintableCard> cards) {
            this(cards, null, null);
        }
    }

    public record PrintableCard(Integer slotIndex, String cardName, byte[] imageBytes, String publicId) {
    }
}
