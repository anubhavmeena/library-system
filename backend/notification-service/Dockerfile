# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY notification-service/pom.xml notification-service/pom.xml
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f notification-service/pom.xml dependency:go-offline -q
COPY notification-service/src notification-service/src
RUN --mount=type=cache,id=maven,target=/root/.m2 \
    mvn -f notification-service/pom.xml package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=builder /build/notification-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
