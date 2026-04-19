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

    boolean dockerEnabled = false

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
        String buildArgsStr = buildArgs.collect { k, v -> "--build-arg ${k}=${v}" }.join(" ")

        def result = system.run_command(
            "docker build -t ${fullImageRef} ${buildArgsStr} -f ${dockerfile} ${buildContext}".trim(),
            SystemCall.SHOW_COMMAND_STATUS_VALUE
        )

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

    // Login to the registry (requires REGISTRY_USER and REGISTRY_TOKEN env vars to be set)
    boolean login() {
        if (!registry) {
            context?.echo("DockerAdapter: no registry configured, skipping login")
            return true
        }
        def result = context?.withCredentials([]) {
            system.run_command(
                'docker login ' + registry + ' -u "$REGISTRY_USER" -p "$REGISTRY_TOKEN"',
                SystemCall.SHOW_COMMAND_STATUS_VALUE
            )
        }
        return result == 0
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
