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
COPY user-service/pom.xml user-service/pom.xml
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mkdir -p /root/.m2/repository/com \
    && cp -r /common-lib-repo/com/library /root/.m2/repository/com/library \
    && mvn -f user-service/pom.xml dependency:go-offline -q
COPY user-service/src user-service/src
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mkdir -p /root/.m2/repository/com \
    && cp -r /common-lib-repo/com/library /root/.m2/repository/com/library \
    && mvn -f user-service/pom.xml package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# su-exec drops root privileges after the entrypoint fixes /app/uploads ownership.
# UID/GID 1000 is pinned explicitly (rather than left to Alpine's auto-assigned
# system UID) so it's guaranteed to match admin-service's "spring" user, since
# both independently-built images share the same /app/uploads volume.
RUN apk add --no-cache su-exec \
    && addgroup -g 1000 -S spring && adduser -u 1000 -S spring -G spring \
    && mkdir -p /app/uploads && chown -R spring:spring /app/uploads
COPY --from=builder /build/user-service/target/*.jar app.jar
COPY user-service/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
EXPOSE 8082
ENTRYPOINT ["/docker-entrypoint.sh"]
