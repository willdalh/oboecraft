package io.github.willdalh.oboecraft.core.item;

/** An item's name in its learning language, the correct answer. Japanese names also have parts and readings. */
public sealed interface Name permits JapaneseName, PlainName {
    String text();
}
