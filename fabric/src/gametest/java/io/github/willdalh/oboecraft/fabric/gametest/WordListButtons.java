package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Finds and clicks the word list button on the inventory, as a player would. */
final class WordListButtons {
    private WordListButtons() {
    }

    static Optional<Button> find(Screen screen) {
        return screen.children().stream()
                .filter(child -> child instanceof Button button && button.getMessage().getString().equals("あ"))
                .map(Button.class::cast)
                .findFirst();
    }

    /** Left-clicks the middle of the button through the screen, so whatever is over it would get the click instead. */
    static void click(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Screen screen = client.gui.screen();
            Button button = find(screen).orElseThrow(() -> new AssertionError("The inventory should have the button"));
            MouseButtonEvent click = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0,
                    button.getY() + button.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            screen.mouseClicked(click, false);
            screen.mouseReleased(click);
        });
    }
}
