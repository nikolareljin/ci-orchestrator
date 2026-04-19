#!groovy

/*
Event types we will monitor from Git/CI triggers.
*/
package io.ciorch.git

import java.io.Serializable

// ***************************************************
// Action/event types we will monitor.
class EventType implements Serializable {
    // PULL_REQUEST
    static final String OPEN_PR = 'opened'
    static final String CLOSED_PR = 'closed'
    static final String SYNC = 'synchronize'
    static final String PUBLISHED = 'published'
    static final String REOPENED = 'reopened'

    // Fixed PR
    static final String UPDATED = "pr_updated"

    // CHECK_RUN
    static final String NEW = 'new'
    static final String DEFAULT = ''

    // Not commonly acted on, kept for completeness
    static final String EDITED = 'edited'
    static final String DELETED = 'deleted'
    static final String PINNED = 'pinned'
    static final String UNPINNED = 'unpinned'
    static final String ASSIGNED = 'assigned'
    static final String UNASSIGNED = 'unassigned'
    static final String UNLABELED = 'unlabeled'
    static final String LABELED = 'labeled'
    static final String LOCKED = 'locked'
    static final String UNLOCKED = 'unlocked'
    static final String TRANSFERRED = 'transferred'
    static final String MILESTONED = 'milestoned'
    static final String DEMILESTONED = 'demilestoned'

    // Review process
    static final String REVIEW_REQUESTED = 'review_requested'
    static final String REVIEW_DISMISSED = 'dismissed'
    static final String REVIEW_SUBMITTED = 'submitted'
    static final String REVIEW_APPROVED = 'approved'
    static final String CHANGES_REQUESTED = 'changes_requested'
    static final String COMMENTED = 'commented'

    // Push events
    static final String PUSH = 'PUSH'
    static final String FORCED_PUSH = 'forced_push'
    static final String TAGGED = 'tagged'

    // Lifecycle
    static final String MERGED = 'merged'
    static final String CANCELED = 'canceled'

    // Generic CI triggers
    static final String E2E = 'e2e'
    static final String SCHEDULED = 'scheduled'
}
