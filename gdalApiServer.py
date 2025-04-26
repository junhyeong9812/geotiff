from flask import Flask, request, jsonify, send_file, after_this_request
import os
import uuid
import json
import subprocess
from rio_cogeo.cogeo import cog_translate
from rio_cogeo.profiles import cog_profiles

# Flask 애플리케이션 인스턴스 생성
app = Flask(__name__)

# -------------------- 공통 상수 --------------------
# Docker Compose에서 바인드한 공유 볼륨 경로
BASE_DIR = "/tmp/cogConverter"
# 폴더가 없으면 생성 (애플리케이션 시작 시 1회 실행)
os.makedirs(BASE_DIR, exist_ok=True)

# -------------------- 헬스 체크 엔드포인트 --------------------
@app.route('/health', methods=['GET'])
def health():
    """
    서비스 상태 확인을 위한 헬스 체크 엔드포인트

    Returns:
        JSON 응답: 서비스가 정상 작동 중임을 알리는 상태 메시지
    """
    # 서비스가 정상 작동 중임을 나타내는 JSON 응답 반환
    return jsonify({"status": "healthy"})

# -------------------- 메타데이터 추출 엔드포인트 --------------------
@app.route('/extractMetadata', methods=['POST'])
def extract_metadata():
    """
    업로드된 지리공간 래스터 파일의 메타데이터를 추출하는 엔드포인트

    Request:
        multipart/form-data 형식으로 'file' 필드에 지리공간 파일을 포함해야 함

    Returns:
        JSON 응답: 추출된 메타데이터 (너비, 높이, 밴드 수, 투영법 등)
        또는 오류 메시지 및 적절한 HTTP 상태 코드
    """
    # 요청에 파일이 포함되어 있는지 확인
    if 'file' not in request.files:
        # 파일이 없는 경우 400 Bad Request 오류 반환
        return jsonify({"error": "파일이 제공되지 않았습니다"}), 400

    # 요청에서 파일 객체 가져오기
    file = request.files['file']
    # 임시 파일을 위한 고유 ID 생성
    temp_id = uuid.uuid4().hex
    # 입력 파일 저장 경로 생성
    input_path = os.path.join(BASE_DIR, f"{temp_id}_input.tif")
    # 업로드된 파일을 임시 경로에 저장
    file.save(input_path)

    try:
        # gdalinfo 명령어를 실행하여 메타데이터를 JSON 형식으로 가져옴
        result = subprocess.run(
            ["gdalinfo", "-json", input_path],
            capture_output=True, text=True, check=True
        )
        # 결과를 JSON 객체로 파싱
        metadata = json.loads(result.stdout)

        # 필요한 메타데이터 정보만 추출하여 응답 객체 생성
        response = {
            "width": metadata.get("size", [0, 0])[0],         # 영상 너비
            "height": metadata.get("size", [0, 0])[1],        # 영상 높이
            "bandCount": len(metadata.get("bands", [])),      # 밴드 수
            "projection": metadata.get("coordinateSystem", {}).get("wkt", ""),  # 투영법(WKT 형식)
            "geotransform": metadata.get("geoTransform", []), # 지리 변환 정보
            "originalName": file.filename                     # 원본 파일명
        }
        # 추출된 메타데이터 JSON 반환
        return jsonify(response)

    except subprocess.CalledProcessError as e:
        # gdalinfo 실행 중 오류 발생 시
        return jsonify({"error": "메타데이터 추출 실패", "details": e.stderr}), 500
    finally:
        # 작업 완료 후 임시 파일 삭제 (성공, 실패 모두)
        if os.path.exists(input_path):
            os.remove(input_path)

# -------------------- COG 변환 엔드포인트 --------------------
@app.route('/convertToCog', methods=['POST'])
def convert_to_cog():
    """
    업로드된 지리공간 래스터 파일을 Cloud Optimized GeoTIFF(COG) 형식으로 변환하는 엔드포인트

    Request:
        multipart/form-data 형식으로 'file' 필드에 지리공간 파일을 포함해야 함

    Returns:
        변환된 COG 파일: 다운로드 가능한 형식으로 반환
        또는 오류 메시지 및 적절한 HTTP 상태 코드
    """
    # 요청에 파일이 포함되어 있는지 확인
    if 'file' not in request.files:
        # 파일이 없는 경우 400 Bad Request 오류 반환
        return jsonify({"error": "파일이 제공되지 않았습니다"}), 400

    # 요청에서 파일 객체 가져오기
    file = request.files['file']
    # 임시 파일을 위한 고유 ID 생성
    temp_id = uuid.uuid4().hex
    # 입력 및 출력 파일 경로 생성
    input_path = os.path.join(BASE_DIR, f"{temp_id}_input.tif")
    output_path = os.path.join(BASE_DIR, f"{temp_id}_output.tif")
    # 업로드된 파일을 임시 경로에 저장
    file.save(input_path)

    try:
        # rio-cogeo 라이브러리를 사용하여 COG로 변환
        # - input_path: 입력 파일 경로
        # - output_path: 출력 파일 경로
        # - cog_profiles.get("deflate"): 압축 프로필(deflate 알고리즘 사용)
        # - quiet: 로그 출력 여부
        # - web_optimized: 웹 최적화 여부 (타일 레이아웃 최적화)
        cog_translate(
            input_path,
            output_path,
            cog_profiles.get("deflate"),
            quiet=True,
            web_optimized=True
        )

        # -------------------- 응답 이후 정리 --------------------
        # 클라이언트에 응답을 반환한 후 실행될 콜백 함수 정의
        @after_this_request
        def cleanup(response):
            # 입력 및 출력 임시 파일 모두 삭제 시도
            for p in (input_path, output_path):
                try:
                    os.remove(p)
                except FileNotFoundError:
                    # 파일이 이미 삭제되었거나 없는 경우 무시
                    pass
            # 원본 응답 반환
            return response
        # ------------------------------------------------------

        # 변환된 COG 파일을 클라이언트에 전송
        # - mimetype: 파일 MIME 타입
        # - as_attachment: 다운로드 형식으로 전송
        # - download_name: 다운로드될 파일명 (원본 파일명에 _to_cog 접미사 추가)
        return send_file(
            output_path,
            mimetype="image/tiff",
            as_attachment=True,
            download_name=f"{os.path.splitext(file.filename)[0]}_to_cog.tif"
        )

    except Exception as e:
        # 변환 작업 중 오류 발생 시
        # 입력 파일 정리 (출력 파일은 아직 생성되지 않았을 수 있음)
        if os.path.exists(input_path):
            os.remove(input_path)
        # 오류 메시지와 함께 500 Internal Server Error 반환
        return jsonify({"error": f"COG 변환 실패: {str(e)}"}), 500


# 애플리케이션 직접 실행 시 서버 시작
if __name__ == '__main__':
    # 모든 네트워크 인터페이스(0.0.0.0)에서 5000번 포트로 서버 실행
    app.run(host='0.0.0.0', port=5000)