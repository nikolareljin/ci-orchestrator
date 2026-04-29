# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-04-19 (post-review fixes)

### Fixed (post-review)
- `PipelineOrchestrator` — pass `config.buildMap()` into `prepare()`, `lint()`, `test()`, and `build()` so adapter behavior is driven by `ciorch.yml` and Jenkinsfile overrides rather than an empty map
- `vars/ciorch.groovy` — apply `lint_command`, `test_command`, `build_command`, and `*_version` keys from `args` into `Config` so preset-var overrides work end-to-end
- `PhpAdapter.lint()` — replace GNU-only `xargs -r` with portable `find -exec sh -c 'for f do ...' sh {} +` so PHP lint works on macOS/BSD agents
- `PipelineOrchestratorTest` — added `cleanup()` to remove the `"counting"` registry entry after each spec, preventing order-dependent test failures

## [0.2.0] - 2026-04-19

### Added
- `NodeAdapter` — Node.js / npm / yarn / pnpm build adapter with lint, test, build steps
- `GoAdapter` — Go modules adapter; runs `go vet`, `golangci-lint` (optional), `go test`, `go build`
- `PhpAdapter` — PHP / Composer adapter; phpcs/php -l lint, phpunit test, composer install build
- `PythonAdapter` — Python adapter with pip / poetry / uv auto-detection; ruff/flake8 lint, pytest test
- `CSharpAdapter` — .NET adapter; `dotnet format --verify-no-changes` lint, `dotnet test`, `dotnet publish`
- `RustAdapter` — Cargo adapter; `cargo clippy`, `cargo fmt --check`, `cargo test`, `cargo build --release`
- `JavaAdapter` — Maven/Gradle auto-detection; checkstyle lint, mvn/gradle test and package
- `CppAdapter` — CMake + make/ninja adapter; cppcheck lint, cmake configure + build
- `GenericAdapter` — arbitrary shell-command adapter driven entirely by `ciorch.yml` config keys
- 8 preset `vars/ciorch_*.groovy` entry points (node, go, java, php, python, csharp, rust, cpp)
- 8 `resources/matrix/*-standard.yml` presets wired to the new adapters
- All 9 adapters registered in `PipelineOrchestrator.BUILD_REGISTRY`
- 177 new unit tests (261 total verified by CI, up from 81 in 0.1.0)
- `PipelineOrchestratorTest` — 3 focused tests covering adapter caching (`prepare()` called once per run) and prepare() failure aborting build steps

### Fixed
- `WebhookParser.processJob()` — plain push events to allowed branches (develop, master, release, etc.) now set `shouldBeProcessed = true`; previously only forced-push and PR events were gated through the allowlist
- `CppAdapter.lint()` — added `--error-exitcode=1` to cppcheck so lint fails when cppcheck reports findings (previously always exited 0)
- Preset `vars/ciorch_*.groovy` entry points — inverted map merge order (`args + [adapter:'X']`) so the preset adapter key always wins over caller-supplied overrides
- `lint_command` config key now consistent snake_case across all adapters (was `lintCommand` in NodeAdapter)
- `lint()` correctly returns `false` on failure (was silently returning `true` in NodeAdapter and PhpAdapter)
- Null-context fallback in `test()` and `build()` now invokes the resolved command directly (was using `eval "$CIORCH_CMD"` without the env var set)
- `PythonAdapter` resolves `python3` vs `python` at prepare-time and uses the detected binary as the test default
- `PythonAdapter` pip no-op path no longer reports a false `dist/` artifact

## [0.1.0] - 2026-04-19

### Added
- Initial repository scaffold with directory structure and Gradle build tooling
- `tests/unit/build.gradle` with JenkinsPipelineUnit 1.23 and Spock 2.3-groovy-3.0 dependencies
- MIT License
- README with logo, platform status table, quick-start example, and full documentation suite
- `io.ciorch.core`: `Version`, `SystemCall`, `Config`, `Notifier`, `PipelineOrchestrator`
- `io.ciorch.git`: `EventType`, `BranchType`, `TaskType`, `GitEvent`, `WebhookParser`, `MatrixLoader`, `MatrixEvaluator`, `GitOperations`
- `io.ciorch.build`: `BuildAdapter` interface, `DockerAdapter`
- `io.ciorch.deploy`: `DeployAdapter` interface
- `vars/ciorch.groovy` Jenkins shared library entry point
- `resources/matrix/default-gitflow.yml`, `github-flow.yml`, `trunk-based.yml`
- 81 unit tests (Spock + JenkinsPipelineUnit)

---

<!-- Template for future entries:

## [X.Y.Z] - YYYY-MM-DD

### Added
- ...

### Changed
- ...

### Fixed
- ...

### Removed
- ...

-->
