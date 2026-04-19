# Build Adapters

Build adapters implement the `io.ciorch.build.BuildAdapter` interface and provide toolchain-specific `lint`, `test`, `build`, and `artifact` steps.

## Available adapters

| Adapter ID | Toolchain | Phase |
|---|---|---|
| `docker` | Generic Docker build | ✅ Phase 1 |
| `node` | Node.js / npm / yarn | 🔜 Phase 2 |
| `php` | PHP / Composer / PHPUnit | 🔜 Phase 2 |
| `python` | Python / pip / pytest | 🔜 Phase 2 |
| `go` | Go modules / `go test` | 🔜 Phase 2 |
| `java-maven` | Java + Maven | 🔜 Phase 2 |
| `java-gradle` | Java + Gradle | 🔜 Phase 2 |
| `dotnet` | .NET / C# / MSBuild | 🔜 Phase 2 |
| `rust` | Rust / Cargo | 🔜 Phase 2 |
| `cpp` | C/C++ / CMake | 🔜 Phase 2 |

## `BuildAdapter` interface

```groovy
interface BuildAdapter {
    /** Validate the environment (tool versions, required env vars). */
    boolean prepare(Map config, def context)

    /** Run static analysis / linting. */
    boolean lint(Map config)

    /** Run the test suite. */
    boolean test(Map config)

    /** Compile / bundle the project. */
    boolean build(Map config)

    /** Return a list of artifact paths produced by build(). */
    List<String> getArtifacts()

    /** Human-readable adapter ID (matches ciorch.yml build.adapter value). */
    String getName()
}
```

## `DockerAdapter` (Phase 1)

The only adapter available in Phase 1. It wraps `docker build`, `docker push`, and `docker tag` with shell-injection-safe env-var passing.

### Configuration

```yaml
ciorch:
  build:
    adapter: docker
    docker:
      enabled: true
      registry: "ghcr.io/myorg"
      image: "my-app"
      tag: "1.2.3"          # defaults to git SHA when omitted
      build_args:
        APP_ENV: production
        BUILD_DATE: "2024-01-01"
```

### Credential injection

`DockerAdapter.login()` reads `REGISTRY_USER` and `REGISTRY_TOKEN` from the environment. Inject them via `withCredentials` before calling `ciorch()`:

```groovy
withCredentials([
    usernamePassword(
        credentialsId: 'my-registry-cred',
        usernameVariable: 'REGISTRY_USER',
        passwordVariable: 'REGISTRY_TOKEN'
    )
]) {
    ciorch(payload: env.PAYLOAD, apiToken: env.GITHUB_TOKEN)
}
```

## Writing a custom build adapter

1. Create a Groovy class in your repo's `src/` directory.
2. Implement `io.ciorch.build.BuildAdapter`.
3. Register it before calling `ciorch()`:

```groovy
// Jenkinsfile
@Library('ci-orchestrator@main') _
import io.ciorch.core.PipelineOrchestrator
import com.example.ElmBuildAdapter

PipelineOrchestrator.registerBuildAdapter('elm', ElmBuildAdapter)

ciorch(
    configPath: 'ciorch.yml',
    payload:    env.PAYLOAD,
    apiToken:   env.GITHUB_TOKEN
)
```

```yaml
# ciorch.yml
ciorch:
  build:
    adapter: elm
```

## Adapter execution order

Within a pipeline run, the orchestrator calls adapter methods in this order:

```
prepare() → lint() → test() → build() → getArtifacts()
```

Any method returning `false` halts the pipeline. If `lint` or `test` is not in the matched task list, the adapter method is still available but not called.
