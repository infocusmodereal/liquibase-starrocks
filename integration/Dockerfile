ARG JAVA_VERSION=17
FROM eclipse-temurin:${JAVA_VERSION}-jdk-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        mariadb-client \
        unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN chmod +x gradlew && ./gradlew --no-daemon -PisRelease=false prepareIntegrationJar
COPY integration ./integration
