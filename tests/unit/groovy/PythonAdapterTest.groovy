package io.ciorch.tests

import io.ciorch.build.adapters.PythonAdapter
import io.ciorch.core.SystemCall
import spock.lang.Specification

class PythonAdapterTest extends Specification {

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

    def "prepare() returns true when python3 is found"() {
        given:
        def system = mockSystem(0)
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
    }

    def "prepare() returns false when python3 and python are not found"() {
        given:
        def system = mockSystem { String cmd ->
            (cmd.contains("python3 --version") || cmd.contains("python --version")) ? 1 : 0
        }
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() detects poetry when pyproject.toml and poetry.lock are present"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("python3 --version")) return 0
            if (cmd.contains("poetry.lock")) return 0
            return 1
        }
        def adapter = new PythonAdapter(mockContext, system)

        when:
        adapter.prepare([:], mockContext)

        then:
        adapter.packageManager == "poetry"
    }

    def "prepare() detects uv when pyproject.toml and uv.lock are present"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("python3 --version")) return 0
            if (cmd.contains("poetry.lock")) return 1
            if (cmd.contains("uv.lock")) return 0
            return 1
        }
        def adapter = new PythonAdapter(mockContext, system)

        when:
        adapter.prepare([:], mockContext)

        then:
        adapter.packageManager == "uv"
    }

    def "prepare() defaults to pip when no lock files found"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("python3 --version")) return 0
            return 1
        }
        def adapter = new PythonAdapter(mockContext, system)

        when:
        adapter.prepare([:], mockContext)

        then:
        adapter.packageManager == "pip"
    }

    def "lint() returns true when ruff is available and passes"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("which ruff") ? 0 : 0
        }
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() returns false when ruff fails"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("which ruff")) return 0
            if (cmd.contains("ruff check")) return 1
            return 0
        }
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "lint() falls back to flake8 when ruff not available"() {
        given:
        def executedCommands = []
        def system = new SystemCall(null, "", "", "", "") {
            @Override
            def run_command(String cmd, int mode) {
                executedCommands << cmd
                if (cmd.contains("which ruff")) return 1
                return 0
            }
            @Override
            def run_command(String cmd, int mode, int timeout) {
                executedCommands << cmd
                if (cmd.contains("which ruff")) return 1
                return 0
            }
        }
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
        executedCommands.any { it.contains("flake8") }
    }

    def "lint() returns false when flake8 fails"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("which ruff")) return 1
            if (cmd.contains("which flake8")) return 0
            if (cmd.contains("flake8 .")) return 1
            return 0
        }
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "lint() skips non-fatally when neither ruff nor flake8 available"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("python3 --version")) return 0
            if (cmd.contains("which")) return 1
            return 0
        }
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() uses lint_command override when provided"() {
        given:
        def capturedCmd = null
        def system = mockSystem { String cmd -> capturedCmd = cmd; return 0 }
        def adapter = new PythonAdapter(null, system)

        when:
        boolean result = adapter.lint([lint_command: "pylint src/"])

        then:
        result == true
        capturedCmd == "pylint src/"
    }

    def "lint() returns false when lint_command override fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([lint_command: "pylint src/"])

        then:
        result == false
    }

    def "test() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == true
    }

    def "test() returns false when pytest fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new PythonAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == false
    }

    def "prepare() with python3 sets pythonCmd correctly (test() default uses python3)"() {
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
        def adapter = new PythonAdapter(null, system)
        adapter.prepare([:], null)

        when:
        adapter.test([:])

        then:
        capturedCmd.startsWith("python3")
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
        def adapter = new PythonAdapter(null, system)

        when:
        boolean result = adapter.test([test_command: "python -m pytest -v tests/"])

        then:
        result == true
        capturedCmd == 'python -m pytest -v tests/'
    }

    def "build() happy path with poetry returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new PythonAdapter(mockContext, system)
        adapter.packageManager = "poetry"

        when:
        boolean result = adapter.build([:])

        then:
        result == true
    }

    def "build() happy path with uv returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new PythonAdapter(mockContext, system)
        adapter.packageManager = "uv"

        when:
        boolean result = adapter.build([:])

        then:
        result == true
    }

    def "build() pip manager is a no-op returning true"() {
        given:
        def system = mockSystem(0)
        def adapter = new PythonAdapter(mockContext, system)
        adapter.packageManager = "pip"

        when:
        boolean result = adapter.build([:])

        then:
        result == true
    }

    def "build() with pip manager returns no artifacts"() {
        given:
        def system = mockSystem(0)
        def adapter = new PythonAdapter(mockContext, system)
        adapter.packageManager = "pip"

        when:
        adapter.build([:])

        then:
        adapter.getArtifacts() == []
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
        def adapter = new PythonAdapter(null, system)
        adapter.packageManager = "poetry"

        when:
        boolean result = adapter.build([build_command: "poetry build --format wheel"])

        then:
        result == true
        capturedCmd == 'poetry build --format wheel'
    }

    def "getArtifacts() returns non-empty list after successful build"() {
        given:
        def system = mockSystem(0)
        def adapter = new PythonAdapter(mockContext, system)
        adapter.packageManager = "poetry"
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
        def adapter = new PythonAdapter(mockContext, system)

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts.isEmpty()
    }

    def "getName() returns 'python'"() {
        given:
        def system = mockSystem(0)
        def adapter = new PythonAdapter(mockContext, system)

        when:
        String name = adapter.getName()

        then:
        name == "python"
    }
}
