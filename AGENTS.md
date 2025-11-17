# Repository Guidelines

## Project Structure & Module Organization
`nlp-onnx-core` hosts the public API, model abstractions, calibration, preprocessing, and ONNX Runtime implementation under `src/main/java/uk/codery/onnx/nlp/**`. Spring auto-configuration lives in `nlp-onnx-spring-boot-starter`, mirroring the same package root with `config` and `boot` packages. Tests sit beside their modules in `src/test/java`, while runnable examples live in `examples/` and reusable automation in `scripts/`. Keep large model artifacts out of Git; point configs to paths under `build/models` or a local cache instead.

## Build, Test, and Development Commands
```bash
./gradlew build                 # Compile all modules and run unit tests
./gradlew :nlp-onnx-core:test   # Targeted module tests (replace module as needed)
./gradlew publishToMavenLocal   # Install artifacts locally for downstream apps
./gradlew clean build           # Fresh build when Gradle caches look stale
```
Use `./gradlew --scan` when troubleshooting CI-only issues to capture the environment.

## Coding Style & Naming Conventions
Code targets Java 21 with 4-space indentation and braces on the same line. Public APIs stay under `uk.codery.onnx.nlp.api`; internal helpers go in `.impl` or `.support` packages to keep surface area tight. Prefer builders (e.g., `TextClassifierBuilder`) and immutable value objects; Lombok annotations (`@Value`, `@Builder`, `@Slf4j`) are available project-wide. Classes follow `NlpFeatureName` casing, and tests mirror class names plus the `Test` suffix. Keep method names descriptive verbs (`warmupClassifier`, `loadModelBundle`).

## Testing Guidelines
JUnit 5 with AssertJ and Mockito is preconfigured via the shared `build.gradle.kts`. Match the naming pattern `shouldDescribeBehavior` for test methods and group edge cases in nested `@Nested` classes when clarity demands it. Aim to cover preprocessing, tokenizer, calibration, and Spring wiring paths; add regression tests for any bug fix touching inference flow. Run `./gradlew test` before opening a PR, and prefer module-scoped runs for faster iteration.

## Commit & Pull Request Guidelines
The repository is a fresh initialization, so establish clean history from day one: write imperative, present-tense commit subjects under ~60 chars (e.g., `Add basic temperature calibration`). Describe rationale and validation in the body when useful. Pull requests should link issues (if any), summarize API or behavior changes, enumerate new commands/config, and attach relevant screenshots or JSON samples for tooling changes. Always mention how you tested (`./gradlew build`, targeted tests, manual classifier run) so reviewers can reproduce quickly.

## Configuration & Model Tips
Keep reusable configs in `docs/` or `examples/` and reference them via relative paths inside tests. When checking in ONNX models for docs, strip to tiny fixtures (<1 MB) to keep the repo lean; larger assets belong in an artifact registry. Document any new configuration keys in `README.md` and, if Spring-related, update the properties table in `docs/` to keep Boot users unblocked.
