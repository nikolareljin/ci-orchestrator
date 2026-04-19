#!groovy

/*
Available types of branches.
*/
package io.ciorch.git

import java.io.Serializable

// ***************************************************
// Available types of branches.
class BranchType implements Serializable {
    static final String FEATURE = 'feature'
    static final String SPRINT = 'sprint'
    static final String FIX = 'fix'
    static final String HOTFIX = 'hotfix'
    static final String RELEASE = 'release'
    static final String DEVELOP = 'develop'
    static final String MASTER = 'master'
    static final String MAIN = 'main'    // alias for MASTER
    static final String TRUNK = 'trunk'  // trunk-based dev

    // Compiled versions of the branches.
    static final String SPRINT_BUILT = 'sprint-built'
    static final String RELEASE_BUILT = 'release-built'
    static final String DEVELOP_BUILT = 'develop-built'
    static final String MASTER_BUILT = 'master-built'
    static final String FIX_BUILT = 'fix-built'
    static final String HOTFIX_BUILT = 'hotfix-built'

    // Tmp branch. Will be moved to MASTER later on.
    static final String PRODUCTION = 'production'

    // Publication branch names (used only for the publication repo)
    static final String ENV_DEV = 'env_dev'
    static final String ENV_QA = 'env_qa'
    static final String ENV_PROD = 'env_prod'

    // Tmp PLUGIN branch type. Used to create PRs into ENV_* on Publication
    static final String PLUGIN = 'plugin'

    // ********************************************
    // Get deployment branch name from the given branch type.
    public static String getDeployBranch(String pluginBranchType) {
        String deployBranchName = ""
        String pluginBranchVal = pluginBranchType

        if (null != pluginBranchVal) {
            pluginBranchVal = pluginBranchVal.toLowerCase()

            switch (pluginBranchVal) {
                case BranchType.DEVELOP:
                case BranchType.SPRINT:
                    deployBranchName = BranchType.ENV_DEV
                    break
                case BranchType.RELEASE:
                    deployBranchName = BranchType.ENV_QA
                    break
                case BranchType.MASTER:
                case BranchType.MAIN:
                case BranchType.TRUNK:
                    deployBranchName = BranchType.ENV_QA
                    break

                // Process deployment environment branches
                case BranchType.ENV_DEV:
                    deployBranchName = BranchType.ENV_DEV
                    break
                case BranchType.ENV_QA:
                    deployBranchName = BranchType.ENV_QA
                    break
                case BranchType.ENV_PROD:
                    deployBranchName = BranchType.ENV_PROD
                    break

                default:
                    deployBranchName = ""
                    break
            }
        }

        return deployBranchName
    }
}
