# Build context is the REPOSITORY ROOT:
#   docker build -f services/payment-service/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY services/payment-service/pom.xml ./services/payment-service/pom.xml
RUN mvn -B -f services/payment-service/pom.xml -q dependency:go-offline

COPY services/payment-service/src ./services/payment-service/src
# Tests run in CI, not in a build image.
RUN mvn -B -f services/payment-service/pom.xml -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S ticketflow && adduser -S ticketflow -G ticketflow
COPY --from=build /workspace/services/payment-service/target/payment-service-*.jar app.jar
USER ticketflow

EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
