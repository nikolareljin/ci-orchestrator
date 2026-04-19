package io.ciorch.tests

import io.ciorch.git.WebhookParser
import io.ciorch.git.GitEvent
import io.ciorch.git.EventType
import io.ciorch.git.BranchType
import spock.lang.Specification
import spock.lang.Unroll

class WebhookParserTest extends Specification {

    // Minimal Jenkins context: only echo is needed by WebhookParser
    def mockContext = [echo: { msg -> }]

    @Unroll
    def "getBranchType(#branch) == #expected"() {
        given:
        WebhookParser parser = new WebhookParser(mockContext, [:])

        expect:
        parser.getBranchType(branch) == expected

        where:
        branch              | expected
        "feature/my-feature"| BranchType.FEATURE
        "master"            | BranchType.MASTER
        "main"              | BranchType.MAIN
        "develop"           | BranchType.DEVELOP
        "sprint/2024-01"    | BranchType.SPRINT
        "release/1.2.3"     | BranchType.RELEASE
        "hotfix/fix-x"      | BranchType.HOTFIX
        "dev"               | BranchType.DEVELOP
    }

    @Unroll
    def "getVersionFromBranch(#branch) == #expected"() {
        given:
        WebhookParser parser = new WebhookParser(mockContext, [:])

        expect:
        parser.getVersionFromBranch(branch) == expected

        where:
        branch              | expected
        "release/1.2.3"     | "1.2.3"
        "hotfix/2.0.1"      | "2.0.1"
        "feature/my-feature"| "0.0.0"
        "master"            | "0.0.0"
    }

    def "toGitEvent returns correct GitEvent for an opened PR payload"() {
        given:
        Map payload = [
            action: "opened",
            pull_request: [
                number: 42,
                title: "My feature",
                body: "Description",
                head: [ref: "feature/my-feature", sha: "abc123"],
                base: [ref: "main"],
                user: [login: "dev-user", avatar_url: "http://example.com/avatar"],
                merged: false,
            ],
            repository: [
                name: "my-repo",
                clone_url: "https://github.com/myorg/my-repo.git",
            ]
        ]
        WebhookParser parser = new WebhookParser(mockContext, payload, "token123", "api-user")
        parser.setValues(false)

        when:
        GitEvent event = parser.toGitEvent()

        then:
        event.srcBranch == "feature/my-feature"
        event.dstBranch == "main"
        event.eventType == EventType.OPEN_PR
        event.prNumber == 42
        event.merged == false
    }

    def "toGitEvent populates repoName from clone_url"() {
        given:
        Map payload = [
            action: "opened",
            pull_request: [
                number: 1,
                title: "Test",
                body: "",
                head: [ref: "feature/test", sha: "deadbeef"],
                base: [ref: "master"],
                user: [login: "tester", avatar_url: ""],
                merged: false,
            ],
            repository: [
                name: "ci-orchestrator",
                clone_url: "https://github.com/myorg/ci-orchestrator.git",
            ]
        ]
        WebhookParser parser = new WebhookParser(mockContext, payload)
        parser.setValues(false)

        when:
        GitEvent event = parser.toGitEvent()

        then:
        event.repoName == "ci-orchestrator"
    }

    def "toGitEvent for closed merged PR sets eventType to merged"() {
        given:
        Map payload = [
            action: "closed",
            pull_request: [
                number: 10,
                title: "Release merge",
                body: "",
                head: [ref: "release/1.0.0", sha: "sha1"],
                base: [ref: "master"],
                user: [login: "releaser", avatar_url: ""],
                merged: true,
                merged_by: [login: "merger", avatar_url: ""],
                merged_at: "2024-01-01T00:00:00Z",
            ],
            repository: [
                name: "repo",
                clone_url: "https://github.com/org/repo.git",
            ]
        ]
        WebhookParser parser = new WebhookParser(mockContext, payload)
        parser.setValues(false)

        when:
        GitEvent event = parser.toGitEvent()

        then:
        event.merged == true
        event.srcBranch == "release/1.0.0"
        event.dstBranch == "master"
    }

    def "getVersionFromBranch on a release branch with v-prefix strips the v"() {
        given:
        WebhookParser parser = new WebhookParser(mockContext, [:])

        expect:
        parser.getVersionFromBranch("release/v1.2.3") == "1.2.3"
    }

    def "srcType is set correctly after setValues"() {
        given:
        Map payload = [
            action: "opened",
            pull_request: [
                number: 5,
                title: "Sprint PR",
                body: "",
                head: [ref: "sprint/2024-q1", sha: "aabbcc"],
                base: [ref: "develop"],
                user: [login: "dev", avatar_url: ""],
                merged: false,
            ],
            repository: [
                name: "repo",
                clone_url: "https://github.com/org/repo.git",
            ]
        ]
        WebhookParser parser = new WebhookParser(mockContext, payload)

        when:
        parser.setValues(false)

        then:
        parser.srcType == BranchType.SPRINT
        parser.dstType == BranchType.DEVELOP
    }
}
