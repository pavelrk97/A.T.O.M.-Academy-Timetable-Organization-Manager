# syntax=docker/dockerfile:1.6
#
# Сборка Java-сервиса в две стадии. Главная оптимизация — BuildKit cache mount
# на /root/.m2: Maven переиспользует загруженные зависимости между сборками
# (и между разными MODULE_PATH, потому что cache mount шарится между Dockerfile'ами
# в рамках одного daemon'а). Раньше каждая из 4 параллельных сборок качала ~/.m2
# с нуля, на медленном канале это занимало 10+ минут; теперь — секунды после
# первой «прогревочной» сборки.
#
# Второй приём: ДО COPY исходников копируем только pom.xml'ы и делаем
# dependency:go-offline. Этот слой Docker'а кешируется, пока pom.xml не меняется,
# и при любой правке исходников Maven уже не лезет в сеть.

FROM maven:3.9.9-eclipse-temurin-21 AS build

ARG MODULE_PATH
ARG MODULE_ARTIFACT

WORKDIR /build

# 1) Только poms — для кеша зависимостей. Этот слой инвалидируется только
#    при изменении любого pom.xml. Изменения в src/ его не трогают.
COPY pom.xml ./
COPY identity-service/pom.xml identity-service/pom.xml
COPY import-service/pom.xml import-service/pom.xml
COPY schedule-api-service/pom.xml schedule-api-service/pom.xml
COPY schedule-import-parser-core/pom.xml schedule-import-parser-core/pom.xml

# 2) Прогрев зависимостей — качаем всё, что нужно для сборки конкретного модуля,
#    в кеш ~/.m2. Cache mount сохраняет /root/.m2 между сборками одного daemon'а.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl ${MODULE_PATH} -am dependency:go-offline -Dmaven.test.skip=true || true

# 3) Теперь исходники. Этот слой инвалидируется при правке кода, но зависимости
#    уже лежат в /root/.m2 — повторных скачиваний не будет.
COPY identity-service identity-service
COPY import-service import-service
COPY schedule-api-service schedule-api-service
COPY schedule-import-parser-core schedule-import-parser-core

# 4) Сама сборка с тем же cache mount.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl ${MODULE_PATH} -am clean package -Dmaven.test.skip=true

RUN find "/build/${MODULE_PATH}/target" -maxdepth 1 -name "${MODULE_ARTIFACT}-*.jar" ! -name "*original*.jar" -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /tmp/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
