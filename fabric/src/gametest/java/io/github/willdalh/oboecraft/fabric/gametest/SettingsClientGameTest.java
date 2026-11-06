package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willdalh.oboecraft.core.settings.LevelSettings;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.settings.ClientSettings;
import io.github.willdalh.oboecraft.fabric.client.settings.ClientSettingsFile;
import io.github.willdalh.oboecraft.fabric.client.settings.SettingsScreen;
import io.github.willdalh.oboecraft.fabric.settings.ServerSettingsFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/**
 * The settings screen in singleplayer: invalid server settings aren't saved, saved ones reach the built-in server and
 * come back to the client with the player's progress, and the clickable buttons setting adds buttons to the answer
 * menu. The default settings are saved again at the end, so the other client game tests use them.
 */
public class SettingsClientGameTest implements FabricClientGameTest {
    private static final Settings CUSTOM = new Settings(List.of(
            new LevelSettings(Duration.ofMinutes(1), 1), new LevelSettings(Duration.ofMinutes(2), 1)),
            Duration.ofSeconds(30), 10, true);

    @Override
    public void runTest(ClientGameTestContext context) {
        SingleplayerProgress.delete();
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.waitFor(client -> OboecraftClient.progress().hasProgress(client.player));
            changingTheSettings(context, singleplayer);
            clickingTheAnswerMenusButtons(context, singleplayer);
        } finally {
            context.runOnClient(client -> OboecraftClient.useClientSettings(ClientSettings.DEFAULTS));
            try {
                ClientSettingsFile.write(ClientSettingsFile.path(), ClientSettings.DEFAULTS);
                ServerSettingsFile.write(ServerSettingsFile.path(), Settings.DEFAULTS);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void changingTheSettings(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.runOnClient(client -> client.gui.setScreen(new SettingsScreen(null)));
        context.waitForScreen(SettingsScreen.class);
        context.takeScreenshot("settings-screen");

        // One demotion too few: nothing is saved, and the screen says why.
        context.runOnClient(client -> {
            fields(client).get(1).setValue("1");
            pressButton(client, "gui.done");
        });
        if (context.computeOnClient(client -> !(client.gui.screen() instanceof SettingsScreen screen)
                || screen.error().getString().isEmpty())) {
            throw new AssertionError("Invalid settings should keep the screen open with an error");
        }

        context.runOnClient(client -> {
            List<EditBox> fields = fields(client);
            fields.get(0).setValue("1m, 2m");
            fields.get(1).setValue("1, 1");
            fields.get(2).setValue("30s");
            fields.get(3).setValue("10");
            // Both toggles, clickable buttons and the drop setting, go from off to on.
            client.gui.screen().children().stream().filter(CycleButton.class::isInstance)
                    .forEach(toggle -> ((CycleButton<?>) toggle).onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0)));
            pressButton(client, "gui.done");
        });
        context.waitForScreen(null);
        singleplayer.getConnection().waitForClientboundPackets();
        context.waitFor(client -> OboecraftClient.progress().settings().equals(CUSTOM));
        Settings onServer = singleplayer.getServer().computeOnServer(server -> OboecraftMod.serverProgress().settings());
        check(onServer.equals(CUSTOM), "the built-in server should use the saved settings, not " + onServer);
        check(ServerSettingsFile.read(ServerSettingsFile.path()).equals(CUSTOM), "the saved settings file");
        check(context.computeOnClient(client -> OboecraftClient.clientSettings().clickableButtons()),
                "clickable buttons should be on");
        check(ClientSettingsFile.read(ClientSettingsFile.path()).clickableButtons(), "the saved client settings file");
    }

    /** A lesson's answer menu has a button for each action, and Show answer and Close do what their keys do. */
    private static void clickingTheAnswerMenusButtons(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        BlockPos target = singleplayer.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            BlockPos ground = player.blockPosition().offset(2, -1, 0);
            player.level().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());
            return ground;
        });
        singleplayer.getServer().runCommand("give @a minecraft:oak_log 4");
        singleplayer.getConnection().waitForClientboundPackets();
        context.getInput().lookAt(target);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitForScreen(AnswerScreen.class);
        singleplayer.getConnection().waitForServerboundPackets();
        context.takeScreenshot("answer-menu-buttons");
        context.runOnClient(client -> pressButton(client, "key.oboecraft.show"));
        context.waitFor(client -> client.gui.screen() instanceof AnswerScreen screen && screen.showsLessonAnswer());
        context.runOnClient(client -> pressButton(client, "key.oboecraft.close"));
        context.waitForScreen(null);
    }

    private static List<EditBox> fields(Minecraft client) {
        return client.gui.screen().children().stream()
                .filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
    }

    private static void pressButton(Minecraft client, String translationKey) {
        String label = Component.translatable(translationKey).getString();
        client.gui.screen().children().stream()
                .filter(child -> child instanceof Button button && button.getMessage().getString().equals(label))
                .map(Button.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError("No button " + label))
                .onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
