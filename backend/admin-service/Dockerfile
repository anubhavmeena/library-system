# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY common-lib/pom.xml common-lib/pom.xml
COPY common-lib/src common-lib/src
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f common-lib/pom.xml install -DskipTests -q
COPY admin-service/pom.xml admin-service/pom.xml
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f admin-service/pom.xml dependency:go-offline -q
COPY admin-service/src admin-service/src
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f admin-service/pom.xml package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# su-exec drops root privileges after the entrypoint fixes /app/uploads ownership.
# UID/GID 1000 is pinned explicitly (rather than left to Alpine's auto-assigned
# system UID) so it's guaranteed to match user-service's "spring" user, since
# both independently-built images share the same /app/uploads volume.
RUN apk add --no-cache su-exec \
    && addgroup -g 1000 -S spring && adduser -u 1000 -S spring -G spring \
    && mkdir -p /app/uploads && chown -R spring:spring /app/uploads
COPY --from=builder /build/admin-service/target/*.jar app.jar
COPY admin-service/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
EXPOSE 8086
ENTRYPOINT ["/docker-entrypoint.sh"]
