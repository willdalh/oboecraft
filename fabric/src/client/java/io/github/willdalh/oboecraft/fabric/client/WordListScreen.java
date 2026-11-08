package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsRules;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.network.RevertItem;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Three separate lists. Each row has the item's icon, name and a button that plays its reading clip, left-justified
 * from the left edge of the tabs. The reading itself isn't shown, so the player recalls it before listening. Revert is
 * confirmed through a context action and waits for the server's update.
 */
public final class WordListScreen extends Screen {
    private static final int TAB_WIDTH = 100;
    private static final int TAB_GAP = 3;
    private static final int TAB_COUNT = 3;
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
    private static final int READING_CLIP_SPRITE_WIDTH = 8;
    private static final int READING_CLIP_SPRITE_HEIGHT = 12;
    private static final int READING_CLIP_BUTTON_SIZE = 20;
    private static final int READING_CLIP_BUTTON_GAP = 4;

    private int group;
    private int cachedGroup = -1;
    private int page;
    private int pageSize;
    private Progress snapshot = Progress.EMPTY;
    private List<NameListEntry> entries = List.of();
    private @Nullable ItemId selected;

    public WordListScreen() { super(Component.translatable("oboecraft.word_list.title")); }

    @Override protected void init() {
        pageSize = Math.max(1, (height - LIST_TOP - LIST_BOTTOM_MARGIN) / ROW_HEIGHT);
        Progress current = OboecraftClient.progress().progress();
        if (snapshot != current || cachedGroup != group) {
            snapshot = current;
            cachedGroup = group;
            entries = OboecraftMod.itemMapping().nameList().entries().stream()
                    .filter(entry -> inGroup(snapshot.state(entry.id())))
                    .sorted(Comparator.comparing(entry -> entry.japaneseName().text())).toList();
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
        for (int row = 0; row < rowsOnPage(); row++) {
            var entry = entries.get(page * pageSize + row);
            addRenderableWidget(SpriteIconButton.builder(Component.translatable("oboecraft.word_list.play_reading"),
                            ignored -> OboecraftClient.readingClips().play(entry.id()), true)
                    .size(READING_CLIP_BUTTON_SIZE, READING_CLIP_BUTTON_SIZE)
                    .sprite(READING_CLIP_SPRITE, READING_CLIP_SPRITE_WIDTH, READING_CLIP_SPRITE_HEIGHT)
                    .withTootip().build())
                    .setPosition(textLeft() + font.width(rowText(entry)) + READING_CLIP_BUTTON_GAP,
                            rowTop(row) + (ICON_SIZE - READING_CLIP_BUTTON_SIZE) / 2);
        }
        if (selected != null) {
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

    private int rowsOnPage() { return Math.min(pageSize, entries.size() - page * pageSize); }

    /** The row's name, cut short so that the reading clip button after it still fits. */
    private String rowText(NameListEntry entry) {
        return fitted(entry.japaneseName().text(),
                listRight() - textLeft() - READING_CLIP_BUTTON_GAP - READING_CLIP_BUTTON_SIZE);
    }

    private boolean inGroup(SrsState state) {
        return SrsRules.canRevert(state, OboecraftClient.progress().settings()) && switch (group) {
            case 0 -> state instanceof SrsState.Known;
            case 1 -> state instanceof SrsState.Ignored;
            default -> state instanceof SrsState.Leveled;
        };
    }

    private static Component groupLabel(int group) {
        return Component.translatable("oboecraft.word_list." + switch (group) {
            case 0 -> "known"; case 1 -> "ignored"; default -> "mastered";
        });
    }

    @Override public void tick() {
        if (minecraft.player == null || !OboecraftClient.progress().hasProgress(minecraft.player)) { onClose(); return; }
        if (snapshot != OboecraftClient.progress().progress()) { selected = null; rebuildWidgets(); }
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int row = (int) Math.floor((event.y() - LIST_TOP) / ROW_HEIGHT);
        int index = page * pageSize + row;
        if (event.button() == 1 && row >= 0 && row < pageSize && index < entries.size()
                && event.x() >= listLeft() && event.x() < listRight()) {
            selected = entries.get(index).id();
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 10, -1);
        for (int row = 0; row < rowsOnPage(); row++) {
            var entry = entries.get(page * pageSize + row);
            int y = rowTop(row);
            if (minecraft.level != null) {
                OboecraftClient.itemIcons().icon(minecraft.level.registryAccess(), entry.id())
                        .ifPresent(icon -> graphics.item(icon, listLeft(), y));
            }
            graphics.text(font, rowText(entry), textLeft(), y + (ICON_SIZE - font.lineHeight) / 2 + 1,
                    entry.id().equals(selected) ? 0xFFFFFF55 : -1);
        }
        if (entries.isEmpty()) graphics.centeredText(font, Component.translatable("oboecraft.word_list.empty"), width / 2, 65, -1);
        graphics.centeredText(font, Component.translatable("oboecraft.word_list.hint", page + 1,
                Math.max(1, (entries.size() + pageSize - 1) / pageSize)), width / 2, height - 16, 0xFFA0A0A0);
    }

    /** The text, cut short with an ellipsis if it is wider than the space it has. */
    private String fitted(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }
}
