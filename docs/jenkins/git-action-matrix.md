# Git Action Matrix

The Git Action Matrix is the rule engine that maps a GitHub event (push, PR opened, PR merged, tag created, etc.) to a list of pipeline tasks. It replaces hard-coded conditional logic with data-driven YAML rules.

## How it works

1. `WebhookParser` parses the incoming GitHub webhook and produces a `GitEvent` (source branch, destination branch, event type, merged flag, etc.).
2. `MatrixLoader` loads the strategy YAML file from `resources/matrix/`.
3. `MatrixEvaluator` classifies the raw branch names into branch types (using `branch_patterns`) then scans the `rules` list for the highest-priority matching rule.
4. `PipelineOrchestrator` dispatches the task list from the matched rule.

## Built-in strategies

| Strategy | File | Description |
|---|---|---|
| `gitflow` | `resources/matrix/default-gitflow.yml` | Classic GitFlow with sprint, release, hotfix, master branches |
| `github-flow` | `resources/matrix/github-flow.yml` | Simple feature-branch → main flow |
| `trunk-based` | `resources/matrix/trunk-based.yml` | Short-lived branches merged directly to trunk/main |

Set the active strategy in `ciorch.yml`:

```yaml
ciorch:
  branching:
    strategy: gitflow   # or: github-flow | trunk-based | custom
```

## Rule schema

```yaml
rules:
  - id: "release-to-master-merged"         # unique identifier
    dst_branch: "master"                    # classified destination branch type
    src_branch: "release"                   # classified source branch type (null = any)
    event: "closed"                         # closed | opened | synchronize | push | new | merged
    merged: true                            # true | false | omit to match either
    tasks: [build, release, commit, tag, deploy]
    priority: 100                           # higher wins when multiple rules match

branch_patterns:
  feature: "^feature/"          # regex matched with Pattern.find() (not full match)
  sprint:  "^sprint/"
  release: "^release/"
  hotfix:  "^hotfix/"
  develop: "^(develop|dev)$"
  master:  "^(master|main)$"
```

### Event types

| Value | GitHub event | Condition |
|---|---|---|
| `opened` | `pull_request` | `action: opened` |
| `synchronize` | `pull_request` | `action: synchronize` (new push to PR branch) |
| `closed` | `pull_request` | `action: closed` (use `merged: true/false` to distinguish) |
| `push` | `push` | Direct push to branch (no PR) |
| `new` | `create` | Branch or tag created |
| `merged` | `pull_request` | `action: closed` + `merged: true` (shorthand) |

### Task identifiers

| Task | Action |
|---|---|
| `lint` | Run `build.lint_command` via the build adapter |
| `test` | Run `build.test_command` via the build adapter |
| `build` | Run `build.build_command` via the build adapter |
| `e2e` | Run end-to-end / integration test suite |
| `deploy` | Run deploy adapter for the matched environment |
| `tag` | Create and push a git tag using the version from the branch name |
| `commit` | Commit any working-tree changes (e.g. version bumps) |
| `release` | Full release build (equivalent to `build` in release mode) |
| `scan` | Run security scanners (Trivy, Gitleaks, SAST) |
| `artifact` | Archive build artifacts |
| `notify` | Post a Slack / webhook notification |

## Custom matrix

Set `branching.strategy: custom` and point `branching.custom_matrix` to your own YAML file:

```yaml
ciorch:
  branching:
    strategy: custom
    custom_matrix: ".ciorch/matrix.yml"
```

The custom file is **merged** with the built-in `default-gitflow.yml`: rules with the same `id` in your custom file override the built-in rule; all other built-in rules remain active. To completely replace a built-in rule, use its `id` and provide a new task list.

## Example: adding a hotfix fast-track rule

```yaml
# .ciorch/matrix.yml
rules:
  - id: "hotfix-master-merged-fasttrack"
    dst_branch: "master"
    src_branch: "hotfix"
    event: "closed"
    merged: true
    tasks: [build, deploy, tag, notify]   # skip lint/test for speed
    priority: 200                          # higher than the default hotfix rule
```

## Rule evaluation logic

When multiple rules match, the one with the **highest `priority`** wins. Within the same priority, evaluation order is the order the rules appear in the YAML file.

A rule matches when **all** of the following are true:

- `dst_branch` equals the classified destination branch type (or is omitted/null = wildcard)
- `src_branch` equals the classified source branch type (or is omitted/null = any source)
- `event` equals the event type string (or is omitted/null = any event)
- `merged` equals the merged flag (or is omitted = either)
