package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.review.ReviewRules;
import io.github.willdalh.oboecraft.fabric.network.CloseReview;
import io.github.willdalh.oboecraft.fabric.network.ReviewAction;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.SubmitAnswer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The answer menu of one review: a typing bar for the item's Japanese name. It uses Minecraft's normal text box, so
 * the keyboard's kanji conversion works. It only sends what the player does and shows what the server sends, and
 * never decides a pass or fail. The game doesn't pause while it is open.
 *
 * <p>Over an open menu, like a chest, the answer menu replaces the menu's screen without closing the menu, and goes
 * back to that screen when it closes. See {@code GuiMixin}.
 */
public final class AnswerScreen extends Screen {
    private static final Component TITLE = Component.translatable("oboecraft.answer_menu.title");
    private static final Component INPUT_LABEL = Component.translatable("oboecraft.answer_menu.input");
    private static final Component WRONG = Component.translatable("oboecraft.answer_menu.wrong")
            .withStyle(ChatFormatting.RED);
    private static final int INPUT_WIDTH = 200;
    private static final int INPUT_HEIGHT = 20;

    private final int reviewId;
    private final ItemId item;
    private final ItemStack icon;
    private final @Nullable AbstractContainerScreen<?> menuScreen;
    private int level;
    private @Nullable EditBox input;
    private boolean wrong;
    private ReviewAnswered.@Nullable Answer shownAnswer;
    private boolean composing;
    private boolean endedByServer;

    /**
     * @param reviewId   the id of the review, which every message about it carries
     * @param item       the item under review
     * @param icon       the stack being reviewed, shown without its name
     * @param level      the item's level as the client knows it, until the server says
     * @param menuScreen the screen of the open menu that the answer menu opens over, or null if none is open
     */
    AnswerScreen(
            int reviewId, ItemId item, ItemStack icon, int level, @Nullable AbstractContainerScreen<?> menuScreen) {
        super(TITLE);
        this.reviewId = reviewId;
        this.item = item;
        this.icon = icon;
        this.level = level;
        this.menuScreen = menuScreen;
    }

    /** The screen of the open menu that the answer menu opened over, or null if it opened over the game. */
    public @Nullable AbstractContainerScreen<?> menuScreen() {
        return menuScreen;
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
        if (ReviewKeys.KNOWN.matches(event)) {
            action(ReviewAction.Action.KNOWN);
            return true;
        }
        if (ReviewKeys.IGNORE.matches(event)) {
            action(ReviewAction.Action.IGNORE);
            return true;
        }
        if (level == 0 && (ReviewKeys.SHOW.matches(event) || ReviewKeys.DONT_KNOW.matches(event))) {
            action(ReviewAction.Action.SHOW_ANSWER);
            return true;
        }
        if (ReviewKeys.CLOSE.matches(event)) {
            onClose();
            return true;
        }
        if (ReviewKeys.ANSWER.matches(event)) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (composing) return true;
        if (ReviewKeys.KNOWN.matches(event)) { action(ReviewAction.Action.KNOWN); return true; }
        if (ReviewKeys.IGNORE.matches(event)) { action(ReviewAction.Action.IGNORE); return true; }
        if (level == 0 && (ReviewKeys.SHOW.matches(event) || ReviewKeys.DONT_KNOW.matches(event))) {
            action(ReviewAction.Action.SHOW_ANSWER); return true;
        }
        if (ReviewKeys.ANSWER.matches(event)) { submit(); return true; }
        if (ReviewKeys.CLOSE.matches(event)) { onClose(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    /** Tracks whether the keyboard's kanji conversion has text that isn't confirmed yet. */
    @Override
    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        composing = event != null;
        return super.preeditUpdated(event);
    }

    private void action(ReviewAction.Action action) {
        ClientPlayNetworking.send(new ReviewAction(reviewId, action));
    }

    public boolean showsLessonAnswer() {
        return shownAnswer != null;
    }

    void showAnswer(ReviewAnswered.Answer answer) {
        input.setValue("");
        wrong = false;
        shownAnswer = answer;
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

    /**
     * Goes back to the open menu's screen, if the answer menu opened over one and that menu is still open. If the
     * menu has closed meanwhile, its screen is gone too.
     */
    @Override
    public void onClose() {
        boolean menuStillOpen = menuScreen != null && minecraft.player != null
                && menuScreen.getMenu() == minecraft.player.containerMenu;
        minecraft.gui.setScreen(menuStillOpen ? menuScreen : null);
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
        int hintY = top + INPUT_HEIGHT + 8;
        for (var line : font.split(ReviewKeys.hint(level == 0), Math.max(100, width - 24))) {
            graphics.text(font, line, centerX - font.width(line) / 2, hintY, 0xFFA0A0A0);
            hintY += 12;
        }
        if (shownAnswer != null) {
            graphics.centeredText(font, Component.literal(shownAnswer.japaneseName() + " (" + shownAnswer.reading() + ")"),
                    centerX, hintY + 12, -1);
        }
        if (wrong) {
            graphics.centeredText(font, WRONG, centerX, hintY + 12, -1);
        }
    }
}
