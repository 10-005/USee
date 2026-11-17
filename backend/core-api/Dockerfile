# 빌드 컨텍스트는 backend/ (docker build -f core-api/Dockerfile -t core-api .)
FROM eclipse-temurin:21.0.12.1_1-jdk AS build
WORKDIR /workspace
COPY . .
# Gradle 의존성·wrapper를 BuildKit 캐시에 보관 → COPY . . 로 레이어가 깨져도 다시 받지 않음
RUN --mount=type=cache,target=/root/.gradle ./gradlew :core-api:bootJar --no-daemon

FROM eclipse-temurin:21.0.12.1_1-jre
ENV TZ=Asia/Seoul
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/core-api/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
