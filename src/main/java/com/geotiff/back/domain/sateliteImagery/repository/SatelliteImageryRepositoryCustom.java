package com.geotiff.back.domain.sateliteImagery.repository;


import com.geotiff.back.domain.sateliteImagery.dto.SatelliteImagerySearchDto;
import com.geotiff.back.domain.sateliteImagery.entity.SatelliteImagery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SatelliteImageryRepositoryCustom {

    Page<SatelliteImagery> searchByMetadata(SatelliteImagerySearchDto searchDto, Pageable pageable);
}