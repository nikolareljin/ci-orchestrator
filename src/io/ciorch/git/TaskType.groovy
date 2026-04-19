#!groovy

/*
Task types we will run for some statuses.
*/
package io.ciorch.git

import java.io.Serializable

// ***************************************************
// Task types we will run for some statuses.
class TaskType implements Serializable {
    // General CI tasks
    static final String TEST = 'test'
    static final String BUILD = 'build'
    static final String TAG = 'tag'
    static final String UPDATE = 'update'
    static final String DEPLOY = 'deploy'
    static final String COMMIT = 'commit'
    static final String PUSH = 'push'

    // Release pipeline
    static final String BUILD_RELEASE = 'build_release'
    static final String RELEASE = 'release'

    // Quality gates
    static final String LINT = 'lint'
    static final String E2E = 'e2e'
    static final String SCAN = 'scan'

    // Artifact management
    static final String ARTIFACT = 'artifact'

    // Notifications
    static final String NOTIFY = 'notify'

    // No-op / default
    static final String DEFAULT = ''
}
