from flask import Flask, request, jsonify, send_file
import os
import uuid
import tempfile
import json
import subprocess
from rio_cogeo.cogeo import cog_translate
from rio_cogeo.profiles import cog_profiles

app = Flask(__name__)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "healthy"})

@app.route('/extractMetadata', methods=['POST'])
def extractMetadata():
    if 'file' not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files['file']

    # 임시 파일 생성
    tempId = str(uuid.uuid4())
    inputPath = os.path.join(tempfile.gettempdir(), f"{tempId}_input.tif")
    file.save(inputPath)

    try:
        # GDAL로 메타데이터 추출
        result = subprocess.run(
            ["gdalinfo", "-json", inputPath],
            capture_output=True, text=True
        )

        if result.returncode != 0:
            return jsonify({"error": "Failed to extract metadata", "details": result.stderr}), 500

        metadata = json.loads(result.stdout)

        # 필요한 메타데이터만 추출
        response = {
            "width": metadata.get("size", [0, 0])[0],
            "height": metadata.get("size", [0, 0])[1],
            "bandCount": len(metadata.get("bands", [])),
            "projection": metadata.get("coordinateSystem", {}).get("wkt", ""),
            "geotransform": metadata.get("geoTransform", []),
            "originalName": file.filename
        }

        return jsonify(response)

    except Exception as e:
        return jsonify({"error": str(e)}), 500

    finally:
        # 임시 파일 삭제
        if os.path.exists(inputPath):
            os.remove(inputPath)

@app.route('/convertToCog', methods=['POST'])
def convertToCog():
    if 'file' not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files['file']

    # 임시 파일 생성
    tempId = str(uuid.uuid4())
    inputPath = os.path.join(tempfile.gettempdir(), f"{tempId}_input.tif")
    outputPath = os.path.join(tempfile.gettempdir(), f"{tempId}_output.tif")
    file.save(inputPath)

    try:
        # COG 프로필 설정
        outputProfile = cog_profiles.get("deflate")

        # COG 변환 실행
        cog_translate(
            inputPath,
            outputPath,
            outputProfile,
            quiet=True,
            web_optimized=True
        )

        # 변환된 파일 반환
        return send_file(
            outputPath,
            mimetype="image/tiff",
            as_attachment=True,
            download_name=f"{os.path.splitext(file.filename)[0]}_to_cog.tif"
        )

    except Exception as e:
        return jsonify({"error": str(e)}), 500

    finally:
        # 임시 파일 삭제
        if os.path.exists(inputPath):
            os.remove(inputPath)
        # 결과 파일은 응답 전송 후 Flask가 자동으로 삭제

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)