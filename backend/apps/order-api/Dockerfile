# 서비스별 이미지. ECR 리포지터리도 4개로 나뉜다(Phase 3-1).
# 태그는 커밋 SHA — 롤백 대상을 특정하기 위해서다.
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew :apps:order-api:build -x test -x spotlessCheck

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/apps/order-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
