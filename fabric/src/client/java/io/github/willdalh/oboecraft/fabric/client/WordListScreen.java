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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Four separate lists, opening on the Learning tab, each in one scrolling list as wide as the tabs above it. Each row
 * has the item's icon, a button that plays its reading clip and its name, in columns, so the buttons and names line up.
 * The reading itself isn't shown, so the player recalls it before listening. On the Learning tab, a locked item's name
 * is {@code ???} and it has no button, and each row also shows either the lock timer or the ban time left, which update
 * live, and the level at the right end. Revert, on the other tabs, is confirmed through a context action and waits for
 * the server's update.
 */
public final class WordListScreen extends Screen implements OverMenuScreen {
    private static final int LEARNING = 0;
    private static final int KNOWN = 1;
    private static final int IGNORED = 2;
    private static final int TAB_COUNT = 4;
    private static final int TAB_GAP = 3;
    private static final int TABS_TOP = 28;
    /** The widest the tabs and the list get, so the timer and level stay close to the names on a wide screen. */
    private static final int MAX_LIST_WIDTH = 400;
    private static final int SIDE_MARGIN = 10;
    private static final int LIST_TOP = 52;
    /** The space below the list, for the sort or revert button and the hint. */
    private static final int LIST_BOTTOM_MARGIN = 52;
    private static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final Identifier READING_CLIP_SPRITE =
            Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, "icon/reading_clip");
    private static final int READING_CLIP_SPRITE_WIDTH = 10;
    private static final int READING_CLIP_SPRITE_HEIGHT = 14;
    private static final int READING_CLIP_BUTTON_SIZE = 20;
    private static final int READING_CLIP_BUTTON_GAP = 4;
    /** The space between the rows and the scrollbar at the right of the list. */
    private static final int SCROLLBAR_GAP = 4;
    /**
     * The width of the lock timer column, left of the level on a Learning row. It fits the labels of the default
     * intervals in both languages; a label for an interval of many days may run into the level.
     */
    private static final int TIMER_WIDTH = 110;
    private static final int COLUMN_GAP = 8;
    private static final int SELECTED_COLOR = 0xFFFFFF55;
    private static final int LOCKED_NAME_COLOR = 0xFFA0A0A0;
    private static final int LEVEL_COLOR = 0xFFAAAAAA;
    private static final int BANNED_COLOR = 0xFFFF5555;
    private static final int HINT_COLOR = 0xFFA0A0A0;

    /** The Learning tab's order, kept until the game closes. Only the client thread uses it. */
    private static LearningOrder learningOrder = LearningOrder.URGENCY;

    private final InventoryScreen inventory;
    private int group = LEARNING;
    private Progress snapshot = Progress.EMPTY;
    private Settings layoutSettings = Settings.DEFAULTS;
    /**
     * The first time after the layout when a row on the Learning tab locks, which moves it among the locked rows, or
     * null if none will.
     */
    private @Nullable Instant nextLock;
    private @Nullable WordList list;
    /** Whether the next layout starts the list at the top, after a change of tab or order, instead of where it was. */
    private boolean scrollToTop;
    private @Nullable ItemId selected;
    private @Nullable Button revert;
    private boolean empty;

    /** Opens over the inventory, which stays open. Close goes back to it, and the inventory key closes both. */
    public WordListScreen(InventoryScreen inventory) {
        super(Component.translatable("oboecraft.word_list.title"));
        this.inventory = inventory;
    }

    @Override public AbstractContainerScreen<?> menuScreen() { return inventory; }

    /**
     * Lays out the tabs, the list and the buttons below it. The list keeps where it was scrolled to unless the tab or
     * order changed.
     */
    @Override protected void init() {
        snapshot = OboecraftClient.progress().progress();
        layoutSettings = settings();
        int tabWidth = (Math.min(MAX_LIST_WIDTH, width - 2 * SIDE_MARGIN) - (TAB_COUNT - 1) * TAB_GAP) / TAB_COUNT;
        int listWidth = TAB_COUNT * tabWidth + (TAB_COUNT - 1) * TAB_GAP;
        int listLeft = (width - listWidth) / 2;
        for (int tab = 0; tab < TAB_COUNT; tab++) {
            int chosen = tab;
            var button = addRenderableWidget(Button.builder(groupLabel(tab), ignored -> {
                group = chosen; selected = null; scrollToTop = true; rebuildWidgets();
            }).bounds(listLeft + tab * (tabWidth + TAB_GAP), TABS_TOP, tabWidth, 20).build());
            button.active = tab != group;
        }

        double scroll = list == null || scrollToTop ? 0 : list.scrollAmount();
        scrollToTop = false;
        list = addRenderableWidget(new WordList(listLeft, listWidth, height - LIST_TOP - LIST_BOTTOM_MARGIN));
        fillList(list);
        list.setScrollAmount(scroll);

        if (group == LEARNING) {
            revert = null;
            addRenderableWidget(Button.builder(sortLabel(), ignored -> {
                learningOrder = learningOrder.next(); scrollToTop = true; rebuildWidgets();
            }).bounds(width / 2 - 65, height - 45, 130, 20).build());
        } else {
            revert = addRenderableWidget(Button.builder(Component.translatable("oboecraft.word_list.revert"), ignored -> {
                if (selected != null) ClientPlayNetworking.send(new RevertItem(selected));
                select(null);
            }).bounds(width / 2 - 65, height - 45, 130, 20).build());
            revert.visible = selected != null;
        }
    }

    /**
     * Fills the list with the tab's rows from the current progress and time, sorted again since the Learning tab's order
     * depends on which items are locked, and remembers when the next Learning row locks.
     */
    private void fillList(WordList list) {
        Instant now = OboecraftClient.progress().now();
        List<NameListEntry> entries = OboecraftMod.itemMapping().nameList().entries().stream()
                .filter(entry -> inGroup(snapshot.state(entry.id())))
                .sorted(entryOrder(now)).toList();
        empty = entries.isEmpty();
        nextLock = group != LEARNING ? null : entries.stream()
                .map(entry -> ((SrsState.Leveled) snapshot.state(entry.id())).locksAt())
                .flatMap(Optional::stream)
                .filter(now::isBefore)
                .min(Comparator.naturalOrder()).orElse(null);
        WordList.Columns columns = list.columns();
        list.replaceEntries(entries.stream().map(entry -> new Row(entry, group == LEARNING
                && SrsRules.isLocked(snapshot.state(entry.id()), now, layoutSettings), columns)).toList());
    }

    /** Selects the row whose Revert button shows, or none. Only the name's color and the button change. */
    private void select(@Nullable ItemId item) {
        selected = item;
        if (revert != null) revert.visible = item != null;
    }

    private boolean inGroup(SrsState state) {
        return switch (group) {
            case LEARNING -> SrsRules.isLearning(state, layoutSettings);
            case KNOWN -> state instanceof SrsState.Known;
            case IGNORED -> state instanceof SrsState.Ignored;
            default -> state instanceof SrsState.Leveled && SrsRules.canRevert(state, layoutSettings);
        };
    }

    /** The Learning tab in its chosen order at {@code now}, and the other tabs by Japanese name. */
    private Comparator<NameListEntry> entryOrder(Instant now) {
        Comparator<NameListEntry> byName = Comparator.comparing(entry -> entry.japaneseName().text());
        if (group != LEARNING) return byName;
        Comparator<NameListEntry> byState = Comparator.comparing(
                entry -> (SrsState.Leveled) snapshot.state(entry.id()), learningOrder.comparator(now, layoutSettings));
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

    /**
     * Without progress there is no word list, so it goes back to the inventory, which then has no button. The screen is
     * laid out again when progress or the settings change. When a Learning row locks, only the list's rows are made
     * again, since the row then shows {@code ???}, loses its button and moves among the locked rows, and the list
     * itself, with its scroll position and a drag of its scrollbar, stays.
     */
    @Override public void tick() {
        if (minecraft.player == null || !OboecraftClient.progress().hasProgress(minecraft.player)) { onClose(); return; }
        if (snapshot != OboecraftClient.progress().progress() || layoutSettings != settings()) {
            selected = null; rebuildWidgets();
        } else if (list != null && nextLock != null && !OboecraftClient.progress().now().isBefore(nextLock)) {
            fillList(list);
        }
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
        if (empty) graphics.centeredText(font, Component.translatable("oboecraft.word_list.empty"), width / 2, 65, -1);
        if (group != LEARNING) {
            graphics.centeredText(font, Component.translatable("oboecraft.word_list.hint"), width / 2, height - 16, HINT_COLOR);
        }
    }

    /** The text, cut short with an ellipsis if it is wider than the space it has. */
    private String fitted(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    /**
     * The scrolling list of the tab's rows, drawn over the world without vanilla's list background and separators, with
     * the scrollbar at its right edge.
     */
    private final class WordList extends ContainerObjectSelectionList<Row> {
        WordList(int left, int listWidth, int listHeight) {
            super(WordListScreen.this.minecraft, listWidth, listHeight, LIST_TOP, ROW_HEIGHT);
            setX(left);
        }

        /**
         * Replaces the rows, keeping the scroll position. A focused row's reading clip button goes with it, so a locked
         * row's clip can't be played from the keyboard.
         */
        @Override public void replaceEntries(Collection<Row> rows) {
            setFocused(null);
            super.replaceEntries(rows);
            refreshScrollAmount();
        }

        @Override public int getRowLeft() { return getX(); }

        @Override public int getRowWidth() { return getWidth() - AbstractScrollArea.SCROLLBAR_WIDTH - SCROLLBAR_GAP; }

        @Override protected int scrollBarX() { return getRight() - scrollbarWidth(); }

        @Override protected void extractListBackground(GuiGraphicsExtractor graphics) { }

        @Override protected void extractListSeparators(GuiGraphicsExtractor graphics) { }

        /**
         * Where each column starts, as offsets from the left of a row. The button and the name follow the icon, and on
         * the Learning tab the level is at the right end, wide enough for the top level, with the timer left of it.
         */
        Columns columns() {
            int button = ICON_SIZE + ICON_GAP;
            int name = button + READING_CLIP_BUTTON_SIZE + READING_CLIP_BUTTON_GAP;
            if (group != LEARNING) return new Columns(button, name, getRowWidth(), 0, 0);
            int level = getRowWidth() - font.width(levelText(layoutSettings.topLevel()));
            int timer = level - COLUMN_GAP - TIMER_WIDTH;
            return new Columns(button, name, timer - COLUMN_GAP, timer, level);
        }

        record Columns(int button, int name, int nameEnd, int timer, int level) { }
    }

    /**
     * A row of the list. Its name and level are made once, when the list is laid out, and its timer or ban each frame.
     * A locked row has {@code ???} for its name and no reading clip button.
     */
    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final ItemId item;
        private final boolean locked;
        private final WordList.Columns columns;
        private final String name;
        private final @Nullable Component level;
        private final @Nullable SpriteIconButton readingClip;

        Row(NameListEntry entry, boolean locked, WordList.Columns columns) {
            item = entry.id();
            this.locked = locked;
            this.columns = columns;
            name = fitted(locked ? "???" : entry.japaneseName().text(), columns.nameEnd() - columns.name());
            level = group == LEARNING ? levelText(((SrsState.Leveled) snapshot.state(item)).level()) : null;
            readingClip = locked ? null : SpriteIconButton.builder(
                            Component.translatable("oboecraft.word_list.play_reading"),
                            ignored -> OboecraftClient.readingClips().play(item), true)
                    .size(READING_CLIP_BUTTON_SIZE, READING_CLIP_BUTTON_SIZE)
                    .sprite(READING_CLIP_SPRITE, READING_CLIP_SPRITE_WIDTH, READING_CLIP_SPRITE_HEIGHT)
                    .withTootip().build();
        }

        @Override public List<? extends GuiEventListener> children() {
            return readingClip == null ? List.of() : List.of(readingClip);
        }

        @Override public List<? extends NarratableEntry> narratables() {
            return readingClip == null ? List.of() : List.of(readingClip);
        }

        /** A right-click on another tab than Learning selects the row, so its Revert button shows. */
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 1 && group != LEARNING) {
                select(item);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override public void extractContent(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int left = getX();
            int iconTop = getY() + (ROW_HEIGHT - ICON_SIZE) / 2;
            int textY = iconTop + (ICON_SIZE - font.lineHeight) / 2 + 1;
            if (minecraft.level != null) {
                OboecraftClient.itemIcons().icon(minecraft.level.registryAccess(), item)
                        .ifPresent(icon -> graphics.item(icon, left, iconTop));
            }
            if (readingClip != null) {
                readingClip.setPosition(left + columns.button(), getY() + (ROW_HEIGHT - READING_CLIP_BUTTON_SIZE) / 2);
                readingClip.extractRenderState(graphics, mouseX, mouseY, delta);
            }
            graphics.text(font, name, left + columns.name(), textY,
                    locked ? LOCKED_NAME_COLOR : item.equals(selected) ? SELECTED_COLOR : -1);
            if (level != null) {
                extractLockState((SrsState.Leveled) snapshot.state(item), left + columns.timer(), iconTop, textY, graphics);
                graphics.text(font, level, left + columns.level(), textY, LEVEL_COLOR);
            }
        }

        /**
         * The lock timer's label and draining bar as in the item tooltip, or the ban time left. When an item locks, its
         * timer goes at once, and the row is laid out again at the next tick.
         */
        private void extractLockState(SrsState.Leveled state, int x, int y, int textY, GuiGraphicsExtractor graphics) {
            Instant now = OboecraftClient.progress().now();
            if (DisplayRules.of(state, now, settings()) instanceof DisplayState.Timed timed) {
                var timer = new ClientLockTimerTooltip(timed.timer());
                timer.extractText(graphics, font, x, y);
                timer.extractImage(font, x, y, timer.getWidth(font), timer.getHeight(font), graphics);
            } else if (SrsRules.isBanned(state, now)) {
                Duration left = Duration.between(now, state.bannedUntil().orElseThrow());
                graphics.text(font, Component.translatable("oboecraft.word_list.banned", TimerText.format(left)),
                        x, textY, BANNED_COLOR);
            }
        }
    }
}
