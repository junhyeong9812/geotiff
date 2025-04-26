package com.geotiff.back.domain.sateliteImagery.service;

import com.geotiff.back.domain.sateliteImagery.dto.SatelliteImagerySearchDto;
import com.geotiff.back.domain.sateliteImagery.entity.SatelliteImagery;
import com.geotiff.back.domain.sateliteImagery.repository.SatelliteImageryRepository;
import com.geotiff.back.global.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 위성 영상 처리 서비스
 * 위성 영상의 조회, 변환, 검색 등의 비즈니스 로직을 처리합니다.
 */
@Slf4j // 로깅 기능을 위한 Lombok 어노테이션
@Service // 스프링 서비스 컴포넌트로 지정
@RequiredArgsConstructor // 필수 필드를 포함한 생성자를 자동 생성하는 Lombok 어노테이션
public class SatelliteImageryService {

    private final S3Service s3Service; // S3 관련 작업을 처리하는 서비스
    private final RestTemplate restTemplate; // HTTP 요청을 보내기 위한 RestTemplate
    private final SatelliteImageryRepository satelliteImageryRepository; // 위성 영상 데이터 저장소
    private final Path tempDirectory; // 임시 파일 저장 디렉토리 경로
    private final String username; // 사용자 이름 (S3 경로에 사용)

    @Value("${app.gdal-api.url}") // application.yml에서 GDAL API URL 값 주입
    private String gdalApiUrl; // GDAL API 서버 URL

    private final String SOURCE_BUCKET = "dev1-apne2-pre-test-scene-bucket"; // 원본 위성 영상이 저장된 S3 버킷명
    private final String TARGET_BUCKET = "dev1-apne2-pre-test-tester-bucket"; // 변환된 위성 영상을 저장할 S3 버킷명

    /**
     * S3에서 사용 가능한 위성 영상 목록 조회
     * SOURCE_BUCKET에서 모든 위성 영상 파일 목록을 가져옵니다.
     *
     * @return 위성 영상 파일 경로 목록
     */
    public List<String> listAvailableImagery() {
        // S3Service를 사용하여 원본 버킷에서 파일 목록 조회 (접두사 없이 모든 파일)
        return s3Service.listFiles(SOURCE_BUCKET, "");
    }

    /**
     * 단일 위성 영상 변환 처리
     * 지정된 위성 영상을 다운로드하고, 메타데이터를 추출하며, COG 형식으로 변환한 후 저장합니다.
     *
     * @param imageryKey 변환할 위성 영상의 S3 키
     * @return 변환 결과 메타데이터가 포함된 SatelliteImagery 객체
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    @Transactional // 트랜잭션으로 처리 (모든 DB 작업이 성공하거나 모두 실패)
    public SatelliteImagery processSingleImagery(String imageryKey) throws IOException {
        // 로그 출력 - 처리 시작
        log.info("단일 위성 영상 처리 중: {}", imageryKey);

        // 1. S3에서 위성 영상 다운로드
        // 파일명만 추출하여 임시 디렉토리에 저장할 경로 생성
        Path downloadedPath = tempDirectory.resolve(getFileName(imageryKey));
        // S3에서 파일 다운로드
        s3Service.downloadFile(SOURCE_BUCKET, imageryKey, downloadedPath);

        try {
            // 2. 메타데이터 추출
            // GDAL API를 호출하여 파일의 메타데이터 추출
            JSONObject metadata = extractMetadata(downloadedPath);
            // 로그 출력 - 메타데이터 추출 완료
            log.info("메타데이터 추출 완료 - {}: {}", imageryKey, metadata.toString());

            // 3. 위성 영상을 COG로 변환
            // GDAL API를 호출하여 파일을 COG 형식으로 변환
            byte[] cogData = convertToCog(downloadedPath);
            // 로그 출력 - 변환 완료
            log.info("COG 형식으로 변환 완료 - {}: 크기 {} 바이트", imageryKey, cogData.length);

            // 4. 결과 파일 저장 (시퀀스 관리)
            // 원본 파일명에서 확장자를 제외한 기본 이름 추출
            String baseName = getBaseFileName(imageryKey);
            // 다음 사용할 시퀀스 번호 가져오기
            int sequence = getNextSequence(baseName);
            // COG 파일명 생성 (원본파일명_to_cog_시퀀스번호.tiff 형식)
            String cogFileName = String.format("%s_to_cog_%d.tiff", baseName, sequence);

            // 변환된 COG 파일을 임시 디렉토리에 저장할 경로 생성
            Path cogFilePath = tempDirectory.resolve(cogFileName);
            // 바이트 데이터를 파일로 저장
            Files.write(cogFilePath, cogData);

            // 5. S3에 업로드
            // 변환된 COG 파일을 대상 버킷에 업로드
            String s3Path = s3Service.uploadFile(TARGET_BUCKET, cogFilePath, cogFileName);

            // 6. 메타데이터 저장
            // 변환 결과 정보를 담을 SatelliteImagery 객체 생성
            SatelliteImagery satelliteImagery = SatelliteImagery.builder()
                    .originalName(imageryKey) // 원본 파일명
                    .cogName(cogFileName) // COG 파일명
                    .width(metadata.getInt("width")) // 이미지 너비
                    .height(metadata.getInt("height")) // 이미지 높이
                    .bandCount(metadata.getInt("bandCount")) // 밴드 수
                    .projection(metadata.optString("projection", "")) // 투영법 (없으면 빈 문자열)
                    .s3Path(s3Path) // S3에 저장된 경로
                    .sequence(sequence) // 시퀀스 번호
                    .fileSize(Files.size(cogFilePath)) // 파일 크기 (바이트)
                    .build();

            // 메타데이터 객체를 DB에 저장
            SatelliteImagery savedImagery = satelliteImageryRepository.save(satelliteImagery);
            // 로그 출력 - 저장 완료
            log.info("메타데이터 저장 완료 - {}: id={}, 시퀀스={}", imageryKey, savedImagery.getId(), sequence);

            // 임시 파일 삭제 (변환된 COG 파일)
            Files.deleteIfExists(cogFilePath);

            // 저장된 메타데이터 반환
            return savedImagery;

        } finally {
            // 임시 파일 삭제 (다운로드된 원본 파일)
            // finally 블록에서 처리하여 예외 발생 시에도 삭제 보장
            Files.deleteIfExists(downloadedPath);
        }
    }

    /**
     * 다중 위성 영상 변환 처리
     * 여러 위성 영상을 순차적으로 처리합니다.
     *
     * @param imageryKeys 처리할 위성 영상 키 목록
     * @return 변환된 위성 영상 메타데이터 목록
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    @Transactional // 트랜잭션으로 처리
    public List<SatelliteImagery> processMultipleImagery(List<String> imageryKeys) throws IOException {
        // 로그 출력 - 처리 시작
        log.info("다중 위성 영상 처리 중: 개수={}", imageryKeys.size());

        // Stream을 사용하여 각 이미지를 순차적으로 처리
        return imageryKeys.stream() // 위성 영상 키 목록을 스트림으로 변환
                .map(key -> { // 각 키에 대해 매핑 작업 수행
                    try {
                        // 단일 위성 영상 처리 메서드 호출
                        return processSingleImagery(key);
                    } catch (IOException e) {
                        // 오류 발생 시 로그 기록 후 null 반환 (해당 항목 건너뜀)
                        log.error("위성 영상 처리 실패: {}", key, e);
                        return null;
                    }
                })
                .filter(img -> img != null) // null이 아닌 결과만 필터링 (처리 실패한 항목 제외)
                .toList(); // 결과를 리스트로 수집
    }

    /**
     * 메타데이터 기반 위성 영상 검색
     * 검색 조건에 맞는 위성 영상을 페이지네이션하여 반환합니다.
     *
     * @param searchDto 검색 조건
     * @param pageable 페이지네이션 정보
     * @return 검색된 위성 영상 페이지
     */
    public Page<SatelliteImagery> searchImagery(SatelliteImagerySearchDto searchDto, Pageable pageable) {
        // 로그 출력 - 검색 시작
        log.info("검색 조건으로 위성 영상 검색 중: {}", searchDto);
        // 리포지토리를 통해 메타데이터 기반 검색 수행
        return satelliteImageryRepository.searchByMetadata(searchDto, pageable);
    }

    /**
     * 메타데이터 추출
     * GDAL API를 호출하여 위성 영상의 메타데이터를 추출합니다.
     *
     * @param filePath 메타데이터를 추출할 파일 경로
     * @return 추출된 메타데이터를 담은 JSONObject
     * @throws IOException API 호출 중 오류 발생 시
     */
    private JSONObject extractMetadata(Path filePath) throws IOException {
        // 로그 출력 - 메타데이터 추출 시작
        log.info("파일에서 메타데이터 추출 중: {}", filePath);

        // GDAL API 서비스에 요청
        // 파일을 바이트 배열로 읽기
        byte[] fileData = Files.readAllBytes(filePath);

        // MultiValueMap 생성 (여러 값을 가질 수 있는 맵, 멀티파트 폼 데이터 구성에 사용)
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // 'file'이라는 키로 파일 데이터 추가 (ByteArrayResource로 감싸서 추가)
        body.add("file", new ByteArrayResource(fileData) {
            @Override
            public String getFilename() {
                // API 요청에 파일명 포함 (원본 파일명 유지)
                return filePath.getFileName().toString();
            }
        });

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        // Content-Type을 multipart/form-data로 설정 (파일 업로드용)
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // HTTP 요청 엔티티 생성 (헤더와 바디 포함)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // RestTemplate을 사용하여 HTTP POST 요청 전송
        ResponseEntity<String> response = restTemplate.exchange(
                gdalApiUrl + "/extractMetadata", // API 엔드포인트 URL
                HttpMethod.POST, // HTTP 메서드
                requestEntity, // 요청 엔티티 (헤더 + 바디)
                String.class // 응답 바디 타입
        );

        // 응답 상태 코드 확인
        if (response.getStatusCode() == HttpStatus.OK) {
            // 정상 응답인 경우 JSON으로 파싱하여 반환
            return new JSONObject(response.getBody());
        } else {
            // 오류 응답인 경우 로그 출력 후 예외 발생
            log.error("메타데이터 추출 실패: 상태={}, 응답={}", response.getStatusCode(), response.getBody());
            throw new IOException("메타데이터 추출 실패: " + response.getStatusCode());
        }
    }

    /**
     * COG 변환
     * GDAL API를 호출하여 위성 영상을 COG 형식으로 변환합니다.
     *
     * @param filePath 변환할 파일 경로
     * @return 변환된 COG 데이터 (바이트 배열)
     * @throws IOException API 호출 중 오류 발생 시
     */
    private byte[] convertToCog(Path filePath) throws IOException {
        // 로그 출력 - COG 변환 시작
        log.info("파일을 COG로 변환 중: {}", filePath);

        // GDAL API 서비스에 요청
        // 파일을 바이트 배열로 읽기
        byte[] fileData = Files.readAllBytes(filePath);

        // MultiValueMap 생성 (멀티파트 폼 데이터 구성용)
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // 'file'이라는 키로 파일 데이터 추가
        body.add("file", new ByteArrayResource(fileData) {
            @Override
            public String getFilename() {
                // API 요청에 파일명 포함
                return filePath.getFileName().toString();
            }
        });

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        // Content-Type을 multipart/form-data로 설정
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // HTTP 요청 엔티티 생성
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // RestTemplate을 사용하여 HTTP POST 요청 전송
        // 응답으로 바이트 배열(변환된 파일 데이터) 수신
        ResponseEntity<byte[]> response = restTemplate.exchange(
                gdalApiUrl + "/convertToCog", // API 엔드포인트 URL
                HttpMethod.POST, // HTTP 메서드
                requestEntity, // 요청 엔티티
                byte[].class // 응답 바디 타입 (바이트 배열)
        );

        // 응답 상태 코드 확인
        if (response.getStatusCode() == HttpStatus.OK) {
            // 정상 응답인 경우 변환된 데이터 반환
            return response.getBody();
        } else {
            // 오류 응답인 경우 로그 출력 후 예외 발생
            log.error("COG 변환 실패: 상태={}", response.getStatusCode());
            throw new IOException("COG 변환 실패");
        }
    }

    /**
     * 파일명 추출
     * 경로에서 파일명만 추출합니다.
     *
     * @param path 전체 경로
     * @return 파일명
     */
    private String getFileName(String path) {
        // 마지막 슬래시(/) 위치 찾기
        int lastSlashPos = path.lastIndexOf('/');
        // 슬래시가 있으면 그 이후 부분을, 없으면 전체 문자열을 반환
        return lastSlashPos >= 0 ? path.substring(lastSlashPos + 1) : path;
    }

    /**
     * 확장자 제외한 기본 파일명 추출
     * 경로에서 파일명을 추출하고 확장자를 제거합니다.
     *
     * @param path 전체 경로
     * @return 확장자를 제외한 기본 파일명
     */
    private String getBaseFileName(String path) {
        // 파일명 추출
        String fileName = getFileName(path);
        // 마지막 점(.) 위치 찾기
        int lastDotPos = fileName.lastIndexOf('.');
        // 점이 있으면 그 이전 부분을, 없으면 전체 문자열을 반환
        return lastDotPos > 0 ? fileName.substring(0, lastDotPos) : fileName;
    }

    /**
     * 다음 시퀀스 번호 가져오기
     * 특정 이름을 가진 파일의 다음 시퀀스 번호를 결정합니다.
     *
     * @param baseName 기본 파일명
     * @return 다음 시퀀스 번호
     */
    private int getNextSequence(String baseName) {
        // 1. DB에서 최대 시퀀스 확인
        // 리포지토리를 통해 해당 이름의 파일에 대한 최대 시퀀스 번호 조회
        Integer maxSequence = satelliteImageryRepository.findMaxSequenceByOriginalName(baseName);
        // 최대 시퀀스가 있으면 +1, 없으면 1부터 시작
        int nextSequence = maxSequence != null ? maxSequence + 1 : 1;

        // 2. S3 버킷에서 파일 존재 여부 확인 및 시퀀스 조정
        boolean fileExists = true;
        // 파일이 존재하지 않을 때까지 시퀀스 증가
        while (fileExists) {
            // 현재 시퀀스로 COG 파일명 생성
            String cogFileName = String.format("%s_to_cog_%d.tiff", baseName, nextSequence);
            // username이 경로에 포함되어 있으므로 이를 고려하여 S3 키 생성
            String s3Key = String.format("%s/%s", username, cogFileName);
            // S3에 해당 키의 파일 존재 여부 확인
            fileExists = s3Service.isFileExists(TARGET_BUCKET, s3Key);
            // 파일이 존재하면 시퀀스 증가
            if (fileExists) {
                nextSequence++;
                // 로그 출력 - 시퀀스 증가
                log.info("파일이 이미 S3 버킷에 존재합니다, 시퀀스 증가: {}", nextSequence);
            }
        }

        // 최종 결정된 시퀀스 번호 반환
        return nextSequence;
    }
}