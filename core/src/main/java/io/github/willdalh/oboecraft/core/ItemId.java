package io.github.willdalh.oboecraft.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** A Minecraft registry identifier passed into the core without a game dependency. */
public record ItemId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public ItemId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Expected a namespaced item id, got: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
