package fr.insastronaute.webapi.storage;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectAclRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3BucketInitializerTest {

    @Test
    void publicBucketPolicyExcludesPrivatePrintRunPdfsAndSkipsLegacyAclHardeningByDefault() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        S3Properties properties = s3Properties();
        ApplicationRunner runner = new S3BucketInitializer().initializeProofBucket(
                s3Client,
                properties,
                new AppSecurityProperties()
        );

        run(runner);

        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3Client).putBucketPolicy(policyCaptor.capture());
        String policy = policyCaptor.getValue().policy();
        assertThat(policy).contains(
                "arn:aws:s3:::insastronaute-public/cards/*",
                "arn:aws:s3:::insastronaute-public/card-categories/*",
                "arn:aws:s3:::insastronaute-public/decks/*",
                "arn:aws:s3:::insastronaute-public/game/*",
                "arn:aws:s3:::insastronaute-public/GameData/*",
                "arn:aws:s3:::insastronaute-public/articles/*",
                "arn:aws:s3:::insastronaute-public/events/*",
                "arn:aws:s3:::insastronaute-public/shop/*",
                "arn:aws:s3:::insastronaute-public/card-print/templates/*"
        );
        assertThat(policy).doesNotContain("arn:aws:s3:::insastronaute-public/*");
        assertThat(policy).doesNotContain("card-print/runs");

        verify(s3Client, never()).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, never()).putObjectAcl(any(PutObjectAclRequest.class));
    }

    @Test
    void hardensExistingRunObjectsWhenLegacyAclHardeningIsEnabled() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("card-print/runs/run.pdf").build())
                .build());
        S3Properties properties = s3Properties();
        properties.setPublicBucketRunAclHardeningEnabled(true);
        ApplicationRunner runner = new S3BucketInitializer().initializeProofBucket(
                s3Client,
                properties,
                new AppSecurityProperties()
        );

        run(runner);

        ArgumentCaptor<PutObjectAclRequest> aclCaptor = ArgumentCaptor.forClass(PutObjectAclRequest.class);
        verify(s3Client).putObjectAcl(aclCaptor.capture());
        assertThat(aclCaptor.getValue().bucket()).isEqualTo("insastronaute-public");
        assertThat(aclCaptor.getValue().key()).isEqualTo("card-print/runs/run.pdf");
        assertThat(aclCaptor.getValue().acl()).isEqualTo(ObjectCannedACL.PRIVATE);
    }

    @Test
    void failsStartupWhenPublicBucketPolicyConfigurationFailsInFailFastMode() {
        S3Client s3Client = mock(S3Client.class);
        S3Properties properties = s3Properties();
        properties.setFailOnPolicyError(true);
        when(s3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("policy failure").build());
        ApplicationRunner runner = new S3BucketInitializer().initializeProofBucket(
                s3Client,
                properties,
                new AppSecurityProperties()
        );

        assertThatThrownBy(() -> run(runner))
                .isInstanceOf(S3Exception.class)
                .hasMessageContaining("policy failure");
    }

    @Test
    void failsStartupWhenProofBucketCorsConfigurationFailsInFailFastMode() {
        S3Client s3Client = mock(S3Client.class);
        S3Properties properties = s3Properties();
        properties.setBucketInitializationEnabled(true);
        properties.setFailOnCorsError(true);
        when(s3Client.putBucketCors(any(PutBucketCorsRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("cors failure").build());
        ApplicationRunner runner = new S3BucketInitializer().initializeProofBucket(
                s3Client,
                properties,
                new AppSecurityProperties()
        );

        assertThatThrownBy(() -> run(runner))
                .isInstanceOf(S3Exception.class)
                .hasMessageContaining("cors failure");
    }

    @Test
    void failsStartupWhenLegacyAclHardeningFailsInFailFastMode() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("card-print/runs/run.pdf").build())
                .build());
        when(s3Client.putObjectAcl(any(PutObjectAclRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("acl failure").build());
        S3Properties properties = s3Properties();
        properties.setPublicBucketRunAclHardeningEnabled(true);
        properties.setFailOnAclError(true);
        ApplicationRunner runner = new S3BucketInitializer().initializeProofBucket(
                s3Client,
                properties,
                new AppSecurityProperties()
        );

        assertThatThrownBy(() -> run(runner))
                .isInstanceOf(S3Exception.class)
                .hasMessageContaining("acl failure");
    }

    private S3Properties s3Properties() {
        S3Properties properties = new S3Properties();
        properties.setEndpoint("http://localhost:8333");
        properties.setBucketInitializationEnabled(false);
        properties.setPublicBucketInitializationEnabled(true);
        properties.setPublicBucket("insastronaute-public");
        return properties;
    }

    private void run(ApplicationRunner runner) throws Exception {
        runner.run(new DefaultApplicationArguments());
    }
}
