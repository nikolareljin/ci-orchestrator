package io.ciorch.tests

import io.ciorch.build.adapters.RustAdapter
import io.ciorch.core.SystemCall
import spock.lang.Specification

class RustAdapterTest extends Specification {

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

    def "prepare() returns true when cargo is found and fetch succeeds"() {
        given:
        def system = mockSystem(0)
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
    }

    def "prepare() returns false when cargo is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("cargo --version") ? 1 : 0
        }
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() returns false when cargo fetch fails"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("cargo fetch") ? 1 : 0
        }
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "lint() happy path returns true when clippy passes and rustfmt available"() {
        given:
        def system = mockSystem(0)
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() returns false when cargo clippy fails"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("cargo clippy") ? 1 : 0
        }
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "lint() returns false when cargo fmt --check fails"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("cargo clippy")) return 0
            if (cmd.contains("which rustfmt")) return 0
            if (cmd.contains("cargo fmt --check")) return 1
            return 0
        }
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "lint() skips fmt non-fatally when rustfmt not available"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("cargo clippy")) return 0
            if (cmd.contains("which rustfmt")) return 1
            return 0
        }
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() uses lint_command override when provided"() {
        given:
        def capturedCmd = null
        def system = mockSystem { String cmd -> capturedCmd = cmd; return 0 }
        def adapter = new RustAdapter(null, system)

        when:
        boolean result = adapter.lint([lint_command: "my-rust-linter"])

        then:
        result == true
        capturedCmd == "my-rust-linter"
    }

    def "lint() returns false when lint_command override fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([lint_command: "my-rust-linter"])

        then:
        result == false
    }

    def "test() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == true
    }

    def "test() returns false when cargo test fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new RustAdapter(mockContext, system)

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
        def adapter = new RustAdapter(null, system)

        when:
        boolean result = adapter.test([test_command: "cargo test --release"])

        then:
        result == true
        capturedCmd == 'cargo test --release'
    }

    def "build() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new RustAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == true
    }

    def "build() returns false when cargo build --release fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new RustAdapter(mockContext, system)

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
        def adapter = new RustAdapter(null, system)

        when:
        boolean result = adapter.build([build_command: "cargo build --release --target x86_64-unknown-linux-musl"])

        then:
        result == true
        capturedCmd == 'cargo build --release --target x86_64-unknown-linux-musl'
    }

    def "getArtifacts() returns non-empty list after successful build"() {
        given:
        def system = mockSystem(0)
        def adapter = new RustAdapter(mockContext, system)
        adapter.build([:])

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts != null
        !artifacts.isEmpty()
        artifacts.contains("target/release/")
    }

    def "getArtifacts() returns empty list before build"() {
        given:
        def system = mockSystem(0)
        def adapter = new RustAdapter(mockContext, system)

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts.isEmpty()
    }

    def "getName() returns 'rust'"() {
        given:
        def system = mockSystem(0)
        def adapter = new RustAdapter(mockContext, system)

        when:
        String name = adapter.getName()

        then:
        name == "rust"
    }
}
