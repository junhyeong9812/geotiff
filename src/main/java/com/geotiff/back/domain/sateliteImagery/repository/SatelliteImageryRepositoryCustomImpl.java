package com.geotiff.back.domain.sateliteImagery.repository;

import com.geotiff.back.domain.sateliteImagery.dto.SatelliteImagerySearchDto;
import com.geotiff.back.domain.sateliteImagery.entity.QSatelliteImagery;
import com.geotiff.back.domain.sateliteImagery.entity.SatelliteImagery;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
public class SatelliteImageryRepositoryCustomImpl implements SatelliteImageryRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<SatelliteImagery> searchByMetadata(SatelliteImagerySearchDto searchDto, Pageable pageable) {
        JPAQueryFactory queryFactory = new JPAQueryFactory(entityManager);
        QSatelliteImagery qSatelliteImagery = QSatelliteImagery.satelliteImagery;

        BooleanBuilder whereBuilder = new BooleanBuilder();

        // DTO 기반 동적 검색 조건 구성
        if (StringUtils.hasText(searchDto.getOriginalName())) {
            whereBuilder.and(qSatelliteImagery.originalName.eq(searchDto.getOriginalName()));
        }

        if (StringUtils.hasText(searchDto.getOriginalNameContains())) {
            whereBuilder.and(qSatelliteImagery.originalName.contains(searchDto.getOriginalNameContains()));
        }

        if (StringUtils.hasText(searchDto.getCogName())) {
            whereBuilder.and(qSatelliteImagery.cogName.eq(searchDto.getCogName()));
        }

        if (searchDto.getWidth() != null) {
            whereBuilder.and(qSatelliteImagery.width.eq(searchDto.getWidth()));
        }

        if (searchDto.getHeight() != null) {
            whereBuilder.and(qSatelliteImagery.height.eq(searchDto.getHeight()));
        }

        if (searchDto.getBandCount() != null) {
            whereBuilder.and(qSatelliteImagery.bandCount.eq(searchDto.getBandCount()));
        }

        if (StringUtils.hasText(searchDto.getProjection())) {
            whereBuilder.and(qSatelliteImagery.projection.contains(searchDto.getProjection()));
        }

        // 전체 카운트 쿼리
        long total = queryFactory
                .selectFrom(qSatelliteImagery)
                .where(whereBuilder)
                .fetchCount();

        // 페이징 적용 쿼리
        JPAQuery<SatelliteImagery> query = queryFactory
                .selectFrom(qSatelliteImagery)
                .where(whereBuilder)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());

        // 정렬 적용
        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                if (order.getProperty().equals("id")) {
                    query.orderBy(order.isAscending() ? qSatelliteImagery.id.asc() : qSatelliteImagery.id.desc());
                } else if (order.getProperty().equals("createdAt")) {
                    query.orderBy(order.isAscending() ? qSatelliteImagery.createdAt.asc() : qSatelliteImagery.createdAt.desc());
                } else if (order.getProperty().equals("originalName")) {
                    query.orderBy(order.isAscending() ? qSatelliteImagery.originalName.asc() : qSatelliteImagery.originalName.desc());
                } else if (order.getProperty().equals("bandCount")) {
                    query.orderBy(order.isAscending() ? qSatelliteImagery.bandCount.asc() : qSatelliteImagery.bandCount.desc());
                } else if (order.getProperty().equals("width")) {
                    query.orderBy(order.isAscending() ? qSatelliteImagery.width.asc() : qSatelliteImagery.width.desc());
                } else if (order.getProperty().equals("height")) {
                    query.orderBy(order.isAscending() ? qSatelliteImagery.height.asc() : qSatelliteImagery.height.desc());
                }
            });
        } else {
            query.orderBy(qSatelliteImagery.id.desc());
        }

        List<SatelliteImagery> content = query.fetch();

        return new PageImpl<>(content, pageable, total);
    }
}