package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.DisplayState;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.display.ClientLockTimerTooltip;
import io.github.willdalh.oboecraft.fabric.client.display.ItemDisplay;
import io.github.willdalh.oboecraft.fabric.client.display.ClientDisplayHooks;
import io.github.willdalh.oboecraft.fabric.client.display.TooltipDisplayContext;
import io.github.willdalh.oboecraft.fabric.client.display.TimerText;
import java.time.Instant;
import java.time.Duration;
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
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
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
                ItemStack indexed = new ItemStack(Items.OAK_LOG);
                check(indexed.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(client.level),
                        null, net.minecraft.world.item.TooltipFlag.NORMAL).getFirst().getString().equals("Oak Log"),
                        "raw tooltip names used by search remain unchanged");
                var searches = new net.minecraft.client.multiplayer.SessionSearchTrees();
                searches.updateCreativeTooltips(client.level.registryAccess(), List.of(indexed));
                check(searches.creativeNameSearch().search("oak log").contains(indexed), "background search indexes real names");
                check(searches.creativeNameSearch().search("???").isEmpty(), "search never indexes masked names");
                nestedNamesAndScopes(client);
                timerTranslations();
                checkDisplay(client, new ItemStack(Items.OAK_LOG), true, false, false);
                ItemStack renamed = new ItemStack(Items.OAK_LOG);
                renamed.set(DataComponents.CUSTOM_NAME, Component.literal("A custom name"));
                renamed.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Keep this lore"))));
                checkDisplay(client, renamed, true, false, false);
                check(Screen.getTooltipFromItem(client, renamed).stream()
                        .anyMatch(line -> line.getString().equals("Keep this lore")), "lore is preserved");
                check(renamed.getHoverName().getString().equals("A custom name"), "the actual stack name is intact");
                checkDisplay(client, OtherModStacks.of(Items.STONE), false, false, false);
                check(!(ItemDisplay.of(ItemStack.EMPTY) instanceof DisplayState.Hidden), "empty stacks are untouched");
            });
            inventoryScreenshot(context, "display-locked", false);

            SrsState unlocked = new SrsState.Leveled(1, Optional.of(NOW.plusSeconds(600)));
            sync(context, singleplayer, unlocked, NOW);
            context.runOnClient(client -> {
                checkDisplay(client, new ItemStack(Items.OAK_LOG), false, true, false);
                ItemStack box = new ItemStack(Items.SHULKER_BOX);
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.OAK_LOG, 4))));
                check(tooltip(client, box).texts.stream().anyMatch(text -> text.contains("Oak Log")),
                        "unlocking restores nested names on the next draw");
            });
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
            sync(context, singleplayer, new SrsState.Leveled(Settings.DEFAULTS.topLevel(), Optional.empty()), NOW);
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
                var captured = OboecraftClient.progress().capture();
                Instant time = captured.now();
                ItemDisplay.inFrame(() -> {
                    check(ItemDisplay.of(new ItemStack(Items.OAK_LOG)) instanceof DisplayState.Hidden, "frame starts locked");
                    OboecraftClient.progress().clear();
                    check(ItemDisplay.of(new ItemStack(Items.OAK_LOG)) instanceof DisplayState.Hidden, "one snapshot per frame");
                });
                check(captured.hasProgress(client.player) && captured.now() == time, "captured view keeps its state and time");
                checkDisplay(client, new ItemStack(Items.OAK_LOG), false, false, false);
                OboecraftClient.progress().sync(client.getConnection(), Progress.EMPTY, Settings.DEFAULTS, NOW);
                checkDisplay(client, new ItemStack(Items.OAK_LOG), true, false, false);
                // A snapshot with no connection must never activate this connection's display.
                OboecraftClient.progress().sync(null, Progress.EMPTY, Settings.DEFAULTS, NOW);
                checkDisplay(client, new ItemStack(Items.OAK_LOG), false, false, false);
                OboecraftClient.progress().sync(client.getConnection(), Progress.EMPTY, Settings.DEFAULTS, NOW);
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
        check(tooltip.fills.stream().anyMatch(fill -> fill.color == ClientDisplayHooks.TIMER_COLOR
                || fill.color == ClientDisplayHooks.WARNING_COLOR) == timed, "timer bar is rendered");
        if (timed) {
            check(!tooltip.components.stream().filter(ClientLockTimerTooltip.class::isInstance)
                    .findFirst().orElseThrow().showTooltipWithItemInHand(),
                    "adding a timer doesn't enable ordinary tooltips with an item carried");
            check(tooltip.texts.stream().anyMatch(text -> text.startsWith("Locks in ")), "localized remaining time is rendered");
            Fill bar = tooltip.fills.stream().filter(fill -> fill.color == (marked
                    ? ClientDisplayHooks.WARNING_COLOR : ClientDisplayHooks.TIMER_COLOR)).findFirst().orElseThrow();
            if (marked) {
                check(bar.width <= 16, "near-expiry bar is nearly empty");
            } else {
                check(bar.width >= 80, "fresh bar is full");
            }
        }
        RecordingGraphics decorations = new RecordingGraphics(client);
        decorations.itemDecorations(client.font, stack, 10, 10);
        check(decorations.fills.stream().anyMatch(fill -> fill.color == ClientDisplayHooks.WARNING_COLOR
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
            var rawImage = bundle.getTooltipImage().orElseThrow();
            check(rawImage.getClass() == bundle.getItem().getTooltipImage(bundle).orElseThrow().getClass(),
                    "the raw bundle image type stays unchanged while timed");
            ClientTooltipComponent original = ClientTooltipComponent.create(rawImage);
            RecordingGraphics graphics = tooltip(client, bundle);
            check(graphics.components.stream().anyMatch(ClientLockTimerTooltip.class::isInstance), "bundle has a timer");
            check(graphics.components.stream().anyMatch(component -> component.getClass() == original.getClass()),
                    "the original bundle component is kept separately");
            check(graphics.texts.stream().anyMatch(text -> text.contains("Empty")), "bundle preview still renders");
            BundleContents.Mutable contents = new BundleContents.Mutable();
            contents.tryInsert(new ItemStack(Items.OAK_LOG, 4));
            contents.toggleSelectedItem(0);
            bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            var preview = tooltip(client, bundle);
            check(preview.texts.contains("???") && !preview.texts.contains("Oak Log"),
                    "a timed bundle's selected locked item name is masked");
        });
        singleplayer.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            BundleContents.Mutable contents = new BundleContents.Mutable();
            contents.tryInsert(new ItemStack(Items.OAK_LOG, 4));
            contents.toggleSelectedItem(0);
            bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            player.getInventory().setItem(0, bundle);
            player.containerMenu.broadcastChanges();
        });
        singleplayer.getConnection().waitForClientboundPackets();
        inventoryScreenshot(context, "display-bundle-locked-content", true);
    }

    private static void nestedNamesAndScopes(Minecraft client) {
        ItemStack log = new ItemStack(Items.OAK_LOG, 4);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(log)));
        var lines = tooltip(client, box).texts;
        check(lines.stream().anyMatch(text -> text.contains("???") && text.contains("4")), "container counts keep masked names");
        check(lines.stream().noneMatch(text -> text.contains("Oak Log")), "container does not leak the locked name");
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        BundleContents.Mutable contents = new BundleContents.Mutable();
        contents.tryInsert(log.copy());
        contents.toggleSelectedItem(0);
        bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
        var preview = tooltip(client, bundle).texts;
        check(preview.stream().filter("???"::equals).count() >= 2, "bundle title and selected item are masked");
        check(!preview.contains("Oak Log"), "bundle caption does not leak a locked name");
        try {
            TooltipDisplayContext.withNames(() -> {
                check(log.getHoverName().getString().equals("???"), "names are hidden in the drawing scope");
                String background = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        Screen.getTooltipFromItem(client, log).getFirst().getString()).join();
                check(background.equals("Oak Log"), "drawing scope never reaches background tooltip callers");
                throw new IllegalStateException("test scope exit");
            });
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("test scope exit"), "expected scope exit");
        }
        check(log.getHoverName().getString().equals("Oak Log"), "drawing scope is restored after exceptions");
    }

    private static void timerTranslations() {
        var previous = net.minecraft.locale.Language.getInstance();
        try {
            for (String language : List.of("en_us", "ja_jp")) {
                java.util.Map<String, String> translations = new java.util.HashMap<>();
                try (var stream = ItemDisplayClientGameTest.class.getResourceAsStream("/assets/oboecraft/lang/" + language + ".json")) {
                    net.minecraft.locale.Language.loadFromJson(stream, translations::put);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                net.minecraft.locale.Language.inject(new net.minecraft.locale.Language() {
                    @Override public String getOrDefault(String key, String fallback) { return translations.getOrDefault(key, fallback); }
                    @Override public boolean has(String key) { return translations.containsKey(key); }
                    @Override public boolean isDefaultRightToLeft() { return false; }
                    @Override public FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text) {
                        return previous.getVisualOrder(text);
                    }
                });
                boolean japanese = language.equals("ja_jp");
                check(TimerText.format(Duration.ofSeconds(90)).getString().equals(japanese ? "1分30秒" : "1m 30s"),
                        "translated separator");
                check(TimerText.format(Duration.ofDays(30).minusSeconds(1)).getString().equals(japanese ? "29日23時間" : "29d 23h"),
                        "long timers show only two units");
                check(TimerText.format(Duration.ofMillis(1)).getString().equals(japanese ? "1秒" : "1s"), "positive time rounds up");
                check(TimerText.format(Duration.ofHours(1).plusSeconds(1)).getString().equals(japanese ? "1時間1秒" : "1h 1s"),
                        "zero units are omitted");
            }
        } finally {
            net.minecraft.locale.Language.inject(previous);
        }
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
            if (components.isEmpty()) {
                components = List.copyOf(lines);
            }
            super.tooltip(font, lines, x, y, positioner, style, extraSpace);
        }
    }
}
