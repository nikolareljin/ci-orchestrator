package io.ciorch.tests

import io.ciorch.build.adapters.GoAdapter
import io.ciorch.core.Config
import io.ciorch.core.SystemCall
import spock.lang.Specification

class GoAdapterTest extends Specification {

    def mockContext = [
        echo: { msg -> },
        withEnv: { List<String> vars, Closure body -> body.call() }
    ]

    private SystemCall mockSystem(int returnCode) {
        return new SystemCall(null, "", "", "", "") {
            @Override
            def run_command(String cmd, int mode) { return returnCode }
            @Override
            def run_command(String cmd, int mode, int timeout) { return returnCode }
        }
    }

    private SystemCall mockSystem(Closure<Integer> handler) {
        return new SystemCall(null, "", "", "", "") {
            @Override
            def run_command(String cmd, int mode) { return handler(cmd) }
            @Override
            def run_command(String cmd, int mode, int timeout) { return handler(cmd) }
        }
    }

    def "prepare() returns true when go is found"() {
        given:
        def system = mockSystem(0)
        def adapter = new GoAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
    }

    def "prepare() returns false when go is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.startsWith("go version") ? 1 : 0
        }
        def adapter = new GoAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() logs go_version informational message when provided"() {
        given:
        def system = mockSystem(0)
        def messages = []
        def ctx = [
            echo: { msg -> messages << msg },
            withEnv: { List<String> vars, Closure body -> body.call() }
        ]
        def adapter = new GoAdapter(ctx, system)

        when:
        adapter.prepare([go_version: "1.22"], ctx)

        then:
        messages.any { it.contains("1.22") }
    }

    def "prepare() logs go_version from config.toolVersions when absent in buildConfig"() {
        given:
        def config = new Config()
        config.toolVersions = [go_version: "1.22"]
        def system = mockSystem(0)
        def messages = []
        def ctx = [
            echo: { msg -> messages << msg },
            withEnv: { List<String> vars, Closure body -> body.call() }
        ]
        def adapter = new GoAdapter(ctx, system, config)

        when:
        adapter.prepare([:], ctx)

        then:
        messages.any { it.contains("1.22") }
    }

    def "lint() happy path returns true"() {
        given:
        // 0 for go vet, 1 for which golangci-lint (not found => skip)
        def system = mockSystem { String cmd ->
            cmd.contains("which golangci-lint") ? 1 : 0
        }
        def adapter = new GoAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() runs golangci-lint when available"() {
        given:
        def executedCommands = []
        def system = new SystemCall(null, "", "", "", "") {
            @Override
            def run_command(String cmd, int mode) {
                executedCommands << cmd
                return 0
            }
            @Override
            def run_command(String cmd, int mode, int timeout) {
                executedCommands << cmd
                return 0
            }
        }
        def adapter = new GoAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
        executedCommands.any { it.contains("golangci-lint") }
    }

    def "lint() returns false when go vet fails"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("go vet") ? 1 : 0
        }
        def adapter = new GoAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "test() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new GoAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == true
    }

    def "test() returns false when go test fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new GoAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == false
    }

    def "test() uses custom test_command from buildConfig"() {
        given:
        def capturedCmd = null
        def system = new SystemCall(null, "", "", "", "") {
            @Override
            def run_command(String cmd, int mode) {
                capturedCmd = cmd
                return 0
            }
            @Override
            def run_command(String cmd, int mode, int timeout) {
                capturedCmd = cmd
                return 0
            }
        }
        // null context so withEnv is skipped and the fallback fires with testCmd directly
        def adapter = new GoAdapter(null, system)

        when:
        boolean result = adapter.test([test_command: "go test -v ./..."])

        then:
        result == true
        capturedCmd == 'go test -v ./...'
    }

    def "build() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new GoAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == true
    }

    def "build() returns false when go build fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new GoAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == false
    }

    def "build() uses custom build_command from buildConfig"() {
        given:
        def capturedCmd = null
        def system = new SystemCall(null, "", "", "", "") {
            @Override
            def run_command(String cmd, int mode) {
                capturedCmd = cmd
                return 0
            }
            @Override
            def run_command(String cmd, int mode, int timeout) {
                capturedCmd = cmd
                return 0
            }
        }
        // null context so withEnv is skipped and the fallback fires with buildCmd directly
        def adapter = new GoAdapter(null, system)

        when:
        boolean result = adapter.build([build_command: "go build -o bin/app ."])

        then:
        result == true
        capturedCmd == 'go build -o bin/app .'
    }

    def "getArtifacts() returns non-empty list after successful build"() {
        given:
        def system = mockSystem(0)
        def adapter = new GoAdapter(mockContext, system)
        adapter.build([:])

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts != null
        !artifacts.isEmpty()
        artifacts.contains("./...")
    }

    def "getArtifacts() returns empty list before build"() {
        given:
        def system = mockSystem(0)
        def adapter = new GoAdapter(mockContext, system)

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts.isEmpty()
    }

    def "getName() returns 'go'"() {
        given:
        def system = mockSystem(0)
        def adapter = new GoAdapter(mockContext, system)

        when:
        String name = adapter.getName()

        then:
        name == "go"
    }
}
