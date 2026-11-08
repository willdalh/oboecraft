package io.github.willdalh.oboecraft.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jspecify.annotations.Nullable;

/**
 * A screen that opens over the screen of the player's open menu, like the inventory, without closing the menu, and goes
 * back to that screen when it closes. {@code GuiMixin} keeps the menu's screen from being told it is removed when this
 * screen replaces it, since that runs the menu's {@code removed} on the client, which empties its result slot.
 */
public interface OverMenuScreen {
    /** The screen of the open menu that this screen opened over, or null if it opened over the game. */
    @Nullable AbstractContainerScreen<?> menuScreen();

    /**
     * Goes back to the open menu's screen, if this screen opened over one and that menu is still open. If the menu has
     * closed meanwhile, its screen is gone too.
     */
    default void returnToMenuScreen(Minecraft minecraft) {
        AbstractContainerScreen<?> menuScreen = menuScreen();
        boolean menuStillOpen = menuScreen != null && minecraft.player != null
                && menuScreen.getMenu() == minecraft.player.containerMenu;
        minecraft.gui.setScreen(menuStillOpen ? menuScreen : null);
    }
}
