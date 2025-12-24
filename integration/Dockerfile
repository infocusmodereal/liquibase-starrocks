FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        mariadb-client \
        unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
