# Jenkins — Getting Started

## Prerequisites

- Jenkins 2.x with the following plugins:
  - [Pipeline](https://plugins.jenkins.io/workflow-aggregator/)
  - [Git](https://plugins.jenkins.io/git/)
  - [Credentials Binding](https://plugins.jenkins.io/credentials-binding/)
  - [Slack Notification](https://plugins.jenkins.io/slack/) *(optional, for `notify.slack`)*
- A Jenkins controller that can load a shared library from GitHub

## Step 1 — Register the library

In **Manage Jenkins → Configure System → Global Pipeline Libraries**, add:

| Field | Value |
|---|---|
| Name | `ci-orchestrator` |
| Default version | `main` (or a release tag) |
| Retrieval method | Modern SCM → GitHub |
| Repository URL | `https://github.com/nikolareljin/ci-orchestrator.git` |
| Credentials | Your GitHub credential (read-only PAT is sufficient) |

## Step 2 — Add `ciorch.yml` to your repo

Create `ciorch.yml` at the root of the consuming repository:

```yaml
ciorch:
  version: "1"
  build:
    adapter: node
    node_version: "20"
    test_command: "npm test"
    build_command: "npm run build"
  deploy:
    adapter: wordpress
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
    strategy: gitflow
  notify:
    slack:
      channel: "#deployments"
```

## Step 3 — Create a `Jenkinsfile`

```groovy
@Library('ci-orchestrator@main') _

ciorch(
    payload:  env.GITHUB_WEBHOOK_PAYLOAD ?: '',
    apiToken: env.GITHUB_TOKEN           ?: '',
    apiUser:  env.GITHUB_USER            ?: ''
)
```

For GitHub webhook-triggered builds, Jenkins passes the payload via the [Generic Webhook Trigger](https://plugins.jenkins.io/generic-webhook-trigger/) plugin. Set the `GITHUB_WEBHOOK_PAYLOAD` variable to the full JSON body of the `push` or `pull_request` event.

## Step 4 — Configure credentials

In **Manage Jenkins → Credentials**, add:

| ID | Kind | Used for |
|---|---|---|
| `github-token` | Secret text | GitHub API calls (PR creation, status updates) |
| `registry-credentials` | Username + Password | Docker registry login |
| `deploy-ssh-key` | SSH private key | SSH-based deployment |
| `slack-token` | Secret text | Slack notifications (if enabled) |

Reference credentials in your `Jenkinsfile`:

```groovy
@Library('ci-orchestrator@main') _

withCredentials([
    string(credentialsId: 'github-token',  variable: 'GITHUB_TOKEN'),
    string(credentialsId: 'slack-token',   variable: 'SLACK_TOKEN'),
    usernamePassword(credentialsId: 'registry-credentials',
                     usernameVariable: 'REGISTRY_USER',
                     passwordVariable: 'REGISTRY_TOKEN')
]) {
    ciorch(
        payload:  env.GITHUB_WEBHOOK_PAYLOAD ?: '',
        apiToken: env.GITHUB_TOKEN,
        apiUser:  env.GITHUB_USER ?: 'ci-bot'
    )
}
```

## What happens next

When a webhook fires, `ciorch` will:

1. Parse the GitHub event payload (PR opened, push, tag, release, etc.)
2. Load the branching strategy matrix (`gitflow` by default)
3. Match the event against the matrix rules to determine which tasks to run
4. Execute tasks in order: `lint → test → build → deploy → tag`
5. Post a Slack notification on completion (if configured)

See [git-action-matrix.md](git-action-matrix.md) for how rules are matched and [configuration.md](configuration.md) for all Jenkins-specific options.
