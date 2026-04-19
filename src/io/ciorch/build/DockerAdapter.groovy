package io.ciorch.build

import java.io.Serializable
import io.ciorch.core.SystemCall
import io.ciorch.core.Config

class DockerAdapter implements BuildAdapter {
    def context = null
    SystemCall system = null
    Config config = null

    String registry = ""       // e.g. "ghcr.io/myorg", "docker.io/myuser"
    String imageName = ""      // e.g. "my-app"
    String imageTag = "latest"
    String dockerfile = "Dockerfile"
    String buildContext = "."

    boolean dockerEnabled = false

    private List<String> artifacts = []

    DockerAdapter(def context, SystemCall system, Config cfg = null) {
        this.context = context
        this.system = system
        this.config = cfg
        if (cfg) {
            this.registry = cfg.dockerRegistry ?: ""
            this.imageName = cfg.dockerImage ?: ""
            this.dockerEnabled = cfg.dockerEnabled
        }
    }

    @Override
    boolean prepare(Map buildConfig, def ctx) {
        this.context = ctx ?: this.context
        // Merge buildConfig overrides
        if (buildConfig.registry) this.registry = buildConfig.registry
        if (buildConfig.image) this.imageName = buildConfig.image
        if (buildConfig.tag) this.imageTag = buildConfig.tag
        if (buildConfig.dockerfile) this.dockerfile = buildConfig.dockerfile
        if (buildConfig.context) this.buildContext = buildConfig.context

        // Verify docker is available
        def result = system.run_command("docker --version", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (result != 0) {
            context?.echo("DockerAdapter: docker not found in PATH")
            return false
        }
        return true
    }

    @Override
    boolean lint(Map buildConfig) {
        // Lint Dockerfile using hadolint if available
        def hadolintCheck = system.run_command("which hadolint", SystemCall.SHOW_COMMAND_STATUS_VALUE)
        if (hadolintCheck != 0) {
            context?.echo("DockerAdapter: hadolint not found, skipping Dockerfile lint")
            return true  // non-fatal
        }
        def result = system.run_command(
            "hadolint ${dockerfile}",
            SystemCall.SHOW_COMMAND_STATUS_VALUE
        )
        return result == 0
    }

    @Override
    boolean test(Map buildConfig) {
        // Docker images are tested post-build via a run check
        // Build must succeed before test is meaningful
        context?.echo("DockerAdapter: test step not applicable before build; use post-build smoke test")
        return true
    }

    @Override
    boolean build(Map buildConfig) {
        String fullImageRef = _buildImageRef()
        context?.echo("DockerAdapter: building ${fullImageRef}")

        Map buildArgs = (buildConfig.build_args ?: [:]) as Map

        // Build the arg list safely — each value passed via env var to avoid injection
        List<String> envBindings = ["CIORCH_IMAGE_REF=${fullImageRef}", "CIORCH_DOCKERFILE=${dockerfile}", "CIORCH_BUILD_CTX=${buildContext}"]
        List<String> argFlags = []
        buildArgs.eachWithIndex { entry, i ->
            envBindings << "CIORCH_BUILD_ARG_KEY_${i}=${entry.key}"
            envBindings << "CIORCH_BUILD_ARG_VAL_${i}=${entry.value}"
            argFlags << "--build-arg \"\$CIORCH_BUILD_ARG_KEY_${i}=\$CIORCH_BUILD_ARG_VAL_${i}\""
        }
        String argFlagsStr = argFlags.join(" ")

        def result = context?.withEnv(envBindings) {
            system.run_command(
                "docker build -t \"\$CIORCH_IMAGE_REF\" ${argFlagsStr} -f \"\$CIORCH_DOCKERFILE\" \"\$CIORCH_BUILD_CTX\"",
                SystemCall.SHOW_COMMAND_STATUS_VALUE
            )
        }

        if (result == 0) {
            artifacts = [fullImageRef]
            return true
        }
        return false
    }

    // Push the built image to the registry
    boolean push(String tag = null) {
        String fullImageRef = _buildImageRef(tag)
        def result = system.run_command(
            "docker push ${fullImageRef}",
            SystemCall.SHOW_COMMAND_STATUS_VALUE
        )
        return result == 0
    }

    // Tag image with an additional tag
    boolean tagImage(String sourceRef, String newTag) {
        String newRef = _buildImageRef(newTag)
        def result = system.run_command(
            "docker tag ${sourceRef} ${newRef}",
            SystemCall.SHOW_COMMAND_STATUS_VALUE
        )
        return result == 0
    }

    // Requires REGISTRY_USER and REGISTRY_TOKEN to be present in the environment.
    // Callers should inject these via withCredentials([usernamePassword(...)]) before calling login().
    boolean login() {
        if (!registry) {
            context?.echo("DockerAdapter: no registry configured, skipping login")
            return true
        }
        context?.withEnv(["CIORCH_REGISTRY=${registry}"]) {
            def result = system.run_command(
                'docker login "$CIORCH_REGISTRY" -u "$REGISTRY_USER" -p "$REGISTRY_TOKEN"',
                SystemCall.SHOW_COMMAND_STATUS_VALUE
            )
            return result == 0
        }
    }

    @Override
    List<String> getArtifacts() {
        return artifacts
    }

    @Override
    String getName() {
        return "docker"
    }

    private String _buildImageRef(String tag = null) {
        String t = tag ?: this.imageTag
        if (registry) {
            return "${registry}/${imageName}:${t}"
        }
        return "${imageName}:${t}"
    }
}
