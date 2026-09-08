# Changelog

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
