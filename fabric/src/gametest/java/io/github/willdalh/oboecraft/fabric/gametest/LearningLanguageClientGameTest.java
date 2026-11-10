package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.WordListScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

/** Real joins choose a language; plain-name lessons have no reading reveal, sound or speaker column. */
public class LearningLanguageClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        String original = context.computeOnClient(client -> client.options.languageCode);
        ProgressFolder.delete();
        try {
            for (LearningLanguage language : LearningLanguage.values()) {
                if (language.hasReadings()) continue;
                context.runOnClient(client -> client.options.languageCode = language.code());
                try (var world = context.worldBuilder().adjustSettings(settings ->
                        settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
                    world.getConnection().waitForChunksRender();
                    context.waitFor(client -> OboecraftClient.progress().hasProgress(client.player));
                    check(context.computeOnClient(client -> OboecraftClient.progress().language()) == language, "join language");
                    world.getServer().runCommand("give @a minecraft:oak_log 4");
                    world.getConnection().waitForClientboundPackets();
                    context.getInput().pressKey(InputConstants.KEY_E);
                    context.waitForScreen(InventoryScreen.class);
                    context.runOnClient(client -> OboecraftClient.reviews().reviewInventorySlot(client.player, 36));
                    context.waitForScreen(AnswerScreen.class);
                    world.getConnection().waitForServerboundPackets();
                    context.getInput().pressKey(InputConstants.KEY_F9);
                    context.waitFor(client -> client.gui.screen() instanceof AnswerScreen screen && screen.showsLessonAnswer());
                    context.getInput().pressKey(InputConstants.KEY_F9);
                    check(context.computeOnClient(client -> client.gui.screen() instanceof AnswerScreen screen
                            && !screen.showsReading()), "plain name never reveals a reading");
                    check(context.computeOnClient(client -> client.gui.screen().children().stream().noneMatch(child ->
                            child instanceof Button button && button.getMessage().getString().equals(
                                    Component.translatable("oboecraft.answer_menu.show_reading").getString()))), "no reading button");
                    context.takeScreenshot("lesson-" + language.code());
                    context.runOnClient(client -> {
                        PlayedReadingClips.take();
                        // A change in-world must not change the active list or its answers.
                        client.options.languageCode = "en_us";
                    });
                    context.waitTicks(2);
                    check(context.computeOnClient(client -> OboecraftClient.progress().language()) == language, "active language is fixed");
                    String answer = OboecraftMod.itemMappings().get(language)
                            .nameList().byId(new ItemId("block.minecraft.oak_log")).orElseThrow().name().text();
                    context.getInput().typeChars(answer);
                    context.getInput().pressKey(InputConstants.KEY_RETURN);
                    context.waitForScreen(InventoryScreen.class);
                    check(context.computeOnClient(client -> OboecraftClient.notification().shown().orElse("").equals(answer)), "pass shows name");
                    check(context.computeOnClient(client -> PlayedReadingClips.take().isEmpty()), "plain pass has no reading clip");
                    WordListButtons.click(context);
                    context.waitForScreen(WordListScreen.class);
                    check(context.computeOnClient(client -> WordListReadingClipButtons.find(client.gui.screen()).isEmpty()), "no speaker column");
                    context.takeScreenshot("word-list-" + language.code());
                }
            }
        } finally {
            context.runOnClient(client -> client.options.languageCode = original);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
