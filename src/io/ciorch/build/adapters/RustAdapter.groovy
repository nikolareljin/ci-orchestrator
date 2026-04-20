package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class RustAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    private List<String> artifacts = []

    RustAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context

        def result = system.run_command("cargo --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (result != 0) {
            context?.echo("RustAdapter: cargo not found in PATH")
            return false
        }

        def fetchResult = system.run_command("cargo fetch", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (fetchResult != 0) {
            context?.echo("RustAdapter: cargo fetch failed")
            return false
        }

        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        def clippyResult = system.run_command("cargo clippy -- -D warnings", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (clippyResult != 0) {
            context?.echo("RustAdapter: cargo clippy failed")
            return false
        }

        def fmtCheck = system.run_command("which rustfmt", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (fmtCheck == 0) {
            def fmtResult = system.run_command("cargo fmt --check", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (fmtResult != 0) {
                context?.echo("RustAdapter: cargo fmt --check failed")
                return false
            }
        } else {
            context?.echo("RustAdapter: rustfmt not found, skipping cargo fmt --check (non-fatal)")
        }

        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd = buildConfig.test_command ?: "cargo test"

        def result = null
        context?.withEnv(["CIORCH_CMD=${testCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String buildCmd = buildConfig.build_command ?: "cargo build --release"

        def result = null
        context?.withEnv(["CIORCH_CMD=${buildCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result == 0) {
            artifacts = ["target/release/"]
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
        return "rust"
    }
}
