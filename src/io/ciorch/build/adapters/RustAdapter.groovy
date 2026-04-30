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
        String overrideCmd = buildConfig.lint_command ?: config?.lintCommand
        if (overrideCmd) {
            def result = system.run_command(overrideCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (result != 0) {
                context?.echo("RustAdapter: lint failed")
                return false
            }
            return true
        }

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
        String testCmd = buildConfig.test_command ?: config?.testCommand ?: "cargo test"

        def result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String defaultBuildCmd = "cargo build --release"
        String buildCmd = buildConfig.build_command ?: config?.buildCommand ?: defaultBuildCmd

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

        if (buildCmd == defaultBuildCmd) {
            return ["target/release/"]
        }

        return []
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
        return "rust"
    }
}
