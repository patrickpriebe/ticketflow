# Build context is the REPOSITORY ROOT:
#   docker build -f services/notification-service/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY services/notification-service/pom.xml ./services/notification-service/pom.xml
RUN mvn -B -f services/notification-service/pom.xml -q dependency:go-offline

COPY services/notification-service/src ./services/notification-service/src
RUN mvn -B -f services/notification-service/pom.xml -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S ticketflow && adduser -S ticketflow -G ticketflow
COPY --from=build /workspace/services/notification-service/target/notification-service-*.jar app.jar
USER ticketflow

EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
