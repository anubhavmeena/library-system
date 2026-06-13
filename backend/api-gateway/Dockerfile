# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY api-gateway/pom.xml api-gateway/pom.xml
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f api-gateway/pom.xml dependency:go-offline -q
COPY api-gateway/src api-gateway/src
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f api-gateway/pom.xml package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /build/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
