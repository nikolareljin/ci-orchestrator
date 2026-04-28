package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class JavaAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    String buildTool = "maven"
    String gradleCmd = "./gradlew"
    private List<String> artifacts = []

    JavaAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context

        def result = system.run_command("java -version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (result != 0) {
            context?.echo("JavaAdapter: java not found in PATH")
            return false
        }

        String javaVersion = buildConfig.java_version ?: config?.toolVersions?.java_version
        if (javaVersion) {
            context?.echo("JavaAdapter: requested java_version=${javaVersion}")
        }

        // Determine build tool: buildConfig key → ciorch.yml raw → file detection
        String toolHint = buildConfig.build_tool ?: config?.raw?.ciorch?.build?.build_tool
        if (toolHint) {
            String requested = toolHint as String
            if (requested == "gradle" || requested == "maven") {
                this.buildTool = requested
            } else {
                context?.echo("JavaAdapter: unknown build_tool '${requested}', defaulting to maven")
                this.buildTool = "maven"
            }
        } else {
            this.buildTool = _detectBuildTool()
        }

        context?.echo("JavaAdapter: using build tool=${this.buildTool}")

        if (this.buildTool == "gradle") {
            // prefer Gradle wrapper (runs on agent workspace); fall back to system gradle
            def wrapperCheck = system.run_command("test -f gradlew", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (wrapperCheck == 0) {
                system.run_command("chmod +x gradlew", SystemCall.SHOW_COMMAND_STATUS_VALUE)
                this.gradleCmd = "./gradlew"
                context?.echo("JavaAdapter: using Gradle wrapper (./gradlew)")
            } else {
                def gradleResult = system.run_command("gradle --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
                if (gradleResult != 0) {
                    context?.echo("JavaAdapter: neither ./gradlew nor gradle found in PATH")
                    return false
                }
                this.gradleCmd = "gradle"
                context?.echo("JavaAdapter: using system gradle")
            }
        } else {
            def mvnResult = system.run_command("mvn --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (mvnResult != 0) {
                context?.echo("JavaAdapter: mvn not found in PATH")
                return false
            }
        }

        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        String lintCmd
        if (buildConfig.lint_command) {
            lintCmd = buildConfig.lint_command
        } else if (config?.lintCommand) {
            lintCmd = config.lintCommand
        } else if (buildTool == "gradle") {
            lintCmd = "${gradleCmd} check -q"
        } else {
            lintCmd = "mvn checkstyle:check -q"
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${lintCmd}"]) {
            result = system.run_command(lintCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(lintCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result != 0) {
            context?.echo("JavaAdapter: lint failed")
            return false
        }
        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd
        if (buildConfig.test_command) {
            testCmd = buildConfig.test_command
        } else if (config?.testCommand) {
            testCmd = config.testCommand
        } else if (buildTool == "gradle") {
            testCmd = "${gradleCmd} test"
        } else {
            testCmd = "mvn test -q"
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
        String buildCmd
        if (buildConfig.build_command) {
            buildCmd = buildConfig.build_command
        } else if (config?.buildCommand) {
            buildCmd = config.buildCommand
        } else if (buildTool == "gradle") {
            buildCmd = "${gradleCmd} build -x test"
        } else {
            buildCmd = "mvn package -DskipTests -q"
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${buildCmd}"]) {
            result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result == 0) {
            artifacts = (buildTool == "gradle") ? ["build/libs/"] : ["target/"]
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
        return "java"
    }

    private String _detectBuildTool() {
        def hasGradle = system.run_command("test -f build.gradle", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        def hasGradleKts = system.run_command("test -f build.gradle.kts", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (hasGradle == 0 || hasGradleKts == 0) return "gradle"
        return "maven"
    }
}
