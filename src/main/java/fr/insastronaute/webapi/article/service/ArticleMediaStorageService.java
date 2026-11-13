package fr.insastronaute.webapi.article.service;

import fr.insastronaute.webapi.storage.S3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.UUID;

@Service
public class ArticleMediaStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleMediaStorageService.class);
    private static final int DELETE_BATCH_SIZE = 1000;

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public ArticleMediaStorageService(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    public void deleteArticleDirectoryQuietly(UUID articleId) {
        String prefix = "articles/" + articleId + "/";
        try {
            deletePrefix(prefix);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not delete article media prefix '{}' from public S3 bucket '{}'.",
                    prefix,
                    s3Properties.getPublicBucket(),
                    exception
            );
        }
    }

    public void deleteObjectQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not delete article media object '{}' from public S3 bucket '{}'.",
                    objectKey,
                    s3Properties.getPublicBucket(),
                    exception
            );
        }
    }

    private void deletePrefix(String prefix) {
        String continuationToken = null;
        do {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());

            List<ObjectIdentifier> objects = response.contents()
                    .stream()
                    .map(S3Object::key)
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();
            deleteObjects(objects);

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);
    }

    private void deleteObjects(List<ObjectIdentifier> objects) {
        if (objects.isEmpty()) {
            return;
        }

        for (int start = 0; start < objects.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, objects.size());
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(s3Properties.getPublicBucket())
                    .delete(Delete.builder().objects(objects.subList(start, end)).build())
                    .build());
        }
    }
}
