# Contributing

Start with [local development](DEVELOPMENT.md), [architecture](docs/architecture.md)
and the [capability contract](docs/capabilities.md). Normal builds require JDK 17
and network access on the first run, but no maintainer credentials or signing key.

## A small contribution

1. Branch from the release branch for the issue you are fixing.
2. Add a regression that fails on the previous implementation.
3. Make a focused change and explain any compatibility boundary.
4. Run unit tests, artifact/doc checks and the relevant real database scenario.
5. Update user-facing examples and release notes when behavior changes.
6. Open a PR with the trigger, resulting behavior, validation and limitations.

Use Conventional Commits, existing Kotlin conventions and small diffs. This
repository currently has no configured Kotlin formatter; avoid formatting
unrelated files. Run `git diff --check`, shell syntax checks and the project's
validation script before submitting.

## Test tiers

```bash
./scripts/dev test prepareIntegrationJar -PbaseVersion=0.3.0
python3 scripts/verify-project.py --jar build/integration/liquibase-starrocks.jar
```

For a different unit runtime add `-PtestLiquibaseVersion=5.0.4`. Docker integration
and the official harness are documented in [DEVELOPMENT.md](DEVELOPMENT.md).
The harness must execute against a disposable database; skipped tests do not
establish support. Its task is separate from ordinary unit tests.

To add a version, create an exact tuple in `compatibility/candidates.json`,
verify the image's architecture, and run the existing scenario contract.
The CI matrix reads that file. Promote a tuple to release evidence only after
its actual test results pass, with JAR hash, source commit and image digest.
Record blocked or unsupported cases explicitly.
