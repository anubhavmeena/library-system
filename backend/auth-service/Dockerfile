# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY common-lib/pom.xml common-lib/pom.xml
COPY common-lib/src common-lib/src
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f common-lib/pom.xml install -DskipTests -q
COPY auth-service/pom.xml auth-service/pom.xml
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f auth-service/pom.xml dependency:go-offline -q
COPY auth-service/src auth-service/src
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f auth-service/pom.xml package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /build/auth-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
