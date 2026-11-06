package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.display.ClientLockTimerTooltip;
import io.github.willdalh.oboecraft.fabric.client.display.ItemDisplay;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/** Exercises the actual tooltip, HUD and item-decoration hooks in a real client with synced server progress. */
public class ItemDisplayClientGameTest implements FabricClientGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Override
    public void runTest(ClientGameTestContext context) {
        SingleplayerProgress.delete();
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.waitFor(client -> OboecraftClient.progress().hasProgress(client.player));
            singleplayer.getServer().runCommand("give @a minecraft:oak_log 4");
            singleplayer.getConnection().waitForClientboundPackets();

            sync(context, singleplayer, SrsState.Leveled.NEW, NOW);
            context.runOnClient(client -> {
                checkDisplay(client, new ItemStack(Items.OAK_LOG), true, false, false);
                ItemStack renamed = new ItemStack(Items.OAK_LOG);
                renamed.set(DataComponents.CUSTOM_NAME, Component.literal("A custom name"));
                renamed.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Keep this lore"))));
                checkDisplay(client, renamed, true, false, false);
                check(Screen.getTooltipFromItem(client, renamed).stream()
                        .anyMatch(line -> line.getString().equals("Keep this lore")), "lore is preserved");
                check(renamed.getHoverName().getString().equals("A custom name"), "the actual stack name is intact");
                checkDisplay(client, OtherModStacks.of(Items.STONE), false, false, false);
                check(!ItemDisplay.of(ItemStack.EMPTY).hidden(), "empty stacks are untouched");
            });
            inventoryScreenshot(context, "display-locked", false);

            SrsState unlocked = new SrsState.Leveled(1, Optional.of(NOW.plusSeconds(600)));
            sync(context, singleplayer, unlocked, NOW);
            context.runOnClient(client -> checkDisplay(client, new ItemStack(Items.OAK_LOG), false, true, false));
            inventoryScreenshot(context, "display-timer", true);

            sync(context, singleplayer, unlocked, NOW.plusSeconds(570));
            context.runOnClient(client -> {
                checkDisplay(client, new ItemStack(Items.OAK_LOG), false, true, true);
                ItemStack hiddenTooltip = new ItemStack(Items.OAK_LOG);
                hiddenTooltip.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
                check(tooltip(client, hiddenTooltip).texts.isEmpty(), "a hidden vanilla tooltip stays hidden");
                var before = OboecraftClient.progress().state(client.player, OAK_LOG);
                tooltip(client, new ItemStack(Items.OAK_LOG));
                check(before.equals(OboecraftClient.progress().state(client.player, OAK_LOG)), "rendering doesn't change progress");
            });
            inventoryScreenshot(context, "display-close-to-locking", true);

            // Only server time changes: the next frame expires the timer, name and marker without a new SRS state.
            sync(context, singleplayer, unlocked, NOW.plusSeconds(600));
            context.runOnClient(client -> checkDisplay(client, new ItemStack(Items.OAK_LOG), true, false, false));
            sync(context, singleplayer, new SrsState.Leveled(OboecraftMod.settings().topLevel(), Optional.empty()), NOW);
            context.runOnClient(client -> checkDisplay(client, new ItemStack(Items.OAK_LOG), false, false, false));

            sync(context, singleplayer, SrsState.Leveled.NEW, NOW);
            for (String mode : List.of("creative", "spectator", "adventure")) {
                singleplayer.getServer().runCommand("gamemode " + mode + " @a");
                singleplayer.getConnection().waitForClientboundPackets();
                context.runOnClient(client -> checkDisplay(client, new ItemStack(Items.OAK_LOG),
                        mode.equals("adventure"), false, false));
            }
            singleplayer.getServer().runCommand("gamemode survival @a");
            singleplayer.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> {
                OboecraftClient.progress().clear();
                checkDisplay(client, new ItemStack(Items.OAK_LOG), false, false, false);
                OboecraftClient.progress().sync(client.getConnection(), Progress.EMPTY, NOW);
                checkDisplay(client, new ItemStack(Items.OAK_LOG), true, false, false);
                // A snapshot with no connection must never activate this connection's display.
                OboecraftClient.progress().sync(null, Progress.EMPTY, NOW);
                checkDisplay(client, new ItemStack(Items.OAK_LOG), false, false, false);
                OboecraftClient.progress().sync(client.getConnection(), Progress.EMPTY, NOW);
            });
            bundleTooltip(context, singleplayer);
        } finally {
            OboecraftMod.clock().setSource(InstantSource.system());
        }
    }

    private static void sync(ClientGameTestContext context, TestSingleplayerContext singleplayer, SrsState state, Instant now) {
        singleplayer.getServer().runOnServer(server -> {
            OboecraftMod.clock().setSource(InstantSource.fixed(now));
            OboecraftMod.serverProgress().update(server.getPlayerList().getPlayers().getFirst(), OAK_LOG, state);
        });
        singleplayer.getConnection().waitForClientboundPackets();
        context.waitFor(client -> OboecraftClient.progress().state(client.player, OAK_LOG).equals(state)
                && !OboecraftClient.progress().now().isBefore(now)
                && OboecraftClient.progress().now().isBefore(now.plusSeconds(20)));
    }

    private static void checkDisplay(Minecraft client, ItemStack stack, boolean hidden, boolean timed, boolean marked) {
        String expectedName = hidden ? "???" : stack.getHoverName().getString();
        check(Screen.getTooltipFromItem(client, stack).getFirst().getString().equals(expectedName), "tooltip name: " + expectedName);
        RecordingGraphics tooltip = tooltip(client, stack);
        check(tooltip.texts.contains(expectedName), "the tooltip renders its name");
        check(tooltip.components.stream().anyMatch(ClientLockTimerTooltip.class::isInstance) == timed, "timer component presence");
        check(tooltip.fills.stream().anyMatch(fill -> fill.color == ItemDisplay.TIMER_COLOR
                || fill.color == ItemDisplay.WARNING_COLOR) == timed, "timer bar is rendered");
        if (timed) {
            check(!tooltip.components.stream().filter(ClientLockTimerTooltip.class::isInstance)
                    .findFirst().orElseThrow().showTooltipWithItemInHand(),
                    "adding a timer doesn't enable ordinary tooltips with an item carried");
            check(tooltip.texts.stream().anyMatch(text -> text.startsWith("Locks in ")), "localized remaining time is rendered");
            Fill bar = tooltip.fills.stream().filter(fill -> fill.color == (marked
                    ? ItemDisplay.WARNING_COLOR : ItemDisplay.TIMER_COLOR)).findFirst().orElseThrow();
            if (marked) {
                check(bar.width <= 16, "near-expiry bar is nearly empty");
            } else {
                check(bar.width >= 80, "fresh bar is full");
            }
        }
        RecordingGraphics decorations = new RecordingGraphics(client);
        decorations.itemDecorations(client.font, stack, 10, 10);
        check(decorations.fills.stream().anyMatch(fill -> fill.color == ItemDisplay.WARNING_COLOR
                && fill.width == 3 && fill.height == 3) == marked, "slot marker presence");

        // A separate HUD observes the held stack and renders the real selected-item label hook.
        ItemStack held = client.player.getInventory().getSelectedItem();
        client.player.getInventory().setItem(client.player.getInventory().getSelectedSlot(), stack);
        try {
            Hud hud = new Hud(client);
            hud.tick(false);
            RecordingGraphics graphics = new RecordingGraphics(client);
            hud.extractRenderState(graphics, DeltaTracker.ZERO);
            if (client.player.isSpectator()) {
                // Vanilla draws the spectator menu instead of a held-item label.
                check(!graphics.texts.contains("???"), "spectator HUD has no masked item label");
            } else {
                check(graphics.texts.contains(expectedName), "hotbar renders " + expectedName + ", got " + graphics.texts);
            }
        } finally {
            client.player.getInventory().setItem(client.player.getInventory().getSelectedSlot(), held);
        }
    }

    private static void bundleTooltip(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var id = OboecraftMod.itemMapping().entryOf(new ItemStack(Items.BUNDLE)).orElseThrow().id();
            OboecraftMod.serverProgress().update(player, id, new SrsState.Leveled(1, Optional.of(NOW.plusSeconds(600))));
        });
        singleplayer.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> {
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            ClientTooltipComponent original = ClientTooltipComponent.create(bundle.getItem().getTooltipImage(bundle).orElseThrow());
            RecordingGraphics graphics = tooltip(client, bundle);
            ClientTooltipComponent combined = graphics.components.stream()
                    .filter(ClientLockTimerTooltip.class::isInstance).findFirst().orElseThrow();
            check(combined.getHeight(client.font) > original.getHeight(client.font), "bundle image is kept below the timer");
            check(graphics.texts.stream().anyMatch(text -> text.contains("Empty")), "bundle preview still renders");
        });
    }

    private static RecordingGraphics tooltip(Minecraft client, ItemStack stack) {
        RecordingGraphics graphics = new RecordingGraphics(client);
        graphics.setTooltipForNextFrame(client.font, stack, 40, 40);
        graphics.extractDeferredElements(40, 40, 0);
        return graphics;
    }

    private static void inventoryScreenshot(ClientGameTestContext context, String name, boolean timed) {
        context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
        // TestInput uses window coordinates; inventory slots use scaled GUI units.
        double[] position = context.computeOnClient(client -> new double[] {
                ((client.getWindow().getGuiScaledWidth() - 176) / 2.0 + 16)
                        * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                ((client.getWindow().getGuiScaledHeight() - 166) / 2.0 + 150)
                        * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()});
        context.getInput().setCursorPos(position[0], position[1]);
        context.waitTicks(2);
        context.runOnClient(client -> {
            int x = (client.getWindow().getGuiScaledWidth() - 176) / 2 + 16;
            int y = (client.getWindow().getGuiScaledHeight() - 166) / 2 + 150;
            RecordingGraphics graphics = new RecordingGraphics(client);
            client.gui.screen().extractRenderState(graphics, x, y, 0);
            graphics.extractDeferredElements(x, y, 0);
            check(graphics.components.stream().anyMatch(ClientLockTimerTooltip.class::isInstance) == timed,
                    "the inventory screen renders the timer when timed");
        });
        context.takeScreenshot(name);
        context.runOnClient(client -> client.gui.setScreen(null));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Fill(int width, int height, int color) {
    }

    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        final List<String> texts = new ArrayList<>();
        final List<Fill> fills = new ArrayList<>();
        List<ClientTooltipComponent> components = List.of();

        RecordingGraphics(Minecraft client) {
            super(client, new GuiRenderState(), 40, 40);
        }

        @Override
        public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            StringBuilder value = new StringBuilder();
            text.accept((index, style, codePoint) -> {
                value.appendCodePoint(codePoint);
                return true;
            });
            texts.add(value.toString());
            super.text(font, text, x, y, color, shadow);
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int color) {
            fills.add(new Fill(x2 - x1, y2 - y1, color));
            super.fill(x1, y1, x2, y2, color);
        }

        @Override
        public void tooltip(Font font, List<ClientTooltipComponent> lines, int x, int y,
                ClientTooltipPositioner positioner, @Nullable Identifier style, boolean extraSpace) {
            components = List.copyOf(lines);
            super.tooltip(font, lines, x, y, positioner, style, extraSpace);
        }
    }
}
