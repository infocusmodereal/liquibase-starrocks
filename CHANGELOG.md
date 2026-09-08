# Changelog

## 0.3.0 (unreleased)

- Reject silent `modifyDataType` operations with actionable validation errors.
- Add `createStarRocksTable` for explicit PRIMARY/DUPLICATE OLAP layouts.
- Probe actual StarRocks identity/version and support custom JDBC ports.
- Respect Liquibase metadata catalog/table names and escaped changeset identity.
- Make explicit lock recovery idempotent and reset cached initialization state.
- Avoid clearing existing lock rows during initialization; exercise competing processes.
- Add metadata replication configuration and validate scalar type parameters.
- Discover PRIMARY keys for snapshots and stop querying unsupported constraints.
- Select a single changeset identity when tagging tied timestamps/orders.
- Add an isolated official Liquibase harness and expanded capability/version matrix.
- Separate thin/distribution JAR outputs and prepare signed bundles without
  command-line credentials or automatic publication.
- Add user, contributor, upgrade, support and release documentation.

Compatibility candidates are still under validation. See `COMPATIBILITY.md`;
this section does not announce a published release.

## 0.2.0 (2026-09-08)

- Compile against Liquibase 5.0.3 and use its inherited lock-table discovery
  instead of overriding the method removed from the newer API.
- Retain runtime regression coverage for 4.23.0, 4.29.1 and 5.0.3.

## 0.1.3

- Skip normal lock release when this service did not acquire the lock, while
  preserving explicit abandoned-lock recovery through `release-locks`.
  Based on the diagnosis and guard proposed by Ashok S (@savdev2026) in
  [PR #5](https://github.com/infocusmodereal/liquibase-starrocks/pull/5).
- Replace the checksum update placeholder with the actual Liquibase checksum
  and correctly escape the stored changeset identity.
- Add unit tests and a Docker/CI matrix for Liquibase 4.23.0, 4.29.1 and 5.0.3.
- Build the integration runner without local publishing files, select the
  exact generated JAR, and enable logs to catch failures despite exit code 0.
- Target JVM 17 explicitly when compiling Kotlin on JDK 21; retain Liquibase
  4.23.0 as the compile API. See `COMPATIBILITY.md` for runtime test boundaries.
