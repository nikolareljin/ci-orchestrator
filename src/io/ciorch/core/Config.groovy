package io.ciorch.core

import java.io.Serializable
import groovy.yaml.YamlSlurper

class Config implements Serializable {
    // Parsed ciorch.yml content
    private Map data = [:]

    // Build config
    String buildAdapter = ""        // node|go|java-maven|java-gradle|dotnet|python|php|rust|cpp|docker
    String testCommand = ""
    String lintCommand = ""
    String buildCommand = ""
    String dockerRegistry = ""
    String dockerImage = ""
    boolean dockerEnabled = false

    // Deploy config
    String deployAdapter = ""       // wordpress|drupal|symfony|django|fastapi|sugarcrmm|dotnetnuke|custom
    Map deployEnvironments = [:]    // environment name → {host, user, path}

    // Branching
    String branchingStrategy = "gitflow"  // gitflow|github-flow|trunk-based|custom
    String customMatrixPath = ""

    // Notifications
    String slackChannel = ""
    String slackToken = ""

    // Security
    boolean sastEnabled = false
    boolean trivyEnabled = false
    boolean gitleaksEnabled = false

    // Runtime / tool versions (populated from build.* in ciorch.yml)
    Map toolVersions = [:]          // e.g. [node_version: "20", php_version: "8.3"]

    // Platform-specific overrides
    Map platformOverrides = [:]

    // Raw data access
    Map raw = [:]

    def context = null

    Config(def context = null) {
        this.context = context
    }

    // Load and parse ciorch.yml from the given path
    boolean load(String yamlPath) {
        try {
            File f = new File(yamlPath)
            if (!f.exists()) {
                context?.echo("Config: ciorch.yml not found at ${yamlPath}")
                return false
            }
            Map parsed = new YamlSlurper().parseText(f.text) as Map
            this.raw = parsed
            Map ciorch = (parsed.ciorch ?: [:]) as Map
            this.data = ciorch
            _populate(ciorch)
            return true
        } catch (Exception ex) {
            context?.echo("Config: error loading ciorch.yml: ${ex.message}")
            return false
        }
    }

    // Load from a Map directly (useful for testing)
    boolean loadMap(Map ciorch) {
        this.data = ciorch
        _populate(ciorch)
        return true
    }

    private void _populate(Map ciorch) {
        Map build = (ciorch.build ?: [:]) as Map
        buildAdapter = (build.adapter ?: "") as String
        testCommand = (build.test_command ?: "") as String
        lintCommand = (build.lint_command ?: "") as String
        buildCommand = (build.build_command ?: "") as String

        Map docker = (build.docker ?: [:]) as Map
        dockerEnabled = (docker.enabled ?: false) as boolean
        dockerRegistry = (docker.registry ?: "") as String
        dockerImage = (docker.image ?: "") as String

        // Tool versions: any key ending in _version
        build.each { k, v ->
            if (k.toString().endsWith('_version')) {
                toolVersions[k] = v
            }
        }

        Map deploy = (ciorch.deploy ?: [:]) as Map
        deployAdapter = (deploy.adapter ?: "") as String
        deployEnvironments = (deploy.environments ?: [:]) as Map

        Map branching = (ciorch.branching ?: [:]) as Map
        branchingStrategy = (branching.strategy ?: "gitflow") as String
        customMatrixPath = (branching.custom_matrix ?: "") as String

        Map notify = (ciorch.notify ?: [:]) as Map
        Map slack = (notify.slack ?: [:]) as Map
        slackChannel = (slack.channel ?: "") as String
        slackToken = (slack.token ?: "") as String

        Map security = (ciorch.security ?: [:]) as Map
        sastEnabled = (security.sast ?: false) as boolean
        trivyEnabled = (security.trivy ?: false) as boolean
        gitleaksEnabled = (security.gitleaks ?: false) as boolean

        platformOverrides = (ciorch.platform ?: [:]) as Map
    }

    // Get deploy environment config by name
    Map getEnvironment(String envName) {
        return (deployEnvironments[envName] ?: [:]) as Map
    }

    // Get a tool version value (e.g. getVersion("node_version") → "20")
    String getVersion(String toolKey) {
        return (toolVersions[toolKey] ?: "") as String
    }

    @Override
    String toString() {
        return "Config[adapter=${buildAdapter}, deploy=${deployAdapter}, strategy=${branchingStrategy}]"
    }
}
