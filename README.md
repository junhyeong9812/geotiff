# COG 변환기 (Cloud Optimized GeoTIFF Converter)

위성 영상을 Cloud Optimized GeoTIFF(COG) As a Service 스타일로 변환하는 스프링 부트 기반 백엔드 서비스입니다.

## 목차

- [개요](#개요)
- [기능](#기능)
- [시스템 아키텍처](#시스템-아키텍처)
- [기술 스택](#기술-스택)
- [API 명세](#api-명세)
- [인프라 구조](#인프라-구조)
  - [Public Cloud 배포 아키텍처](#public-cloud-배포-아키텍처)
  - [이벤트 기반 처리 아키텍처](#이벤트-기반-처리-아키텍처)
- [사용된 외부 라이브러리 및 오픈소스](#사용된-외부-라이브러리-및-오픈소스)
- [개발 및 배포 환경](#개발-및-배포-환경)
- [마이크로서비스 구성](#마이크로서비스-구성)
- [데이터베이스 스키마](#데이터베이스-스키마)

## 개요

본 서비스는 위성 영상 데이터를 Cloud Optimized GeoTIFF(COG) 형식으로 변환하여 클라우드 환경에서 효율적으로 처리할 수 있도록 지원합니다. COG 형식은 대용량 지리공간 래스터 데이터를 HTTP 범위 요청을 통해 효율적으로 액세스할 수 있게 해주는 표준 포맷으로, 대규모 위성 영상 데이터를 클라우드에서 효율적으로 처리하는 데 이상적입니다.

## 기능

- **위성 영상 목록 조회**: S3 버킷에 저장된 위성 영상 파일 목록을 조회합니다.
- **단일 위성 영상 변환**: 지정된 위성 영상을 COG 형식으로 변환합니다.
- **다중 위성 영상 일괄 변환**: 여러 위성 영상을 COG 형식으로 일괄 변환합니다.
- **메타데이터 기반 검색**: 변환된 위성 영상을 다양한 메타데이터 기준으로 검색합니다.
- **시퀀스 관리**: 동일한 원본 파일에 대한 변환 시 시퀀스 번호를 자동 관리합니다.

## 시스템 아키텍처

시스템은 다음과 같은 구성 요소로 이루어져 있습니다:

1. **Spring Boot 백엔드 애플리케이션**:
  - REST API 제공
  - S3 통합 및 메타데이터 관리
  - GDAL API 서비스와 통신

2. **GDAL API 서비스(Python/Flask)**:
  - GDAL 및 rio-cogeo 라이브러리를 활용한 위성 영상 처리
  - 메타데이터 추출 및 COG 변환 기능 제공
  - 마이크로서비스 아키텍처로 구현

3. **S3 스토리지**:
  - 원본 위성 영상 저장 (SOURCE_BUCKET)
  - 변환된 COG 파일 저장 (TARGET_BUCKET)

4. **H2 데이터베이스**:
  - 메타데이터 저장 및 검색
  - 시퀀스 관리

## 기술 스택

### 백엔드 (Java/Spring)
- **프레임워크**: Spring Boot 3.x
- **ORM**: Spring Data JPA
- **쿼리 처리**: QueryDSL
- **클라우드 스토리지**: AWS S3 Java SDK 2.x
- **유틸리티**: Lombok, Jackson

### GDAL API 서비스 (Python)
- **웹 프레임워크**: Flask
- **지리공간 처리**: GDAL, rasterio, rio-cogeo
- **서버**: Gunicorn

### 데이터베이스 & 인프라
- **데이터베이스**: H2 Database (개발), PostgreSQL (운영)
- **컨테이너화**: Docker, Docker Compose
- **로깅 & 모니터링**: Slf4j, p6spy

## API 명세

### 위성 영상 목록 조회

```
GET /api/imagery/list
```

### 단일 위성 영상 변환

```
POST /api/imagery/convert?imageryKey={imageryKey}
```

### 다중 위성 영상 변환

```
POST /api/imagery/convert-batch
Content-Type: application/json

["imageryKey1", "imageryKey2", ...]
```

### 메타데이터 기반 검색 (GET)

```
GET /api/imagery?originalName={originalName}&width={width}&height={height}...
```

### 메타데이터 고급 검색 (POST)

```
POST /api/imagery/search
Content-Type: application/json

{
  "originalName": "example",
  "width": 1000,
  "height": 1000,
  "bandCount": 3,
  ...
}
```

## GDAL API 서비스 엔드포인트

### 헬스 체크

```
GET /health
```

### 메타데이터 추출

```
POST /extractMetadata
Content-Type: multipart/form-data

file: [binary file data]
```

### COG 변환

```
POST /convertToCog
Content-Type: multipart/form-data

file: [binary file data]
```

## 인프라 구조

### Public Cloud 배포 아키텍처

![image](https://github.com/user-attachments/assets/31d65d74-5bf4-48b3-9121-2c6b9e138a5c)



AWS 클라우드에 해당 서비스를 배포하는 아키텍처는 다음과 같습니다:

1. **네트워크 계층**:
  - **VPC**: 전체 서비스를 위한 격리된 네트워크 환경
  - **서브넷**: 퍼블릭 서브넷(ALB), 프라이빗 서브넷(ECS, RDS)
  - **보안 그룹**: 서비스 간 트래픽 제어

2. **컨테이너 & 오케스트레이션 계층**:
  - **ALB(Application Load Balancer)**: 외부 트래픽 분산
  - **ECS(Elastic Container Service) 또는 EKS(Elastic Kubernetes Service)**:
    - **Spring Boot 애플리케이션 컨테이너**: Java 기반 백엔드
    - **GDAL API 컨테이너**: Python/Flask 기반 GDAL 처리 서비스
  - **ECR(Elastic Container Registry)**: 컨테이너 이미지 저장소

3. **데이터 계층**:
  - **S3 버킷(원본)**: 업로드된 원본 위성 영상
  - **S3 버킷(변환됨)**: 변환된 COG 형식 파일
  - **RDS(Relational Database Service)**: PostgreSQL 데이터베이스(운영 환경)
  - **ElastiCache**: API 응답 캐싱

4. **모니터링 & 로깅**:
  - **CloudWatch**: 애플리케이션 및 인프라 모니터링
  - **X-Ray**: 분산 추적
  - **CloudTrail**: API 호출 감사

5. **보안 계층**:
  - **IAM**: 리소스 접근 제어
  - **KMS**: 데이터 암호화
  - **Secrets Manager**: 인증 정보 관리

### 이벤트 기반 처리 아키텍처

API 요청을 통한 처리가 아닌, 원격 저장소에 영상을 업로드했을 때 자동으로 변환이 이루어지는 이벤트 기반 아키텍처:
![image](https://github.com/user-attachments/assets/80a4939c-2121-40ec-85dd-cac8479a5773)



1. **이벤트 소스**:
  - **S3 버킷**: 원본 위성 영상 업로드 시 이벤트 발생
  - **S3 이벤트 알림**: 객체 생성 이벤트 발행

2. **메시지 처리**:
  - **Lambda 함수 또는 EventBridge**: 이벤트 수신 및 필터링
  - **SQS**: 처리 큐, 안정적인 메시지 전달 보장
  - **DLQ(Dead Letter Queue)**: 실패한 메시지 처리

3. **처리 계층**:
  - **ECS 작업 또는 Fargate 태스크**:
    - SQS 메시지 폴링
    - Spring Boot 애플리케이션으로 처리 요청
    - GDAL API 서비스를 통한 변환 처리
  - **Auto Scaling**: 큐 길이에 따른 작업자 스케일링

4. **결과 처리**:
  - **S3 버킷(변환됨)**: 변환된 COG 파일 저장
  - **DynamoDB**: 처리 상태 추적
  - **RDS**: 영구적인 메타데이터 저장
  - **SNS**: 처리 완료 알림

5. **모니터링 & 관리**:
  - **CloudWatch**: 지표 및 로그 모니터링
  - **Step Functions**: 워크플로우 오케스트레이션 (선택 사항)
  - **X-Ray**: 분산 추적

이 아키텍처의 장점:
- S3에 파일 업로드만으로 자동 변환 워크플로우 트리거
- 비동기 처리를 통한 확장성 및 복원력 향상
- 배치 처리 가능성 (비용 효율성)
- 처리 실패에 대한 재시도 및 DLQ 메커니즘

## 사용된 외부 라이브러리 및 오픈소스

| 라이브러리/오픈소스 | 버전 | 사용 목적 |
|-------------------|------|-----------|
| Spring Boot | 3.x | Java 백엔드 애플리케이션 프레임워크 |
| AWS Java SDK for S3 | 2.x | S3 버킷 접근 및 파일 관리 |
| QueryDSL | 5.0.x | 동적 쿼리 생성 |
| Jackson | 2.x | JSON 처리 |
| Lombok | 1.18.x | 코드 간소화 및 보일러플레이트 제거 |
| H2 Database | 2.x | 개발 및 테스트용 인메모리 데이터베이스 |
| p6spy | 3.9.x | SQL 로깅 |
| Flask | 2.x | Python 백엔드 웹 프레임워크 |
| rio-cogeo | 4.x | Cloud Optimized GeoTIFF 생성 |
| rasterio | 1.3.x | 지리공간 래스터 데이터 처리 |
| GDAL | 3.6.x | 지리공간 데이터 변환 라이브러리 |
| Gunicorn | 20.x | Python WSGI HTTP 서버 |

## 개발 및 배포 환경

### 개발 환경

- Java 17+
- Python 3.9+
- Spring Boot 3.x
- GDAL 3.6.x
- Docker & Docker Compose

### 로컬 환경 설정

```bash
# 공유 볼륨을 위한 폴더 생성
mkdir -p ./tempData

# Docker Compose로 서비스 시작
docker-compose up -d
```

### Docker 컨테이너 구성

전체 애플리케이션은 Docker Compose를 사용하여 두 개의 주요 컨테이너로 배포됩니다:

```yaml
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: cog-converter-app
    ports:
      - "8080:8080"
    depends_on:
      - gdalapi
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - APP_GDAL_API_URL=http://gdalapi:5000
    volumes:
      - ./tempData:/tmp/cogConverter

  gdalapi:
    build:
      context: .
      dockerfile: Dockerfile.gdalApi
    container_name: cog-converter-gdalapi
    ports:
      - "5000:5000"
    volumes:
      - ./tempData:/tmp/cogConverter
```

## 마이크로서비스 구성

### 1. Spring Boot 백엔드 애플리케이션 (Java)
- S3 통합 및 메타데이터 관리
- REST API 제공
- 비즈니스 로직 처리
- 파일 및 메타데이터 관리

### 2. GDAL API 서비스 (Python/Flask)
- GDAL 및 rio-cogeo 기반 지리공간 처리
- 두 가지 주요 엔드포인트:
  - `/extractMetadata`: 위성 영상 메타데이터 추출
  - `/convertToCog`: 위성 영상을 COG 형식으로 변환
- 입력 파일 처리 및 임시 파일 관리

### 통신 흐름
1. 클라이언트 → Spring Boot API 요청
2. Spring Boot → S3에서 파일 다운로드
3. Spring Boot → GDAL API로 메타데이터 추출 요청
4. Spring Boot → GDAL API로 COG 변환 요청
5. Spring Boot → 변환된 파일을 S3에 업로드
6. Spring Boot → 메타데이터를 DB에 저장
7. Spring Boot → 클라이언트에 결과 반환

### 공유 볼륨
두 컨테이너는 `/tmp/cogConverter` 디렉토리를 공유 볼륨으로 사용하여 임시 파일을 효율적으로 교환합니다.

## 데이터베이스 스키마

### SatelliteImagery 엔티티

```sql
CREATE TABLE satellite_imagery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    cog_name VARCHAR(255) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    band_count INT NOT NULL,
    projection CLOB,
    s3_path VARCHAR(255),
    sequence INT,
    file_size BIGINT,
    created_at TIMESTAMP NOT NULL
);
```

---

© 2025 Cloud Optimized GeoTIFF Converter. All rights reserved.

# geotiff Convert COG
## 자료조사
GEOTIFF 관련 자료
- https://stackoverflow.com/questions/3716462/how-can-i-write-a-geotiff-in-java
- 실제 구현사례를 찾기 힘듬
### 구현 방식 
- A. GDAL을 활용한 변환 및 geotools를 활용한 메타데이터 추출
    - https://gdal.org/en/stable/
- B. Cogger/rio-cogeo를 활용한 변환 및 geotools를 활용한 메타데이터 추출
  - https://github.com/airbusgeo/cogger
  - https://cogeotiff.github.io/rio-cogeo/
- C. geotools를 활용한 메타데이터 추출 및 내부 변환코드 실제 구현
  
  - java로 구현한 구현사례가 존재하지 않지만 Cogger를 분석하여 기능 구현 가능성

    - TIFF 이미지>>타일 단위로 분할 >> 오버뷰 생성 >> 새롭게 TIFF 구조로 작성Tileindex구조화>>파일순서 맞추기??
    -  TIle 기반 저장 코드를 작성
    -  Overview생성 코드 작성
    -  COG-Like 구조로 TIFF 직접 생성

### 정리
    A,B안이 그나마 현실적
    C안은 시간이 너무 걸릴 수 있음
    
    A안으로 만든다면
    Docker의 GDAL이미지를 통한 GDAL서버를 만들고
    App서버에서 메타 데이터 자장 후 CLI를 GDAL서버로 전달하여 이미지 변환작업
    및 app을 통한 S3 업로드
    
    아니라면
    implementation 'org.gdal:gdal:3.8.0' 의존성에 대해서 찾아보고
    이 의존성을 공부할 필요성이 존재.

    B안이라면 Cogger/rio-cogeo 서버를 올리고 해당 서버와 app서버를 통한
    연계
    
    C안 >>10일안에 LLM없이 불가능.


### 
