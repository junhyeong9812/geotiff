package com.geotiff.back.domain.sateliteImagery.repository;

import com.geotiff.back.domain.sateliteImagery.entity.SatelliteImagery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SatelliteImageryRepository extends JpaRepository<SatelliteImagery, Long>,
        QuerydslPredicateExecutor<SatelliteImagery>, SatelliteImageryRepositoryCustom {

    @Query("SELECT MAX(s.sequence) FROM SatelliteImagery s WHERE s.originalName = :originalName")
    Integer findMaxSequenceByOriginalName(@Param("originalName") String originalName);
}