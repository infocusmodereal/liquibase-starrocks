# Compatibility and validation

Validation on 2026-09-08 for the released 0.1.3 changes. These results
do not apply retroactively to the published 0.1.2 artifact.

| Liquibase runtime | StarRocks | Integration JDK | Unit tests | Integration |
| --- | --- | --- | --- | --- |
| 4.23.0 | 3.1.7 | 17 | 11 passed | Passed |
| 4.29.1 | 4.1.1 | 21 | 11 passed | Passed |
| 5.0.3 | 4.1.1 | 21 | 11 passed | Passed |

Unit tests ran locally on OpenJDK 17 against each selected Liquibase runtime.
Integration ran in native ARM64 Docker containers with Connector/J 8.4.0.
The CI workflow repeats the matrix on Linux using the JDK in each row.

Integration covers a fresh migration, two no-op updates, one changelog row,
`clear-checksums` followed by recalculation and validation, explicit recovery
of an abandoned lock from a new process, and an update after recovery. Unit
tests additionally cover plugin/type discovery, failed acquisition, release
errors, affected-row validation, metadata key/distribution SQL, and escaped
stored changelog identity during checksum updates.

This is bounded evidence, not full compatibility certification. Rollbacks,
diff/snapshot generation, every change type, multi-node failover and concurrent
distributed migrations are outside this matrix. Lock ownership isolation was
tested at the executor boundary, not under a multi-process stress workload.

## Runtime versus compile dependency in 0.1.3

Version 0.1.3 compiles against Liquibase 4.23.0. Tests and CLI
integration successfully ran that compiled extension with 5.0.3, but directly
changing the compile dependency to 5.0.3 fails because
`hasDatabaseChangeLogLockTable` no longer overrides a superclass method.
Version 0.2.0 addresses that source/API migration by removing the obsolete
override and using core lock-table discovery.
Do not interpret the 0.1.3 matrix as approval to simply replace its compile
dependency version.

## 0.2.0 release

The compile API and default test runtime are now Liquibase 5.0.3. The test
compile API remains 4.23.0 to retain tests that also exercise the oldest
supported runtime; `-PtestLiquibaseVersion` selects the actual test runtime.
The CI matrix builds the new source against 5.0.3 and exercises all three
runtimes with real StarRocks. All three combinations passed on the merged
0.2.0 source in [GitHub Actions](https://github.com/infocusmodereal/liquibase-starrocks/actions/runs/34263979486).
The exact signed release JAR additionally passed local integration with
Liquibase 5.0.3, StarRocks 4.1.1 and JDK 21. The local matrix above records
the initial 0.1.3 validation; the same matrix was repeated in CI for 0.2.0.

[Liquibase 5.0.3 release notes](https://github.com/liquibase/liquibase/releases/tag/v5.0.3)
and its [build configuration](https://github.com/liquibase/liquibase/blob/v5.0.3/pom.xml)
were reviewed. The release targets Java 17 bytecode; integration here uses 21.

## Regressions demonstrated

- Published extension 0.1.2 with Liquibase 4.29.1 / StarRocks 4.1.1 / JDK 21:
  the second update produced `SEVERE: Could not release lock` despite a success
  exit code. The harness failed as intended with explicit INFO logging.
- Before the lock fix, three lock lifecycle unit tests failed. With only the
  PR #5 guard, two recovery tests failed. The completed fix passes all seven
  lock lifecycle tests, including propagation of real failures.
- The checksum regression test failed on the previous literal `8:new_checksum`
  and passes with a real checksum and the escaped stored changeset identity.

The exact StarRocks 4.1.0 image from #6 failed during BE startup, on both
attempted architectures. StarRocks documents this image problem and its fix
in 4.1.1 in the [4.1 release notes](https://docs.starrocks.io/releasenotes/release-4.1/).
Therefore the database-backed reproduction uses 4.1.1, not the exact reported
database version. Both published and patched extensions used that same server
version for the comparison.

## Evidence and release status

Detailed local logs and before/after reports are under ignored
`integration/.cache/review/`. CI uploads test reports and integration logs.
No publishing credentials are required for these checks.

Version 0.1.3 was published to Maven Central on 2026-09-08. The public plugin
JAR, sources JAR, Javadoc JAR and POM were downloaded and their SHA-256 hashes
matched the signed release bundle. The exact signed plugin JAR also passed
the migration, checksum and abandoned-lock recovery integration test before
publication. See the [0.1.3 release](https://github.com/infocusmodereal/liquibase-starrocks/releases/tag/v0.1.3).

Version 0.2.0 was also published to Maven Central and GitHub on 2026-09-08.
Its four public artifacts were downloaded and matched the SHA-256 hashes of
the signed bundle. See the [0.2.0 release](https://github.com/infocusmodereal/liquibase-starrocks/releases/tag/v0.2.0)
and its attached verification record for the source commit and artifact hashes.

Local development builds on the 0.3 branch default to 0.3.0-SNAPSHOT.
Published 0.2.0 is the separate signed release, built with
`-PbaseVersion=0.2.0 -PisRelease=true`.

## Versioned compatibility registry

[compatibility/releases.json](compatibility/releases.json) records the exact
0.2.0 combinations above, including whether evidence applies to source-built
code or the signed release artifact. Earlier 0.1.x evidence remains in this
document; the initial JSON registry does not reconstruct missing provenance.

A passing row covers only its named scenarios, runtime, driver, JDK,
architecture and deployment topology. It does not imply support for every
patch in a version family. A source-built CI pass is not an artifact test.
Missing image digests and local reports are identified explicitly.

The [0.3 proposal](docs/roadmap-0.3.md) defines candidate versions, capability
coverage, evidence requirements and the proposed support policy. All new
combinations remain untested until actual results are recorded. No 0.3 release
has been published or certified by this planning change.
