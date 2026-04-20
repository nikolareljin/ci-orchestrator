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

        if (buildConfig.java_version) {
            context?.echo("JavaAdapter: requested java_version=${buildConfig.java_version}")
        }

        // Determine build tool: config key takes precedence, then file detection
        if (buildConfig.build_tool) {
            this.buildTool = buildConfig.build_tool
        } else {
            this.buildTool = _detectBuildTool()
        }

        context?.echo("JavaAdapter: using build tool=${this.buildTool}")

        if (this.buildTool == "gradle") {
            def gradleResult = system.run_command("gradle --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (gradleResult != 0) {
                context?.echo("JavaAdapter: gradle not found in PATH")
                return false
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
        } else if (buildTool == "gradle") {
            lintCmd = "./gradlew check -q"
        } else {
            lintCmd = "mvn checkstyle:check -q"
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${lintCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
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
        } else if (buildTool == "gradle") {
            testCmd = "./gradlew test"
        } else {
            testCmd = "mvn test -q"
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${testCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String buildCmd
        if (buildConfig.build_command) {
            buildCmd = buildConfig.build_command
        } else if (buildTool == "gradle") {
            buildCmd = "./gradlew build -x test"
        } else {
            buildCmd = "mvn package -DskipTests -q"
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${buildCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
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

    @groovy.transform.CompileStatic
    private String _detectBuildTool() {
        File gradleFile = new File("build.gradle")
        File gradleKtsFile = new File("build.gradle.kts")
        if (gradleFile.exists() || gradleKtsFile.exists()) return "gradle"
        return "maven"
    }
}
