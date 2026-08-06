FROM node:16-alpine AS frontend
WORKDIR /build/kafka-ui-react-app
RUN corepack enable
COPY kafka-ui-react-app/package.json kafka-ui-react-app/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY kafka-ui-react-app/ ./
RUN pnpm build

FROM maven:3.8-eclipse-temurin-17 AS backend
WORKDIR /build
COPY pom.xml ./
COPY kafka-ui-contract/pom.xml kafka-ui-contract/pom.xml
COPY kafka-ui-serde-api/pom.xml kafka-ui-serde-api/pom.xml
COPY kafka-ui-api/pom.xml kafka-ui-api/pom.xml
RUN mvn -B -pl kafka-ui-api -am dependency:go-offline
COPY kafka-ui-contract/ kafka-ui-contract/
COPY kafka-ui-serde-api/ kafka-ui-serde-api/
COPY kafka-ui-api/ kafka-ui-api/
COPY --from=frontend /build/kafka-ui-react-app/build kafka-ui-react-app/build
COPY etc/ etc/
RUN mvn -B -pl kafka-ui-api -am -Pprod -DskipTests -DskipUIBuild=true \
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
