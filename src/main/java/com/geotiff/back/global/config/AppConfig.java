package com.geotiff.back.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class AppConfig {

    @Value("${app.cog.temp-dir:/tmp/cog-converter}")
    private String tempDir;

    @Bean
    public Path tempDirectory() {
        Path path = Paths.get(tempDir);
        File directory = path.toFile();

        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new RuntimeException("Failed to create temporary directory: " + tempDir);
            }
        }

        return path;
    }
}