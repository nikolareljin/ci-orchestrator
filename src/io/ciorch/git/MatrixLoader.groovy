#!groovy

/*
Loads a GitFlow matrix from a built-in YAML strategy file or a custom file path,
and provides a merge utility for combining built-in and project-level overrides.
*/
package io.ciorch.git

import java.io.Serializable
import groovy.yaml.YamlSlurper

class MatrixLoader implements Serializable {

    // Load a built-in strategy from resources/matrix/<strategy>.yml
    // strategyName: "default-gitflow" | "github-flow" | "trunk-based"
    static Map loadBuiltin(String strategyName, def context = null) {
        String resourcePath = "matrix/${strategyName}.yml"
        try {
            String content = MatrixLoader.class.classLoader
                .getResourceAsStream(resourcePath)?.text
            if (!content) {
                context?.echo("MatrixLoader: built-in strategy '${strategyName}' not found")
                return [rules: [], branch_patterns: [:]]
            }
            return new YamlSlurper().parseText(content) as Map
        } catch (Exception ex) {
            context?.echo("MatrixLoader: error loading '${strategyName}': ${ex.message}")
            return [rules: [], branch_patterns: [:]]
        }
    }

    // Load a custom matrix YAML from an absolute file path
    static Map loadCustom(String filePath, def context = null) {
        try {
            File f = new File(filePath)
            if (!f.exists()) {
                context?.echo("MatrixLoader: custom matrix file not found: ${filePath}")
                return [rules: [], branch_patterns: [:]]
            }
            return new YamlSlurper().parseText(f.text) as Map
        } catch (Exception ex) {
            context?.echo("MatrixLoader: error loading custom matrix from '${filePath}': ${ex.message}")
            return [rules: [], branch_patterns: [:]]
        }
    }

    // Merge two loaded matrices; customRules take priority over builtinRules (by id)
    static Map merge(Map builtin, Map custom) {
        Map result = [
            branch_patterns: [:],
            rules: []
        ]
        // Merge branch_patterns: custom overrides builtin
        result.branch_patterns = (builtin.branch_patterns ?: [:]) + (custom.branch_patterns ?: [:])
        // Merge rules: custom rules replace builtin rules with same id
        Map builtinById = ((builtin.rules ?: []) as List).collectEntries { [(it.id): it] }
        Map customById = ((custom.rules ?: []) as List).collectEntries { [(it.id): it] }
        builtinById.putAll(customById)
        result.rules = builtinById.values().toList()
        return result
    }
}
