# syntax=docker/dockerfile:1
# 서비스별 이미지. ECR 리포지터리도 4개로 나뉜다(Phase 3-1).
# 태그는 커밋 SHA — 롤백 대상을 특정하기 위해서다.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
# COPY . .가 모노레포 전체를 한 레이어로 묶어서 backend/ 아무 파일이나 바뀌면
# 레이어 캐시가 통째로 무효화된다 - 그래서 레이어 캐시가 아니라 BuildKit
# cache mount로 ~/.gradle(의존성+빌드 캐시)을 따로 영속화한다. 레이어 히트
# 여부와 무관하게 항상 재사용됨. sharing=locked는 동시 빌드 시 캐시 손상 방지.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :apps:catalog-api:build -x test -x spotlessCheck

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/apps/catalog-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
