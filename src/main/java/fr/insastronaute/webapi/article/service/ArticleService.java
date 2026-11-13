package fr.insastronaute.webapi.article.service;

import fr.insastronaute.webapi.article.domain.Article;
import fr.insastronaute.webapi.article.domain.ArticleMedia;
import fr.insastronaute.webapi.article.domain.ArticleStatus;
import fr.insastronaute.webapi.article.domain.Category;
import fr.insastronaute.webapi.article.dto.ArticleCreateResponse;
import fr.insastronaute.webapi.article.dto.ArticleDto;
import fr.insastronaute.webapi.article.dto.ArticleMediaDto;
import fr.insastronaute.webapi.article.dto.ArticleMediaUploadResponse;
import fr.insastronaute.webapi.article.dto.ArticleSummaryDto;
import fr.insastronaute.webapi.article.dto.ArticleUpdateRequest;
import fr.insastronaute.webapi.article.dto.CategoryDto;
import fr.insastronaute.webapi.article.repository.ArticleMediaRepository;
import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.article.repository.CategoryRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.storage.S3Properties;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ArticleService {

    private static final String ARTICLE_OBJECT_PREFIX = "articles/";
    private static final String COVER_OBJECT_DIRECTORY = "cover/";
    private static final String DEFAULT_FILE_NAME = "media";
    private static final String DEFAULT_COVER_FILE_NAME = "cover";
    private static final long MAX_MEDIA_SIZE_BYTES = 15L * 1024L * 1024L;
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS_BY_TYPE = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp"),
            "video/mp4", Set.of("mp4"),
            "video/webm", Set.of("webm")
    );
    private static final Map<String, String> DEFAULT_EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "video/mp4", "mp4",
            "video/webm", "webm"
    );
    private static final Set<String> ALLOWED_COVER_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Tika TIKA = new Tika();

    private final ArticleRepository articleRepository;
    private final ArticleMediaRepository articleMediaRepository;
    private final CategoryRepository categoryRepository;
    private final HtmlSanitizerService htmlSanitizerService;
    private final ArticleMediaStorageService articleMediaStorageService;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public ArticleService(
            ArticleRepository articleRepository,
            ArticleMediaRepository articleMediaRepository,
            CategoryRepository categoryRepository,
            HtmlSanitizerService htmlSanitizerService,
            ArticleMediaStorageService articleMediaStorageService,
            S3Client s3Client,
            S3Properties s3Properties
    ) {
        this.articleRepository = articleRepository;
        this.articleMediaRepository = articleMediaRepository;
        this.categoryRepository = categoryRepository;
        this.htmlSanitizerService = htmlSanitizerService;
        this.articleMediaStorageService = articleMediaStorageService;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    @Transactional
    public ArticleCreateResponse initializeArticle() {
        Article article = new Article();
        article.setStatus(ArticleStatus.WRITING);
        article.setTitle("");

        return new ArticleCreateResponse(articleRepository.save(article).getId());
    }

    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> listArticles() {
        return articleRepository.findAllByOrderByUpdatedAtDescCreatedAtDesc()
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArticleDto getArticle(UUID articleId) {
        return toDto(findArticle(articleId));
    }

    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> listPublishedArticles() {
        return articleRepository.findByStatusOrderByUpdatedAtDescCreatedAtDesc(ArticleStatus.PUBLISHED)
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArticleDto getPublishedArticle(UUID articleId) {
        return articleRepository.findByIdAndStatus(articleId, ArticleStatus.PUBLISHED)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Article introuvable."));
    }

    @Transactional
    public ArticleMediaUploadResponse uploadMedia(UUID articleId, MultipartFile file) {
        Article article = findArticle(articleId);
        validateFile(file);

        String fileType = detectedFileType(file);
        String originalFileName = safeFileName(file.getOriginalFilename(), fileType);
        validateFileExtension(originalFileName, fileType);
        String objectKey = ARTICLE_OBJECT_PREFIX + article.getId() + "/" + UUID.randomUUID() + "-" + originalFileName;

        putObject(objectKey, fileType, file);

        ArticleMedia media = new ArticleMedia();
        media.setArticle(article);
        media.setObjectKey(objectKey);
        media.setOriginalFileName(originalFileName);
        media.setFileType(fileType);
        media.setS3Url(publicUrl(objectKey));

        ArticleMedia savedMedia = articleMediaRepository.save(media);
        return new ArticleMediaUploadResponse(savedMedia.getId(), savedMedia.getS3Url());
    }

    @Transactional
    public ArticleDto uploadCover(UUID articleId, MultipartFile file) {
        Article article = findArticle(articleId);
        validateFile(file);

        String fileType = detectedFileType(file);
        if (!ALLOWED_COVER_TYPES.contains(fileType)) {
            throw new BadRequestException("Format d'image de couverture non autorisé.");
        }

        String originalFileName = safeFileName(file.getOriginalFilename(), fileType, DEFAULT_COVER_FILE_NAME);
        validateFileExtension(originalFileName, fileType);
        String oldObjectKey = article.getCoverImageObjectKey();
        String objectKey = ARTICLE_OBJECT_PREFIX + article.getId() + "/" + COVER_OBJECT_DIRECTORY + UUID.randomUUID() + "-" + originalFileName;

        putObject(objectKey, fileType, file);

        article.setCoverImageUrl(publicUrl(objectKey));
        article.setCoverImageObjectKey(objectKey);
        Article savedArticle = articleRepository.save(article);

        if (!objectKey.equals(oldObjectKey)) {
            articleMediaStorageService.deleteObjectQuietly(oldObjectKey);
        }

        return toDto(savedArticle);
    }

    @Transactional(readOnly = true)
    public List<ArticleMediaDto> getArticleMedia(UUID articleId) {
        if (!articleRepository.existsById(articleId)) {
            throw new NotFoundException("Article introuvable.");
        }

        return articleMediaRepository.findByArticleIdOrderByCreatedAtAsc(articleId)
                .stream()
                .map(this::toMediaDto)
                .toList();
    }

    @Transactional
    public ArticleDto updateArticle(UUID articleId, ArticleUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Le contenu de l'article est requis.");
        }

        Article article = findArticle(articleId);
        applyCoverImageUrl(article, request.coverImageUrl());
        article.setTitle(request.title() == null ? "" : request.title().trim());
        article.setContentHtml(htmlSanitizerService.sanitize(request.contentHtml()));
        article.setCategory(findCategory(request.categoryId()));
        article.setStatus(request.status() == null ? article.getStatus() : request.status());

        return toDto(articleRepository.save(article));
    }

    @Transactional
    public void deleteArticle(UUID articleId) {
        Article article = findArticle(articleId);
        articleRepository.delete(article);
        articleMediaStorageService.deleteArticleDirectoryQuietly(articleId);
    }

    private Article findArticle(UUID articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> new NotFoundException("Article introuvable."));
    }

    private Category findCategory(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable."));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Un fichier média est requis.");
        }

        if (file.getSize() > MAX_MEDIA_SIZE_BYTES) {
            throw new BadRequestException("Le fichier média dépasse la taille maximale de 15 Mo.");
        }
    }

    private void applyCoverImageUrl(Article article, String requestedCoverImageUrl) {
        String normalizedCoverImageUrl = blankToNull(requestedCoverImageUrl);
        validateCoverImageUrl(normalizedCoverImageUrl);
        String currentCoverImageUrl = blankToNull(article.getCoverImageUrl());

        if (Objects.equals(currentCoverImageUrl, normalizedCoverImageUrl)) {
            return;
        }

        String oldObjectKey = article.getCoverImageObjectKey();
        article.setCoverImageUrl(normalizedCoverImageUrl);
        article.setCoverImageObjectKey(null);
        articleMediaStorageService.deleteObjectQuietly(oldObjectKey);
    }

    private void validateCoverImageUrl(String coverImageUrl) {
        if (coverImageUrl == null) {
            return;
        }

        try {
            URI uri = URI.create(coverImageUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new BadRequestException("L'URL de couverture doit être une URL HTTP ou HTTPS.");
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("L'URL de couverture est invalide.");
        }
    }

    private void putObject(String objectKey, String fileType, MultipartFile file) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(s3Properties.getPublicBucket())
                            .key(objectKey)
                            .contentType(fileType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .acl(ObjectCannedACL.PUBLIC_READ)
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire le fichier média.");
        } catch (RuntimeException exception) {
            throw new BadRequestException("Impossible d'enregistrer le fichier média.");
        }
    }

    private String detectedFileType(MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            String detectedType = TIKA.detect(inputStream);
            String normalizedType = detectedType == null ? "" : detectedType.toLowerCase(Locale.ROOT);

            if (!ALLOWED_EXTENSIONS_BY_TYPE.containsKey(normalizedType)) {
                throw new BadRequestException("Format de média non autorisé.");
            }

            return normalizedType;
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire le fichier média.");
        }
    }

    private void validateFileExtension(String fileName, String fileType) {
        String extension = extension(fileName);
        if (extension.isBlank() || !ALLOWED_EXTENSIONS_BY_TYPE.get(fileType).contains(extension)) {
            throw new BadRequestException("L'extension du fichier média ne correspond pas à son format.");
        }
    }

    private String safeFileName(String fileName, String fileType) {
        return safeFileName(fileName, fileType, DEFAULT_FILE_NAME);
    }

    private String safeFileName(String fileName, String fileType, String defaultBaseName) {
        String value = fileName == null || fileName.isBlank()
                ? defaultBaseName + "." + DEFAULT_EXTENSION_BY_TYPE.get(fileType)
                : fileName;
        int lastSeparatorIndex = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (lastSeparatorIndex >= 0) {
            value = value.substring(lastSeparatorIndex + 1);
        }

        value = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^[.-]+", "")
                .replaceAll("[.-]+$", "");

        if (value.isBlank() || extension(value).isBlank()) {
            return defaultBaseName + "." + DEFAULT_EXTENSION_BY_TYPE.get(fileType);
        }
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    private String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String publicUrl(String objectKey) {
        String endpoint = s3Properties.getPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = s3Properties.getEndpoint();
        }

        if (endpoint == null || endpoint.isBlank()) {
            throw new BadRequestException("Le point d'accès public S3 est requis pour publier un média d'article.");
        }

        String baseUrl = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(baseUrl + "/" + s3Properties.getPublicBucket() + "/" + objectKey).toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ArticleDto toDto(Article article) {
        return new ArticleDto(
                article.getId(),
                article.getTitle(),
                article.getContentHtml(),
                article.getCoverImageUrl(),
                article.getStatus(),
                toCategoryDto(article.getCategory()),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }

    private ArticleSummaryDto toSummaryDto(Article article) {
        return new ArticleSummaryDto(
                article.getId(),
                article.getTitle(),
                article.getCoverImageUrl(),
                article.getStatus(),
                toCategoryDto(article.getCategory()),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }

    private CategoryDto toCategoryDto(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryDto(category.getId(), category.getName(), category.getSlug());
    }

    private ArticleMediaDto toMediaDto(ArticleMedia media) {
        return new ArticleMediaDto(
                media.getId(),
                media.getArticle().getId(),
                media.getS3Url(),
                media.getFileType(),
                media.getOriginalFileName(),
                media.getCreatedAt()
        );
    }
}
