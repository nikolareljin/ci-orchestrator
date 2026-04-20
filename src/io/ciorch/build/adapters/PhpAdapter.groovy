package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class PhpAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    private List<String> artifacts = []

    PhpAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context

        def phpResult = system.run_command("php --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (phpResult != 0) {
            context?.echo("PhpAdapter: php not found in PATH")
            return false
        }

        def composerResult = system.run_command("composer --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (composerResult != 0) {
            context?.echo("PhpAdapter: composer not found in PATH")
            return false
        }

        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        def phpcsCheck = system.run_command("test -x vendor/bin/phpcs", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (phpcsCheck == 0) {
            def result = system.run_command("vendor/bin/phpcs", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (result != 0) {
                context?.echo("PhpAdapter: lint failed")
                return false
            }
        } else {
            context?.echo("PhpAdapter: vendor/bin/phpcs not found, falling back to php -l src/")
            def result = system.run_command("php -l src/", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (result != 0) {
                context?.echo("PhpAdapter: lint failed")
                return false
            }
        }
        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd = buildConfig.test_command ?: "vendor/bin/phpunit"

        def result = null
        context?.withEnv(["CIORCH_CMD=${testCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String buildCmd = buildConfig.build_command ?: "composer install --no-dev --optimize-autoloader"

        def result = null
        context?.withEnv(["CIORCH_CMD=${buildCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result == 0) {
            artifacts = ["vendor/"]
            return true
        }
        return false
    }

    @Override
    List<String> getArtifacts() {
        return artifacts
    }

    @Override
    String getName() {
        return "php"
    }
}
