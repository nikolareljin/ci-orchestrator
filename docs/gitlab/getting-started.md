# GitLab CI — Getting Started

> **Status:** Phase 5 — not yet implemented. This document describes the planned integration.

## Planned approach

`ci-orchestrator` will ship a set of GitLab CI component templates under `gitlab/`:

```
gitlab/
├── ci/
│   ├── base.yml          # hidden job template (.ciorch-base)
│   ├── deploy.yml        # deploy job
│   └── pr-gate.yml       # PR quality gate
├── presets/
│   ├── node.yml
│   ├── php.yml
│   ├── python.yml
│   ├── go.yml
│   └── java.yml
├── security/
│   ├── trivy.yml
│   ├── gitleaks.yml
│   └── sast.yml
└── deploy/
    ├── wordpress.yml
    ├── drupal.yml
    ├── symfony.yml
    ├── django.yml
    └── fastapi.yml
```

## Planned usage

```yaml
# .gitlab-ci.yml
include:
  - project: nikolareljin/ci-orchestrator
    ref: main
    file: gitlab/presets/php.yml
  - project: nikolareljin/ci-orchestrator
    ref: main
    file: gitlab/deploy/wordpress.yml

variables:
  CIORCH_DEPLOY_ENV: staging
```

## Timeline

GitLab CI template implementation is planned for Phase 5. Follow the [GitHub repository](https://github.com/nikolareljin/ci-orchestrator) for updates.
