# Publishing to Maven Central (Central Portal API)

This project publishes via the Central Portal API by uploading a signed bundle.
Keep secrets out of git; `gradle.properties` and `secret-key.asc` are ignored.

## Prerequisites
- Verified namespace for `io.github.infocusmodereal` in the Central Portal.
- Central Portal **user token** (username + password) from
  `https://central.sonatype.com/usertoken`.
- GPG signing key with matching `secret-key.asc`.

## 1) Configure secrets (local only)
Option A: `gradle.properties` (gitignored)
```
centralToken=BASE64(username:password)
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSPHRASE
```

Option B: environment variables
```
export CENTRAL_TOKEN="$(printf "%s:%s" "$CENTRAL_USER" "$CENTRAL_PASS" | base64 | tr -d '\n')"
```

## 2) Build the Central bundle
Local JDK 17 + gpg:
```
./gradlew clean shadowJar sourcesJar javadocJar generatePomFileForMavenPublication createCentralBundle \
  -PbaseVersion=0.2.0 -PisRelease=true
```

If you do not have a local JDK/gpg, use Docker:
```
docker run --rm -v "$PWD:/workspace" -w /workspace eclipse-temurin:17-jdk-jammy \
  bash -lc 'apt-get update && apt-get install -y --no-install-recommends gnupg \
    && rm -rf /var/lib/apt/lists/* \
    && signing_pass=$(grep "^signing.password=" gradle.properties | cut -d= -f2-) \
    && gpg --batch --yes --pinentry-mode loopback --passphrase "$signing_pass" --import secret-key.asc \
    && ./gradlew clean shadowJar sourcesJar javadocJar generatePomFileForMavenPublication createCentralBundle \
      -PbaseVersion=0.2.0 -PisRelease=true'
```

Bundle output:
```
build/distributions/central-bundle.zip
```

## 3) Upload the bundle (Central Portal API)
```
TOKEN="${CENTRAL_TOKEN:-$(grep '^centralToken=' gradle.properties | cut -d= -f2-)}"
curl --request POST \
  --header "Authorization: Bearer ${TOKEN}" \
  --form bundle=@build/distributions/central-bundle.zip \
  "https://central.sonatype.com/api/v1/publisher/upload?name=liquibase-starrocks-0.2.0&publishingType=AUTOMATIC"
```

This returns a deployment ID.

## 4) Check deployment status
```
curl --request POST \
  --header "Authorization: Bearer ${TOKEN}" \
  "https://central.sonatype.com/api/v1/publisher/status?id=<deployment_id>"
```

If you set `publishingType=USER_MANAGED`, finalize the publish in the portal UI.

## 5) Verify in Maven Central
- `https://repo1.maven.org/maven2/io/github/infocusmodereal/liquibase-starrocks/0.2.0/`

Indexing delay is normal. Maven Central may show the artifacts quickly, but
third-party indexes (e.g. mvnrepository.com) can lag by hours or days.
