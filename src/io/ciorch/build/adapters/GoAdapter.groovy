package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class GoAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    private List<String> artifacts = []

    GoAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context

        def result = system.run_command("go version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (result != 0) {
            context?.echo("GoAdapter: go not found in PATH")
            return false
        }

        if (buildConfig.go_version) {
            context?.echo("GoAdapter: requested go_version=${buildConfig.go_version} (informational only)")
        }

        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        def vetResult = system.run_command("go vet ./...", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (vetResult != 0) {
            context?.echo("GoAdapter: go vet failed")
            return false
        }

        def lintCheck = system.run_command("which golangci-lint", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (lintCheck == 0) {
            def lintResult = system.run_command("golangci-lint run ./...", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (lintResult != 0) {
                context?.echo("GoAdapter: golangci-lint failed")
                return false
            }
        } else {
            context?.echo("GoAdapter: golangci-lint not found, skipping (non-fatal)")
        }

        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd = buildConfig.test_command ?: "go test ./..."

        def result = null
        context?.withEnv(["CIORCH_CMD=${testCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String buildCmd = buildConfig.build_command ?: "go build ./..."

        def result = null
        context?.withEnv(["CIORCH_CMD=${buildCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result == 0) {
            artifacts = ["./..."]
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
        return "go"
    }
}
