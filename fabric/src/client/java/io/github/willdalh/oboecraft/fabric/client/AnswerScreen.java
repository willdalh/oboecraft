package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.review.ReviewRules;
import io.github.willdalh.oboecraft.fabric.network.CloseReview;
import io.github.willdalh.oboecraft.fabric.network.SubmitAnswer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The answer menu of one review: a typing bar for the item's Japanese name. It uses Minecraft's normal text box, so
 * the keyboard's kanji conversion works. It only sends what the player does and shows what the server sends, and
 * never decides a pass or fail. The game doesn't pause while it is open.
 */
public final class AnswerScreen extends Screen {
    private static final Component TITLE = Component.translatable("oboecraft.answer_menu.title");
    private static final Component INPUT_LABEL = Component.translatable("oboecraft.answer_menu.input");
    private static final Component HINT = Component.translatable("oboecraft.answer_menu.hint");
    private static final Component WRONG = Component.translatable("oboecraft.answer_menu.wrong")
            .withStyle(ChatFormatting.RED);
    private static final int INPUT_WIDTH = 200;
    private static final int INPUT_HEIGHT = 20;

    private final int reviewId;
    private final ItemId item;
    private final ItemStack icon;
    private int level;
    private @Nullable EditBox input;
    private boolean wrong;
    private boolean composing;
    private boolean endedByServer;

    /**
     * @param reviewId the id of the review, which every message about it carries
     * @param item     the item under review
     * @param icon     the stack being reviewed, shown without its name
     * @param level    the item's level as the client knows it, until the server says
     */
    AnswerScreen(int reviewId, ItemId item, ItemStack icon, int level) {
        super(TITLE);
        this.reviewId = reviewId;
        this.item = item;
        this.icon = icon;
        this.level = level;
    }

    int reviewId() {
        return reviewId;
    }

    ItemId item() {
        return item;
    }

    /** Whether the menu shows that the last answer was wrong. Client game tests read it. */
    public boolean showsWrongAnswer() {
        return wrong;
    }

    @Override
    protected void init() {
        String typed = input == null ? "" : input.getValue();
        input = new EditBox(font, (width - INPUT_WIDTH) / 2, height / 2, INPUT_WIDTH, INPUT_HEIGHT, INPUT_LABEL);
        input.setMaxLength(SubmitAnswer.MAX_LENGTH);
        input.setValue(typed);
        input.setResponder(value -> wrong = false);
        addRenderableWidget(input);
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(input);
    }

    /**
     * While the keyboard's kanji conversion has text that isn't confirmed, every key belongs to the conversion.
     * Minecraft hands every key to the screen anyway, so the menu ignores them: an Enter that confirms the conversion
     * doesn't submit, an Esc that cancels it doesn't close the menu, and a Backspace doesn't delete confirmed text.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (composing) {
            return true;
        }
        if (event.isConfirmation()) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Tracks whether the keyboard's kanji conversion has text that isn't confirmed yet. */
    @Override
    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        composing = event != null;
        return super.preeditUpdated(event);
    }

    private void submit() {
        String typed = input.getValue();
        if (ReviewRules.isAttempt(typed)) {
            ClientPlayNetworking.send(new SubmitAnswer(reviewId, typed));
        }
    }

    /** The server started the review, at this level. */
    void started(int level) {
        this.level = level;
    }

    /** The server said the answer was wrong. The typing bar is cleared, and the review goes on. */
    void answeredWrong() {
        input.setValue("");
        wrong = true;
    }

    /** The server ended the review, so the menu closes without telling it. */
    void end() {
        endedByServer = true;
        onClose();
    }

    /** Closing the menu in any other way than the server ending the review cancels the review. */
    @Override
    public void removed() {
        if (!endedByServer && ClientPlayNetworking.canSend(CloseReview.TYPE)) {
            ClientPlayNetworking.send(new CloseReview(reviewId));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int centerX = width / 2;
        int top = height / 2;
        graphics.fakeItem(icon, centerX - 8, top - 44);
        graphics.centeredText(font, Component.translatable("oboecraft.answer_menu.level", level), centerX, top - 20, -1);
        graphics.centeredText(font, HINT, centerX, top + INPUT_HEIGHT + 8, 0xFFA0A0A0);
        if (wrong) {
            graphics.centeredText(font, WRONG, centerX, top + INPUT_HEIGHT + 22, -1);
        }
    }
}
