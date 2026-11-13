package fr.insastronaute.webapi.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3ConfigTest {

    @Test
    void s3PresignerUsesPublicEndpointWhenConfigured() {
        S3Properties properties = new S3Properties();
        properties.setEndpoint("http://seaweedfs:8333");
        properties.setPublicEndpoint("https://s3.example.com");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        properties.setRegion("us-east-1");

        try (S3Presigner presigner = new S3Config().s3Presigner(properties)) {
            String url = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(5))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket("proofs")
                            .key("proofs/user/challenge/file.png")
                            .build())
                    .build()).url().toString();

            assertThat(url).startsWith("https://s3.example.com/proofs/proofs/user/challenge/file.png");
            assertThat(url).doesNotContain("seaweedfs");
        }
    }
}
