package io.ciorch.build.adapters

import java.io.Serializable
import io.ciorch.build.BuildAdapter
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class PythonAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    String packageManager = "pip"
    private String pythonCmd = "python3"

    private List<String> artifacts = []

    PythonAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context

        def result = system.run_command("python3 --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (result != 0) {
            result = system.run_command("python --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (result != 0) {
                context?.echo("PythonAdapter: python3/python not found in PATH")
                return false
            }
            this.pythonCmd = "python"
        } else {
            this.pythonCmd = "python3"
        }

        // Detect package manager
        def hasPoetryLock = system.run_command("test -f pyproject.toml && test -f poetry.lock", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (hasPoetryLock == 0) {
            this.packageManager = "poetry"
            context?.echo("PythonAdapter: detected package manager: poetry")
        } else {
            def hasUvLock = system.run_command("test -f pyproject.toml && test -f uv.lock", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (hasUvLock == 0) {
                this.packageManager = "uv"
                context?.echo("PythonAdapter: detected package manager: uv")
            } else {
                this.packageManager = "pip"
                context?.echo("PythonAdapter: detected package manager: pip")
            }
        }

        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        def ruffCheck = system.run_command("which ruff", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (ruffCheck == 0) {
            def result = system.run_command("ruff check .", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (result != 0) {
                context?.echo("PythonAdapter: ruff check failed")
                return false
            }
            return true
        }

        def flake8Check = system.run_command("which flake8", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (flake8Check == 0) {
            def result = system.run_command("flake8 .", SystemCall.SHOW_COMMAND_STATUS_VALUE)
            if (result != 0) {
                context?.echo("PythonAdapter: flake8 failed")
                return false
            }
            return true
        }

        context?.echo("PythonAdapter: no linter found (ruff, flake8), skipping lint (non-fatal)")
        return true
    }

    @Override
    boolean test(Map buildConfig) {
        String testCmd = buildConfig.test_command ?: "${pythonCmd} -m pytest"

        def result = null
        context?.withEnv(["CIORCH_CMD=${testCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(testCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

        return result == 0
    }

    @Override
    boolean build(Map buildConfig) {
        String defaultCmd = null
        if (buildConfig.build_command) {
            defaultCmd = buildConfig.build_command
        } else if (packageManager == "poetry") {
            defaultCmd = "poetry build"
        } else if (packageManager == "uv") {
            defaultCmd = "uv build"
        } else {
            // pip: no-op
            context?.echo("PythonAdapter: pip build — no distribution build step, skipping")
            return true
        }

        def result = null
        context?.withEnv(["CIORCH_CMD=${defaultCmd}"]) {
            result = system.run_command('eval "$CIORCH_CMD"', SystemCall.SHOW_COMMAND_STATUS_VALUE)
        }
        if (result == null) result = system.run_command(defaultCmd, SystemCall.SHOW_COMMAND_STATUS_VALUE)

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
        return "python"
    }
}
