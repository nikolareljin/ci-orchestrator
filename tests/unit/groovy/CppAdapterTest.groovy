package io.ciorch.tests

import io.ciorch.build.adapters.CppAdapter
import io.ciorch.core.SystemCall
import spock.lang.Specification

class CppAdapterTest extends Specification {

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

    def "prepare() returns true when cmake and ninja are found"() {
        given:
        def system = mockSystem(0)
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
    }

    def "prepare() returns true when cmake and make are found (no ninja)"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("ninja --version")) return 1
            return 0
        }
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
    }

    def "prepare() returns false when cmake is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("cmake --version") ? 1 : 0
        }
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() returns false when neither ninja nor make found"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("cmake --version")) return 0
            return 1
        }
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "lint() cppcheck passes returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() cppcheck fails returns false"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("which cppcheck")) return 0
            if (cmd.contains("cppcheck")) return 1
            return 0
        }
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "lint() skips non-fatally when cppcheck is absent"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("which cppcheck") ? 1 : 0
        }
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() uses custom lint_command from buildConfig"() {
        given:
        def capturedCmd = null
        def system = mockSystem { String cmd ->
            capturedCmd = cmd
            return 0
        }
        // null context so withEnv is skipped and the fallback fires with lintCmd directly
        def adapter = new CppAdapter(null, system)

        when:
        boolean result = adapter.lint([lint_command: "clang-tidy src/*.cpp"])

        then:
        result == true
        capturedCmd == "clang-tidy src/*.cpp"
    }

    def "test() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == true
    }

    def "test() returns false when ctest fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new CppAdapter(mockContext, system)

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
        def adapter = new CppAdapter(null, system)

        when:
        boolean result = adapter.test([test_command: "ctest --test-dir build -V"])

        then:
        result == true
        capturedCmd == "ctest --test-dir build -V"
    }

    def "build() returns true with artifact build/ on success"() {
        given:
        def system = mockSystem(0)
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == true
        adapter.getArtifacts() == ["build/"]
    }

    def "build() returns false when cmake configure fails"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("cmake -B build") ? 1 : 0
        }
        def adapter = new CppAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == false
    }

    def "build() returns false when build command fails"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("cmake -B build")) return 0
            return 1
        }
        def adapter = new CppAdapter(mockContext, system)

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
                if (cmd.contains("cmake -B build")) return 0
                capturedCmd = cmd
                return 0
            }
            @Override
            def run_command(String cmd, int mode, int timeout) {
                if (cmd.contains("cmake -B build")) return 0
                capturedCmd = cmd
                return 0
            }
        }
        // null context so withEnv is skipped and the fallback fires with buildCmd directly
        def adapter = new CppAdapter(null, system)

        when:
        boolean result = adapter.build([build_command: "cmake --build build --config Release --parallel 8"])

        then:
        result == true
        capturedCmd == "cmake --build build --config Release --parallel 8"
    }

    def "getArtifacts() returns empty list before build"() {
        given:
        def system = mockSystem(0)
        def adapter = new CppAdapter(mockContext, system)

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts.isEmpty()
    }

    def "getName() returns 'cpp'"() {
        given:
        def system = mockSystem(0)
        def adapter = new CppAdapter(mockContext, system)

        when:
        String name = adapter.getName()

        then:
        name == "cpp"
    }
}
