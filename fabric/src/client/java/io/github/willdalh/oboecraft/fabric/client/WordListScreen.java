package io.github.willdalh.oboecraft.fabric.client;

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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Three separate lists. Revert is confirmed through a context action and waits for the server's update. */
public final class WordListScreen extends Screen {
    private int group;
    private int page;
    private int pageSize;
    private Progress snapshot = Progress.EMPTY;
    private List<NameListEntry> entries = List.of();
    private @Nullable ItemId selected;

    public WordListScreen() { super(Component.translatable("oboecraft.word_list.title")); }

    @Override protected void init() {
        pageSize = Math.max(1, (height - 120) / 24);
        snapshot = OboecraftClient.progress().progress();
        entries = OboecraftMod.itemMapping().nameList().entries().stream()
                .filter(entry -> inGroup(snapshot.state(entry.id())))
                .sorted(Comparator.comparing(entry -> entry.japaneseName().text())).toList();
        page = Math.min(page, Math.max(0, (entries.size() - 1) / pageSize));
        for (int tab = 0; tab < 3; tab++) {
            int chosen = tab;
            var button = addRenderableWidget(Button.builder(groupLabel(tab), ignored -> {
                group = chosen; page = 0; selected = null; rebuildWidgets();
            }).bounds(width / 2 - 153 + tab * 103, 28, 100, 20).build());
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
        if (selected != null) {
            ItemId item = selected;
            addRenderableWidget(Button.builder(Component.translatable("oboecraft.word_list.revert"), ignored -> {
                ClientPlayNetworking.send(new RevertItem(item));
                selected = null; rebuildWidgets();
            }).bounds(width / 2 - 65, height - 45, 130, 20).build());
        }
    }

    private boolean inGroup(SrsState state) {
        return SrsRules.canRevert(state, OboecraftMod.settings()) && switch (group) {
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
        int row = (int) (event.y() - 58) / 24;
        int index = page * pageSize + row;
        if (event.button() == 1 && event.y() >= 58 && row >= 0 && row < pageSize
                && index < entries.size() && Math.abs(event.x() - width / 2.0) <= Math.min(240, width / 2 - 10)) {
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
        for (int row = 0; row < pageSize && page * pageSize + row < entries.size(); row++) {
            var entry = entries.get(page * pageSize + row);
            var name = entry.japaneseName();
            graphics.centeredText(font, Component.literal(name.text() + " (" + name.reading() + ")"),
                    width / 2, 60 + row * 24, entry.id().equals(selected) ? 0xFFFFFF55 : -1);
        }
        if (entries.isEmpty()) graphics.centeredText(font, Component.translatable("oboecraft.word_list.empty"), width / 2, 65, -1);
        graphics.centeredText(font, Component.translatable("oboecraft.word_list.hint", page + 1,
                Math.max(1, (entries.size() + pageSize - 1) / pageSize)), width / 2, height - 16, 0xFFA0A0A0);
    }
}
