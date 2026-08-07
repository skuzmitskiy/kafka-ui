FROM node:16.15.0-alpine AS frontend
# openapi-generator-cli downloads a JAR and needs a JRE
RUN apk add --no-cache openjdk17-jre-headless \
    && npm install -g pnpm@7.4.0 \
    && pnpm --version
WORKDIR /build
# openapitools.json resolves the spec via ../kafka-ui-contract/...
COPY kafka-ui-contract/src/main/resources/swagger/kafka-ui-api.yaml \
     kafka-ui-contract/src/main/resources/swagger/kafka-ui-api.yaml
WORKDIR /build/kafka-ui-react-app
COPY kafka-ui-react-app/package.json kafka-ui-react-app/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY kafka-ui-react-app/ ./
RUN pnpm gen:sources \
    && pnpm build

FROM maven:3.8-eclipse-temurin-17 AS backend
# Российское зеркало Maven Central (GitVerse). Переопределение:
#   docker build --build-arg MAVEN_MIRROR_URL=https://your-mirror ...
ARG MAVEN_MIRROR_URL=https://mvn-mirror.gitverse.ru
WORKDIR /build
COPY deploy/docker/maven-settings.xml /tmp/maven-settings.xml
RUN mkdir -p /root/.m2 \
    && sed "s|__MAVEN_MIRROR_URL__|${MAVEN_MIRROR_URL}|g" /tmp/maven-settings.xml \
       > /root/.m2/settings.xml \
    && echo "Using Maven mirror: ${MAVEN_MIRROR_URL}"
ENV MAVEN_OPTS="-Dhttps.protocols=TLSv1.2"
# Не используем dependency:go-offline: при только pom.xml он пытается
# скачать локальные reactor-модули (kafka-ui-contract) как внешние артефакты.
COPY pom.xml ./
COPY kafka-ui-contract/ kafka-ui-contract/
COPY kafka-ui-serde-api/ kafka-ui-serde-api/
COPY kafka-ui-api/ kafka-ui-api/
# Parent POM lists kafka-ui-e2e-checks; needed for reactor resolution only.
COPY kafka-ui-e2e-checks/pom.xml kafka-ui-e2e-checks/pom.xml
COPY --from=frontend /build/kafka-ui-react-app/build kafka-ui-react-app/build
COPY etc/ etc/
RUN mvn -B -s /root/.m2/settings.xml -pl kafka-ui-api -am -Pprod -DskipTests -DskipUIBuild=true \
    -Ddocker.skip=true package

FROM azul/zulu-openjdk-alpine:17
RUN apk add --no-cache gcompat \
    && addgroup -S kafkaui \
    && adduser -S kafkaui -G kafkaui \
    && mkdir -p /etc/kafkaui \
    && chown -R kafkaui:kafkaui /etc/kafkaui
COPY --from=backend /build/kafka-ui-api/target/kafka-ui-api-*.jar /kafka-ui-api.jar
USER kafkaui
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/kafka-ui-api.jar"]
