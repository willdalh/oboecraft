package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.settings.ClientSettings;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** The name and reading require separate presses, through keyboard and buttons, in lessons and after a fail. */
public final class AnswerRevealClientGameTest implements FabricClientGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientSettings previous = context.computeOnClient(client -> OboecraftClient.clientSettings());
        context.runOnClient(client -> OboecraftClient.useClientSettings(new ClientSettings(true, 100)));
        SingleplayerProgress.delete();
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.waitFor(client -> OboecraftClient.progress().hasProgress(client.player));
            singleplayer.getServer().runCommand("time set day");
            singleplayer.getServer().runCommand("weather clear");
            lesson(context, singleplayer);
            wrongLessonAnswer(context, singleplayer);
            failedReview(context, singleplayer);
        } finally {
            context.runOnClient(client -> OboecraftClient.useClientSettings(previous));
        }
    }

    private static void lesson(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        open(context, singleplayer, "minecraft:oak_log");
        context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_F9, 0, 0)));
        context.waitFor(client -> client.gui.screen() instanceof AnswerScreen screen && screen.showsLessonAnswer());
        check(!context.computeOnClient(client -> answer(client).showsReading()), "The first press must show only the name");

        // A held key's repeat is not another deliberate request to reveal the reading.
        context.runOnClient(client -> {
            client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_F9, 0, 0));
            check(!answer(client).showsReading(), "Holding Show answer must not skip the name-only stage");
            client.gui.screen().keyReleased(new KeyEvent(InputConstants.KEY_F9, 0, 0));
        });
        screenshot(context, "answer-reveal-lesson-name");
        context.getInput().typeChars("おーく");
        context.getInput().pressKey(InputConstants.KEY_F9);
        context.runOnClient(client -> {
            check(answer(client).showsReading(), "The next Show answer press must reveal the reading");
            check(input(client).getValue().equals("おーく"), "Revealing the reading must preserve typed text");
            check(!button(client, "oboecraft.answer_menu.show_reading").active, "The revealed reading stays visible");
            input(client).setValue("");
        });
        screenshot(context, "answer-reveal-lesson-reading");
        close(context, singleplayer);
    }

    private static void wrongLessonAnswer(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        open(context, singleplayer, "minecraft:oak_planks");
        context.runOnClient(client -> button(client, "key.oboecraft.show")
                .onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0)));
        context.waitFor(client -> answer(client).showsLessonAnswer());
        check(!context.computeOnClient(client -> answer(client).showsReading()), "The Show answer button reveals only the name");
        context.runOnClient(client -> button(client, "oboecraft.answer_menu.show_reading")
                .onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0)));
        check(context.computeOnClient(client -> answer(client).showsReading()), "The second button press reveals the reading");
        close(context, singleplayer);

        // A fresh review starts with its reading hidden, including when a wrong lesson answer reveals the name.
        open(context, singleplayer, "minecraft:oak_planks");
        context.getInput().typeChars("wrong");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitFor(client -> answer(client).showsLessonAnswer());
        check(!context.computeOnClient(client -> answer(client).showsReading()), "Wrong lesson answers reveal only the name");
        close(context, singleplayer);
    }

    private static void failedReview(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> OboecraftMod.serverProgress().update(
                server.getPlayerList().getPlayers().getFirst(), OAK_LOG,
                new SrsState.Leveled(1, Optional.of(OboecraftMod.serverProgress().now()))));
        singleplayer.getConnection().waitForClientboundPackets();
        open(context, singleplayer, "minecraft:oak_log");
        context.getInput().pressKey(InputConstants.KEY_DELETE);
        context.waitFor(client -> answer(client).showsFail());
        check(!context.computeOnClient(client -> answer(client).showsReading()), "A fail initially reveals only the name");
        screenshot(context, "answer-reveal-failed-name");
        int remaining = singleplayer.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().getInventory().getItem(0).getCount());
        context.getInput().pressKey(InputConstants.KEY_F9);
        check(context.computeOnClient(client -> answer(client).showsFail() && answer(client).showsReading()),
                "Show answer after a fail must reveal the reading and leave the menu open");
        int afterReading = singleplayer.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().getInventory().getItem(0).getCount());
        check(remaining == afterReading, "Revealing a reading must not punish the review again");
        screenshot(context, "answer-reveal-failed-reading");
        context.waitTicks(12);
        close(context, singleplayer);
    }

    private static void open(ClientGameTestContext context, TestSingleplayerContext singleplayer, String item) {
        singleplayer.getServer().runCommand("item replace entity @a hotbar.0 with " + item + " 4");
        singleplayer.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> check(OboecraftClient.reviews().reviewInventorySlot(client.player, 36),
                "The locked item must open a review"));
        context.waitForScreen(AnswerScreen.class);
        singleplayer.getConnection().waitForServerboundPackets();
        singleplayer.getConnection().waitForClientboundPackets();
    }

    private static void close(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(null);
        singleplayer.getConnection().waitForServerboundPackets();
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.waitTicks(5);
        context.runOnClient(client -> client.gui.toastManager().clear());
        context.takeScreenshot(name);
    }

    private static AnswerScreen answer(Minecraft client) {
        return (AnswerScreen) client.gui.screen();
    }

    private static EditBox input(Minecraft client) {
        return client.gui.screen().children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow();
    }

    private static Button button(Minecraft client, String key) {
        String label = Component.translatable(key).getString();
        return client.gui.screen().children().stream()
                .filter(child -> child instanceof Button button && button.getMessage().getString().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
