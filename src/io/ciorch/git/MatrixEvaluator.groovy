#!groovy

/*
Evaluates a loaded matrix against a GitEvent to determine the task list to execute.
Rules are matched by dst_branch, src_branch, event type, and optional merged flag.
The highest-priority matching rule wins.
*/
package io.ciorch.git

import java.io.Serializable
import groovy.transform.CompileStatic

class MatrixEvaluator implements Serializable {
    List<Map> rules
    Map<String, String> branchPatterns

    MatrixEvaluator(Map matrixData) {
        this.rules = (matrixData.rules ?: []) as List<Map>
        this.branchPatterns = (matrixData.branch_patterns ?: [:]) as Map<String, String>
    }

    // Classify a raw branch name into a branch type token using branchPatterns.
    // Returns the matched type key (e.g. "feature", "sprint") or the original name if no pattern matches.
    String classifyBranch(String branchName) {
        if (!branchName) return ""
        for (Map.Entry<String, String> entry : branchPatterns.entrySet()) {
            if (branchName.matches(entry.value)) {
                return entry.key
            }
        }
        return branchName  // fallback: use raw name
    }

    // Find the highest-priority matching rule for the given event.
    // Returns the rule Map or null if no match.
    Map findRule(String dstBranch, String srcBranch, String event, boolean merged = false) {
        String dstType = classifyBranch(dstBranch)
        String srcType = srcBranch ? classifyBranch(srcBranch) : null

        List<Map> matches = rules.findAll { Map rule ->
            boolean dstMatch = (rule.dst_branch == null || rule.dst_branch == dstType || rule.dst_branch == dstBranch)
            boolean srcMatch = (rule.src_branch == null || rule.src_branch == srcType || rule.src_branch == srcBranch)
            boolean eventMatch = (rule.event == null || rule.event == event)
            boolean mergedMatch = (!rule.containsKey('merged') || rule.merged == merged)
            return dstMatch && srcMatch && eventMatch && mergedMatch
        }

        if (!matches) return null

        // Return highest priority rule (higher number = higher priority)
        return matches.max { Map rule -> (rule.priority ?: 0) as int }
    }

    // Return the task list for the matching rule, or empty list if no match.
    List<String> getTasks(String dstBranch, String srcBranch, String event, boolean merged = false) {
        Map rule = findRule(dstBranch, srcBranch, event, merged)
        return rule ? (rule.tasks ?: []) as List<String> : []
    }

    // Convenience: evaluate from a GitEvent.
    List<String> evaluateEvent(GitEvent event) {
        return getTasks(event.dstBranch, event.srcBranch, event.eventType, event.merged)
    }
}
