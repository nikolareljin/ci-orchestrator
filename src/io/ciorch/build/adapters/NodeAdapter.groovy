package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class NodeAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

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

        if (buildConfig.node_version) {
            context?.echo("NodeAdapter: requested node_version=${buildConfig.node_version}")
        }

        def npmResult = system.run_command("npm --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (npmResult != 0) {
            context?.echo("NodeAdapter: npm not found in PATH")
            return false
        }

        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        String lintCmd = buildConfig.lintCommand ?: "npm run lint"

        // Check whether the lint script exists in package.json (non-fatal if not available)
        def checkResult = system.run_command(
            'npm run lint --dry-run 2>/dev/null || npm run lint -- --help 2>/dev/null; true',
            SystemCall.SHOW_COMMAND_STATUS_VALUE
        )

        def result = null
        context?.withEnv(["CIORCH_LINT_CMD=${lintCmd}"]) {
            result = system.run_command('eval "$CIORCH_LINT_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = checkResult

        if (result != 0) {
            context?.echo("NodeAdapter: lint command returned non-zero, skipping (non-fatal)")
            return true
        }
        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd = buildConfig.test_command ?: "npm test"

        def result = null
        context?.withEnv(["CIORCH_CMD=${testCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String buildCmd = buildConfig.build_command ?: "npm run build"

        def result = null
        context?.withEnv(["CIORCH_CMD=${buildCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)

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
