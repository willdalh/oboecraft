package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.DisplayRules;
import io.github.willdalh.oboecraft.core.srs.DisplayState;
import io.github.willdalh.oboecraft.core.srs.LearningOrder;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsRules;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.display.ClientLockTimerTooltip;
import io.github.willdalh.oboecraft.fabric.client.display.TimerText;
import io.github.willdalh.oboecraft.fabric.network.RevertItem;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Four separate lists, opening on the Learning tab. Each row has the item's icon, name and a button that plays its
 * reading clip, left-justified from the left edge of the tabs. The reading itself isn't shown, so the player recalls it
 * before listening. On the Learning tab, a locked item's name is {@code ???} and it has no button, and each row also
 * shows the level and either the lock timer or the ban time left, which update live. Revert, on the other tabs, is
 * confirmed through a context action and waits for the server's update.
 */
public final class WordListScreen extends Screen implements OverMenuScreen {
    private static final int LEARNING = 0;
    private static final int KNOWN = 1;
    private static final int IGNORED = 2;
    private static final int TAB_WIDTH = 75;
    private static final int TAB_GAP = 3;
    private static final int TAB_COUNT = 4;
    private static final int TABS_WIDTH = TAB_COUNT * TAB_WIDTH + (TAB_COUNT - 1) * TAB_GAP;
    private static final int LIST_TOP = 56;
    /** The space below the list, for the page buttons and the hint. */
    private static final int LIST_BOTTOM_MARGIN = 64;
    private static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int RIGHT_MARGIN = 10;
    private static final Identifier READING_CLIP_SPRITE =
            Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, "icon/reading_clip");
    private static final int READING_CLIP_SPRITE_WIDTH = 10;
    private static final int READING_CLIP_SPRITE_HEIGHT = 14;
    private static final int READING_CLIP_BUTTON_SIZE = 20;
    private static final int READING_CLIP_BUTTON_GAP = 4;
    /** The width of the lock timer column at the right of a Learning row, which fits the longest label. */
    private static final int TIMER_WIDTH = 110;
    private static final int LEVEL_GAP = 8;
    private static final int LOCKED_NAME_COLOR = 0xFFA0A0A0;
    private static final int LEVEL_COLOR = 0xFFAAAAAA;
    private static final int BANNED_COLOR = 0xFFFF5555;

    /** The Learning tab's order, kept until the game closes. Only the client thread uses it. */
    private static LearningOrder learningOrder = LearningOrder.URGENCY;

    private final InventoryScreen inventory;
    private int group = LEARNING;
    private int cachedGroup = -1;
    private @Nullable LearningOrder cachedOrder;
    private int page;
    private int pageSize;
    private Progress snapshot = Progress.EMPTY;
    private List<NameListEntry> entries = List.of();
    /** The names of the rows on the current page, fitted to the screen once per layout. */
    private List<String> rowTexts = List.of();
    /** Whether each row on the current page was locked when it was laid out, since a locked row looks different. */
    private List<Boolean> rowsLocked = List.of();
    private @Nullable ItemId selected;
    /** Where the Learning tab's level column starts, wide enough for the top level, set once per layout. */
    private int levelLeft;

    /** Opens over the inventory, which stays open. Close goes back to it, and the inventory key closes both. */
    public WordListScreen(InventoryScreen inventory) {
        super(Component.translatable("oboecraft.word_list.title"));
        this.inventory = inventory;
    }

    @Override public AbstractContainerScreen<?> menuScreen() { return inventory; }

    @Override protected void init() {
        pageSize = Math.max(1, (height - LIST_TOP - LIST_BOTTOM_MARGIN) / ROW_HEIGHT);
        Progress current = OboecraftClient.progress().progress();
        if (snapshot != current || cachedGroup != group || cachedOrder != learningOrder) {
            snapshot = current;
            cachedGroup = group;
            cachedOrder = learningOrder;
            entries = OboecraftMod.itemMapping().nameList().entries().stream()
                    .filter(entry -> inGroup(snapshot.state(entry.id())))
                    .sorted(entryOrder()).toList();
        }
        page = Math.min(page, Math.max(0, (entries.size() - 1) / pageSize));
        for (int tab = 0; tab < TAB_COUNT; tab++) {
            int chosen = tab;
            var button = addRenderableWidget(Button.builder(groupLabel(tab), ignored -> {
                group = chosen; page = 0; selected = null; rebuildWidgets();
            }).bounds(listLeft() + tab * (TAB_WIDTH + TAB_GAP), 28, TAB_WIDTH, 20).build());
            button.active = tab != group;
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
            page--; selected = null; rebuildWidgets();
        }).bounds(width / 2 - 100, height - 45, 30, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
            page++; selected = null; rebuildWidgets();
        }).bounds(width / 2 + 70, height - 45, 30, 20).build());
        next.active = (page + 1) * pageSize < entries.size();
        levelLeft = timerLeft() - LEVEL_GAP - font.width(levelText(settings().topLevel()));
        List<NameListEntry> pageEntries = pageEntries();
        rowsLocked = pageRowsLocked();
        rowTexts = IntStream.range(0, pageEntries.size())
                .mapToObj(row -> rowText(pageEntries.get(row), rowsLocked.get(row))).toList();
        for (int row = 0; row < rowTexts.size(); row++) {
            if (rowsLocked.get(row)) continue;
            var entry = pageEntries.get(row);
            addRenderableWidget(SpriteIconButton.builder(Component.translatable("oboecraft.word_list.play_reading"),
                            ignored -> OboecraftClient.readingClips().play(entry.id()), true)
                    .size(READING_CLIP_BUTTON_SIZE, READING_CLIP_BUTTON_SIZE)
                    .sprite(READING_CLIP_SPRITE, READING_CLIP_SPRITE_WIDTH, READING_CLIP_SPRITE_HEIGHT)
                    .withTootip().build())
                    .setPosition(textLeft() + font.width(rowTexts.get(row)) + READING_CLIP_BUTTON_GAP,
                            rowTop(row) + (ICON_SIZE - READING_CLIP_BUTTON_SIZE) / 2);
        }
        if (group == LEARNING) {
            addRenderableWidget(Button.builder(sortLabel(), ignored -> {
                learningOrder = learningOrder.next(); page = 0; rebuildWidgets();
            }).bounds(width / 2 - 65, height - 45, 130, 20).build());
        } else if (selected != null) {
            ItemId item = selected;
            addRenderableWidget(Button.builder(Component.translatable("oboecraft.word_list.revert"), ignored -> {
                ClientPlayNetworking.send(new RevertItem(item));
                selected = null; rebuildWidgets();
            }).bounds(width / 2 - 65, height - 45, 130, 20).build());
        }
    }

    /** Rows start at the left edge of the tabs and reach the right margin of the screen. */
    private int listLeft() { return width / 2 - TABS_WIDTH / 2; }

    private int listRight() { return width - RIGHT_MARGIN; }

    private int textLeft() { return listLeft() + ICON_SIZE + ICON_GAP; }

    private int rowTop(int row) { return LIST_TOP + row * ROW_HEIGHT; }

    /** On the Learning tab, the level and the lock timer or ban are right of the name. */
    private int nameRight() {
        return group == LEARNING ? levelLeft - LEVEL_GAP : listRight();
    }

    private int timerLeft() { return listRight() - TIMER_WIDTH; }

    /**
     * The row's name, cut short so that the reading clip button after it still fits. A locked item's name is
     * {@code ???}, with no button.
     */
    private String rowText(NameListEntry entry, boolean locked) {
        if (locked) return fitted("???", nameRight() - textLeft());
        return fitted(entry.japaneseName().text(),
                nameRight() - textLeft() - READING_CLIP_BUTTON_GAP - READING_CLIP_BUTTON_SIZE);
    }

    private List<NameListEntry> pageEntries() {
        return entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize));
    }

    /** Whether each row on the current page is locked now. Only Learning rows can be locked. */
    private List<Boolean> pageRowsLocked() {
        Instant now = OboecraftClient.progress().now();
        return pageEntries().stream().map(entry -> group == LEARNING && SrsRules.isLocked(snapshot.state(entry.id()), now, settings()))
                .toList();
    }

    private boolean inGroup(SrsState state) {
        return switch (group) {
            case LEARNING -> LearningOrder.isLearning(state, settings());
            case KNOWN -> state instanceof SrsState.Known;
            case IGNORED -> state instanceof SrsState.Ignored;
            default -> state instanceof SrsState.Leveled && SrsRules.canRevert(state, settings());
        };
    }

    /** The Learning tab in its chosen order, and the other tabs by Japanese name. */
    private Comparator<NameListEntry> entryOrder() {
        Comparator<NameListEntry> byName = Comparator.comparing(entry -> entry.japaneseName().text());
        if (group != LEARNING) return byName;
        Comparator<NameListEntry> byState = Comparator.comparing(
                entry -> (SrsState.Leveled) snapshot.state(entry.id()), learningOrder.comparator());
        return byState.thenComparing(byName);
    }

    private static Settings settings() { return OboecraftClient.progress().settings(); }

    private static Component groupLabel(int group) {
        return Component.translatable("oboecraft.word_list." + switch (group) {
            case LEARNING -> "learning"; case KNOWN -> "known"; case IGNORED -> "ignored"; default -> "mastered";
        });
    }

    private static Component sortLabel() {
        return Component.translatable("oboecraft.word_list.sort",
                Component.translatable("oboecraft.word_list.sort." + switch (learningOrder) {
                    case URGENCY -> "urgency"; case LEVEL -> "level";
                }));
    }

    private static Component levelText(int level) {
        return Component.translatable("oboecraft.word_list.level", level);
    }

    /** Without progress there is no word list, so it goes back to the inventory, which then has no button. */
    @Override public void tick() {
        if (minecraft.player == null || !OboecraftClient.progress().hasProgress(minecraft.player)) { onClose(); return; }
        if (snapshot != OboecraftClient.progress().progress()) { selected = null; rebuildWidgets(); return; }
        if (!rowsLocked.equals(pageRowsLocked())) rebuildWidgets();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int row = (int) Math.floor((event.y() - LIST_TOP) / ROW_HEIGHT);
        int index = page * pageSize + row;
        if (event.button() == 1 && group != LEARNING && row >= 0 && row < pageSize && index < entries.size()
                && event.x() >= listLeft() && event.x() < listRight()) {
            selected = entries.get(index).id();
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyInventory.matches(event)) { closeInventory(); return true; }
        if (ReviewKeys.CLOSE.matches(event)) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override public void onClose() { returnToMenuScreen(minecraft); }

    /**
     * Closes the inventory as its own inventory key does, so the server is told it closed, without showing it again. Its
     * {@code removed} was skipped when the word list opened over it, so it runs here, as vanilla runs it on closing.
     */
    private void closeInventory() {
        if (minecraft.player == null || inventory.getMenu() != minecraft.player.containerMenu) {
            returnToMenuScreen(minecraft);
            return;
        }
        inventory.onClose();
        inventory.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 10, -1);
        Instant now = OboecraftClient.progress().now();
        for (int row = 0; row < rowTexts.size(); row++) {
            var entry = entries.get(page * pageSize + row);
            int y = rowTop(row);
            int textY = y + (ICON_SIZE - font.lineHeight) / 2 + 1;
            if (minecraft.level != null) {
                OboecraftClient.itemIcons().icon(minecraft.level.registryAccess(), entry.id())
                        .ifPresent(icon -> graphics.item(icon, listLeft(), y));
            }
            graphics.text(font, rowTexts.get(row), textLeft(), textY,
                    rowsLocked.get(row) ? LOCKED_NAME_COLOR : entry.id().equals(selected) ? 0xFFFFFF55 : -1);
            if (group == LEARNING) extractLearningState((SrsState.Leveled) snapshot.state(entry.id()), now, y, textY, graphics);
        }
        if (entries.isEmpty()) graphics.centeredText(font, Component.translatable("oboecraft.word_list.empty"), width / 2, 65, -1);
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        graphics.centeredText(font, Component.translatable(
                group == LEARNING ? "oboecraft.word_list.page" : "oboecraft.word_list.hint", page + 1, pages),
                width / 2, height - 16, 0xFFA0A0A0);
    }

    /**
     * The level, then the lock timer's label and draining bar as in the item tooltip, or the ban time left. When an item
     * locks, its timer goes at once, and its name is replaced by {@code ???} at the next tick's layout.
     */
    private void extractLearningState(SrsState.Leveled state, Instant now, int y, int textY, GuiGraphicsExtractor graphics) {
        graphics.text(font, levelText(state.level()), levelLeft, textY, LEVEL_COLOR);
        if (DisplayRules.of(state, now, settings()) instanceof DisplayState.Timed timed) {
            var timer = new ClientLockTimerTooltip(timed.timer());
            timer.extractText(graphics, font, timerLeft(), y);
            timer.extractImage(font, timerLeft(), y, timer.getWidth(font), timer.getHeight(font), graphics);
        } else if (SrsRules.isBanned(state, now)) {
            Duration left = Duration.between(now, state.bannedUntil().orElseThrow());
            graphics.text(font, Component.translatable("oboecraft.word_list.banned", TimerText.format(left)),
                    timerLeft(), textY, BANNED_COLOR);
        }
    }

    /** The text, cut short with an ellipsis if it is wider than the space it has. */
    private String fitted(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }
}
