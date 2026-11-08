package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.fabric.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * The あ button on the survival and adventure inventory that opens the word list. It is added through Fabric API's
 * screen events, not a mixin into the inventory screen, so mods that change that screen keep working. It is only there
 * while the player has progress, and it follows the inventory when the recipe book moves it.
 */
final class WordListButton {
    /** The size of the recipe book button. */
    private static final int WIDTH = 20;
    private static final int HEIGHT = 18;
    /** Where vanilla puts the recipe book button, from the inventory's left edge, and its width. */
    private static final int RECIPE_BOOK_BUTTON_LEFT = 104;
    private static final int RECIPE_BOOK_BUTTON_WIDTH = 20;
    private static final int GAP = 2;
    /** From the inventory's left edge: right of the recipe book button. */
    private static final int LEFT = RECIPE_BOOK_BUTTON_LEFT + RECIPE_BOOK_BUTTON_WIDTH + GAP;
    /** From the inventory's top edge: level with the recipe book button. */
    private static final int TOP = 61;
    private static final Component LABEL = Component.literal("あ");

    private final Minecraft minecraft;
    private final InventoryScreen inventory;
    private final Button button;
    private boolean added;

    private WordListButton(Minecraft minecraft, InventoryScreen inventory) {
        this.minecraft = minecraft;
        this.inventory = inventory;
        button = Button.builder(LABEL, ignored -> minecraft.gui.setScreen(new WordListScreen(inventory)))
                .size(WIDTH, HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("oboecraft.word_list.title")))
                .build();
    }

    /**
     * Adds the button to every inventory screen as it is built. The creative inventory is another screen, so it gets
     * none. Building the screen again, like on a resize, makes a new button, and the screen's own events go with it.
     */
    static void register() {
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (!(screen instanceof InventoryScreen inventory)) {
                return;
            }
            WordListButton button = new WordListButton(minecraft, inventory);
            button.update();
            ScreenEvents.afterTick(screen).register(ignored -> button.update());
            // The recipe book button moves the inventory at once, so the button follows without waiting for a tick.
            ScreenMouseEvents.afterMouseClick(screen).register((ignored, event, consumed) -> {
                button.update();
                return consumed;
            });
            ScreenKeyboardEvents.afterKeyPress(screen).register((ignored, event) -> button.update());
        });
    }

    /**
     * Shows the button while the player has progress, so it appears when progress arrives with the inventory open, and
     * places it from the inventory's edges, which move when the recipe book opens or closes.
     */
    private void update() {
        boolean shown = minecraft.player != null && OboecraftClient.progress().hasProgress(minecraft.player);
        if (shown != added) {
            if (shown) {
                Screens.getWidgets(inventory).add(button);
            } else {
                Screens.getWidgets(inventory).remove(button);
            }
            added = shown;
        }
        if (shown) {
            AbstractContainerScreenAccessor edges = (AbstractContainerScreenAccessor) inventory;
            button.setPosition(edges.oboecraft$leftPos() + LEFT, edges.oboecraft$topPos() + TOP);
        }
    }
}
