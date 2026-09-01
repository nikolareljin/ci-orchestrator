package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class CSharpAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    private List<String> artifacts = []

    CSharpAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context

        def result = system.run_command("dotnet --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (result != 0) {
            context?.echo("CSharpAdapter: dotnet not found in PATH")
            return false
        }

        String dotnetVersion = buildConfig.dotnet_version ?: config?.toolVersions?.dotnet_version
        if (dotnetVersion) {
            context?.echo("CSharpAdapter: requested dotnet_version=${dotnetVersion} (informational only)")
        }

        def restoreResult = system.run_command("dotnet restore", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (restoreResult != 0) {
            context?.echo("CSharpAdapter: dotnet restore failed")
            return false
        }

        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        String lintCmd = buildConfig.lint_command ?: config?.lintCommand ?: "dotnet format --verify-no-changes"

        def result = system.run_command(lintCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result != 0) {
            context?.echo("CSharpAdapter: lint failed")
            return false
        }
        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd = buildConfig.test_command ?: config?.testCommand ?: "dotnet test"

        def result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String defaultBuildCmd = "dotnet publish -c Release -o publish/"
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

        String outputPath = extractOutputPath(buildCmd)
        if (outputPath) {
            return [outputPath]
        }

        if (buildCmd == defaultBuildCmd) {
            return ["publish/"]
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

    private String extractOutputPath(String buildCmd) {
        if (!buildCmd) {
            return null
        }

        def matcher = buildCmd =~ /(?:^|\s)(?:-o\s+|--output(?:\s+|=))(["']?)([^"'\s]+)\1/
        if (matcher.find()) {
            return matcher.group(2)
        }

        return null
    }

    @Override
    List<String> getArtifacts() {
        return artifacts
    }

    @Override
    String getName() {
        return "csharp"
    }
}
