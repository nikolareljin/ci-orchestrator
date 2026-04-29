package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class CppAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    private String buildBackend = "make"
    private List<String> artifacts = []

    CppAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context

        def cmakeResult = system.run_command("cmake --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (cmakeResult != 0) {
            context?.echo("CppAdapter: cmake not found in PATH")
            return false
        }

        // Detect build backend: prefer ninja if present, fall back to make
        def ninjaResult = system.run_command("ninja --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (ninjaResult == 0) {
            this.buildBackend = "ninja"
            context?.echo("CppAdapter: using ninja as build backend")
        } else {
            def makeResult = system.run_command("make --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (makeResult != 0) {
                context?.echo("CppAdapter: neither ninja nor make found in PATH")
                return false
            }
            this.buildBackend = "make"
            context?.echo("CppAdapter: using make as build backend")
        }

        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        String lintCmd = buildConfig.lint_command ?: config?.lintCommand ?: null

        if (lintCmd) {
            def result = null
            context?.withEnv(["CIORCH_CMD=${lintCmd}"]) {
                result = system.run_command(lintCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
            }
            if (result == null) result = system.run_command(lintCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (result != 0) {
                context?.echo("CppAdapter: lint failed")
                return false
            }
            return true
        }

        // Check if cppcheck is available
        def whichResult = system.run_command("which cppcheck", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (whichResult != 0) {
            context?.echo("CppAdapter: cppcheck not found, skipping lint (non-fatal)")
            return true
        }

        def result = system.run_command("cppcheck --enable=warning .", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (result != 0) {
            context?.echo("CppAdapter: cppcheck failed")
            return false
        }
        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd = buildConfig.test_command ?: config?.testCommand ?: "ctest --test-dir build"

        def result = null
        context?.withEnv(["CIORCH_CMD=${testCmd}"]) {
            result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        // Configure step: pass -G Ninja when ninja was detected in prepare()
        String generatorFlag = (buildBackend == "ninja") ? " -G Ninja" : ""
        def configureResult = system.run_command(
            "cmake -B build -DCMAKE_BUILD_TYPE=Release${generatorFlag}",
            SystemCall.SHOW_COMMAND_STATUS_VALUE
        )
        if (configureResult != 0) {
            context?.echo("CppAdapter: cmake configure failed")
            return false
        }

        String buildCmd = buildConfig.build_command ?: config?.buildCommand ?: "cmake --build build --parallel 4"

        def result = null
        context?.withEnv(["CIORCH_CMD=${buildCmd}"]) {
            result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(buildCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        if (result == 0) {
            artifacts = ["build/"]
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
        return "cpp"
    }
}
