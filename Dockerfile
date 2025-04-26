FROM openjdk:17-jdk-slim

# 임시 디렉토리 생성
RUN mkdir -p /tmp/cogConverter && chmod 777 /tmp/cogConverter

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]