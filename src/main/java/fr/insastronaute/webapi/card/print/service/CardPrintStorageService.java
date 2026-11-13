package fr.insastronaute.webapi.card.print.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@Service
public class CardPrintStorageService {

    private static final long MAX_TEMPLATE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final String TEMPLATE_PREFIX = "card-print/templates/";
    private static final String RUN_PREFIX = "card-print/runs/";

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public CardPrintStorageService(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    public StoredTemplatePdf storeTemplate(UUID templateId, MultipartFile pdf) {
        byte[] bytes = validateTemplate(pdf);
        PageSize pageSize = readPageSize(bytes);
        String objectKey = TEMPLATE_PREFIX + templateId + "/template.pdf";
        putPublicPdf(objectKey, bytes, "public, max-age=31536000, immutable");
        return new StoredTemplatePdf(objectKey, publicUrl(objectKey), pageSize.pageCount(), pageSize.widthPt(), pageSize.heightPt());
    }

    public StoredRunPdf storeRun(UUID runId, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new BadRequestException("Le PDF généré est vide.");
        }
        String objectKey = RUN_PREFIX + runId + ".pdf";
        putPrivatePdf(objectKey, pdfBytes, "private, max-age=0, no-cache");
        return new StoredRunPdf(objectKey);
    }

    public byte[] readPublicObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BadRequestException("La clé de fichier est requise.");
        }
        ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(s3Properties.getPublicBucket())
                .key(objectKey)
                .build());
        return bytes.asByteArray();
    }

    public void deletePublicObjectQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException ignored) {
            // Template metadata deletion should not be blocked by stale public object cleanup.
        }
    }

    private byte[] validateTemplate(MultipartFile pdf) {
        if (pdf == null || pdf.isEmpty()) {
            throw new BadRequestException("Le PDF patron est requis.");
        }
        if (pdf.getSize() > MAX_TEMPLATE_SIZE_BYTES) {
            throw new BadRequestException("Le PDF patron ne doit pas dépasser 20 Mo.");
        }

        String contentType = pdf.getContentType();
        if (contentType != null && !contentType.isBlank() && !"application/pdf".equalsIgnoreCase(contentType)) {
            throw new BadRequestException("Le patron doit être un PDF.");
        }

        try {
            byte[] bytes = pdf.getBytes();
            readPageSize(bytes);
            return bytes;
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire le PDF patron.");
        }
    }

    private PageSize readPageSize(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() < 2) {
                throw new BadRequestException("Le PDF patron doit contenir au moins deux pages: recto puis verso.");
            }
            PDRectangle frontBox = document.getPage(0).getMediaBox();
            PDRectangle backBox = document.getPage(1).getMediaBox();
            if (Float.compare(frontBox.getWidth(), backBox.getWidth()) != 0
                    || Float.compare(frontBox.getHeight(), backBox.getHeight()) != 0) {
                throw new BadRequestException("Les pages recto et verso du patron doivent avoir les mêmes dimensions.");
            }
            return new PageSize(document.getNumberOfPages(), (double) frontBox.getWidth(), (double) frontBox.getHeight());
        } catch (IOException exception) {
            throw new BadRequestException("Le PDF patron est invalide.");
        }
    }

    private void putPublicPdf(String objectKey, byte[] bytes, String cacheControl) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(objectKey)
                        .contentType("application/pdf")
                        .cacheControl(cacheControl)
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    private void putPrivatePdf(String objectKey, byte[] bytes, String cacheControl) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.getPublicBucket())
                        .key(objectKey)
                        .contentType("application/pdf")
                        .cacheControl(cacheControl)
                        .acl(ObjectCannedACL.PRIVATE)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    private String publicUrl(String objectKey) {
        String endpoint = s3Properties.getPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = s3Properties.getEndpoint();
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new BadRequestException("Le point d'accès public S3 est requis pour publier les PDF d'impression.");
        }

        String baseUrl = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(baseUrl + "/" + s3Properties.getPublicBucket() + "/" + objectKey).toString();
    }

    public record StoredTemplatePdf(String objectKey, String url, Integer pageCount, Double pageWidthPt, Double pageHeightPt) {
    }

    public record StoredRunPdf(String objectKey) {
    }

    private record PageSize(Integer pageCount, Double widthPt, Double heightPt) {
    }
}
