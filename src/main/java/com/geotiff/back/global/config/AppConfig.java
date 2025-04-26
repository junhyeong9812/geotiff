package com.geotiff.back.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 애플리케이션 기본 설정을 위한 Configuration 클래스
 */
@Configuration
public class AppConfig {

    /**
     * 임시 디렉토리 경로
     */
    @Value("${app.cog.temp-dir:/tmp/cogConverter}")
    private String tempDir;

    /**
     * 임시 디렉토리 경로 빈 생성
     * 경로가 존재하지 않으면 생성합니다.
     *
     * @return 임시 디렉토리 경로
     */
    @Bean
    public Path tempDirectory() {
        Path path = Paths.get(tempDir);
        File directory = path.toFile();

        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new RuntimeException("임시 디렉토리 생성 실패: " + tempDir);
            }
        }

        return path;
    }

    /**
     * RestTemplate 빈 생성
     * HTTP 요청을 위한 RestTemplate 객체를 생성합니다.
     *
     * @return RestTemplate 객체
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}