# 멀티스테이지 빌드
FROM gradle:8.14.2-jdk17 AS builder

# 작업 디렉토리 설정
WORKDIR /app

# Gradle wrapper와 빌드 파일들 복사
COPY gradle gradle
COPY gradlew .
COPY gradlew.bat .
COPY build.gradle .
COPY settings.gradle .

# 소스 코드 복사
COPY src src

# Gradle 빌드 실행
RUN ./gradlew clean build -x test

# 런타임 스테이지
FROM openjdk:17-jdk-slim

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 환경변수를 통한 TNS_ADMIN 설정
ENV ORACLE_TNS_ADMIN=/app/wallet

# 포트 노출 (Spring Boot 기본 포트)
EXPOSE 8080

ENTRYPOINT ["java", "-Doracle.net.tns_admin=${ORACLE_TNS_ADMIN}", "-jar", "app.jar"]