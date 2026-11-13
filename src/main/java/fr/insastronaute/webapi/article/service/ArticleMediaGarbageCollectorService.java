package fr.insastronaute.webapi.article.service;

import fr.insastronaute.webapi.article.repository.ArticleMediaRepository;
import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ArticleMediaGarbageCollectorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleMediaGarbageCollectorService.class);
    private static final String ARTICLE_OBJECT_PREFIX = "articles/";
    private static final int DELETE_BATCH_SIZE = 1000;

    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final ArticleMediaRepository articleMediaRepository;
    private final ArticleRepository articleRepository;

    public ArticleMediaGarbageCollectorService(
            S3Client s3Client,
            S3Properties s3Properties,
            ArticleMediaRepository articleMediaRepository,
            ArticleRepository articleRepository
    ) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.articleMediaRepository = articleMediaRepository;
        this.articleRepository = articleRepository;
    }

    public void deleteOrphanedArticleMedia() {
        // Deux sources de verite, parce que le modele en a deux : les medias du
        // corps sont des lignes article_media, la couverture est une colonne de
        // l'article. N'en lire qu'une revenait a supprimer toutes les
        // couvertures televersees a chaque passe.
        Set<String> registeredObjectKeys = new HashSet<>(articleMediaRepository.findAllObjectKeys());
        registeredObjectKeys.addAll(articleRepository.findAllCoverImageObjectKeys());
        List<ObjectIdentifier> orphanedObjects = new ArrayList<>();
        int scannedObjects = 0;
        int deletedObjects = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .prefix(ARTICLE_OBJECT_PREFIX)
                    .continuationToken(continuationToken)
                    .build());

            for (S3Object object : response.contents()) {
                scannedObjects++;
                if (!registeredObjectKeys.contains(object.key())) {
                    orphanedObjects.add(ObjectIdentifier.builder().key(object.key()).build());
                    if (orphanedObjects.size() == DELETE_BATCH_SIZE) {
                        deletedObjects += deleteObjects(orphanedObjects);
                        orphanedObjects.clear();
                    }
                }
            }

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        deletedObjects += deleteObjects(orphanedObjects);
        LOGGER.info(
                "Article media S3 cleanup finished: scanned {} objects under '{}', deleted {} orphaned objects.",
                scannedObjects,
                ARTICLE_OBJECT_PREFIX,
                deletedObjects
        );
    }

    private int deleteObjects(List<ObjectIdentifier> objects) {
        if (objects.isEmpty()) {
            return 0;
        }

        s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(s3Properties.getPublicBucket())
                .delete(Delete.builder().objects(List.copyOf(objects)).build())
                .build());
        return objects.size();
    }
}
