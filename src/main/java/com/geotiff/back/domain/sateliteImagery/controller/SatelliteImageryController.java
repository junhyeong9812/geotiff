package com.geotiff.back.domain.sateliteImagery.controller;

import com.geotiff.back.domain.sateliteImagery.dto.SatelliteImagerySearchDto;
import com.geotiff.back.domain.sateliteImagery.entity.SatelliteImagery;
import com.geotiff.back.domain.sateliteImagery.service.SatelliteImageryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 위성 영상 처리 컨트롤러
 * 위성 영상 목록 조회, 변환, 검색 등의 RESTful API를 제공합니다.
 * 클라이언트의 HTTP 요청을 처리하고 적절한 응답을 반환합니다.
 */
@Slf4j // Lombok 어노테이션으로 SLF4J 로거를 자동 생성합니다.
@RestController // 이 클래스가 REST API 컨트롤러임을 선언하며, 모든 메서드의 반환값은 응답 본문으로 직접 변환됩니다.
@RequestMapping("/api/imagery") // 이 컨트롤러의 모든 엔드포인트에 공통으로 적용되는 기본 URL 경로입니다.
@RequiredArgsConstructor // Lombok 어노테이션으로 final 필드를 파라미터로 갖는 생성자를 자동 생성합니다.
public class SatelliteImageryController {

    // 위성 영상 처리 로직을 담당하는 서비스 클래스입니다.
    // final로 선언되어 생성자 주입 방식으로 의존성이 주입됩니다.
    private final SatelliteImageryService satelliteImageryService;

    /**
     * 사용 가능한 위성 영상 목록 조회 API
     * S3 버킷에서 사용 가능한 모든 위성 영상 파일 목록을 반환합니다.
     * HTTP GET 요청을 처리합니다.
     *
     * @return 위성 영상 파일명 목록을 담은 ResponseEntity 객체
     */
    @GetMapping("/list") // HTTP GET 요청을 "/api/imagery/list" 경로에 매핑합니다.
    public ResponseEntity<List<String>> listImagery() {
        // 요청 수신 로그를 INFO 레벨로 기록합니다.
        log.info("위성 영상 목록 조회 요청 수신");

        // 서비스 계층의 메서드를 호출하여 S3에서 위성 영상 목록을 조회합니다.
        List<String> imagery = satelliteImageryService.listAvailableImagery();

        // HTTP 200 OK 상태 코드와 함께 조회된 목록을 응답 본문에 포함하여 반환합니다.
        // ResponseEntity.ok()는 상태 코드 200과 함께 본문을 반환하는 유틸리티 메서드입니다.
        return ResponseEntity.ok(imagery);
    }

    /**
     * 단일 위성 영상 변환 API
     * 지정된 위성 영상을 COG(Cloud Optimized GeoTIFF) 형식으로 변환합니다.
     * HTTP POST 요청을 처리합니다.
     *
     * @param imageryKey 변환할 위성 영상의 S3 키(경로)로, 요청 파라미터로 전달됩니다.
     * @return 변환된 위성 영상 메타데이터 또는 오류 정보를 담은 ResponseEntity 객체
     */
    @PostMapping("/convert") // HTTP POST 요청을 "/api/imagery/convert" 경로에 매핑합니다.
    public ResponseEntity<?> convertSingleImagery(@RequestParam String imageryKey) {
        // 요청 수신 로그를 INFO 레벨로 기록합니다. 변환할 영상의 키도 함께 기록합니다.
        log.info("단일 위성 영상 변환 요청 수신: {}", imageryKey);

        try {
            // 서비스 계층의 메서드를 호출하여 위성 영상을 처리합니다.
            // 이 과정에서 영상 다운로드, 메타데이터 추출, COG 변환, S3 업로드 등이 수행됩니다.
            SatelliteImagery result = satelliteImageryService.processSingleImagery(imageryKey);

            // 변환 성공 시 HTTP 200 OK 상태 코드와 함께 변환 결과를 응답 본문에 포함하여 반환합니다.
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            // 변환 과정에서 오류 발생 시 로그를 ERROR 레벨로 기록합니다.
            // 오류 메시지와 스택 트레이스도 함께 기록합니다.
            log.error("위성 영상 변환 오류: {}", imageryKey, e);

            // 클라이언트에게 반환할 오류 정보를 Map으로 구성합니다.
            Map<String, String> error = new HashMap<>();
            // 오류 유형 정보를 추가합니다.
            error.put("error", "위성 영상 변환 실패");
            // 상세 오류 메시지를 추가합니다.
            error.put("message", e.getMessage());

            // HTTP 500 Internal Server Error 상태 코드와 함께 오류 정보를 응답 본문에 포함하여 반환합니다.
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 다중 위성 영상 변환 API
     * 여러 위성 영상을 COG 형식으로 일괄 변환합니다.
     * HTTP POST 요청을 처리합니다.
     *
     * @param imageryKeys 변환할 위성 영상 키 목록으로, 요청 본문(JSON)으로 전달됩니다.
     * @return 변환된 위성 영상 메타데이터 목록 또는 오류 정보를 담은 ResponseEntity 객체
     */
    @PostMapping("/convert-batch") // HTTP POST 요청을 "/api/imagery/convert-batch" 경로에 매핑합니다.
    public ResponseEntity<?> convertMultipleImagery(@RequestBody List<String> imageryKeys) {
        // 요청 수신 로그를 INFO 레벨로 기록합니다. 변환할 영상 개수도 함께 기록합니다.
        log.info("다중 위성 영상 변환 요청 수신: 개수={}", imageryKeys.size());

        try {
            // 서비스 계층의 메서드를 호출하여 여러 위성 영상을 일괄 처리합니다.
            // 내부적으로는 각 영상에 대해 단일 영상 처리 로직이 순차적으로 적용됩니다.
            List<SatelliteImagery> results = satelliteImageryService.processMultipleImagery(imageryKeys);

            // 변환 성공 시 HTTP 200 OK 상태 코드와 함께 변환 결과 목록을 응답 본문에 포함하여 반환합니다.
            return ResponseEntity.ok(results);
        } catch (IOException e) {
            // 변환 과정에서 오류 발생 시 로그를 ERROR 레벨로 기록합니다.
            log.error("다중 위성 영상 변환 오류", e);

            // 클라이언트에게 반환할 오류 정보를 Map으로 구성합니다.
            Map<String, String> error = new HashMap<>();
            // 오류 유형 정보를 추가합니다.
            error.put("error", "다중 위성 영상 변환 실패");
            // 상세 오류 메시지를 추가합니다.
            error.put("message", e.getMessage());

            // HTTP 500 Internal Server Error 상태 코드와 함께 오류 정보를 응답 본문에 포함하여 반환합니다.
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 메타데이터 기반 위성 영상 검색 API (기본)
     * 요청 파라미터를 기반으로 위성 영상을 검색합니다.
     * HTTP GET 요청을 처리합니다.
     *
     * @param searchDto 검색 조건 DTO로, 요청 파라미터를 객체로 바인딩합니다.
     * @param pageable 페이지네이션 정보로, 기본 페이지 크기는 20으로 설정됩니다.
     * @return 검색된 위성 영상 페이지를 담은 ResponseEntity 객체
     */
    @GetMapping // HTTP GET 요청을 기본 경로 "/api/imagery"에 매핑합니다.
    public ResponseEntity<Page<SatelliteImagery>> searchImagery(
            @ModelAttribute SatelliteImagerySearchDto searchDto, // HTTP 요청 파라미터를 DTO 객체로 변환합니다.
            @PageableDefault(size = 20) Pageable pageable) { // 페이지 정보를 추출하며, 기본 크기는 20으로 설정합니다.

        // 요청 수신 로그를 INFO 레벨로 기록합니다. 검색 조건도 함께 기록합니다.
        log.info("위성 영상 검색 요청 수신: {}", searchDto);

        // 서비스 계층의 메서드를 호출하여 검색 조건에 맞는 위성 영상을 페이지네이션하여 조회합니다.
        Page<SatelliteImagery> results = satelliteImageryService.searchImagery(searchDto, pageable);

        // HTTP 200 OK 상태 코드와 함께 검색 결과를 응답 본문에 포함하여 반환합니다.
        return ResponseEntity.ok(results);
    }

    /**
     * 메타데이터 기반 위성 영상 고급 검색 API
     * 요청 본문의 JSON을 기반으로 위성 영상을 검색합니다.
     * HTTP POST 요청을 처리합니다.
     *
     * @param searchDto 검색 조건 DTO로, 요청 본문(JSON)을 객체로 바인딩합니다.
     * @param pageable 페이지네이션 정보로, 기본 페이지 크기는 20으로 설정됩니다.
     * @return 검색된 위성 영상 페이지를 담은 ResponseEntity 객체
     */
    @PostMapping("/search") // HTTP POST 요청을 "/api/imagery/search" 경로에 매핑합니다.
    public ResponseEntity<Page<SatelliteImagery>> advancedSearch(
            @RequestBody SatelliteImagerySearchDto searchDto, // HTTP 요청 본문을 DTO 객체로 변환합니다.
            @PageableDefault(size = 20) Pageable pageable) { // 페이지 정보를 추출하며, 기본 크기는 20으로 설정합니다.

        // 요청 수신 로그를 INFO 레벨로 기록합니다. 검색 조건도 함께 기록합니다.
        log.info("위성 영상 고급 검색 요청 수신: {}", searchDto);

        // 서비스 계층의 메서드를 호출하여 검색 조건에 맞는 위성 영상을 페이지네이션하여 조회합니다.
        // 기본 검색 API와 동일한 서비스 메서드를 사용하지만, 요청 형식이 다릅니다.
        Page<SatelliteImagery> results = satelliteImageryService.searchImagery(searchDto, pageable);

        // HTTP 200 OK 상태 코드와 함께 검색 결과를 응답 본문에 포함하여 반환합니다.
        return ResponseEntity.ok(results);
    }
}