package io.github.willdalh.oboecraft.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
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

    /**
     * Registers the bindings, which happens when this class is loaded. It has to run during client initialization,
     * since Fabric API only takes bindings before Minecraft reads the options.
     */
    static void register() {}

    static Component hint(boolean lesson) {
        MutableComponent result = Component.empty();
        boolean first = true;
        for (Binding binding : lesson ? new Binding[]{ANSWER, CLOSE, KNOWN, IGNORE, SHOW, DONT_KNOW}
                : new Binding[]{ANSWER, CLOSE, DONT_KNOW, KNOWN, IGNORE}) {
            if (!first) result.append("  ·  ");
            first = false;
            result.append(binding.label()).append(": ").append(Component.translatable(binding.mapping().getName()));
        }
        return result;
    }

    /** After a fail, the menu only shows the answer, and the answer and close keys close it. */
    static Component closeHint() {
        return Component.translatable("oboecraft.answer_menu.close_hint", CLOSE.label());
    }
}
