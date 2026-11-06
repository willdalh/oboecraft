package io.github.willdalh.oboecraft.core.lock;

/** What happens when a player interacts with a stack. */
public enum LockDecision {
    /** The action goes ahead as normal. */
    ALLOW,
    /** The action is stopped, and the answer menu opens for the item. */
    REVIEW,
    /** The action is stopped, and the player is told how long the item's ban has left. No answer menu opens. */
    BANNED,
    /** Blocked without opening a review. */
    DENY
}
