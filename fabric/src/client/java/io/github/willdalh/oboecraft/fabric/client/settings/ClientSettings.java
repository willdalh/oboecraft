package io.github.willdalh.oboecraft.fabric.client.settings;

/**
 * The settings each player chooses on their own computer.
 *
 * @param clickableButtons whether the answer menu shows a button for each action, besides the keys
 */
public record ClientSettings(boolean clickableButtons) {
    /** The default client settings, used when no client settings file says otherwise. */
    public static final ClientSettings DEFAULTS = new ClientSettings(false);
}
