# ci-orchestrator

Multi-platform CI/CD orchestration library for Jenkins, GitHub Actions, GitLab CI, and Bitbucket Pipelines.

Supports building and deploying any language stack (Node, Go, Java, PHP, Python, C#, Rust, C++) to any CMS or framework (WordPress, Drupal, Symfony, Django, FastAPI, and more).

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Phase](https://img.shields.io/badge/phase-1%20Jenkins%20Core-blue)
![Build](https://img.shields.io/badge/build-passing-brightgreen)

## Platforms

| Platform | Status |
|---|---|
| Jenkins | 🚧 Phase 1 |
| GitHub Actions | 🔜 Phase 4 |
| GitLab CI | 🔜 Phase 5 |
| Bitbucket Pipelines | 🔜 Phase 6 |

## Quick Start (Jenkins)

```groovy
@Library('ci-orchestrator@production') _

ciorch {
    build {
        adapter = 'node'
        nodeVersion = '20'
        testCommand = 'npm test'
        buildCommand = 'npm run build'
    }
}
```

## Configuration

Place `ciorch.yml` at the root of your consuming repository. See [docs/ciorch-yml-reference.md](docs/ciorch-yml-reference.md).

## Running Tests

```bash
./gradlew :tests:unit:test
```

## Project Structure

```
ci-orchestrator/
├── src/io/ciorch/        # Groovy shared library source
├── vars/                 # Jenkins global vars (entry points)
├── resources/            # Matrix configs, schemas, templates
├── gitlab/               # GitLab CI templates and presets
├── bitbucket/            # Bitbucket Pipes and templates
├── tests/unit/           # JenkinsPipelineUnit + Spock tests
└── docs/                 # Per-platform documentation
```

## Contributing

1. Fork and clone the repo.
2. Create a feature branch from `main`.
3. Add tests for any new behaviour.
4. Open a pull request against `main`.

## License

MIT — see [LICENSE](LICENSE)
