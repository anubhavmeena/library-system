# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY common-lib/pom.xml common-lib/pom.xml
COPY common-lib/src common-lib/src
# Installs common-lib, then copies the built artifact into a normal (non-cache-mount)
# image layer at /common-lib-repo. BuildKit's GHA layer cache can mark this RUN
# "CACHED" (skipped) on unchanged inputs without the underlying --mount=type=cache
# volume actually being populated in a fresh builder instance (every CI run here gets
# a brand-new ephemeral builder) — since this RUN's only real output lives inside
# that cache mount, its resulting layer is an effectively-empty no-op, which is
# trivially cache-matchable regardless of whether .m2 is actually populated this run.
# Keeping a copy in a regular layer lets every later step restore it unconditionally.
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f common-lib/pom.xml install -DskipTests -q \
    && mkdir -p /common-lib-repo/com \
    && cp -r /root/.m2/repository/com/library /common-lib-repo/com/library
COPY membership-service/pom.xml membership-service/pom.xml
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mkdir -p /root/.m2/repository/com \
    && cp -r /common-lib-repo/com/library /root/.m2/repository/com/library \
    && mvn -f membership-service/pom.xml dependency:go-offline -q
COPY membership-service/src membership-service/src
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mkdir -p /root/.m2/repository/com \
    && cp -r /common-lib-repo/com/library /root/.m2/repository/com/library \
    && mvn -f membership-service/pom.xml package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /build/membership-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
