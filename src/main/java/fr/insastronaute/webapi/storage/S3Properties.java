package fr.insastronaute.webapi.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "s3")
public class S3Properties {

    private String endpoint;

    private String publicEndpoint;

    private String accessKey;

    private String secretKey;

    private String region = "eu-fr";

    private String bucket = "insastronaute-proofs";

    private String publicBucket = "insastronaute-public";

    private Boolean bucketInitializationEnabled = true;

    private Boolean bucketCorsEnabled = true;

    private Boolean publicBucketInitializationEnabled = true;

    private Boolean publicBucketRunAclHardeningEnabled = false;

    private Boolean failOnPolicyError = false;

    private Boolean failOnCorsError = false;

    private Boolean failOnAclError = false;

    private Integer presignedUrlDurationSeconds = 300;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPublicBucket() {
        return publicBucket;
    }

    public void setPublicBucket(String publicBucket) {
        this.publicBucket = publicBucket;
    }

    public Integer getPresignedUrlDurationSeconds() {
        return presignedUrlDurationSeconds;
    }

    public void setPresignedUrlDurationSeconds(Integer presignedUrlDurationSeconds) {
        this.presignedUrlDurationSeconds = presignedUrlDurationSeconds;
    }

    public Boolean getBucketInitializationEnabled() {
        return bucketInitializationEnabled;
    }

    public void setBucketInitializationEnabled(Boolean bucketInitializationEnabled) {
        this.bucketInitializationEnabled = bucketInitializationEnabled;
    }

    public Boolean getBucketCorsEnabled() {
        return bucketCorsEnabled;
    }

    public void setBucketCorsEnabled(Boolean bucketCorsEnabled) {
        this.bucketCorsEnabled = bucketCorsEnabled;
    }

    public Boolean getPublicBucketInitializationEnabled() {
        return publicBucketInitializationEnabled;
    }

    public void setPublicBucketInitializationEnabled(Boolean publicBucketInitializationEnabled) {
        this.publicBucketInitializationEnabled = publicBucketInitializationEnabled;
    }

    public Boolean getPublicBucketRunAclHardeningEnabled() {
        return publicBucketRunAclHardeningEnabled;
    }

    public void setPublicBucketRunAclHardeningEnabled(Boolean publicBucketRunAclHardeningEnabled) {
        this.publicBucketRunAclHardeningEnabled = publicBucketRunAclHardeningEnabled;
    }

    public Boolean getFailOnPolicyError() {
        return failOnPolicyError;
    }

    public void setFailOnPolicyError(Boolean failOnPolicyError) {
        this.failOnPolicyError = failOnPolicyError;
    }

    public Boolean getFailOnCorsError() {
        return failOnCorsError;
    }

    public void setFailOnCorsError(Boolean failOnCorsError) {
        this.failOnCorsError = failOnCorsError;
    }

    public Boolean getFailOnAclError() {
        return failOnAclError;
    }

    public void setFailOnAclError(Boolean failOnAclError) {
        this.failOnAclError = failOnAclError;
    }
}
