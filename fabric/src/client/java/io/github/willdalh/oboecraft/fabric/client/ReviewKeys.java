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
    public static final Binding ANSWER = bind("answer", InputConstants.KEY_RETURN, false);
    public static final Binding CLOSE = bind("close", InputConstants.KEY_ESCAPE, false);
    public static final Binding KNOWN = bind("known", InputConstants.KEY_K, true);
    public static final Binding IGNORE = bind("ignore", InputConstants.KEY_I, true);
    public static final Binding SHOW = bind("show", InputConstants.KEY_S, true);
    public static final Binding DONT_KNOW = bind("dont_know", InputConstants.KEY_DELETE, false);
    public static final Binding WORD_LIST = bind("word_list", InputConstants.KEY_J, false);

    private ReviewKeys() {}

    /** Vanilla Controls stores one key. Default letter shortcuts require Ctrl; rebound keys stand alone. */
    public record Binding(KeyMapping mapping, boolean defaultControl) {
        private boolean controlRequired() { return defaultControl && mapping.isDefault(); }
        public boolean matches(KeyEvent event) {
            return mapping.matches(event) && (!controlRequired() || (event.modifiers() & InputConstants.MOD_CONTROL) != 0);
        }
        public boolean matches(MouseButtonEvent event) { return mapping.matchesMouse(event); }
        Component label() {
            return Component.literal(controlRequired() ? "Ctrl+" : "").append(mapping.getTranslatedKeyMessage());
        }
    }

    private static Binding bind(String name, int key, boolean control) {
        return new Binding(KeyMappingHelper.registerKeyMapping(new KeyMapping("key.oboecraft." + name, key, CATEGORY)), control);
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

    static Component hint(boolean lesson) {
        MutableComponent result = Component.empty();
        for (Binding binding : lesson ? new Binding[]{ANSWER, CLOSE, KNOWN, IGNORE, SHOW, DONT_KNOW}
                : new Binding[]{ANSWER, CLOSE, KNOWN, IGNORE}) {
            if (!result.getString().isEmpty()) result.append("  ·  ");
            result.append(binding.label()).append(": ").append(Component.translatable(binding.mapping().getName()));
        }
        return result;
    }
}
