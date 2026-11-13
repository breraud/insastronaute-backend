package fr.insastronaute.webapi.storage;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectAclRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Configuration
public class S3BucketInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3BucketInitializer.class);

    @Bean
    ApplicationRunner initializeProofBucket(
            S3Client s3Client,
            S3Properties properties,
            AppSecurityProperties securityProperties
    ) {
        return ignored -> {
            if (Boolean.TRUE.equals(properties.getBucketInitializationEnabled())
                    && !isBlank(properties.getEndpoint())
                    && !isBlank(properties.getBucket())) {
                try {
                    s3Client.headBucket(HeadBucketRequest.builder()
                            .bucket(properties.getBucket())
                            .build());
                    LOGGER.info("S3 proof bucket '{}' is available.", properties.getBucket());
                } catch (NoSuchBucketException exception) {
                    createBucket(s3Client, properties);
                } catch (S3Exception exception) {
                    if (exception.statusCode() == 404) {
                        createBucket(s3Client, properties);
                    } else {
                        throw exception;
                    }
                }

                configureCors(s3Client, properties, securityProperties.getAllowedOrigins());
            }

            initializePublicBucket(s3Client, properties);
        };
    }

    private static void initializePublicBucket(S3Client s3Client, S3Properties properties) {
        if (!Boolean.TRUE.equals(properties.getPublicBucketInitializationEnabled())
                || isBlank(properties.getEndpoint())
                || isBlank(properties.getPublicBucket())) {
            return;
        }

        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(properties.getPublicBucket())
                    .build());
            LOGGER.info("S3 public bucket '{}' is available.", properties.getPublicBucket());
        } catch (NoSuchBucketException exception) {
            createPublicBucket(s3Client, properties);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                createPublicBucket(s3Client, properties);
            } else {
                throw exception;
            }
        }

        configurePublicReadPolicy(s3Client, properties);
        if (Boolean.TRUE.equals(properties.getPublicBucketRunAclHardeningEnabled())) {
            makePrintRunPdfsPrivate(s3Client, properties);
        }
    }

    private static void createBucket(S3Client s3Client, S3Properties properties) {
        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(properties.getBucket())
                .build());
        LOGGER.info("Created S3 proof bucket '{}'.", properties.getBucket());
    }

    private static void createPublicBucket(S3Client s3Client, S3Properties properties) {
        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(properties.getPublicBucket())
                .build());
        LOGGER.info("Created S3 public bucket '{}'.", properties.getPublicBucket());
    }

    private static void configurePublicReadPolicy(S3Client s3Client, S3Properties properties) {
        String bucket = properties.getPublicBucket();
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Sid": "PublicReadApplicationAssets",
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": "s3:GetObject",
                      "Resource": [
                        "arn:aws:s3:::%s/cards/*",
                        "arn:aws:s3:::%s/card-categories/*",
                        "arn:aws:s3:::%s/decks/*",
                        "arn:aws:s3:::%s/game/*",
                        "arn:aws:s3:::%s/GameData/*",
                        "arn:aws:s3:::%s/articles/*",
                        "arn:aws:s3:::%s/events/*",
                        "arn:aws:s3:::%s/shop/*",
                        "arn:aws:s3:::%s/card-print/templates/*"
                      ]
                    }
                  ]
                }
                """.formatted(bucket, bucket, bucket, bucket, bucket, bucket, bucket, bucket, bucket);

        try {
            s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(bucket)
                    .policy(policy)
                    .build());
            LOGGER.info("Configured public read policy for S3 public bucket '{}'.", bucket);
        } catch (S3Exception exception) {
            if (Boolean.TRUE.equals(properties.getFailOnPolicyError())) {
                throw exception;
            }
            LOGGER.warn(
                    "Could not configure public read policy for S3 public bucket '{}'. Configure anonymous GetObject manually.",
                    bucket,
                    exception
            );
        }
    }

    private static void makePrintRunPdfsPrivate(S3Client s3Client, S3Properties properties) {
        String bucket = properties.getPublicBucket();
        String continuationToken = null;
        int updatedCount = 0;

        try {
            do {
                ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix("card-print/runs/")
                        .continuationToken(continuationToken)
                        .build());
                for (software.amazon.awssdk.services.s3.model.S3Object object : response.contents()) {
                    s3Client.putObjectAcl(PutObjectAclRequest.builder()
                            .bucket(bucket)
                            .key(object.key())
                            .acl(ObjectCannedACL.PRIVATE)
                            .build());
                    updatedCount++;
                }
                continuationToken = response.nextContinuationToken();
            } while (continuationToken != null);

            if (updatedCount > 0) {
                LOGGER.info("Marked {} card print run PDF object(s) private in S3 public bucket '{}'.", updatedCount, bucket);
            }
        } catch (S3Exception exception) {
            if (Boolean.TRUE.equals(properties.getFailOnAclError())) {
                throw exception;
            }
            LOGGER.warn(
                    "Could not mark card print run PDFs private in S3 public bucket '{}'. Update card-print/runs/ ACLs manually.",
                    bucket,
                    exception
            );
        }
    }

    private static void configureCors(S3Client s3Client, S3Properties properties, List<String> allowedOrigins) {
        if (!Boolean.TRUE.equals(properties.getBucketCorsEnabled()) || allowedOrigins == null || allowedOrigins.isEmpty()) {
            return;
        }

        CORSRule corsRule = CORSRule.builder()
                .id("insastronaute-proof-uploads")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "HEAD", "POST")
                .allowedHeaders("*")
                .exposeHeaders("ETag")
                .maxAgeSeconds(3600)
                .build();

        try {
            s3Client.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(properties.getBucket())
                    .corsConfiguration(CORSConfiguration.builder()
                            .corsRules(corsRule)
                            .build())
                    .build());
            LOGGER.info("Configured CORS for S3 proof bucket '{}' and origins {}.", properties.getBucket(), allowedOrigins);
        } catch (S3Exception exception) {
            if (Boolean.TRUE.equals(properties.getFailOnCorsError())) {
                throw exception;
            }
            LOGGER.warn(
                    "Could not configure CORS for S3 proof bucket '{}'. Configure it manually for origins {}.",
                    properties.getBucket(),
                    allowedOrigins,
                    exception
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
