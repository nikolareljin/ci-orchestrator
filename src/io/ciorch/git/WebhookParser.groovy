#!groovy
/*
WebhookParser — parses a GitHub webhook JSON payload into structured fields.
Supports pull_request, push, create (tag), and release events.
*/
package io.ciorch.git

import java.io.Serializable
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import io.ciorch.git.EventType
import io.ciorch.git.BranchType
import io.ciorch.core.Version

class WebhookParser implements Serializable {

    public Map json = null
    public def context = null

    // New Branch
    public boolean isNewBranch = false
    // Push and Forced push
    public boolean isForcedPush = false
    public boolean isPush = false
    public boolean isTag = false
    // PR
    public boolean isPR = false
    public boolean isOpenedPR = false
    public boolean isClosedPR = false
    public boolean isMerged = false
    // PR Review
    public boolean isReviewRequested = false
    public boolean isApprovedPR = false
    // Should this payload be processed?
    public boolean shouldBeProcessed = false

    public String repositoryUrl = ""
    public String repositorySshUrl = ""
    public String ssh_url = ""
    public String git_url = ""
    public String pulls_url = ""
    public String statuses_url = ""
    public String url = ""

    // Merge commit SHA
    public String sha = ""
    // Repo path (tmp folder path)
    public String repoPath = ""

    public String srcBranch = ""
    public String srcType = ""
    public String dstBranch = ""
    public String dstType = ""
    public String branch = ""

    public String repoName = ""

    // Github commit message
    public String gitMessage = ""
    // Who merged / opened the PR
    public String mergedByName = ""
    public String mergedByAvatar = ""
    public String openedByName = ""
    public String openedByAvatar = ""

    // Who approved the PR?
    public String approvedByName = ""
    // Approval / disapproval message
    public String approvalMessage = ""

    // Passed to downstream CI steps
    public String triggeredByAvatar = ""
    public String triggeredBy = ""

    // PR dates
    public String created_at = ""
    public String updated_at = ""
    public String closed_at = ""
    public String merged_at = ""
    public String pushed_at = ""

    public int prNumber = 0
    public String prUrl = ""
    public String action = ""
    public String actionType = ""

    ArrayList<String> reviewersList = null

    public String getStatusURL = ""

    public Version version = null

    public String apiToken = ""
    public String apiUser = ""

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructor.
     * @param context  Jenkins pipeline context (the {@code this} from a Jenkinsfile)
     * @param jsonPayload  pre-parsed webhook payload map
     * @param apiToken  GitHub service-account token (optional)
     * @param apiUser   GitHub service-account username (optional)
     */
    WebhookParser(def context, Map jsonPayload, String apiToken = "", String apiUser = "") {
        this.context = context
        this.json = jsonPayload
        this.apiToken = apiToken
        this.apiUser = apiUser
        this.prNumber = 0
        this.action = EventType.DEFAULT
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Populates all fields by analysing the webhook payload.
     * @param allowNonSemver whether non-semver version strings are accepted
     */
    public setValues(boolean allowNonSemver = false) {
        this.action = this.getAction()
        this.isPush = this.checkPush()
        this.isForcedPush = this.checkForcedPush()
        this.isPR = this.checkPR()
        this.getDates()

        // Src branch
        this.srcBranch = this.getSourceBranch()
        this.srcType = this.getBranchType(this.srcBranch)

        // Dst branch
        this.dstBranch = this.getDestinationBranch()
        this.dstType = this.getBranchType(this.dstBranch)

        // Repo info
        this.repositoryUrl = (String) this.getRepositoryUrl()
        this.repositorySshUrl = (String) this.getRepositorySshUrl()
        this.repoName = (String) this.getRepoName()
        this.prUrl = this.getPRUrl()
        this.sha = this.getSha()

        // Git commit message
        this.gitMessage = (String) this.getMessage()

        this.checkReview()
        this.mergedByName = (String) this.checkMerged()
        this.openedByName = (String) this.checkOpened()
        this.isNewBranch = (boolean) this.checkNewBranch()

        this.gitMessage = (String) this.getMessage()

        if (this.isNewBranch) {
            this.action = EventType.NEW
            this.setAction(EventType.NEW)
            this.context.echo "************** This is the Action: ${this.action}"

            // Update created_at with pushed_at (new branches don't have created_at)
            this.created_at = this.pushed_at
        }

        this.context.echo "** src branch: ${this.srcBranch}" as String

        String versionVal = this.getVersionFromBranch(this.srcBranch)
        this.context.echo "** Version val: ${versionVal}" as String
        if ("" != versionVal) {
            this.version = new Version(this.context, versionVal)
            this.version.setNonSemver(allowNonSemver)
        }

        if (EventType.OPEN_PR == this.action || EventType.CLOSED_PR == this.action) {
            this.actionType = this.action
        }

        this.context.echo "** WEBHOOK PARSER ACTION: ${this.action} (${this.actionType}) **"
        this.setShouldBeProcessed()
    }

    /**
     * Sets the shouldBeProcessed flag.
     */
    public void setShouldBeProcessed() {
        this.shouldBeProcessed = this.processJob() as boolean
        this.context.echo "*** Should be processed?: ${this.shouldBeProcessed} ***"
    }

    /**
     * Set action value.
     */
    public setAction(String action) {
        if (null != action && "" != action) {
            this.action = action
        }
    }

    /**
     * Resets the JSON map to prevent NonSerialized issues.
     */
    public resetJson() {
        this.context.echo("Reset Json @ WebhookParser")
        this.json = null
    }

    /**
     * Converts this parsed payload into a {@link GitEvent} value object.
     * @return populated GitEvent
     */
    GitEvent toGitEvent() {
        return new GitEvent([
            srcBranch      : this.srcBranch,
            srcType        : this.srcType,
            dstBranch      : this.dstBranch,
            dstType        : this.dstType,
            eventType      : this.actionType ?: this.action,
            merged         : this.isMerged,
            prNumber       : this.prNumber,
            sha            : this.sha,
            repoUrl        : this.repositoryUrl,
            repoName       : this.repoName,
            triggeredBy    : this.triggeredBy,
            triggeredByAvatar: this.triggeredByAvatar,
            commitMessage  : this.gitMessage,
            versionString  : this.version?.get() ?: ""
        ])
    }

    // -------------------------------------------------------------------------
    // Action / event detection
    // -------------------------------------------------------------------------

    /**
     * Returns the action value from the payload.
     */
    public String getAction() {
        String actionVal = ""

        this.action = EventType.DEFAULT

        Map json = this.json
        def action = json.action

        if (null != action) {
            this.action = "${action}" as String
            actionVal = this.action
        } else {
            this.action = EventType.DEFAULT as String
            actionVal = this.action
        }

        def ref = json.ref
        def before = json.before
        def after = json.after
        def ref_type = json.ref_type
        def base_ref = json.base_ref

        if (null != before) {
            if (null != ref && null == ref_type && ("0000000000000000000000000000000000000000" == (String) before ||
                    (null != base_ref && "refs/heads/develop" == base_ref.toString()))) {
                this.action = EventType.NEW as String
                actionVal = this.action
                return EventType.NEW as String
            }
            if (null != ref && null == ref_type && ("0000000000000000000000000000000000000000" == (String) after &&
                    null == base_ref)) {
                this.action = EventType.UPDATED as String
                actionVal = this.action
                return EventType.UPDATED as String
            }
        } else {
            if (null != ref && null != ref_type) {
                if ("branch" == (String) ref_type) {
                    this.action = EventType.NEW as String
                    actionVal = this.action
                    return EventType.NEW as String
                }
            }
        }

        action = null
        ref = null
        before = null
        ref_type = null
        base_ref = null

        return actionVal as String
    }

    // -------------------------------------------------------------------------
    // Push / tag detection
    // -------------------------------------------------------------------------

    /**
     * Returns true when the webhook represents a branch push.
     */
    private boolean checkPush() {
        Map json = this.json
        this.isPush = false

        def ref = json.ref
        String refVal = ""

        def before = json.before
        String beforeVal = ""
        def after = json.after

        def created = json.created
        def base_ref = json.base_ref

        if (null == ref) {
            this.isPush = false

            before = null
            after = null
            ref = null

            return false
        }

        if (null != ref) {
            refVal = (String) ref

            if (refVal.contains("/heads/")) {
                this.isPush = true

                before = null
                after = null
                ref = null

                return true
            }
        } else {
            if (null != before) {
                beforeVal = (String) before
                if ("0000000000000000000000000000000000000000" == beforeVal ||
                        (null != created && true == created) ||
                        (null != base_ref && "refs/heads/develop" == base_ref.toString())
                ) {
                    this.isPush = true
                    this.action = EventType.NEW

                    before = null
                    after = null
                    ref = null

                    return true
                }
            }
        }

        before = null
        after = null
        ref = null

        this.isPush = false
        return false
    }

    /**
     * Returns true when the push was a force-push.
     */
    private boolean checkForcedPush() {
        Map json = this.json
        boolean is_push = this.isPush

        String message = this.getMessage()

        if (is_push) {
            def forced = json.forced
            boolean forcedVal = false

            if (null != forced) {
                forcedVal = (boolean) forced
                if (forcedVal || '.Forced Update.' == message) {
                    this.actionType = EventType.FORCED_PUSH
                    this.isForcedPush = true

                    return true
                }
            }
        }

        this.isForcedPush = false
        return false
    }

    /**
     * Returns true when the webhook represents a new tag being pushed.
     */
    private boolean checkTag() {
        Map json = this.json
        def ref = json.ref

        if (null == ref) {
            this.isTag = false
            return false
        }

        if (ref.contains("/tags/")) {
            this.isTag = true
            return true
        } else {
            def ref_type = json.ref_type
            String ref_typeVal
            if (null != ref_type) {
                ref_typeVal = (String) ref_type

                if ("tag" == ref_typeVal) {
                    this.actionType = EventType.TAGGED
                    this.isTag = true
                    return true
                }
            }
        }
        this.isTag = false
        return false
    }

    // -------------------------------------------------------------------------
    // Pull-request detection
    // -------------------------------------------------------------------------

    /**
     * Returns true when the webhook payload represents a pull-request event.
     */
    private boolean checkPR() {
        Map json = this.json

        def pull_request = json.pull_request

        if (null == pull_request) {
            this.isPR = false
            return false
        }

        def action = json.action
        String actionVal = ""

        def pr_number = json.get("number")

        this.context.echo "check PR: action: ${action.toString()}"

        if (null == pr_number && null != pull_request) {
            pr_number = pull_request.get("number")
        }
        this.prNumber = (null != pr_number) ? pr_number.toInteger() : 0

        if (null != action) {
            actionVal = action as String
        }

        // Check if reviewers were requested
        if (EventType.REVIEW_REQUESTED == actionVal) {
            def requested_reviewers = pull_request.requested_reviewers
            ArrayList<String> reviewersList = new ArrayList<String>()
            for (def reviewer in requested_reviewers) {
                reviewersList.add(reviewer.login as String)
            }
            this.reviewersList = reviewersList
            this.actionType = EventType.REVIEW_REQUESTED
            requested_reviewers = null
        }

        // Process Open PR / Synchronize
        if (
            (EventType.OPEN_PR == actionVal || EventType.SYNC == actionVal || EventType.OPEN_PR == this.action) &&
                (null != action && null != pull_request && '' != pull_request)
        ) {
            this.isPR = true
            this.isOpenedPR = true
            this.actionType = EventType.OPEN_PR

            return true
        }

        // Process Closed (merged) PR
        if (EventType.CLOSED_PR == actionVal || EventType.CLOSED_PR == this.action) {
            this.isPR = true
            this.isClosedPR = true
            this.actionType = EventType.CLOSED_PR
            this.isOpenedPR = false

            return true
        }

        // Process Open PR (second pass)
        if (EventType.OPEN_PR == actionVal || EventType.OPEN_PR == this.action) {
            this.isPR = true
            this.isOpenedPR = true
            this.actionType = EventType.OPEN_PR
            this.isClosedPR = false

            return true
        }

        return false
    }

    // -------------------------------------------------------------------------
    // Opened / merged / new-branch helpers
    // -------------------------------------------------------------------------

    /**
     * Retrieves the actual git committer from a specially-formatted commit message.
     * Falls back to the supplied gitUser login when the pattern is not matched.
     *
     * @param gitMessageContent commit message text
     * @param gitUser           login from the GitHub payload
     * @return resolved username
     */
    @NonCPS
    private String getRealGitCommitter(String gitMessageContent, String gitUser) {
        def match = (gitMessageContent =~ /PR:\s.*,\sBy:\s\[([^\]]+)\]/)
        if (match.find()) {
            return match.group(1) as String
        }
        return gitUser
    }

    /**
     * Determines who opened the PR and populates avatar/user fields.
     * @return login of the user who opened the PR, or empty string
     */
    private String checkOpened() {
        Map json = this.json

        def pull_request = json.get("pull_request")

        if (null != pull_request) {
            def user = pull_request.get("user")
            if (null != user) {
                def avatar = user.get("avatar_url")
                if (null != avatar) {
                    this.openedByAvatar = "${avatar}&s=21" as String
                    this.triggeredByAvatar = this.openedByAvatar
                }
                def login = user.get("login")
                if (null != login) {
                    String userFromMessage = this.getRealGitCommitter(this.gitMessage as String, login as String)
                    this.openedByName = userFromMessage
                    this.triggeredBy = userFromMessage
                    return userFromMessage as String
                }
            }
        }

        return ""
    }

    /**
     * Determines who merged the PR and populates related fields.
     * @return login of the merger, or empty string
     */
    private String checkMerged() {
        Map json = this.json

        String merged_by_login = ""

        def pull_request = json.get("pull_request")

        if (null != pull_request) {
            def merged_by = pull_request.get("merged_by")
            if (null != merged_by) {
                def avatar = merged_by.get("avatar_url")
                if (null != avatar) {
                    this.mergedByAvatar = "${avatar}&s=21" as String
                    this.triggeredByAvatar = this.mergedByAvatar
                }
                def login = merged_by.get("login")
                if (null != login) {
                    this.mergedByName = login as String
                    this.triggeredBy = login as String
                    this.actionType = EventType.MERGED
                    this.isMerged = true
                    return login as String
                }
            }

            // Closed but not merged?
            def merged = pull_request.get("merged") as boolean
            def merged_at = pull_request.get("merged_at") as String
            if (null == merged || !merged || null == merged_at || "null" == merged_at) {
                if ("" == this.actionType || null == this.actionType) {
                    this.actionType = EventType.CANCELED
                }
                this.isMerged = false
                return ""
            }
        }

        return ""
    }

    /**
     * Checks whether a new branch was created and populates related fields.
     * @return true when a new branch creation is detected
     */
    public boolean checkNewBranch() {
        Map json = this.json

        def ref = json.ref
        def ref_type = json.ref_type
        def before = json.before
        def after = json.after
        def base_ref = json.base_ref
        def sender = json.sender
        def repository = json.repository

        def branch = null

        this.isNewBranch = false

        // Get the name/avatar of the branch creator.
        if (null != sender) {
            if (null == this.openedByName || "" == this.openedByName) {
                if (null != sender.login) {
                    this.openedByName = (String) sender.login
                }
            }
            if (null == this.openedByAvatar || "" == this.openedByAvatar) {
                if (null != sender.avatar_url) {
                    this.openedByAvatar = (String) sender.avatar_url
                    this.triggeredByAvatar = this.openedByAvatar
                }
            }
        }

        // Grab the date the new branch was created.
        if (null != repository) {
            if (null == this.pushed_at || "" == this.pushed_at) {
                if (null != repository.pushed_at) {
                    this.pushed_at = (String) repository.pushed_at
                }
            }
        }

        // New branch just created (ref + before present).
        if (null != ref && null != before) {
            if (null != ref_type) {
                if ("branch" == ref_type) {
                    this.isNewBranch = true
                    this.actionType = EventType.NEW
                    this.action = EventType.NEW
                }
            }
        }

        if (null == ref && null == ref_type) {
            // Special case: src and dst are the same versioned branch type — treat as forced push.
            if (this.srcType == this.dstType &&
                    (BranchType.SPRINT == this.srcType || BranchType.RELEASE == this.srcType || BranchType.HOTFIX == this.srcType) &&
                    null == this.action
            ) {
                this.isForcedPush = true
                return true
            }

            this.isNewBranch = false
        }

        if (null != ref_type) {
            if ("branch" == ref_type.toString()) {
                branch = ref

                this.actionType = EventType.NEW
                this.isNewBranch = true
                this.action = EventType.NEW

                if (this.srcBranch == this.dstBranch && '' != this.srcBranch && null != this.srcBranch) {
                    this.branch = this.srcBranch
                }

                return true
            }
        }

        if (null == branch) {
            this.context.echo "Branch not defined!"
        } else {
            this.branch = branch.toString()
        }

        this.isNewBranch = false
        return false
    }

    // -------------------------------------------------------------------------
    // Review detection
    // -------------------------------------------------------------------------

    /**
     * Parses review events (submitted, dismissed, approved) and populates fields.
     */
    private checkReview() {
        Map json = this.json

        def review = json.get("review")
        def action = json.get("action")
        String actionVal = ""

        if (null != action) {
            actionVal = (String) action

            if ((EventType.REVIEW_SUBMITTED == actionVal || EventType.REVIEW_DISMISSED == actionVal) && null != review) {
                def state = review.state
                String stateVal = ""
                if (null != state) {
                    stateVal = (String) state
                    this.actionType = EventType.REVIEW_SUBMITTED

                    if (stateVal) {
                        switch (stateVal.toLowerCase()) {
                            case "approved":
                                this.isApprovedPR = true
                                this.actionType = EventType.REVIEW_APPROVED
                                break
                            case "dismissed":
                                this.isApprovedPR = false
                                this.actionType = EventType.REVIEW_DISMISSED
                                break
                            case "edited":
                                this.isApprovedPR = false
                                this.actionType = EventType.EDITED
                                break
                            case EventType.CHANGES_REQUESTED:
                                this.isApprovedPR = false
                                this.actionType = EventType.CHANGES_REQUESTED
                                break
                            default:
                                this.isApprovedPR = false
                                this.actionType = stateVal.toLowerCase()
                                break
                        }
                    }

                    def body = review.body
                    if (null != body) {
                        this.approvalMessage = body as String
                    }

                    def user = review.user
                    if (null != user) {
                        def login = user.login
                        if (null != login) {
                            this.approvedByName = login as String
                        }
                        login = null
                    }
                    user = null
                }
            }
        }

        if (null != action && EventType.REVIEW_REQUESTED == action.toString()) {
            this.isReviewRequested = true
            this.actionType = EventType.REVIEW_REQUESTED
        }
    }

    // -------------------------------------------------------------------------
    // SHA / date helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the merge-commit SHA from the pull_request block.
     * @return SHA string, or empty string when not present
     */
    public String getSha() {
        Map json = this.json

        def pull_request = json.get("pull_request")
        def merge_commit_sha = ''

        if (null != pull_request && '' != pull_request) {
            merge_commit_sha = pull_request.get("merge_commit_sha")

            if (null != merge_commit_sha) {
                this.context.echo "Branch PR: ${merge_commit_sha}"
                this.sha = merge_commit_sha as String
                return merge_commit_sha as String
            }
        }

        return merge_commit_sha as String
    }

    /**
     * Populates date fields (created_at, updated_at, closed_at, merged_at) from the payload.
     * @return true on success, false on parse error
     */
    private boolean getDates() {
        try {
            Map json = this.json
            def pull_request = json.pull_request
            def commits = json.commits

            if (null != pull_request && '' != pull_request) {
                def created_at = pull_request.get("created_at")
                if (null != created_at) {
                    this.created_at = created_at as String
                }

                def updated_at = pull_request.get("updated_at")
                if (null != updated_at) {
                    this.updated_at = updated_at as String
                }

                def closed_at = pull_request.get("closed_at")
                if (null != closed_at) {
                    this.closed_at = closed_at as String
                }

                def merged_at = pull_request.get("merged_at")
                if (null != merged_at) {
                    this.merged_at = merged_at as String
                }
            } else {
                if (null != commits && '' != commits) {
                    def timestamp = commits?.getAt(0)?.timestamp
                    if (null != timestamp && '' != timestamp) {
                        this.created_at = timestamp as String
                    }
                }
            }
            return true
        } catch (Exception ex) {
            this.context.echo "Error occurred: ${ex.toString()}"
            return false
        }
    }

    // -------------------------------------------------------------------------
    // Branch helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the branch name (preferring PR head branch, falling back to ref).
     * @return branch name string
     */
    public String getBranch() {
        Map json = this.json

        def ref = json.get("ref")
        def ref_type = json.get("ref_type")
        def master_branch = json.get("master_branch")
        def pull_request = json.get("pull_request")

        def head = null
        def branch_PR = null
        String branch_PRVal = ""
        String branch = ""

        if (null != pull_request && '' != pull_request) {
            head = pull_request.get("head")
            if (null != head) {
                branch_PR = head.get("ref")
                if (null != branch_PR) {
                    branch_PRVal = (String) branch_PR
                    this.context.echo "Branch PR: ${branch_PRVal}"
                    this.branch = branch_PRVal as String
                    return branch_PRVal as String
                }
            }
        }

        if (null == ref && null != branch_PR) {
            ref = branch_PR
        }

        if (null != ref_type) {
            String ref_typeVal = (String) ref_type
            if ("tag" == ref_typeVal && null != master_branch) {
                branch = (String) master_branch
            }
        } else {
            if (null != ref) {
                branch = (String) ref
            }
        }

        if (null == branch || "" == branch) {
            this.context.echo "Branch not defined!"
            this.branch = ""
            return ""
        }

        branch = branch.replaceAll("refs/heads/", '')

        if (null != branch_PR && '' != branch_PR) {
            branch = branch_PRVal
        }

        this.branch = branch as String
        return branch as String
    }

    /**
     * Returns the source (head) branch name.
     * @return source branch name string
     */
    private String getSourceBranch() {
        Map json = this.json
        String branch = ""

        def ref = json.get("ref")
        def ref_type = json.get("ref_type")
        def master_branch = json.get("master_branch")
        def pull_request = json.get("pull_request")

        if (null != pull_request) {
            def head = pull_request.get("head")
            if (null != head) {
                def ref_2 = head.get("ref")
                if (null != ref_2) {
                    return ref_2.toString()
                }
            }
        }

        if (null != ref_type && ("tag" == ref_type) && null != master_branch) {
            branch = (String) master_branch
        } else {
            if (null != ref) {
                branch = (String) ref
            }
        }

        if (null == branch) {
            this.context.echo "Branch not defined!"
            return ""
        }

        branch = branch.replaceAll("refs/heads/", '')

        return (String) branch
    }

    /**
     * Returns the destination (base) branch name.
     * @return destination branch name string
     */
    private String getDestinationBranch() {
        Map json = this.json

        def ref = json.get("ref")
        def ref_type = json.get("ref_type")
        def master_branch = json.get("master_branch")
        def pull_request = json.get("pull_request")

        def base = null
        def branch_PR = null

        String branch = ""

        if (null != pull_request && '' != pull_request) {
            base = pull_request.base
            if (null != base) {
                branch_PR = base.ref
                if (null != branch_PR) {
                    this.dstBranch = branch_PR as String
                    return branch_PR as String
                }
            }
        }

        if (null == ref && null != branch_PR) {
            ref = branch_PR
            branch = ref as String
            this.dstBranch = branch as String
            return branch as String
        }

        // TAG ref type: use the master/default branch as destination.
        if (null != ref_type && "tag" == ref_type.toString() && null != master_branch) {
            branch = master_branch as String
            this.dstBranch = branch as String
        } else {
            if (null != ref) {
                branch = ref as String
                this.dstBranch = branch as String
            }
        }

        if (null == branch || "" == branch) {
            this.context.echo "Branch not defined!"
            return ""
        }

        branch = branch.replaceAll("refs/heads/", '')

        this.dstBranch = branch as String
        return branch as String
    }

    /**
     * Maps a branch name to its {@link BranchType} constant.
     *
     * @param branchName the full or short branch name
     * @return matching BranchType constant string
     */
    @NonCPS
    public String getBranchType(String branchName) {
        String branchPrefix = ""

        if (branchName instanceof String) {
            branchName = branchName.toLowerCase()
        } else return (String) BranchType.FEATURE

        String[] values
        if (branchName.contains('sprint/') || branchName.contains('release/') || branchName.contains('hotfix/') || branchName.contains('plugin/')) {
            values = branchName.split(/\//)
            if (null != values[0]) {
                branchPrefix = (String) values[0] as String
                branchPrefix = branchPrefix.trim()
                branchPrefix = branchPrefix.toLowerCase()

                boolean isBuiltCode = branchName.contains('-built')

                switch (branchPrefix) {
                    case BranchType.SPRINT:
                        if (isBuiltCode) {
                            return BranchType.SPRINT_BUILT
                        }
                        return BranchType.SPRINT
                        break
                    case BranchType.FEATURE:
                        return BranchType.FEATURE
                        break
                    case BranchType.FIX:
                        return BranchType.FIX
                        break
                    case BranchType.HOTFIX:
                        if (isBuiltCode) {
                            return BranchType.HOTFIX_BUILT
                        }
                        return BranchType.HOTFIX
                        break
                    case BranchType.RELEASE:
                        if (isBuiltCode) {
                            return BranchType.RELEASE_BUILT
                        }
                        return BranchType.RELEASE
                        break
                    case "dev":
                    case BranchType.DEVELOP:
                        if (isBuiltCode) {
                            return BranchType.DEVELOP_BUILT
                        }
                        return BranchType.DEVELOP
                        break
                    case "production":
                    case BranchType.MASTER:
                        if (isBuiltCode) {
                            return BranchType.MASTER_BUILT
                        }
                        return BranchType.MASTER
                        break
                    case BranchType.PLUGIN:
                        return BranchType.PLUGIN
                        break
                    default:
                        return BranchType.FEATURE
                        break
                }
            }
        } else {
            switch (branchName) {
                case "production":
                case "master":
                    return BranchType.MASTER
                    break
                case "main":
                    return BranchType.MAIN
                    break
                case "trunk":
                    return BranchType.TRUNK
                    break
                case "develop":
                case "dev":
                    return BranchType.DEVELOP
                    break
                case "develop-built":
                    return BranchType.DEVELOP_BUILT
                    break
                case "master-built":
                    return BranchType.MASTER_BUILT
                    break
                case "env_dev":
                    return BranchType.ENV_DEV
                    break
                case "env_qa":
                    return BranchType.ENV_QA
                    break
                case "env_prod":
                    return BranchType.ENV_PROD
                    break
                default:
                    return BranchType.FEATURE
                    break
            }
            return (String) BranchType.FEATURE
        }
        return (String) branchPrefix.toLowerCase()
    }

    // -------------------------------------------------------------------------
    // Version helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a semantic version string from a branch name (e.g. {@code release/1.2.3}).
     *
     * @param branchVal branch name to inspect; defaults to srcBranch
     * @return version string, or {@code "0.0.0"} when none is found
     */
    @NonCPS
    public String getVersionFromBranch(String branchVal = "") {
        String branchName = ("" != branchVal) ? branchVal : (String) this.srcBranch as String

        if (null == branchName || "" == branchName) {
            branchName = (String) this.dstBranch as String
        }

        String versionVal = "0.0.0"

        String[] values
        if (null != branchName && "" != branchName) {
            if (branchName.contains('bugfix/')) {
                return "0.0.0" as String
            }
            if (branchName.contains('release/') || branchName.contains('hotfix/')) {
                values = branchName.split(/\//)
                // release/X.Y.Z  → index 1
                // only two-segment release/X.Y.Z format is supported
                int valueIndex = 1
                if (values.length > valueIndex && values[valueIndex]) {
                    versionVal = values[valueIndex] as String
                    versionVal = versionVal.trim()
                    versionVal = (String) Version.getCleanNumber(versionVal)
                }
            }
        }

        return versionVal as String
    }

    // -------------------------------------------------------------------------
    // Message / URL helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the commit or PR title+body as a single trimmed string.
     * @return git message string
     */
    private String getMessage() {
        Map json = this.json
        def head_commit = json.head_commit
        def pull_request = json.pull_request

        String message = ""

        if (null == head_commit) {
            if (null != pull_request) {
                String title = pull_request.title ? pull_request.title as String : ""
                String body = pull_request.body ? pull_request.body as String : ""
                message = body ? "${title} - ${body}" : title
            }
        } else {
            def message_2 = head_commit.message
            if (null != message_2) {
                message = (String) message_2 as String
            }
        }

        message = message.trim()
        message = message.replaceAll("\\r\\n", " ")
        message = message.replaceAll("\r\n", " ")

        return (String) message
    }

    /**
     * Returns the HTML URL of the PR, or the head-commit URL for pushes.
     * @return URL string
     */
    private String getPRUrl() {
        Map json = this.json
        def pull_request = json.get("pull_request")

        String urlVal = ""

        if (null != pull_request) {
            def html_url = pull_request.get("html_url")
            if (null != html_url) {
                urlVal = (String) html_url as String
            }
        } else {
            def head_commit = json.head_commit
            if (null != head_commit) {
                def url = head_commit.url
                if (null != url) {
                    urlVal = url as String
                }
            }
        }

        return (String) urlVal
    }

    // -------------------------------------------------------------------------
    // Repository helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the repository name extracted from the clone URL.
     * @return repo name string (without {@code .git} suffix)
     */
    @NonCPS
    public String getRepoName() {
        Map json = this.json

        def repository = json.get("repository")
        def path = null
        String pathVal = ""

        if (null != repository) {
            def clone_url = repository.get("clone_url")
            if (null != clone_url) {
                path = clone_url
                pathVal = (String) clone_url
            }
            clone_url = null
        }

        String result = ""

        if (null != path && "" != pathVal) {
            result = pathVal

            if (result.contains('http') || result.contains('git@') || result.contains('git:')) {
                result = result.substring(path.lastIndexOf('/') + 1, path.length())
                result = result.replace('.git', '')
            }
        }

        path = null
        repository = null

        return result as String
    }

    /**
     * Returns the HTTPS clone URL and populates ssh_url, git_url, pulls_url, and url fields.
     * @return HTTPS clone URL string
     */
    public String getRepositoryUrl() {
        Map json = this.json

        def repository = json.get("repository")
        String result = ""

        if (null != repository) {
            def clone_url = repository.get("clone_url")
            if (null != clone_url) {
                result = (String) clone_url
            }

            def ssh_url = repository.ssh_url
            if (null != ssh_url) {
                this.ssh_url = ssh_url as String
            }
            def git_url = repository.git_url
            if (null != git_url) {
                this.git_url = git_url as String
            }
            def pulls_url = repository.pulls_url
            if (null != pulls_url) {
                String tmp = pulls_url as String
                tmp = tmp.replaceAll("\\{/number\\}", "")
                this.pulls_url = tmp
            }
            def url = repository.url
            if (null != url) {
                this.url = url as String
            }
        }

        repository = null

        return result as String
    }

    /**
     * Returns the SSH clone URL.
     * @return SSH URL string
     */
    public String getRepositorySshUrl() {
        Map json = this.json
        def repository = json.repository
        String url = ""
        if (null != repository) {
            def ssh_url = repository.ssh_url
            if (null != ssh_url) {
                url = (String) ssh_url
            }
        }
        this.ssh_url = url
        return url as String
    }

    // -------------------------------------------------------------------------
    // Branch-name validation
    // -------------------------------------------------------------------------

    /**
     * Validates that the destination branch name follows naming conventions.
     * Logs a warning and returns false when the name contains too many path segments.
     * @return true if the branch name is acceptable
     */
    public boolean checkBranchNaming() {
        ArrayList<String> allowedList = new ArrayList<String>([
            BranchType.SPRINT,
            BranchType.HOTFIX,
            BranchType.RELEASE,
            BranchType.DEVELOP,
            BranchType.MASTER
        ])
        if (this.isClosedPR || this.isForcedPush || this.isNewBranch || this.isOpenedPR) {
            if (null != this.dstType) {
                if (this.dstType in allowedList || allowedList.contains(this.dstType)) {
                    int count_slashes = this.dstBranch.count("/")
                    if (count_slashes > 2) {
                        this.context.echo("Please correct your branch name: `${this.dstBranch}`. " +
                            "Branch names should be in one of the formats: " +
                            "feat/TICKET/string, sprint/PUB/string, hotfix/PUB/VERSION, release/PUB/VERSION" as String)
                        return false
                    }
                }
            }
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Job-processing gate
    // -------------------------------------------------------------------------

    /**
     * Decides whether the parsed event should trigger a downstream CI job.
     * @return true when the job should proceed
     */
    public boolean processJob() {
        boolean result = false

        ArrayList<String> allowedList = new ArrayList<String>([
            BranchType.SPRINT,
            BranchType.HOTFIX,
            BranchType.RELEASE,
            BranchType.DEVELOP,
            BranchType.MASTER,
            BranchType.PLUGIN,
            BranchType.ENV_DEV,
            BranchType.ENV_QA,
            BranchType.ENV_PROD
        ])

        // Canceled merge — skip.
        if (EventType.CANCELED == this.actionType) {
            this.context.echo("This PR was canceled..")
            this.shouldBeProcessed = false
            return false
        }

        if (this.isClosedPR || this.isForcedPush || this.isNewBranch || this.isOpenedPR || EventType.SYNC == this.actionType) {
            if (this.dstType in allowedList || allowedList.contains(this.dstType)) {
                result = true
            }
        }

        // Hotfix branches cannot push into sprint branches.
        if (BranchType.HOTFIX == this.srcType && BranchType.SPRINT == this.dstType) {
            result = false
        }

        // Closed PR without a merge => skip.
        if (EventType.CLOSED_PR == this.actionType && !this.isMerged) {
            result = false
        }

        // Push to environment branches is always processed.
        if (this.dstType in [BranchType.ENV_DEV, BranchType.ENV_QA] && this.isPush) {
            result = true
        }

        this.context.echo("** Process this job? Answer: ${result.toString()}" as String)

        return result
    }
}
