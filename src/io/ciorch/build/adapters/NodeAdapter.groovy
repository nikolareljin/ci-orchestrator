package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class NodeAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    String packageManager = "npm"
    private List<String> artifacts = []

    NodeAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context

        def result = system.run_command("node --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (result != 0) {
            context?.echo("NodeAdapter: node not found in PATH")
            return false
        }

        String nodeVersion = buildConfig.node_version ?: config?.toolVersions?.node_version
        if (nodeVersion) {
            context?.echo("NodeAdapter: requested node_version=${nodeVersion}")
        }

        // lockfile-based package manager detection
        def yarnLock = system.run_command("test -f yarn.lock", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        def pnpmLock = system.run_command("test -f pnpm-lock.yaml", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (yarnLock == 0) {
            packageManager = "yarn"
        } else if (pnpmLock == 0) {
            packageManager = "pnpm"
        } else {
            packageManager = "npm"
        }

        def pmResult = system.run_command("${packageManager} --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (pmResult != 0) {
            context?.echo("NodeAdapter: ${packageManager} not found in PATH")
            return false
        }

        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        String lintCmd = buildConfig.lint_command ?: config?.lintCommand ?: "${packageManager} run lint"

        def result = null
        context?.withEnv(["CIORCH_CMD=${lintCmd}"]) {
            result = system.run_command(lintCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(lintCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result != 0) {
            context?.echo("NodeAdapter: lint failed")
            return false
        }
        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd = buildConfig.test_command ?: config?.testCommand ?: "${packageManager} test"

        def result = null
        context?.withEnv(["CIORCH_CMD=${testCmd}"]) {
            result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String buildCmd = buildConfig.build_command ?: config?.buildCommand ?: "${packageManager} run build"

        def result = null
        context?.withEnv(["CIORCH_CMD=${buildCmd}"]) {
            result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result == 0) {
            artifacts = ["dist/"]
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
        return "node"
    }
}
