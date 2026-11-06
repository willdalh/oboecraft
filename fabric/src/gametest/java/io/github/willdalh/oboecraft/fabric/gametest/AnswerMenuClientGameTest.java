package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
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
        deleteSingleplayerProgress();
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

            // The typing bar holds a correct answer while the keyboard's kanji conversion still has text that isn't
            // confirmed. The Enter that confirms the conversion must not submit the answer, or it would pass here.
            context.getInput().typeChars("オークの原木");
            context.runOnClient(client -> client.keyboardHandler.textEditing(
                    client.getWindow().handle(), PreeditEvent.fromSdlTextEditing("げん", 2, 0)));
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            singleplayer.getConnection().waitForServerboundPackets();
            context.waitTicks(2);
            assertStillReviewing(context, singleplayer);

            // The conversion is cancelled, so Enter now submits the answer, which passes.
            context.runOnClient(client -> client.keyboardHandler.textEditing(client.getWindow().handle(), null));
            context.getInput().pressKey(InputConstants.KEY_RETURN);
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

    /** Singleplayer progress is shared across worlds, so a run starts from no progress. */
    private static void deleteSingleplayerProgress() {
        Path progress = FabricLoader.getInstance().getGameDir().resolve("oboecraft");
        if (!Files.exists(progress)) {
            return;
        }
        try (Stream<Path> files = Files.walk(progress)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
