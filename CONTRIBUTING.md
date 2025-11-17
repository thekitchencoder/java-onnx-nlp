# Contributing to java-onnx-nlp

Thanks for helping improve this ONNX NLP classifier toolkit! The guidelines below keep the project consistent and easy to review.

## Getting Started
- Requires Java 21+, Gradle 8.6+, and ONNX Runtime-friendly hardware.
- Clone the repository, then run `./gradlew tasks` once so Gradle downloads wrappers and plugins.
- Modules:
  - `nlp-onnx-core` — public API, model loading, calibration, preprocessing, tokenizer plumbing.
  - `nlp-onnx-spring-boot-starter` — Spring Boot auto-configuration and properties.
  - `examples/` and `docs/` provide runnable samples and reference material; `scripts/` contains training helpers (Python/uv).

## Development Workflow
1. Create a feature branch (`git checkout -b feature/short-description`).
2. Make focused changes with clear commits. Keep commit subjects imperative and under ~60 chars.
3. Run the relevant Gradle tasks:
   - `./gradlew build` — full compile + unit tests.
   - `./gradlew :module:test` — targeted tests for fast iteration.
   - `./gradlew publishToMavenLocal` — optional for testing downstream integrations.
4. Add or update tests for every behavior change (preprocessing, tokenization, calibration, or Spring wiring).
5. Update documentation (README, docs/, AGENTS.md) when APIs, config keys, or commands change.

## Coding Standards
- Java 21 syntax with 4-space indentation. Keep public interfaces under `uk.codery.onnx.nlp.api`; implementation details belong in `.impl` or module-internal packages.
- Prefer immutable value objects and builders (`TextClassifierBuilder`, `ModelConfig`); Lombok is available via `io.freefair.lombok`.
- Test classes mirror the target class name plus `Test` suffix, and methods follow `shouldDescribeBehavior`.
- Keep imports ordered (static first) and remove unused code before committing.

## Pull Requests
- Provide a clear summary, screenshots/logs when relevant, and mention how you tested (`./gradlew build`, module tests, manual classifier run, etc.).
- Link issues or discussions that the PR resolves.
- If the change impacts public APIs or configuration, highlight the migration notes in the PR description.

## Reporting Issues
- Include steps to reproduce, relevant logs/stack traces, Gradle/Java versions, and whether the issue appears in `nlp-onnx-core`, the Spring starter, or scripts.
- For model/asset-related bugs, share a minimal repro model if licensing permits; otherwise describe the model structure (input/output tensors, vocabulary size).

The maintainers reserve the right to close issues or PRs that are off-scope, unresponsive, or lack required details. Thank you for contributing!
