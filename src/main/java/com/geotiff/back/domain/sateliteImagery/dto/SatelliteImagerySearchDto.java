package com.geotiff.back.domain.sateliteImagery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 위성 영상 메타데이터 검색을 위한 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatelliteImagerySearchDto {

    private String originalName;
    private String originalNameContains;
    private String cogName;
    private Integer width;
    private Integer height;
    private Integer bandCount;
    private String projection;

    // DTO를 Map으로 변환하는 정적 메서드 (필요하다면 사용)
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();

        if (originalName != null) {
            map.put("originalName", originalName);
        }

        if (originalNameContains != null) {
            map.put("originalNameContains", originalNameContains);
        }

        if (cogName != null) {
            map.put("cogName", cogName);
        }

        if (width != null) {
            map.put("width", width);
        }

        if (height != null) {
            map.put("height", height);
        }

        if (bandCount != null) {
            map.put("bandCount", bandCount);
        }

        if (projection != null) {
            map.put("projection", projection);
        }

        return map;
    }
}