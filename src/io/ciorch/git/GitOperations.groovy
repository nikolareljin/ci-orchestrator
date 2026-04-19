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
        String command = "git clone -b ${branch} ${repoUrl} ${targetDir}"
        def result = system.run_command(command, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        return result == 0
    }

    // Checkout a branch
    boolean checkout(String branch, boolean create = false) {
        String flag = create ? "-b " : ""
        def result = system.git_command("git checkout ${flag}${branch}", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        return result == 0
    }

    // Commit staged changes
    boolean commit(String message) {
        def result = system.git_command(
            "git commit -m '${message}'",
            SystemCall.SHOW_COMMAND_STATUS_VALUE
        )
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
        String msgArg = message ? "-a ${tagName} -m '${message}'" : tagName
        system.git_command("git tag ${msgArg}", SystemCall.SHOW_COMMAND_STATUS_VALUE)
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
        String command = "curl -s -u ${apiUser}:${apiToken} -X POST '${url}' -H 'Content-Type: application/json' -d '${json}'"
        String truncated = "curl -s -u ${apiUser}:[REDACTED] -X POST '${url}' ..."
        String result = system.run_command_with_secrets(command, truncated, SystemCall.SHOW_COMMAND_OUTPUT) as String
        try {
            return new JsonSlurper().parseText(result) as Map
        } catch (Exception ex) {
            context?.echo("GitOperations: API response parse failed: ${ex.message}")
            return [:]
        }
    }
}
