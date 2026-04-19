# `ciorch.yml` Reference

Place `ciorch.yml` at the root of any repository that consumes `ci-orchestrator`. All platforms (Jenkins, GitHub Actions, GitLab CI, Bitbucket Pipelines) read this file.

## Minimal example

```yaml
ciorch:
  version: "1"
  build:
    adapter: php
  deploy:
    adapter: wordpress
    environments:
      production:
        host: prod.example.com
        user: deploy
        path: /var/www/html
```

## Full schema

```yaml
ciorch:
  version: "1"            # required; currently always "1"

  build:
    adapter: php           # node | go | java-maven | java-gradle | dotnet | python
                           # php | rust | cpp | docker
    php_version: "8.3"     # adapter-specific version hint (node_version, go_version, etc.)
    test_command: "vendor/bin/phpunit"
    lint_command: "composer install && vendor/bin/phpcs"
    build_command: "npm run build"   # only needed when the adapter has no default build step
    docker:
      enabled: true
      registry: "ghcr.io/myorg"
      image: "my-app"
      tag: "latest"        # defaults to git sha when omitted
      build_args:          # forwarded as --build-arg
        APP_ENV: production

  deploy:
    adapter: wordpress     # wordpress | drupal | symfony | django | fastapi
                           # sugarcmm | dotnetnuke | custom
    environments:
      staging:
        host: staging.example.com
        user: deploy
        path: /var/www/staging
      production:
        host: prod.example.com
        user: deploy
        path: /var/www/html

  branching:
    strategy: gitflow      # gitflow | github-flow | trunk-based | custom
    custom_matrix: ".ciorch/matrix.yml"   # only read when strategy: custom

  notify:
    slack:
      channel: "#deployments"
      token: "${SLACK_TOKEN}"   # reference a Jenkins credential / GH secret

  security:
    sast: true
    trivy: true
    gitleaks: true

  platform:
    github_actions:
      runner: ubuntu-latest
    gitlab:
      image: "ubuntu:24.04"
    bitbucket:
      size: 2x
```

## Fields reference

### `build.adapter`

| Value | Language / toolchain |
|---|---|
| `node` | Node.js / npm / yarn |
| `php` | PHP / Composer |
| `python` | Python / pip / uv |
| `go` | Go modules |
| `java-maven` | Java + Maven |
| `java-gradle` | Java + Gradle |
| `dotnet` | .NET / C# |
| `rust` | Rust / Cargo |
| `cpp` | C/C++ / CMake |
| `docker` | Generic Docker build (default when no adapter specified) |

### `deploy.adapter`

| Value | Target |
|---|---|
| `wordpress` | WordPress via WP-CLI + rsync |
| `drupal` | Drupal via Drush + rsync |
| `symfony` | Symfony via Deployer.php |
| `django` | Django via SSH + manage.py |
| `fastapi` | FastAPI via SSH + uvicorn restart |
| `sugarcrmm` | SugarCRM / SuiteCRM |
| `dotnetnuke` | DNN / DotNetNuke |
| `custom` | User-supplied adapter class |

### `branching.strategy`

| Value | Description |
|---|---|
| `gitflow` | Classic GitFlow: feature → sprint → release → master (default) |
| `github-flow` | Simplified: feature branches → main |
| `trunk-based` | Short-lived branches → trunk/main |
| `custom` | Load rules from `branching.custom_matrix` path |

See [git-action-matrix.md](jenkins/git-action-matrix.md) for the rule format.

### `security` flags

| Flag | Tool | What it does |
|---|---|---|
| `sast` | Language-native SAST | Static analysis for code vulnerabilities |
| `trivy` | Aqua Trivy | Container image + filesystem vulnerability scan |
| `gitleaks` | Gitleaks | Secret / credential leak detection |
