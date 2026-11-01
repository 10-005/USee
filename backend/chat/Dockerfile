# 빌드 컨텍스트는 backend/ (docker build -f chat/Dockerfile -t chat .)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :chat:bootJar --no-daemon

FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/chat/build/libs/*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
