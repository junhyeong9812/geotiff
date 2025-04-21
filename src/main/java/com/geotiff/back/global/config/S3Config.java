package com.geotiff.back.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.util.Map;

@Configuration
public class S3Config {

    @Value("${aws.credentials.path}")
    private Resource credentialsResource;

    @Bean
    public S3Client s3Client() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> credentials = mapper.readValue(credentialsResource.getInputStream(), Map.class);

        String accessKeyId = credentials.get("access_key_id");
        String secretAccessKey = credentials.get("secret_access_key");
        String regionName = credentials.get("region");

        if (accessKeyId == null || secretAccessKey == null || regionName == null) {
            throw new IllegalArgumentException("AWS credentials or region is missing in the credentials file");
        }

        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .region(Region.of(regionName))
                .build();
    }

    @Bean
    public String bucketName() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> credentials = mapper.readValue(credentialsResource.getInputStream(), Map.class);
        return credentials.get("bucket_name");
    }

    @Bean
    public String username() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> credentials = mapper.readValue(credentialsResource.getInputStream(), Map.class);
        return credentials.get("username");
    }
}