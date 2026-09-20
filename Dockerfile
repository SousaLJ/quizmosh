# syntax=docker/dockerfile:1
FROM node:22-alpine AS web-build
WORKDIR /web
COPY quizmosh-web/package*.json ./
RUN npm ci --no-audit --no-fund
COPY quizmosh-web/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-25 AS java-build
WORKDIR /source
COPY pom.xml ./
COPY quizmosh-domain/ quizmosh-domain/
COPY quizmosh-application/ quizmosh-application/
COPY quizmosh-protocol/ quizmosh-protocol/
COPY quizmosh-inmemory/ quizmosh-inmemory/
COPY quizmosh-server/ quizmosh-server/
COPY --from=web-build /web/dist/ quizmosh-server/src/main/resources/static/
RUN mvn -B -ntp verify

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 quizmosh && useradd --uid 10001 --gid 10001 --no-create-home quizmosh
COPY --from=java-build --chown=10001:10001 /source/quizmosh-server/target/quizmosh-server-0.1.0-SNAPSHOT.jar /app/ludrivo.jar
RUN mkdir -p /app/data && chown 10001:10001 /app/data
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Djava.io.tmpdir=/tmp"
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/ludrivo.jar"]
