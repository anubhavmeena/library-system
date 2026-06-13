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
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /build/admin-service/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
