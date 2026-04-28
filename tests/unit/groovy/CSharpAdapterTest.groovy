package io.ciorch.tests

import io.ciorch.build.adapters.CSharpAdapter
import io.ciorch.core.Config
import io.ciorch.core.SystemCall
import spock.lang.Specification

class CSharpAdapterTest extends Specification {

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

    def "prepare() returns true when dotnet is found and restore succeeds"() {
        given:
        def system = mockSystem(0)
        def adapter = new CSharpAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
    }

    def "prepare() returns false when dotnet is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("dotnet --version") ? 1 : 0
        }
        def adapter = new CSharpAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() returns false when dotnet restore fails"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("dotnet restore") ? 1 : 0
        }
        def adapter = new CSharpAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() logs dotnet_version when provided in buildConfig"() {
        given:
        def system = mockSystem(0)
        def messages = []
        def ctx = [
            echo: { msg -> messages << msg },
            withEnv: { List<String> vars, Closure body -> body.call() }
        ]
        def adapter = new CSharpAdapter(ctx, system)

        when:
        adapter.prepare([dotnet_version: "8.0"], ctx)

        then:
        messages.any { it.contains("8.0") }
    }

    def "prepare() logs dotnet_version from config.toolVersions when absent in buildConfig"() {
        given:
        def config = new Config()
        config.toolVersions = [dotnet_version: "8.0"]
        def system = mockSystem(0)
        def messages = []
        def ctx = [
            echo: { msg -> messages << msg },
            withEnv: { List<String> vars, Closure body -> body.call() }
        ]
        def adapter = new CSharpAdapter(ctx, system, config)

        when:
        adapter.prepare([:], ctx)

        then:
        messages.any { it.contains("8.0") }
    }

    def "lint() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new CSharpAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() returns false when dotnet format --verify-no-changes fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new CSharpAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "test() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new CSharpAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == true
    }

    def "test() returns false when dotnet test fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new CSharpAdapter(mockContext, system)

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
        def adapter = new CSharpAdapter(null, system)

        when:
        boolean result = adapter.test([test_command: "dotnet test --filter Category=Unit"])

        then:
        result == true
        capturedCmd == 'dotnet test --filter Category=Unit'
    }

    def "build() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new CSharpAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == true
    }

    def "build() returns false when dotnet publish fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new CSharpAdapter(mockContext, system)

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
        def adapter = new CSharpAdapter(null, system)

        when:
        boolean result = adapter.build([build_command: "dotnet publish -c Release -r linux-x64 -o out/"])

        then:
        result == true
        capturedCmd == 'dotnet publish -c Release -r linux-x64 -o out/'
    }

    def "getArtifacts() returns non-empty list after successful build"() {
        given:
        def system = mockSystem(0)
        def adapter = new CSharpAdapter(mockContext, system)
        adapter.build([:])

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts != null
        !artifacts.isEmpty()
        artifacts.contains("publish/")
    }

    def "getArtifacts() returns empty list before build"() {
        given:
        def system = mockSystem(0)
        def adapter = new CSharpAdapter(mockContext, system)

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts.isEmpty()
    }

    def "getName() returns 'csharp'"() {
        given:
        def system = mockSystem(0)
        def adapter = new CSharpAdapter(mockContext, system)

        when:
        String name = adapter.getName()

        then:
        name == "csharp"
    }
}
