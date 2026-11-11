package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.ReadingClips;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import java.util.Optional;

/**
 * The playable loop through a real client in singleplayer: right-clicking with a locked oak log opens the answer
 * menu instead of placing it, a pass unlocks it, and then it can be placed.
 */
public class AnswerMenuClientGameTest implements FabricClientGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            for (var binding : new io.github.willdalh.oboecraft.fabric.client.ReviewKeys.Binding[]{
                    io.github.willdalh.oboecraft.fabric.client.ReviewKeys.KNOWN,
                    io.github.willdalh.oboecraft.fabric.client.ReviewKeys.IGNORE,
                    io.github.willdalh.oboecraft.fabric.client.ReviewKeys.SHOW}) {
                for (var other : client.options.keyMappings) {
                    if (other != binding.mapping() && binding.mapping().same(other)) {
                        throw new AssertionError("Default review binding conflicts with " + other.getName());
                    }
                }
            }
        });
        ProgressFolder.delete();
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            BlockPos target = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos ground = player.blockPosition().offset(2, -1, 0);
                player.level().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
                player.level().setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());
                return ground;
            });
            singleplayer.getServer().runCommand("give @a minecraft:oak_log 4");
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitFor(client -> OboecraftClient.progress().hasProgress(client.player));
            context.getInput().lookAt(target);
            context.waitTick();

            // A new item is locked, so right-clicking opens the answer menu instead of placing the log.
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitForScreen(AnswerScreen.class);
            singleplayer.getConnection().waitForServerboundPackets();
            assertBlock(singleplayer, target.above(), false, "a locked oak log isn't placed");
            context.takeScreenshot("answer-menu");

            // A wrong lesson answer reveals only the name, and the lesson goes on.
            context.getInput().typeChars("オーク");
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitFor(client -> client.gui.screen() instanceof AnswerScreen screen && screen.showsLessonAnswer());
            assertStillReviewing(context, singleplayer);
            assertShowsReading(context, false, "A wrong lesson answer must show only the name");

            // Show answer then shows the reading too, and keeps what is typed.
            context.getInput().typeChars("オーク");
            context.getInput().pressKey(InputConstants.KEY_F9);
            assertShowsReading(context, true, "Show answer after the name must show the reading");
            if (!context.computeOnClient(client -> client.gui.screen().children().stream()
                    .anyMatch(child -> child instanceof EditBox box && box.getValue().equals("オーク")))) {
                throw new AssertionError("Showing the reading must keep the typed text");
            }

            // The typing bar holds a correct answer while the keyboard's kanji conversion still has text that isn't
            // confirmed. The keys belong to the conversion: Esc doesn't close the menu, and the Enter that confirms
            // the conversion doesn't submit the answer, or it would pass here.
            context.getInput().typeChars("の原木");
            context.runOnClient(client -> client.keyboardHandler.textEditing(
                    client.getWindow().handle(), PreeditEvent.fromSdlTextEditing("げん", 2, 0)));
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            singleplayer.getConnection().waitForServerboundPackets();
            context.waitTicks(2);
            context.getInput().pressKey(InputConstants.KEY_NUMPADENTER);
            singleplayer.getConnection().waitForServerboundPackets();
            assertStillReviewing(context, singleplayer);

            // A boss bar is shown, so the notification after the pass must be drawn below it.
            singleplayer.getServer().runCommand("bossbar add oboecraft:test \"Boss\"");
            singleplayer.getServer().runCommand("bossbar set oboecraft:test players @a");

            // The conversion is cancelled, so numpad Enter now submits the answer, which passes.
            context.runOnClient(client -> client.keyboardHandler.textEditing(client.getWindow().handle(), null));
            context.getInput().pressKey(InputConstants.KEY_NUMPADENTER);
            context.waitFor(client -> !(client.gui.screen() instanceof AnswerScreen));
            singleplayer.getConnection().waitForClientboundPackets();
            SrsState clientState = context.computeOnClient(
                    client -> OboecraftClient.progress().state(client.player, OAK_LOG));
            if (!(clientState instanceof SrsState.Leveled leveled) || leveled.level() != 1) {
                throw new AssertionError("The client should have the oak log at level 1, not " + clientState);
            }

            // The pass shows the Japanese name, with its kanji, at the top of the screen, and it goes away again.
            Optional<String> notified = context.computeOnClient(client -> OboecraftClient.notification().shown());
            if (!notified.equals(Optional.of("オークの原木"))) {
                throw new AssertionError("A pass should show オークの原木 in the notification, not " + notified);
            }
            NotificationPixels.assertShown(context, "pass-notification");
            singleplayer.getServer().runCommand("bossbar remove oboecraft:test");
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitFor(client -> OboecraftClient.notification().shown().isEmpty());
            NotificationPixels.assertNotShown(context, "pass-notification-gone");

            // The oak log is unlocked, so it is placed.
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            singleplayer.getConnection().waitForServerboundPackets();
            context.waitTicks(2);
            assertBlock(singleplayer, target.above(), true, "an unlocked oak log is placed");

            // Locked again at level 1, Don't know fails: half of the 3 logs in hand, rounded up, are removed, and the
            // menu shows the answer until it is closed.
            singleplayer.getServer().runOnServer(server -> OboecraftMod.serverProgress().update(
                    server.getPlayerList().getPlayers().getFirst(), OAK_LOG,
                    new SrsState.Leveled(1, Optional.of(OboecraftMod.serverProgress().now()))));
            singleplayer.getConnection().waitForClientboundPackets();
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitForScreen(AnswerScreen.class);
            singleplayer.getConnection().waitForServerboundPackets();
            // Delete edits typed text and doesn't fail the review. Only on an empty typing bar is it Don't know.
            context.getInput().typeChars("abc");
            context.getInput().pressKey(InputConstants.KEY_LEFT);
            context.getInput().pressKey(InputConstants.KEY_DELETE);
            singleplayer.getConnection().waitForServerboundPackets();
            context.waitTicks(2);
            if (context.computeOnClient(client -> !(client.gui.screen() instanceof AnswerScreen screen)
                    || screen.showsFail())) {
                throw new AssertionError("Delete with typed text must not fail the review");
            }
            context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
            context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
            // Enter is still held, as when it sent the answer that failed, so its repeats must not close the menu.
            context.runOnClient(client -> client.gui.screen().keyPressed(
                    new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0)));
            context.runOnClient(client -> PlayedReadingClips.take());
            context.getInput().pressKey(InputConstants.KEY_DELETE);
            context.waitFor(client -> client.gui.screen() instanceof AnswerScreen screen && screen.showsFail());
            // The fail says the reading of the failed item.
            List<Identifier> failClips = context.computeOnClient(client -> PlayedReadingClips.take());
            if (!failClips.equals(List.of(ReadingClips.soundEvent(OAK_LOG).orElseThrow()))) {
                throw new AssertionError("A fail should play the oak log's reading clip, not " + failClips);
            }
            context.waitTicks(12);
            context.runOnClient(client -> client.gui.screen().keyPressed(
                    new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0)));
            if (!context.computeOnClient(client -> client.gui.screen() instanceof AnswerScreen)) {
                throw new AssertionError("A held Enter's repeat must not close the answer after a fail");
            }
            context.runOnClient(client -> client.gui.screen().keyReleased(
                    new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0)));
            assertShowsReading(context, false, "A fail must show only the name");
            // Show answer shows the reading, and the menu stays open.
            context.getInput().pressKey(InputConstants.KEY_F9);
            assertShowsReading(context, true, "Show answer after a fail must show the reading");
            if (!context.computeOnClient(client -> PlayedReadingClips.take()).isEmpty()) {
                throw new AssertionError("Show answer after a fail must not play the reading clip again");
            }
            if (!context.computeOnClient(client -> client.gui.screen() instanceof AnswerScreen screen && screen.showsFail())) {
                throw new AssertionError("Showing the reading after a fail must keep the menu open");
            }
            context.takeScreenshot("answer-menu-failed");
            int logsLeft = singleplayer.getServer().computeOnServer(server ->
                    server.getPlayerList().getPlayers().getFirst().getMainHandItem().getCount());
            if (logsLeft != 1) {
                throw new AssertionError("A fail at level 1 should leave 1 of 3 oak logs, not " + logsLeft);
            }
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitFor(client -> !(client.gui.screen() instanceof AnswerScreen));

            // The oak log is banned, so right-clicking neither places it nor opens the answer menu.
            singleplayer.getConnection().waitForClientboundPackets();
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            singleplayer.getConnection().waitForServerboundPackets();
            context.waitTicks(2);
            if (context.computeOnClient(client -> client.gui.screen() instanceof AnswerScreen)) {
                throw new AssertionError("A banned item must not open the answer menu");
            }
            int logsAfterBan = singleplayer.getServer().computeOnServer(server ->
                    server.getPlayerList().getPlayers().getFirst().getMainHandItem().getCount());
            if (logsAfterBan != 1) {
                throw new AssertionError("A banned oak log must not be placed");
            }

            // Start a fresh lesson, mark it known, then revert it through the real word list.
            singleplayer.getServer().runOnServer(server -> OboecraftMod.serverProgress().update(
                    server.getPlayerList().getPlayers().getFirst(), OAK_LOG, SrsState.Leveled.NEW));
            singleplayer.getConnection().waitForClientboundPackets();
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitForScreen(AnswerScreen.class);
            singleplayer.getConnection().waitForServerboundPackets();
            // Show answer is held: the press shows the name, and its repeats must not show the reading too.
            context.runOnClient(client -> client.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(
                    InputConstants.KEY_F9, 0, 0)));
            context.waitFor(client -> client.gui.screen() instanceof AnswerScreen screen && screen.showsLessonAnswer());
            context.runOnClient(client -> client.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(
                    InputConstants.KEY_F9, 0, 0)));
            assertShowsReading(context, false, "A held Show answer's repeat must not show the reading");
            context.runOnClient(client -> client.gui.screen().keyReleased(new net.minecraft.client.input.KeyEvent(
                    InputConstants.KEY_F9, 0, 0)));
            context.runOnClient(client -> client.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(
                    InputConstants.KEY_F7, 0, 0)));
            context.waitFor(client -> OboecraftClient.progress().state(client.player, OAK_LOG) instanceof SrsState.Known
                    && !(client.gui.screen() instanceof AnswerScreen));
            context.getInput().pressKey(InputConstants.KEY_E);
            context.waitForScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen.class);
            WordListButtons.click(context);
            context.waitForScreen(io.github.willdalh.oboecraft.fabric.client.WordListScreen.class);
            context.runOnClient(client -> client.gui.screen().children().stream()
                    .filter(child -> child instanceof net.minecraft.client.gui.components.Button button
                            && button.getMessage().getString().equals("Known"))
                    .map(child -> (net.minecraft.client.gui.components.Button) child).findFirst().orElseThrow()
                    .onPress(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0)));
            context.takeScreenshot("word-list-known");
            if (!context.computeOnClient(client -> OboecraftClient.itemIcons()
                    .icon(client.level.registryAccess(), OAK_LOG)
                    .filter(icon -> icon.is(net.minecraft.world.item.Items.OAK_LOG)).isPresent())) {
                throw new AssertionError("The word list must show the oak log's icon");
            }
            if (!context.computeOnClient(client -> WordListReadingClipButtons.find(client.gui.screen()).stream()
                    .anyMatch(button -> button.getMessage().getString().equals("Play reading")))) {
                throw new AssertionError("The word list must have a reading clip button on the oak log's row");
            }
            context.runOnClient(client -> {
                var screen = client.gui.screen();
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(screen.width / 2.0, 62,
                        new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0)), false);
                var revert = screen.children().stream()
                        .filter(child -> child instanceof net.minecraft.client.gui.components.Button button
                                && button.getMessage().getString().equals("Revert to level 0"))
                        .map(child -> (net.minecraft.client.gui.components.Button) child).findFirst().orElseThrow();
                revert.onPress(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            });
            context.waitFor(client -> OboecraftClient.progress().state(client.player, OAK_LOG).equals(SrsState.Leveled.NEW));
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen.class);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(null);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitForScreen(AnswerScreen.class);
            singleplayer.getConnection().waitForServerboundPackets();
            context.runOnClient(client -> client.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(
                    InputConstants.KEY_F8, 0, 0)));
            context.waitFor(client -> OboecraftClient.progress().state(client.player, OAK_LOG) instanceof SrsState.Ignored
                    && !(client.gui.screen() instanceof AnswerScreen));
            singleplayer.getConnection().waitForClientboundPackets();
            if (!context.computeOnClient(client -> client.player.getMainHandItem().isEmpty())) {
                throw new AssertionError("Ignore must drop the held stack on both sides");
            }
        }
    }

    private static void assertStillReviewing(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        if (!context.computeOnClient(client -> client.gui.screen() instanceof AnswerScreen)) {
            throw new AssertionError("The answer menu should still be open");
        }
        boolean passed = singleplayer.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            return !(OboecraftMod.serverProgress().state(player, OAK_LOG).equals(SrsState.Leveled.NEW));
        });
        if (passed) {
            throw new AssertionError("Enter during a kanji conversion submitted the answer");
        }
    }

    private static void assertShowsReading(ClientGameTestContext context, boolean shown, String what) {
        if (context.computeOnClient(client -> client.gui.screen() instanceof AnswerScreen screen
                && screen.showsReading()) != shown) {
            throw new AssertionError(what);
        }
    }

    private static void assertBlock(TestSingleplayerContext singleplayer, BlockPos pos, boolean placed, String what) {
        boolean isOakLog = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(pos).is(Blocks.OAK_LOG));
        if (isOakLog != placed) {
            throw new AssertionError(what);
        }
    }
}
