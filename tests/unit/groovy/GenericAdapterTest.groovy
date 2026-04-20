package io.ciorch.tests

import io.ciorch.build.adapters.GenericAdapter
import io.ciorch.core.SystemCall
import spock.lang.Specification

class GenericAdapterTest extends Specification {

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

    def "prepare() runs install_command when provided and returns result"() {
        given:
        def system = mockSystem(0)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([install_command: "apt-get install -y libfoo"], mockContext)

        then:
        result == true
    }

    def "prepare() returns false when install_command fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([install_command: "apt-get install -y libfoo"], mockContext)

        then:
        result == false
    }

    def "prepare() skips and returns true when no install_command configured"() {
        given:
        def system = mockSystem(0)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
    }

    def "prepare() runs prepare_command when provided"() {
        given:
        def capturedCmd = null
        def system = mockSystem { String cmd ->
            capturedCmd = cmd
            return 0
        }
        // null context so withEnv is skipped
        def adapter = new GenericAdapter(null, system)

        when:
        boolean result = adapter.prepare([prepare_command: "make deps"], null)

        then:
        result == true
        capturedCmd == "make deps"
    }

    def "lint() runs lint_command when provided and returns true on success"() {
        given:
        def system = mockSystem(0)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([lint_command: "my-linter ."])

        then:
        result == true
    }

    def "lint() returns false when lint_command fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([lint_command: "my-linter ."])

        then:
        result == false
    }

    def "lint() skips and returns true when no lint_command configured"() {
        given:
        def system = mockSystem(0)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "test() runs test_command and captures exact command"() {
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
        def adapter = new GenericAdapter(null, system)

        when:
        boolean result = adapter.test([test_command: "make test"])

        then:
        result == true
        capturedCmd == "make test"
    }

    def "test() returns false when test_command fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.test([test_command: "make test"])

        then:
        result == false
    }

    def "test() skips and returns true when no test_command configured"() {
        given:
        def system = mockSystem(0)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == true
    }

    def "build() runs build_command, captures exact command, and uses artifacts from config"() {
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
        def adapter = new GenericAdapter(null, system)

        when:
        boolean result = adapter.build([build_command: "make all", artifacts: ["dist/", "bin/"]])

        then:
        result == true
        capturedCmd == "make all"
        adapter.getArtifacts() == ["dist/", "bin/"]
    }

    def "build() returns false when build_command fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.build([build_command: "make all"])

        then:
        result == false
    }

    def "build() skips and returns true with empty artifacts when no build_command configured"() {
        given:
        def system = mockSystem(0)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == true
        adapter.getArtifacts().isEmpty()
    }

    def "getArtifacts() returns empty list before build"() {
        given:
        def system = mockSystem(0)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts.isEmpty()
    }

    def "getName() returns 'generic'"() {
        given:
        def system = mockSystem(0)
        def adapter = new GenericAdapter(mockContext, system)

        when:
        String name = adapter.getName()

        then:
        name == "generic"
    }
}
