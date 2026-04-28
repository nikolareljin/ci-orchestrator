package io.ciorch.tests

import io.ciorch.build.adapters.JavaAdapter
import io.ciorch.core.Config
import io.ciorch.core.SystemCall
import spock.lang.Specification

class JavaAdapterTest extends Specification {

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

    def "prepare() returns true for maven project when java and mvn are found"() {
        given:
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system)

        when:
        // force maven mode to avoid file-based detection picking up build.gradle in cwd
        boolean result = adapter.prepare([build_tool: "maven"], mockContext)

        then:
        result == true
    }

    def "prepare() returns false when java is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("java -version") ? 1 : 0
        }
        def adapter = new JavaAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() returns false when mvn is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("mvn --version") ? 1 : 0
        }
        def adapter = new JavaAdapter(mockContext, system)

        when:
        // force maven mode to avoid file-based detection picking up build.gradle in cwd
        boolean result = adapter.prepare([build_tool: "maven"], mockContext)

        then:
        result == false
    }

    def "prepare() detects gradle when build_tool config key is 'gradle'"() {
        given:
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([build_tool: "gradle"], mockContext)

        then:
        result == true
        adapter.buildTool == "gradle"
    }

    def "prepare() returns false when gradle not found for gradle project"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("gradle --version") ? 1 : 0
        }
        def adapter = new JavaAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([build_tool: "gradle"], mockContext)

        then:
        result == false
    }

    def "prepare() logs java_version when provided in buildConfig"() {
        given:
        def system = mockSystem(0)
        def messages = []
        def ctx = [
            echo: { msg -> messages << msg },
            withEnv: { List<String> vars, Closure body -> body.call() }
        ]
        def adapter = new JavaAdapter(ctx, system)

        when:
        adapter.prepare([java_version: "17"], ctx)

        then:
        messages.any { it.contains("17") }
    }

    def "prepare() logs java_version from config.toolVersions when absent in buildConfig"() {
        given:
        def config = new Config()
        config.toolVersions = [java_version: "21"]
        def system = mockSystem(0)
        def messages = []
        def ctx = [
            echo: { msg -> messages << msg },
            withEnv: { List<String> vars, Closure body -> body.call() }
        ]
        def adapter = new JavaAdapter(ctx, system, config)

        when:
        adapter.prepare([build_tool: "maven"], ctx)

        then:
        messages.any { it.contains("21") }
    }

    def "prepare() reads build_tool from config.raw when absent in buildConfig"() {
        given:
        def config = new Config()
        config.raw = [ciorch: [build: [build_tool: "gradle"]]]
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system, config)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
        adapter.buildTool == "gradle"
    }

    def "lint() happy path returns true (maven)"() {
        given:
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() returns false when checkstyle fails (maven)"() {
        given:
        def system = mockSystem(1)
        def adapter = new JavaAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "lint() happy path returns true (gradle)"() {
        given:
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system)
        adapter.buildTool = "gradle"

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() returns false when gradle check fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new JavaAdapter(mockContext, system)
        adapter.buildTool = "gradle"

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "lint() uses custom lint_command from buildConfig"() {
        given:
        def capturedCmd = null
        def system = mockSystem { String cmd ->
            capturedCmd = cmd
            return 0
        }
        // null context so withEnv is skipped and the fallback fires with lintCmd directly
        def adapter = new JavaAdapter(null, system)

        when:
        boolean result = adapter.lint([lint_command: "my-custom-lint"])

        then:
        result == true
        capturedCmd == "my-custom-lint"
    }

    def "test() happy path returns true (maven)"() {
        given:
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == true
    }

    def "test() returns false when mvn test fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new JavaAdapter(mockContext, system)

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
        def adapter = new JavaAdapter(null, system)

        when:
        boolean result = adapter.test([test_command: "mvn test -Dgroups=unit"])

        then:
        result == true
        capturedCmd == "mvn test -Dgroups=unit"
    }

    def "build() returns maven artifact path on success"() {
        given:
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system)
        // explicitly set maven so the test is not influenced by file-based detection
        adapter.buildTool = "maven"

        when:
        boolean result = adapter.build([:])

        then:
        result == true
        adapter.getArtifacts() == ["target/"]
    }

    def "build() returns gradle artifact path on success"() {
        given:
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system)
        adapter.buildTool = "gradle"

        when:
        boolean result = adapter.build([:])

        then:
        result == true
        adapter.getArtifacts() == ["build/libs/"]
    }

    def "build() returns false when build command fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new JavaAdapter(mockContext, system)

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
        def adapter = new JavaAdapter(null, system)

        when:
        boolean result = adapter.build([build_command: "mvn package -P production"])

        then:
        result == true
        capturedCmd == "mvn package -P production"
    }

    def "getArtifacts() returns empty list before build"() {
        given:
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system)

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts.isEmpty()
    }

    def "getName() returns 'java'"() {
        given:
        def system = mockSystem(0)
        def adapter = new JavaAdapter(mockContext, system)

        when:
        String name = adapter.getName()

        then:
        name == "java"
    }
}
