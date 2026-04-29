package io.ciorch.tests

import io.ciorch.build.adapters.PhpAdapter
import io.ciorch.core.SystemCall
import spock.lang.Specification

class PhpAdapterTest extends Specification {

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

    def "prepare() returns true when php and composer are found"() {
        given:
        def system = mockSystem(0)
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == true
    }

    def "prepare() returns false when php is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("php --version") ? 1 : 0
        }
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "prepare() returns false when composer is not found"() {
        given:
        def system = mockSystem { String cmd ->
            cmd.contains("composer --version") ? 1 : 0
        }
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.prepare([:], mockContext)

        then:
        result == false
    }

    def "lint() happy path returns true when phpcs is available"() {
        given:
        def system = mockSystem(0)
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
    }

    def "lint() falls back to php -l when phpcs not found"() {
        given:
        def executedCommands = []
        def system = new SystemCall(null, "", "", "", "") {
            @Override
            def run_command(String cmd, int mode) {
                executedCommands << cmd
                // phpcs not found, fallback to php -l
                if (cmd.contains("test -x vendor/bin/phpcs")) return 1
                return 0
            }
            @Override
            def run_command(String cmd, int mode, int timeout) {
                executedCommands << cmd
                if (cmd.contains("test -x vendor/bin/phpcs")) return 1
                return 0
            }
        }
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
        executedCommands.any { it.contains("find src") && it.contains("php -l") }
    }

    def "lint() skips php -l fallback when src/ does not exist"() {
        given:
        def executedCommands = []
        def system = new SystemCall(null, "", "", "", "") {
            @Override
            def run_command(String cmd, int mode) {
                executedCommands << cmd
                if (cmd.contains("test -x vendor/bin/phpcs")) return 1  // phpcs absent
                if (cmd.contains("test -d src")) return 1               // src/ absent
                return 0
            }
            @Override
            def run_command(String cmd, int mode, int timeout) {
                executedCommands << cmd
                if (cmd.contains("test -x vendor/bin/phpcs")) return 1
                if (cmd.contains("test -d src")) return 1
                return 0
            }
        }
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == true
        !executedCommands.any { it.contains("find src") }
    }

    def "lint() returns false when phpcs is found but fails"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("test -x vendor/bin/phpcs")) return 0  // phpcs found
            if (cmd.contains("vendor/bin/phpcs")) return 1          // phpcs run fails
            return 0
        }
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "lint() returns false when phpcs absent and php -l fails"() {
        given:
        def system = mockSystem { String cmd ->
            if (cmd.contains("test -x vendor/bin/phpcs")) return 1  // phpcs absent
            if (cmd.contains("test -d src")) return 0               // src/ exists
            if (cmd.contains("find src")) return 1                  // php -l fails
            return 0
        }
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([:])

        then:
        result == false
    }

    def "lint() uses lint_command override when provided"() {
        given:
        def capturedCmd = null
        def system = mockSystem { String cmd -> capturedCmd = cmd; return 0 }
        def adapter = new PhpAdapter(null, system)

        when:
        boolean result = adapter.lint([lint_command: "php-cs-fixer fix --dry-run"])

        then:
        result == true
        capturedCmd == "php-cs-fixer fix --dry-run"
    }

    def "lint() returns false when lint_command override fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.lint([lint_command: "php-cs-fixer fix --dry-run"])

        then:
        result == false
    }

    def "test() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.test([:])

        then:
        result == true
    }

    def "test() returns false when phpunit fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new PhpAdapter(mockContext, system)

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
        def adapter = new PhpAdapter(null, system)

        when:
        boolean result = adapter.test([test_command: "php artisan test"])

        then:
        result == true
        capturedCmd == 'php artisan test'
    }

    def "build() happy path returns true"() {
        given:
        def system = mockSystem(0)
        def adapter = new PhpAdapter(mockContext, system)

        when:
        boolean result = adapter.build([:])

        then:
        result == true
    }

    def "build() returns false when composer install fails"() {
        given:
        def system = mockSystem(1)
        def adapter = new PhpAdapter(mockContext, system)

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
        def adapter = new PhpAdapter(null, system)

        when:
        boolean result = adapter.build([build_command: "composer install"])

        then:
        result == true
        capturedCmd == 'composer install'
    }

    def "getArtifacts() returns non-empty list after successful build"() {
        given:
        def system = mockSystem(0)
        def adapter = new PhpAdapter(mockContext, system)
        adapter.build([:])

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts != null
        !artifacts.isEmpty()
        artifacts.contains("vendor/")
    }

    def "getArtifacts() returns empty list before build"() {
        given:
        def system = mockSystem(0)
        def adapter = new PhpAdapter(mockContext, system)

        when:
        List<String> artifacts = adapter.getArtifacts()

        then:
        artifacts.isEmpty()
    }

    def "getName() returns 'php'"() {
        given:
        def system = mockSystem(0)
        def adapter = new PhpAdapter(mockContext, system)

        when:
        String name = adapter.getName()

        then:
        name == "php"
    }
}
