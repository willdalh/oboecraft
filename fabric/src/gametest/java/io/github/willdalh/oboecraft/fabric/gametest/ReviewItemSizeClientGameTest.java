package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.settings.ClientSettings;
import io.github.willdalh.oboecraft.fabric.client.settings.ClientSettingsFile;
import io.github.willdalh.oboecraft.fabric.client.settings.ReviewItemScaleSlider;
import io.github.willdalh.oboecraft.fabric.client.settings.SettingsScreen;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/** Exercises the size control through the real settings screen and captures reviews of two different items. */
public final class ReviewItemSizeClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        ClientSettings previous = context.computeOnClient(client -> OboecraftClient.clientSettings());
        context.runOnClient(client -> OboecraftClient.useClientSettings(ClientSettings.DEFAULTS));
        SingleplayerProgress.delete();
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.waitFor(client -> OboecraftClient.progress().hasProgress(client.player));
            singleplayer.getServer().runCommand("time set day");
            singleplayer.getServer().runCommand("weather clear");

            review(context, singleplayer, "minecraft:oak_log", "review-item-size-100");
            context.runOnClient(client -> client.gui.setScreen(new SettingsScreen(null)));
            context.waitForScreen(SettingsScreen.class);
            context.runOnClient(client -> {
                check(slider(client).scalePercent() == 100, "The default size must be 100%");
                setSlider(client, 300);
                check(slider(client).scalePercent() == 300, "The size slider must select 300%");
                client.gui.toastManager().clear();
            });
            context.takeScreenshot("review-item-size-settings");
            context.runOnClient(client -> pressButton(client, "gui.done"));
            context.waitForScreen(null);
            check(context.computeOnClient(client -> OboecraftClient.clientSettings().reviewItemScalePercent()) == 300,
                    "Done must apply the selected size");
            check(ClientSettingsFile.read(ClientSettingsFile.path()).reviewItemScalePercent() == 300,
                    "Done must save the selected size");

            // Reopening must retain the saved size, and cancelling a new choice must leave it unchanged.
            context.runOnClient(client -> {
                client.gui.setScreen(new SettingsScreen(null));
                check(slider(client).scalePercent() == 300, "Reopening settings must show the saved size");
                setSlider(client, 50);
                pressButton(client, "gui.cancel");
            });
            context.waitForScreen(null);
            check(context.computeOnClient(client -> OboecraftClient.clientSettings().reviewItemScalePercent()) == 300,
                    "Cancel must keep the previous size");
            check(ClientSettingsFile.read(ClientSettingsFile.path()).reviewItemScalePercent() == 300,
                    "Cancel must not rewrite the saved size");

            review(context, singleplayer, "minecraft:oak_log", "review-item-size-300");
            review(context, singleplayer, "minecraft:diamond_sword", "review-item-size-sword-300");
        } finally {
            context.runOnClient(client -> OboecraftClient.useClientSettings(previous));
            try {
                ClientSettingsFile.write(ClientSettingsFile.path(), previous);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void review(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            String item, String screenshot) {
        singleplayer.getServer().runCommand("item replace entity @a hotbar.0 with " + item);
        singleplayer.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> {
            check(OboecraftClient.reviews().reviewInventorySlot(client.player, 36), "The item must open a review");
        });
        context.waitForScreen(AnswerScreen.class);
        singleplayer.getConnection().waitForServerboundPackets();
        singleplayer.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
        context.runOnClient(client -> client.gui.toastManager().clear());
        context.takeScreenshot(screenshot);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(null);
        singleplayer.getConnection().waitForServerboundPackets();
    }

    private static ReviewItemScaleSlider slider(Minecraft client) {
        return client.gui.screen().children().stream().filter(ReviewItemScaleSlider.class::isInstance)
                .map(ReviewItemScaleSlider.class::cast).findFirst().orElseThrow();
    }

    private static void setSlider(Minecraft client, int percent) {
        ReviewItemScaleSlider slider = slider(client);
        double position = (double) (percent - ClientSettings.MIN_REVIEW_ITEM_SCALE)
                / (ClientSettings.MAX_REVIEW_ITEM_SCALE - ClientSettings.MIN_REVIEW_ITEM_SCALE);
        MouseButtonEvent click = new MouseButtonEvent(slider.getX() + 4 + position * (slider.getWidth() - 8),
                slider.getY() + 10, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        check(slider.mouseClicked(click, false), "The slider must accept a left click");
        slider.mouseReleased(click);
    }

    private static void pressButton(Minecraft client, String key) {
        String label = Component.translatable(key).getString();
        client.gui.screen().children().stream()
                .filter(child -> child instanceof Button button && button.getMessage().getString().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow()
                .onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
