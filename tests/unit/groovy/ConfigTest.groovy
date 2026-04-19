package io.ciorch.tests

import io.ciorch.core.Config
import spock.lang.Specification

class ConfigTest extends Specification {

    def "default buildAdapter is empty string when no map loaded"() {
        when:
        Config config = new Config(null)

        then:
        config.buildAdapter == ""
    }

    def "default branchingStrategy is gitflow"() {
        when:
        Config config = new Config(null)

        then:
        config.branchingStrategy == "gitflow"
    }

    def "loadMap sets buildAdapter and deployAdapter"() {
        given:
        Config config = new Config(null)

        when:
        config.loadMap([
            build:  [adapter: "php"],
            deploy: [adapter: "wordpress"]
        ])

        then:
        config.buildAdapter == "php"
        config.deployAdapter == "wordpress"
    }

    def "loadMap sets docker config fields"() {
        given:
        Config config = new Config(null)

        when:
        config.loadMap([
            build: [
                docker: [
                    enabled:  true,
                    registry: "ghcr.io/myorg",
                    image:    "my-app"
                ]
            ]
        ])

        then:
        config.dockerEnabled == true
        config.dockerRegistry == "ghcr.io/myorg"
        config.dockerImage == "my-app"
    }

    def "loadMap sets branchingStrategy"() {
        given:
        Config config = new Config(null)

        when:
        config.loadMap([branching: [strategy: "trunk-based"]])

        then:
        config.branchingStrategy == "trunk-based"
    }

    def "loadMap sets slack notification config"() {
        given:
        Config config = new Config(null)

        when:
        config.loadMap([
            notify: [
                slack: [
                    channel: "#deploys",
                    token:   "xoxb-123"
                ]
            ]
        ])

        then:
        config.slackChannel == "#deploys"
        config.slackToken == "xoxb-123"
    }

    def "loadMap sets security flags"() {
        given:
        Config config = new Config(null)

        when:
        config.loadMap([
            security: [
                sast:   true,
                trivy:  false,
                gitleaks: true
            ]
        ])

        then:
        config.sastEnabled == true
        config.trivyEnabled == false
        config.gitleaksEnabled == true
    }

    def "loadMap sets deployEnvironments with nested host"() {
        given:
        Config config = new Config(null)

        when:
        config.loadMap([
            deploy: [
                adapter: "wordpress",
                environments: [
                    staging: [host: "staging.example.com"]
                ]
            ]
        ])

        then:
        config.deployEnvironments != null
        (config.deployEnvironments as Map).staging != null
        (config.deployEnvironments as Map).staging.host == "staging.example.com"
    }

    def "getEnvironment returns the correct environment map"() {
        given:
        Config config = new Config(null)
        config.loadMap([
            deploy: [
                environments: [
                    prod: [host: "prod.example.com", user: "deploy"]
                ]
            ]
        ])

        when:
        Map env = config.getEnvironment("prod")

        then:
        env.host == "prod.example.com"
        env.user == "deploy"
    }

    def "getEnvironment returns empty map for unknown env"() {
        given:
        Config config = new Config(null)
        config.loadMap([:])

        when:
        Map env = config.getEnvironment("nonexistent")

        then:
        env == [:]
    }

    def "loadMap captures tool versions"() {
        given:
        Config config = new Config(null)

        when:
        config.loadMap([
            build: [
                adapter:      "node",
                node_version: "20",
                php_version:  "8.3"
            ]
        ])

        then:
        config.getVersion("node_version") == "20"
        config.getVersion("php_version") == "8.3"
    }

    def "loadMap sets customMatrixPath from branching.custom_matrix"() {
        given:
        Config config = new Config(null)

        when:
        config.loadMap([
            branching: [
                strategy:      "custom",
                custom_matrix: "/path/to/matrix.yml"
            ]
        ])

        then:
        config.customMatrixPath == "/path/to/matrix.yml"
    }

    def "loadMap returns true on success"() {
        when:
        Config config = new Config(null)
        boolean result = config.loadMap([build: [adapter: "docker"]])

        then:
        result == true
    }

    def "toString contains adapter and strategy"() {
        given:
        Config config = new Config(null)
        config.loadMap([
            build:     [adapter: "go"],
            deploy:    [adapter: "custom"],
            branching: [strategy: "github-flow"]
        ])

        when:
        String s = config.toString()

        then:
        s.contains("go")
        s.contains("custom")
        s.contains("github-flow")
    }
}
