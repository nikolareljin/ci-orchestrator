package io.ciorch.build

import java.io.Serializable

interface BuildAdapter extends Serializable {
    // Validate environment (check required tools exist)
    boolean prepare(Map config, def context)

    // Run static analysis / linting
    boolean lint(Map config)

    // Run unit/integration tests
    boolean test(Map config)

    // Compile or bundle the artifact
    boolean build(Map config)

    // Return artifact file paths produced by build()
    List<String> getArtifacts()

    // Human-readable adapter identifier (e.g. "node", "php", "go")
    String getName()
}
