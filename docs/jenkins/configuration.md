# Jenkins — Configuration Reference

## `ciorch()` call parameters

The global variable entry point accepts a `Map` of arguments:

| Parameter | Type | Default | Description |
|---|---|---|---|
| `configPath` | `String` | `'ciorch.yml'` | Path to config file, relative to workspace root |
| `payload` | `String` | `''` | Raw GitHub webhook JSON payload |
| `apiToken` | `String` | `''` | GitHub API token (for PR creation, status posting) |
| `apiUser` | `String` | `''` | GitHub username associated with the token |

## Custom config path

```groovy
ciorch(
    configPath: '.ciorch/pipeline.yml',
    payload:    env.GITHUB_WEBHOOK_PAYLOAD,
    apiToken:   env.GITHUB_TOKEN,
    apiUser:    env.GITHUB_USER
)
```

## Registering a custom build adapter

Custom adapters must implement `io.ciorch.build.BuildAdapter` and be available on the library classpath (e.g. in the consuming repo's `src/` directory if [library loading from SCM with `src/`](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#directory-structure) is configured).

```groovy
@Library('ci-orchestrator@main') _
import io.ciorch.core.PipelineOrchestrator
import com.example.MyCustomAdapter

// Register before calling ciorch()
PipelineOrchestrator.registerBuildAdapter('my-adapter', MyCustomAdapter)

ciorch(
    payload:  env.GITHUB_WEBHOOK_PAYLOAD,
    apiToken: env.GITHUB_TOKEN,
    apiUser:  env.GITHUB_USER
)
```

The `ciorch.yml` in the consuming repo should then set `build.adapter: my-adapter`.

## Registering a custom deploy adapter

```groovy
import io.ciorch.core.PipelineOrchestrator
import com.example.MyDeployAdapter

PipelineOrchestrator.registerDeployAdapter('my-platform', MyDeployAdapter)
```

Custom deploy adapters must implement `io.ciorch.deploy.DeployAdapter`.

## Environment variable injection

All secrets must be injected via Jenkins' `withCredentials` block. `ci-orchestrator` reads the following ambient environment variables when they are present:

| Variable | Used by | Purpose |
|---|---|---|
| `REGISTRY_USER` | `DockerAdapter` | Docker registry username |
| `REGISTRY_TOKEN` | `DockerAdapter` | Docker registry password/token |
| `SLACK_TOKEN` | `Notifier` | Slack bot token (overrides `ciorch.yml` value) |
| `GITHUB_TOKEN` | `GitOperations` | GitHub API token |
| `GITHUB_USER` | `GitOperations` | GitHub API username |

## Timeout and retry

The default command timeout is **6000 seconds (100 minutes)** per shell command, set in `SystemCall.DEFAULT_TIMEOUT`. To override for a specific call, construct a `SystemCall` directly with a custom timeout:

```groovy
// Advanced: override timeout
import io.ciorch.core.SystemCall
def system = new SystemCall(this, apiUser, apiToken, 'tmp_ciorch', env.WORKSPACE)
// system is then passed to PipelineOrchestrator constructor if constructing manually
```

## Jenkins agent requirements

`ci-orchestrator` runs shell commands directly on the Jenkins agent. The agent must have:

- `git` (any recent version)
- `docker` (if `build.docker.enabled: true`)
- `curl` (for GitHub API calls and webhook notifications)
- The build toolchain for your chosen `build.adapter` (e.g. `node`, `composer`, `go`, `mvn`, etc.)
- SSH access to deploy targets (for SSH-based deploy adapters)
