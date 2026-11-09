package io.github.willdalh.oboecraft.fabric.gametest;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;

/** Finds the reading clip buttons in the rows of the word list, which are inside its scrolling list. */
final class WordListReadingClipButtons {
    private WordListReadingClipButtons() {
    }

    /** The reading clip buttons on the screen, in the list's order. */
    static List<SpriteIconButton> find(ContainerEventHandler screen) {
        return descendants(screen).filter(SpriteIconButton.class::isInstance).map(SpriteIconButton.class::cast).toList();
    }

    private static Stream<GuiEventListener> descendants(ContainerEventHandler parent) {
        return parent.children().stream().flatMap(child -> Stream.concat(Stream.of(child),
                child instanceof ContainerEventHandler container ? descendants(container) : Stream.empty()));
    }
}
