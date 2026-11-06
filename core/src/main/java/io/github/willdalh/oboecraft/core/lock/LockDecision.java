package io.github.willdalh.oboecraft.core.lock;

/** What happens when a player interacts with a stack. */
public enum LockDecision {
    /** The action goes ahead as normal. */
    ALLOW,
    /** The action is stopped, and the answer menu opens for the item. */
    REVIEW
}
