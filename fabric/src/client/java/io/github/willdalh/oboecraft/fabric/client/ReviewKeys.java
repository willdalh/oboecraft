package io.github.willdalh.oboecraft.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/** Screen-local actions use the Controls bindings, without consuming keys typed into the answer. */
public final class ReviewKeys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("oboecraft", "reviews"));
    public static final Binding ANSWER = bind("answer", InputConstants.KEY_RETURN);
    public static final Binding CLOSE = bind("close", InputConstants.KEY_ESCAPE);
    public static final Binding KNOWN = bind("known", InputConstants.KEY_F7);
    public static final Binding IGNORE = bind("ignore", InputConstants.KEY_F8);
    public static final Binding SHOW = bind("show", InputConstants.KEY_F9);
    public static final Binding DONT_KNOW = bind("dont_know", InputConstants.KEY_DELETE);
    public static final Binding WORD_LIST = bind("word_list", InputConstants.KEY_J);

    private ReviewKeys() {}

    /** Uses the same single-key binding for Controls, matching and the on-screen hint. */
    public record Binding(KeyMapping mapping) {
        public boolean matches(KeyEvent event) {
            return mapping.matches(event);
        }
        public boolean matches(MouseButtonEvent event) { return mapping.matchesMouse(event); }
        Component label() {
            return mapping.getTranslatedKeyMessage();
        }
    }

    private static Binding bind(String name, int key) {
        return new Binding(KeyMappingHelper.registerKeyMapping(new KeyMapping("key.oboecraft." + name, key, CATEGORY)));
    }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (WORD_LIST.mapping().consumeClick()) {
                if (client.player != null && client.gui.screen() == null && OboecraftClient.progress().hasProgress(client.player)) {
                    client.gui.setScreen(new WordListScreen());
                }
            }
        });
    }

    static Component hint(boolean lesson, boolean answerShown, boolean readingShown) {
        MutableComponent result = Component.empty();
        boolean first = true;
        for (Binding binding : lesson ? new Binding[]{ANSWER, CLOSE, KNOWN, IGNORE, SHOW, DONT_KNOW}
                : new Binding[]{ANSWER, CLOSE, DONT_KNOW, KNOWN, IGNORE}) {
            if (binding == SHOW && readingShown) continue;
            if (!first) result.append("  ·  ");
            first = false;
            result.append(binding.label()).append(": ").append(binding == SHOW ? revealLabel(answerShown)
                    : Component.translatable(binding.mapping().getName()));
        }
        return result;
    }

    /** Show answer's label: its name from the Controls screen, or Show reading once the Japanese name is shown. */
    static Component revealLabel(boolean answerShown) {
        return Component.translatable(answerShown ? "oboecraft.answer_menu.show_reading" : SHOW.mapping().getName());
    }

    /** After a fail, Show answer reveals the reading; Answer and Close still close the menu. */
    static Component closeHint(boolean canRevealReading) {
        MutableComponent result = Component.translatable("oboecraft.answer_menu.close_hint", CLOSE.label());
        if (canRevealReading) result.append("  ·  ").append(SHOW.label()).append(": ").append(revealLabel(true));
        return result;
    }
}
