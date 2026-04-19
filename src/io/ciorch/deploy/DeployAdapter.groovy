package io.ciorch.deploy

import java.io.Serializable

interface DeployAdapter extends Serializable {
    // Validate deploy configuration
    boolean validate(Map deployConfig, def context)

    // Pre-deploy steps (maintenance mode, cache flush, backup)
    boolean preDeploy(Map deployConfig, String environment)

    // Execute the deployment (rsync, CLI tool, API call, etc.)
    boolean deploy(Map deployConfig, String environment)

    // Post-deploy steps (cache warm, smoke test, maintenance mode off)
    boolean postDeploy(Map deployConfig, String environment)

    // Rollback to previous state
    boolean rollback(Map deployConfig, String environment)

    // Adapter identifier matching ciorch.yml deploy.adapter value
    String getAdapterId()
}
