package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

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
        SingleplayerProgress.delete();
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

            // A wrong lesson answer reveals the name, and the lesson goes on.
            context.getInput().typeChars("オーク");
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitFor(client -> client.gui.screen() instanceof AnswerScreen screen && screen.showsLessonAnswer());
            assertStillReviewing(context, singleplayer);

            // The typing bar holds a correct answer while the keyboard's kanji conversion still has text that isn't
            // confirmed. The keys belong to the conversion: Esc doesn't close the menu, and the Enter that confirms
            // the conversion doesn't submit the answer, or it would pass here.
            context.getInput().typeChars("オークの原木");
            context.runOnClient(client -> client.keyboardHandler.textEditing(
                    client.getWindow().handle(), PreeditEvent.fromSdlTextEditing("げん", 2, 0)));
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            singleplayer.getConnection().waitForServerboundPackets();
            context.waitTicks(2);
            context.getInput().pressKey(InputConstants.KEY_NUMPADENTER);
            singleplayer.getConnection().waitForServerboundPackets();
            assertStillReviewing(context, singleplayer);

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

            // The oak log is unlocked, so it is placed.
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            singleplayer.getConnection().waitForServerboundPackets();
            context.waitTicks(2);
            assertBlock(singleplayer, target.above(), true, "an unlocked oak log is placed");

            // Start a fresh lesson, mark it known, then revert it through the real word list.
            singleplayer.getServer().runOnServer(server -> OboecraftMod.serverProgress().update(
                    server.getPlayerList().getPlayers().getFirst(), OAK_LOG, SrsState.Leveled.NEW));
            singleplayer.getConnection().waitForClientboundPackets();
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitForScreen(AnswerScreen.class);
            singleplayer.getConnection().waitForServerboundPackets();
            context.runOnClient(client -> client.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(
                    InputConstants.KEY_F7, 0, 0)));
            context.waitFor(client -> OboecraftClient.progress().state(client.player, OAK_LOG) instanceof SrsState.Known
                    && !(client.gui.screen() instanceof AnswerScreen));
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitForScreen(io.github.willdalh.oboecraft.fabric.client.WordListScreen.class);
            context.takeScreenshot("word-list-known");
            context.runOnClient(client -> {
                var screen = client.gui.screen();
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(screen.width / 2.0, 62,
                        new net.minecraft.client.input.MouseButtonInfo(1, 0)), false);
                var revert = screen.children().stream()
                        .filter(child -> child instanceof net.minecraft.client.gui.components.Button button
                                && button.getMessage().getString().equals("Revert to level 0"))
                        .map(child -> (net.minecraft.client.gui.components.Button) child).findFirst().orElseThrow();
                revert.onPress(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            });
            context.waitFor(client -> OboecraftClient.progress().state(client.player, OAK_LOG).equals(SrsState.Leveled.NEW));
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
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

    private static void assertBlock(TestSingleplayerContext singleplayer, BlockPos pos, boolean placed, String what) {
        boolean isOakLog = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(pos).is(Blocks.OAK_LOG));
        if (isOakLog != placed) {
            throw new AssertionError(what);
        }
    }
}
