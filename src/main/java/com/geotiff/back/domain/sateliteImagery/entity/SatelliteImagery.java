package com.geotiff.back.domain.sateliteImagery.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "satellite_imagery")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SatelliteImagery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String cogName;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(nullable = false)
    private Integer bandCount;

    @Lob
    @Column
    private String projection;

    @Column
    private String s3Path;

    @Column
    private Integer sequence;

    @Column
    private Long fileSize;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
