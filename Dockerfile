ARG DOCKER_HUB_MIRROR=docker.m.daocloud.io
FROM ${DOCKER_HUB_MIRROR}/library/eclipse-temurin:21-jdk-jammy@sha256:8878012b286ef00032346bfbdd55b10e9f5bf923430e3a85c7c3d6e6db6f4605 AS build

WORKDIR /workspace
COPY . .
RUN --mount=type=cache,id=vgo-maven,target=/root/.m2,sharing=locked \
    chmod +x mvnw \
    && ./mvnw -B -ntp -DskipTests -pl bootstrap -am clean package

FROM ${DOCKER_HUB_MIRROR}/library/eclipse-temurin:21-jre-jammy@sha256:bce52ea7da1f72e6bf5bec505e63b6eb55ba79ad1226903579f77eab1a80139a

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl fontconfig fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 hrapp \
    && useradd --system --uid 10001 --gid hrapp --home-dir /app --shell /usr/sbin/nologin hrapp

WORKDIR /app
COPY --from=build --chown=hrapp:hrapp /workspace/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir -p /app/logs /app/.runtime/objects && chown -R hrapp:hrapp /app/logs /app/.runtime

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
