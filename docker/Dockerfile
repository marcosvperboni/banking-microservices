# Generic multi-stage build for any module in this Maven reactor.
# Usage: podman build --build-arg MODULE=account-service -f docker/Dockerfile -t account-service .
FROM maven:3.9-eclipse-temurin-25 AS build
ARG MODULE
WORKDIR /workspace
COPY pom.xml .
COPY common-security/pom.xml common-security/pom.xml
COPY customer-service/pom.xml customer-service/pom.xml
COPY account-service/pom.xml account-service/pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY common-security/src common-security/src
COPY customer-service/src customer-service/src
COPY account-service/src account-service/src
COPY transaction-service/src transaction-service/src
COPY notification-service/src notification-service/src
COPY api-gateway/src api-gateway/src
RUN mvn -B -pl ${MODULE} -am package -DskipTests -q

FROM eclipse-temurin:25-jre AS runtime
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
