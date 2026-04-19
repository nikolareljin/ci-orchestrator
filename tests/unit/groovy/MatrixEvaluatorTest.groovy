package io.ciorch.tests

import io.ciorch.git.MatrixEvaluator
import io.ciorch.git.MatrixLoader
import spock.lang.Specification
import spock.lang.Unroll

class MatrixEvaluatorTest extends Specification {

    static Map INLINE_MATRIX = [
        rules: [
            [id: "release-master-merged", dst_branch: "master",  src_branch: "release", event: "closed_pr", merged: true,  tasks: ["BUILD", "TAG", "DEPLOY"], priority: 100],
            [id: "feature-any-push",      dst_branch: "feature", src_branch: null,      event: "push",      merged: false, tasks: ["TEST"],                   priority: 10],
            [id: "any-master-new",        dst_branch: "master",  src_branch: null,      event: "new",       merged: false, tasks: ["NOTIFY"],                  priority: 5],
        ],
        branch_patterns: [
            feature: "^feature/",
            master:  "^(master|main)\$",
            release: "^release/",
            sprint:  "^sprint/",
            hotfix:  "^hotfix/",
            develop: "^(develop|dev)\$",
        ]
    ]

    MatrixEvaluator evaluator

    def setup() {
        evaluator = new MatrixEvaluator(INLINE_MATRIX)
    }

    @Unroll
    def "classifyBranch(#branch) == #expected"() {
        expect:
        evaluator.classifyBranch(branch) == expected

        where:
        branch             | expected
        "feature/my-feat"  | "feature"
        "master"           | "master"
        "main"             | "master"
        "sprint/2024-01"   | "sprint"
        "release/1.2.3"    | "release"
        "hotfix/critical"  | "hotfix"
        "develop"          | "develop"
        "dev"              | "develop"
    }

    def "classifyBranch falls back to raw name when no pattern matches"() {
        when:
        String result = evaluator.classifyBranch("unknown/branch")

        then:
        // no pattern matches → raw name is returned
        result == "unknown/branch"
    }

    def "findRule returns correct rule for release-to-master merged closed_pr"() {
        when:
        Map rule = evaluator.findRule("master", "release/1.2.3", "closed_pr", true)

        then:
        rule != null
        rule.id == "release-master-merged"
    }

    def "findRule returns correct rule tasks"() {
        when:
        Map rule = evaluator.findRule("master", "release/1.2.3", "closed_pr", true)

        then:
        rule.tasks == ["BUILD", "TAG", "DEPLOY"]
    }

    def "findRule returns rule for feature push (null src_branch matches any src)"() {
        when:
        Map rule = evaluator.findRule("feature/x", null, "push", false)

        then:
        rule != null
        rule.id == "feature-any-push"
    }

    def "findRule returns null when no rule matches"() {
        when:
        Map rule = evaluator.findRule("hotfix/x", "feature/y", "push", false)

        then:
        rule == null
    }

    def "getTasks returns task list for matching event"() {
        when:
        List<String> tasks = evaluator.getTasks("master", "release/1.2.3", "closed_pr", true)

        then:
        tasks == ["BUILD", "TAG", "DEPLOY"]
    }

    def "getTasks returns empty list when no rule matches"() {
        when:
        List<String> tasks = evaluator.getTasks("develop", "feature/x", "push", false)

        then:
        tasks == []
    }

    def "higher priority rule wins when multiple rules match"() {
        given:
        Map matrix = [
            rules: [
                [id: "low",  dst_branch: "master", src_branch: null, event: "new", merged: false, tasks: ["LOW"],  priority: 5],
                [id: "high", dst_branch: "master", src_branch: null, event: "new", merged: false, tasks: ["HIGH"], priority: 50],
            ],
            branch_patterns: [
                master: "^(master|main)\$"
            ]
        ]
        MatrixEvaluator ev = new MatrixEvaluator(matrix)

        when:
        Map rule = ev.findRule("master", null, "new", false)

        then:
        rule.id == "high"
    }

    def "findRule with any-master-new rule matches master push/new"() {
        when:
        Map rule = evaluator.findRule("master", null, "new", false)

        then:
        rule != null
        rule.id == "any-master-new"
        rule.tasks == ["NOTIFY"]
    }

    def "MatrixLoader.loadBuiltin loads default-gitflow and evaluator finds release-master-closed-pr rule"() {
        given:
        Map matrix = MatrixLoader.loadBuiltin("default-gitflow")

        when:
        MatrixEvaluator ev = new MatrixEvaluator(matrix)
        Map rule = ev.findRule("master", "release/1.0.0", "closed", true)

        then:
        matrix.rules != null
        (matrix.rules as List).size() > 0
        rule != null
        rule.id == "release-master-closed-pr"
        (rule.tasks as List).containsAll(["build", "tag", "deploy"])
    }

    def "MatrixLoader.loadBuiltin returns empty structure for unknown strategy"() {
        when:
        Map matrix = MatrixLoader.loadBuiltin("nonexistent-strategy")

        then:
        (matrix.rules as List).isEmpty()
    }
}
