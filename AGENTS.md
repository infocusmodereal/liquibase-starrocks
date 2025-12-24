# AGENTS.md

These instructions apply to all agents working in this repository.

## Workflow
- Keep changes minimal and focused; avoid drive-by refactors.
- Prefer small, reviewable diffs; explain any necessary complexity.
- Update docs/tests when behavior changes or new features are added.
- Do not remove or rewrite existing content unless explicitly requested.

## Commits
- Use Conventional Commits for commit messages (e.g. `feat: ...`, `fix: ...`, `chore: ...`).
- Keep commit subjects imperative and under 72 characters when possible.

## Code Quality
- Follow existing Kotlin/Gradle conventions in the project.
- Avoid introducing new dependencies unless required; justify them in the PR/notes.
- Add or update tests for new behavior when reasonable.

## Useful Commands
- Build: `./gradlew build shadowJar`
- Test: `./gradlew test`
