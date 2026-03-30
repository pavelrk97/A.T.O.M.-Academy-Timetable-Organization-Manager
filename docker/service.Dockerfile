FROM maven:3.9.9-eclipse-temurin-21 AS build

ARG MODULE_PATH
ARG MODULE_ARTIFACT

WORKDIR /build

COPY pom.xml ./
COPY identity-service identity-service
COPY import-service import-service
COPY schedule-api-service schedule-api-service
COPY schedule-import-parser-core schedule-import-parser-core

RUN mvn -B -pl ${MODULE_PATH} -am clean package -DskipTests
RUN find "/build/${MODULE_PATH}/target" -maxdepth 1 -name "${MODULE_ARTIFACT}-*.jar" ! -name "*original*.jar" -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /tmp/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

