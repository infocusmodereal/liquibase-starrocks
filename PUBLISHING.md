# Publishing a release

Publication is a separate maintainer action after the release candidate passes
[compatibility validation](COMPATIBILITY.md). Building a bundle never uploads it.

## Prerequisites

Use JDK 17 and the Gradle wrapper. The maintainer needs a verified
`io.github.infocusmodereal` namespace in the [Central Portal](https://central.sonatype.com/).
Normal builds and tests need no publishing credentials.

For signing, place the private key in ignored `secret-key.asc` and its passphrase
in the ignored `gradle.properties` property `signing.password`. Gradle signs in
memory. Never copy either file into an integration container, source bundle or
issue report. Neither GPG command-line arguments nor a local GPG installation
are required.

## Prepare the candidate

From the reviewed release commit, run:

```bash
./gradlew --no-daemon -PbaseVersion=0.3.0 -PisRelease=true clean test createCentralBundle
```

The task fails if any of the four publication artifacts or signatures is missing.
Verify every signature and checksum (a public keyring also works):

```bash
python3 scripts/verify-release.py --key secret-key.asc --directory build/central/io/github/infocusmodereal/liquibase-starrocks/0.3.0
```

The verifier reads only public key packets and uses the PGP libraries from the
pinned Gradle distribution. Run it with Java 17 or newer. It never decrypts or
prints the signing key.

Inspect `build/distributions/liquibase-starrocks-0.3.0-central.zip`. It contains
JAR, sources, Javadoc and POM, their signatures, and MD5/SHA-1/SHA-256/SHA-512
checksums. Only the Kotlin runtime is bundled; the host supplies Liquibase and
MySQL Connector/J. The thin development JAR has a separate classifier.

## Validate before publication

- Run the integration matrix against this exact signed JAR using `INTEGRATION_JAR`.
- Run the upstream harness, unit matrix, documentation and artifact checks.
- Record the source commit, artifact hash, exact runtime/server tuple, image
  digest, architecture, scenarios and evidence URLs for each passing case.
- Recheck changes from 0.2.0 and document any unsupported or blocked combinations.
- Create release notes and preserve the tested bundle without rebuilding it.

## Publish and verify

Upload the validated bundle through the Central Portal, inspect validation,
and publish that deployment. Create the matching Git tag and GitHub release
from the recorded source commit. Attach the same JARs and verification record.

Download all four public files from Maven Central and compare SHA-256 with the
validated bundle. Record the publication status separately from candidate test
status. Indexing delays do not constitute a failed upload; inspect the existing
deployment before retrying. Never publish a different bundle under the same version.

The previous command-line `uploadToCentral` and passphrase-based checksum tasks
were removed in 0.3 because they placed authentication material in process
arguments and verbose output. Use the staged bundle workflow above.
