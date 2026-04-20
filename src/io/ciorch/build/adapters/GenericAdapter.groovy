package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class GenericAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    private List<String> artifacts = []

    GenericAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context

        // Fall back to raw build config from ciorch.yml when orchestrator passes [:]
        Map rawBuild = (config?.raw?.ciorch?.build ?: [:]) as Map
        String installCmd = buildConfig.install_command ?: buildConfig.prepare_command ?:
            rawBuild.install_command ?: rawBuild.prepare_command ?: null

        if (!installCmd) {
            context?.echo("GenericAdapter: no install command configured, skipping prepare")
            return true
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${installCmd}"]) {
            result = system.run_command(installCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(installCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean lint(Map buildConfig) {
        Map rawBuild = (config?.raw?.ciorch?.build ?: [:]) as Map
        String lintCmd = buildConfig.lint_command ?: config?.lintCommand ?: rawBuild.lint_command ?: null

        if (!lintCmd) {
            context?.echo("GenericAdapter: no lint_command configured, skipping lint (non-fatal)")
            return true
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${lintCmd}"]) {
            result = system.run_command(lintCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(lintCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result != 0) {
            context?.echo("GenericAdapter: lint failed")
            return false
        }
        return true
    }

    @Override
    boolean test(Map buildConfig) {
        Map rawBuild = (config?.raw?.ciorch?.build ?: [:]) as Map
        String testCmd = buildConfig.test_command ?: config?.testCommand ?: rawBuild.test_command ?: null

        if (!testCmd) {
            context?.echo("GenericAdapter: no test_command configured, skipping test (non-fatal)")
            return true
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${testCmd}"]) {
            result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        Map rawBuild = (config?.raw?.ciorch?.build ?: [:]) as Map
        String buildCmd = buildConfig.build_command ?: config?.buildCommand ?: rawBuild.build_command ?: null

        if (!buildCmd) {
            context?.echo("GenericAdapter: no build_command configured, skipping build (non-fatal)")
            artifacts = []
            return true
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${buildCmd}"]) {
            result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result == 0) {
            def configArtifacts = buildConfig.artifacts ?: rawBuild.artifacts
            artifacts = configArtifacts ? (List<String>) configArtifacts : []
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
        return "generic"
    }
}
