package io.ciorch.git

import java.io.Serializable
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import io.ciorch.core.SystemCall

class GitOperations implements Serializable {
    def context = null
    SystemCall system = null
    String apiToken = ""
    String apiUser = ""
    String gitEmail = "ciorch@ci-orchestrator.io"
    String gitUsername = "ci-orchestrator"

    // Supported hosting providers
    static final String GITHUB = "github"
    static final String GITLAB = "gitlab"
    static final String BITBUCKET = "bitbucket"

    GitOperations(def context, SystemCall system, String apiToken = "", String apiUser = "") {
        this.context = context
        this.system = system
        this.apiToken = apiToken
        this.apiUser = apiUser
    }

    // Configure git identity for commits
    void configureIdentity(String email = null, String username = null) {
        system.set_git_config(email ?: this.gitEmail, username ?: this.gitUsername)
    }

    // Clone a repository
    boolean clone(String repoUrl, String branch, String targetDir) {
        def result = null
        context?.withEnv(["CIORCH_CLONE_URL=${repoUrl}", "CIORCH_CLONE_BRANCH=${branch}", "CIORCH_CLONE_DIR=${targetDir}"]) {
            result = system.run_command(
                'git clone -b "$CIORCH_CLONE_BRANCH" "$CIORCH_CLONE_URL" "$CIORCH_CLONE_DIR"',
                SystemCall.SHOW_COMMAND_STATUS_VALUE
            )
        }
        return result == 0
    }

    // Checkout a branch
    boolean checkout(String branch, boolean create = false) {
        def result = null
        String flag = create ? "-b" : ""
        context?.withEnv(["CIORCH_CHECKOUT_BRANCH=${branch}", "CIORCH_CHECKOUT_FLAG=${flag}"]) {
            result = system.git_command(
                'git checkout $CIORCH_CHECKOUT_FLAG "$CIORCH_CHECKOUT_BRANCH"'.trim(),
                SystemCall.SHOW_COMMAND_STATUS_VALUE
            )
        }
        return result == 0
    }

    // Commit staged changes
    boolean commit(String message) {
        def result = context?.withEnv(["CIORCH_COMMIT_MSG=${message}"]) {
            system.git_command('git commit -m "$CIORCH_COMMIT_MSG"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        return result == 0
    }

    // Push branch to remote
    boolean push(String remote = "origin", String branch = "") {
        String branchArg = branch ?: ""
        def result = system.git_command(
            "git push ${remote} ${branchArg}".trim(),
            SystemCall.SHOW_COMMAND_STATUS_VALUE
        )
        return result == 0
    }

    // Create and push a tag
    boolean tag(String tagName, String message = "") {
        def tagResult = null
        if (message) {
            context?.withEnv(["CIORCH_TAG_NAME=${tagName}", "CIORCH_TAG_MSG=${message}"]) {
                tagResult = system.git_command('git tag -a "$CIORCH_TAG_NAME" -m "$CIORCH_TAG_MSG"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
            }
        } else {
            context?.withEnv(["CIORCH_TAG_NAME=${tagName}"]) {
                tagResult = system.git_command('git tag "$CIORCH_TAG_NAME"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
            }
        }
        if (tagResult != 0) return false
        return push("origin", "refs/tags/${tagName}")
    }

    // Get current branch name
    String getCurrentBranch() {
        return system.git_command("git rev-parse --abbrev-ref HEAD") as String
    }

    // Get latest commit SHA
    String getCommitSha(String ref = "HEAD") {
        return system.git_command("git rev-parse ${ref}") as String
    }

    // Check if a tag exists
    boolean tagExists(String tagName) {
        def result = system.run_command(
            "git tag -l '${tagName}'",
            SystemCall.SHOW_COMMAND_OUTPUT
        )
        return result?.toString()?.trim() == tagName
    }

    // Create a GitHub PR via API
    Map createGithubPR(String repoSlug, String head, String base, String title, String body = "") {
        String url = "https://api.github.com/repos/${repoSlug}/pulls"
        Map payload = [title: title, head: head, base: base, body: body]
        return _githubApiPost(url, payload)
    }

    // Generic GitHub API POST
    private Map _githubApiPost(String url, Map payload) {
        String json = JsonOutput.toJson(payload)
        String result = ""
        try {
            context?.withEnv(["CIORCH_GH_TOKEN=${apiToken}", "CIORCH_GH_USER=${apiUser}", "CIORCH_GH_URL=${url}", "CIORCH_GH_BODY=${json}"]) {
                result = context?.sh(
                    script: 'curl -s -u "$CIORCH_GH_USER:$CIORCH_GH_TOKEN" -X POST "$CIORCH_GH_URL" -H "Content-Type: application/json" -d "$CIORCH_GH_BODY"',
                    returnStdout: true
                )?.trim() ?: ""
            }
            return new groovy.json.JsonSlurperClassic().parseText(result) as Map
        } catch (Exception ex) {
            context?.echo("GitOperations: API request failed: ${ex.message}")
            return [:]
        }
    }
}
