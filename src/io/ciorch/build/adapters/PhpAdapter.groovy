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
        String overrideCmd = buildConfig.lint_command ?: config?.lintCommand
        if (overrideCmd) {
            def result = system.run_command(overrideCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (result != 0) {
                context?.echo("PhpAdapter: lint failed")
                return false
            }
            return true
        }

        def phpcsCheck = system.run_command("test -x vendor/bin/phpcs", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (phpcsCheck == 0) {
            def result = system.run_command("vendor/bin/phpcs", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (result != 0) {
                context?.echo("PhpAdapter: lint failed")
                return false
            }
        } else {
            def srcCheck = system.run_command("test -d src", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (srcCheck != 0) {
                context?.echo("PhpAdapter: src/ not found, skipping php -l fallback")
                return true
            }
            // php -l requires individual files; iterate all .php files under src/
            context?.echo("PhpAdapter: vendor/bin/phpcs not found, falling back to php -l on src/ files")
            def result = system.run_command(
                "find src -type f -name '*.php' -exec sh -c 'for f do php -l \"\$f\" || exit 1; done' sh {} +",
                SystemCall.SHOW_COMMAND_STATUS_VALUE
            )
            if (result != 0) {
                context?.echo("PhpAdapter: lint failed")
                return false
            }
        }
        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd = buildConfig.test_command ?: config?.testCommand ?: "vendor/bin/phpunit"

        def result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String defaultBuildCmd = "composer install --no-dev --optimize-autoloader"
        String buildCmd = buildConfig.build_command ?: config?.buildCommand ?: defaultBuildCmd
        artifacts = []

        def result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result == 0) {
            artifacts = resolveArtifacts(buildConfig, buildCmd, defaultBuildCmd)
            return true
        }
        return false
    }

    private List<String> resolveArtifacts(Map buildConfig, String buildCmd, String defaultBuildCmd) {
        Map rawBuild = (config?.raw?.ciorch?.build ?: [:]) as Map
        List<String> configuredArtifacts = normalizeArtifacts(buildConfig?.artifacts ?: rawBuild.artifacts)
        if (configuredArtifacts) {
            return configuredArtifacts
        }

        return (buildCmd == defaultBuildCmd) ? ["vendor/"] : []
    }

    private List<String> normalizeArtifacts(def configuredArtifacts) {
        if (!configuredArtifacts) {
            return []
        }

        if (configuredArtifacts instanceof Collection) {
            return configuredArtifacts.collect { it?.toString() }.findAll { it }
        }

        return [configuredArtifacts.toString()]
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
