# Deploy Adapters

Deploy adapters implement the `io.ciorch.deploy.DeployAdapter` interface and provide platform-specific pre-deploy, deploy, post-deploy, and rollback steps.

## Available adapters

| Adapter ID | Target | Phase |
|---|---|---|
| `wordpress` | WordPress via WP-CLI + rsync | 🔜 Phase 3 |
| `drupal` | Drupal via Drush + rsync | 🔜 Phase 3 |
| `symfony` | Symfony via Deployer.php | 🔜 Phase 3 |
| `django` | Django via SSH + manage.py | 🔜 Phase 3 |
| `fastapi` | FastAPI via SSH + uvicorn | 🔜 Phase 3 |
| `sugarcrmm` | SugarCRM / SuiteCRM | 🔜 Phase 3 |
| `dotnetnuke` | DNN / DotNetNuke | 🔜 Phase 3 |

## `DeployAdapter` interface

```groovy
interface DeployAdapter {
    /** Validate config and connectivity before deploying. */
    boolean validate(Map deployConfig, def context)

    /** Pre-deployment: maintenance mode, cache flush, DB backup. */
    boolean preDeploy(Map deployConfig, String environment)

    /** Main deployment: file sync, migrations, service restart. */
    boolean deploy(Map deployConfig, String environment)

    /** Post-deployment: cache warm, smoke test, maintenance mode off. */
    boolean postDeploy(Map deployConfig, String environment)

    /** Roll back to the previous state on failure. */
    boolean rollback(Map deployConfig, String environment)

    /** Adapter ID — must match ciorch.yml deploy.adapter value. */
    String getAdapterId()
}
```

## Environment resolution

When the pipeline runs a `deploy` task, the orchestrator resolves the target environment from the event context:

- A PR merged into `master`/`main` deploys to `production`
- A PR merged into `env_prod` deploys to `production`
- A PR merged into `env_qa` deploys to `staging`
- A PR merged into `env_dev` deploys to `development`

Override this mapping by adjusting the matrix rules (see [git-action-matrix.md](git-action-matrix.md)) or by specifying environment keys in `ciorch.yml` that match your branch naming.

## Environment configuration

```yaml
ciorch:
  deploy:
    adapter: wordpress
    environments:
      development:
        host: dev.example.com
        user: deploy
        path: /var/www/dev
        wp_cli: /usr/local/bin/wp
      staging:
        host: staging.example.com
        user: deploy
        path: /var/www/staging
      production:
        host: prod.example.com
        user: deploy
        path: /var/www/html
```

## Writing a custom deploy adapter

```groovy
// src/com/example/K8sDeployAdapter.groovy
package com.example

import io.ciorch.deploy.DeployAdapter
import java.io.Serializable

class K8sDeployAdapter implements DeployAdapter, Serializable {

    def context
    def system
    def config

    K8sDeployAdapter(def context, def system, def config) {
        this.context = context
        this.system  = system
        this.config  = config
    }

    @Override boolean validate(Map deployConfig, def ctx) {
        return system.run_command('kubectl version --client', 0) != null
    }

    @Override boolean preDeploy(Map deployConfig, String environment) { true }

    @Override boolean deploy(Map deployConfig, String environment) {
        Map env = deployConfig.environments?."${environment}" ?: [:]
        String namespace = env.namespace ?: 'default'
        String image     = config.dockerImage ?: 'app'
        context.withEnv(["CIORCH_NS=${namespace}", "CIORCH_IMG=${image}"]) {
            system.run_command(
                'kubectl set image deployment/app app="$CIORCH_IMG" -n "$CIORCH_NS"', 1
            )
        }
        return true
    }

    @Override boolean postDeploy(Map deployConfig, String environment) { true }
    @Override boolean rollback(Map deployConfig, String environment)   { true }
    @Override String  getAdapterId() { 'k8s' }
}
```

Register it in your `Jenkinsfile`:

```groovy
@Library('ci-orchestrator@main') _
import io.ciorch.core.PipelineOrchestrator
import com.example.K8sDeployAdapter

PipelineOrchestrator.registerDeployAdapter('k8s', K8sDeployAdapter)
ciorch(payload: env.PAYLOAD, apiToken: env.GITHUB_TOKEN)
```

```yaml
ciorch:
  deploy:
    adapter: k8s
    environments:
      production:
        namespace: prod
```
