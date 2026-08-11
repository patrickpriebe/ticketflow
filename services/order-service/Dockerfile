# Build context is the REPOSITORY ROOT, not this directory:
#   docker build -f services/order-service/Dockerfile .
# The build needs contracts/openapi/, which the pom copies into the jar.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies first: this layer is only invalidated when the pom changes, so
# ordinary source edits do not re-download the world.
COPY services/order-service/pom.xml ./services/order-service/pom.xml
RUN mvn -B -f services/order-service/pom.xml -q dependency:go-offline

COPY contracts ./contracts
COPY services/order-service/src ./services/order-service/src
# Tests are not run here. They run in CI, where Docker is available for
# Testcontainers - a build image is the wrong place for integration tests.
RUN mvn -B -f services/order-service/pom.xml -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run as root.
RUN addgroup -S ticketflow && adduser -S ticketflow -G ticketflow
COPY --from=build /workspace/services/order-service/target/order-service-*.jar app.jar
USER ticketflow

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
