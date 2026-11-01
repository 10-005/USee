# 빌드 컨텍스트는 backend/ (docker build -f simulation/Dockerfile -t simulation .)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :simulation:bootJar --no-daemon

FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/simulation/build/libs/*.jar app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
