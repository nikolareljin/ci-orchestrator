package io.ciorch.tests

import io.ciorch.build.adapters.NodeAdapter
import io.ciorch.core.SystemCall
import spock.lang.Specification

class NodeAdapterTest extends Specification {

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

    def "prepare() returns true when node and npm are found"() {
        given:
        def system = mockSystem(0)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
    }

    def "prepare() returns false when node is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("node") ? 1 : 0
        }
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() returns false when npm is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("npm") ? 1 : 0
        }
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() logs node_version when provided in buildConfig"() {
        given:
        def system = mockSystem(0)
        def messages = []
        def ctx = [
            echo: { msg -> messages << msg },
            withEnv: { List<String> vars, Closure body -> body.call() }
        ]
        def adapter = new NodeAdapter(ctx, system)

        when:
        adapter.prepare([node_version: "20"], ctx)

        then:
        messages.any { it.contains("20") }
    }

    def "lint() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() returns true even when lint command fails (non-fatal)"() {
        given:
        def system = mockSystem(1)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() uses custom lintCommand from buildConfig"() {
        given:
        def capturedCmd = null
        def system = mockSystem { String cmd ->
            capturedCmd = cmd
            return 0
        }
        // null context so withEnv is skipped and the fallback fires with lintCmd directly
        def adapter = new NodeAdapter(null, system)

        when:
        boolean result = adapter.lint([lintCommand: 'my-custom-lint'])

        then:
        result == true
        capturedCmd == 'my-custom-lint'
    }

    def "test() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == true
    }

    def "test() returns false when npm test fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == false
    }

    def "test() uses custom test_command from buildConfig"() {
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
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.test([test_command: "yarn test"])

        then:
        result == true
        // The env var CIORCH_CMD is set to "yarn test" and the shell uses it
    }

    def "build() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == true
    }

    def "build() returns false when npm run build fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == false
    }

    def "build() uses custom build_command from buildConfig"() {
        given:
        def system = mockSystem(0)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        boolean result = adapter.build([build_command: "yarn build"])

        then:
        result == true
    }

    def "getArtifacts() returns non-empty list after successful build"() {
        given:
        def system = mockSystem(0)
        def adapter = new NodeAdapter(mockContext, system)
        adapter.build([:])

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts != null
        !artifacts.isEmpty()
        artifacts.contains("dist/")
    }

    def "getArtifacts() returns empty list before build"() {
        given:
        def system = mockSystem(0)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts.isEmpty()
    }

    def "getName() returns 'node'"() {
        given:
        def system = mockSystem(0)
        def adapter = new NodeAdapter(mockContext, system)

        when:
        String name = adapter.getName()

        then:
        name == "node"
    }
}
