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
