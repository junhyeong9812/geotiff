package com.geotiff.back.global.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final String username;

    /**
     * S3 버킷에서 파일 목록 조회
     */
    public List<String> listFiles(String bucketName, String prefix) {
        log.info("버킷에서 파일 목록 조회 중: 버킷명={}, 접두사={}", bucketName, prefix);

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        List<String> files = response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());

        log.info("버킷에서 {}개의 파일을 찾았습니다", files.size());
        return files;
    }

    /**
     * S3에서 파일 다운로드
     */
    public Path downloadFile(String bucketName, String key, Path targetPath) throws IOException {
        log.info("S3에서 파일 다운로드 중: 버킷명={}, 키={}, 대상 경로={}", bucketName, key, targetPath);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest)) {
            Files.copy(s3Object, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("파일 다운로드 성공: {}", targetPath);
            return targetPath;
        } catch (Exception e) {
            log.error("S3에서 파일 다운로드 중 오류 발생: {}", e.getMessage(), e);
            throw new IOException("S3에서 파일 다운로드 실패: " + e.getMessage(), e);
        }
    }

    /**
     * S3에 파일 업로드
     */
    public String uploadFile(String bucketName, Path filePath, String key) throws IOException {
        log.info("S3에 파일 업로드 중: 버킷명={}, 파일 경로={}, 키={}", bucketName, filePath, key);

        String finalKey = String.format("%s/%s", username, key);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(finalKey)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromFile(filePath.toFile()));
            log.info("파일 업로드 성공: s3://{}/{}", bucketName, finalKey);

            return finalKey;
        } catch (Exception e) {
            log.error("S3에 파일 업로드 중 오류 발생: {}", e.getMessage(), e);
            throw new IOException("S3에 파일 업로드 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 파일 존재 여부 확인
     */
    public boolean isFileExists(String bucketName, String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}