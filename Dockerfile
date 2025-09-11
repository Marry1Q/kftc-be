FROM openjdk:17-jdk-slim

# JAR 복사
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} /app/app.jar

WORKDIR /app

# 환경변수를 통한 TNS_ADMIN 설정
ENV ORACLE_TNS_ADMIN=/app/wallet

ENTRYPOINT ["java", "-Doracle.net.tns_admin=${ORACLE_TNS_ADMIN}", "-jar", "app.jar"]