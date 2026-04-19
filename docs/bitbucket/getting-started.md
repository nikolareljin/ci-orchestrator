# Bitbucket Pipelines — Getting Started

> **Status:** Phase 6 — not yet implemented. This document describes the planned integration.

## Planned approach

`ci-orchestrator` will ship Bitbucket Pipes — containerised shell scripts that can be called from any `bitbucket-pipelines.yml`:

```
bitbucket/
├── pipes/
│   ├── ciorch-build/     # builds any language stack
│   ├── ciorch-deploy/    # deploys to any supported CMS/framework
│   └── ciorch-sast/      # runs Trivy + Gitleaks
└── templates/
    ├── node.yml
    ├── php.yml
    ├── python.yml
    ├── go.yml
    └── deploy-wordpress.yml
```

## Planned usage

```yaml
# bitbucket-pipelines.yml
pipelines:
  pull-requests:
    '**':
      - step:
          name: Build & Test
          script:
            - pipe: docker://ghcr.io/nikolareljin/ciorch-build:latest
              variables:
                ADAPTER: php
                TEST_COMMAND: vendor/bin/phpunit
  branches:
    main:
      - step:
          name: Deploy to Production
          script:
            - pipe: docker://ghcr.io/nikolareljin/ciorch-deploy:latest
              variables:
                ADAPTER: wordpress
                ENVIRONMENT: production
                DEPLOY_HOST: $PROD_HOST
                DEPLOY_USER: $PROD_USER
                DEPLOY_PATH: /var/www/html
```

## Timeline

Bitbucket Pipelines pipe implementation is planned for Phase 6. Follow the [GitHub repository](https://github.com/nikolareljin/ci-orchestrator) for updates.
