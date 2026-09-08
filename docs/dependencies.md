# Dependencies

| Component | Role |
| --- | --- |
| Liquibase core 5.0.3 | Compile API; supplied by CLI or embedded host. Additional runtimes are tested separately. |
| Kotlin 1.8.0 standard library | Included in the distribution JAR. |
| MySQL Connector/J 8.4.0 | Separate JDBC runtime dependency; excluded from the shaded JAR. |
| JUnit Jupiter 5.10.0 / Mockito 5.5.0 | Unit tests only. |
| Liquibase Test Harness 1.0.12 | Isolated integration source set; compile API 5.0.3, Java 17. |
| Groovy 5.0.6 / Spock 2.4 / JUnit 6.1.0 | Harness execution only. |

The harness is test-only and has its own upstream FSL-1.1-ALv2 license. Its
unneeded database drivers are excluded by using explicit dependencies. Review
upstream license notices when changing dependencies; the project's Apache-2.0
license does not replace dependency licenses. The production POM records
runtime dependencies, and `scripts/verify-project.py` checks the distribution
for accidentally bundled core/JDBC classes or missing service providers.
