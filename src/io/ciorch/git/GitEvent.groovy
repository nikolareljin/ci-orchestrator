#!groovy

/*
Value object holding a parsed Git/CI event.
*/
package io.ciorch.git

import java.io.Serializable

class GitEvent implements Serializable {
    String srcBranch = ""
    String srcType = ""
    String dstBranch = ""
    String dstType = ""
    String eventType = ""
    boolean merged = false
    int prNumber = 0
    String sha = ""
    String repoUrl = ""
    String repoName = ""
    String triggeredBy = ""
    String triggeredByAvatar = ""
    String commitMessage = ""
    String versionString = ""

    GitEvent() {}

    GitEvent(Map data) {
        data.each { k, v -> if (this.hasProperty(k)) this[k] = v }
    }

    @Override
    String toString() {
        return "GitEvent[src=${srcBranch}(${srcType}) -> dst=${dstBranch}(${dstType}), event=${eventType}, merged=${merged}]"
    }
}
