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

/**
 * S3 연결 설정을 위한 Configuration 클래스
 * AWS 인증 정보를 로드하여 S3Client 빈을 생성합니다.
 */
@Configuration
public class S3Config {

    /**
     * AWS 인증 정보가 담긴 파일 리소스
     */
    @Value("${aws.credentials.path}")
    private Resource credentialsResource;

    /**
     * S3Client 빈 생성
     * 인증 정보 파일에서 액세스 키, 시크릿 키, 리전 정보를 읽어와 S3Client를 구성합니다.
     *
     * @return 구성된 S3Client 객체
     * @throws IOException 인증 정보 파일 읽기 실패 시 발생
     */
    @Bean
    public S3Client s3Client() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> credentials = mapper.readValue(credentialsResource.getInputStream(), Map.class);

        String accessKeyId = credentials.get("access_key_id");
        String secretAccessKey = credentials.get("secret_access_key");
        String regionName = credentials.get("region");

        if (accessKeyId == null || secretAccessKey == null || regionName == null) {
            throw new IllegalArgumentException("인증 정보 파일에 AWS 액세스 키, 시크릿 키 또는 리전 정보가 없습니다");
        }

        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .region(Region.of(regionName))
                .build();
    }

    /**
     * S3 버킷 이름 빈 생성
     * 인증 정보 파일에서 버킷 이름을 읽어옵니다.
     *
     * @return S3 버킷 이름
     * @throws IOException 인증 정보 파일 읽기 실패 시 발생
     */
    @Bean
    public String bucketName() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> credentials = mapper.readValue(credentialsResource.getInputStream(), Map.class);
        return credentials.get("bucket_name");
    }

    /**
     * 사용자 이름 빈 생성
     * 인증 정보 파일에서 사용자 이름을 읽어옵니다.
     *
     * @return 사용자 이름
     * @throws IOException 인증 정보 파일 읽기 실패 시 발생
     */
    @Bean
    public String username() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> credentials = mapper.readValue(credentialsResource.getInputStream(), Map.class);
        return credentials.get("username");
    }
}