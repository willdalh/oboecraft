package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.review.ReviewRules;
import io.github.willdalh.oboecraft.core.review.ReviewStart;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.network.CloseReview;
import io.github.willdalh.oboecraft.fabric.network.ReviewAction;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.SubmitAnswer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
    private static final Component ATTEMPT_LEFT = Component.translatable("oboecraft.answer_menu.attempt_left")
            .withStyle(ChatFormatting.GOLD);
    private static final Component FAILED = Component.translatable("oboecraft.answer_menu.failed")
            .withStyle(ChatFormatting.RED);
    private static final int INPUT_WIDTH = 200;
    private static final int INPUT_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    /** The space around a button's label. */
    private static final int BUTTON_PADDING = 12;
    private static final int BUTTON_GAP = 4;
    /** How long the answer stays on screen after a fail before a key can close the menu, so a double press doesn't. */
    private static final long FAIL_SHOWN_MILLIS = 500;

    private final int reviewId;
    private final ItemId item;
    private final ItemStack icon;
    private final @Nullable AbstractContainerScreen<?> menuScreen;
    private int level;
    private int attemptsLeft;
    private List<FormattedCharSequence> hintLines = List.of();
    private @Nullable EditBox input;
    private boolean wrong;
    private ReviewAnswered.@Nullable Answer shownAnswer;
    private boolean readingShown;
    private int buttonsTop;
    private boolean failed;
    /** When the menu showed the fail, in {@link Util#getMillis()}. */
    private long failedAtMillis;
    /** Keys pressed and not released yet. Minecraft sends a held key's repeats as presses, which this tells apart. */
    private final Set<Integer> heldKeys = new HashSet<>();
    private boolean composing;
    private boolean endedByServer;

    /**
     * @param reviewId   the id of the review, which every message about it carries
     * @param item       the item under review
     * @param icon       the stack being reviewed, shown without its name
     * @param expected   the item's level and attempts left as the client knows them, until the server says
     * @param menuScreen the screen of the open menu that the answer menu opens over, or null if none is open
     */
    AnswerScreen(int reviewId, ItemId item, ItemStack icon, ReviewStart.Started expected,
            @Nullable AbstractContainerScreen<?> menuScreen) {
        super(TITLE);
        this.reviewId = reviewId;
        this.item = item;
        this.icon = icon;
        this.level = expected.level();
        this.attemptsLeft = expected.attemptsLeft();
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

    /** Whether the review failed, so the menu shows the answer until the player closes it. Client game tests read it. */
    public boolean showsFail() {
        return failed;
    }

    /** Whether the player has chosen to reveal the reading after seeing the Japanese name. */
    public boolean showsReading() {
        return shownAnswer != null && readingShown;
    }

    @Override
    protected void init() {
        String typed = input == null ? "" : input.getValue();
        input = new EditBox(font, (width - INPUT_WIDTH) / 2, height / 2, INPUT_WIDTH, INPUT_HEIGHT, INPUT_LABEL);
        input.setMaxLength(SubmitAnswer.MAX_LENGTH);
        input.setValue(typed);
        input.setEditable(!failed);
        input.setVisible(!failed);
        input.setResponder(value -> wrong = false);
        addRenderableWidget(input);
        buttonsTop = height;
        if (OboecraftClient.clientSettings().clickableButtons()) {
            addActionButtons();
        }
        updateHint();
    }

    /**
     * One button for each action the keys can do now, at the bottom, when the clickable buttons setting is on. The
     * buttons are as wide as the longest label, in one row or more if the screen is too narrow. After a fail, only
     * revealing the reading and closing are available.
     */
    private void addActionButtons() {
        List<Button> buttons = new ArrayList<>();
        if (failed) {
            buttons.add(revealButton());
            buttons.add(actionButton(ReviewKeys.CLOSE, () -> {
                if (failShownLongEnough()) {
                    onClose();
                }
            }));
        } else {
            buttons.add(actionButton(ReviewKeys.ANSWER, this::submit));
            buttons.add(actionButton(ReviewKeys.DONT_KNOW, () -> action(ReviewAction.Action.DONT_KNOW)));
            buttons.add(actionButton(ReviewKeys.KNOWN, () -> action(ReviewAction.Action.KNOWN)));
            buttons.add(actionButton(ReviewKeys.IGNORE, () -> action(ReviewAction.Action.IGNORE)));
            if (level == 0) {
                buttons.add(revealButton());
            }
            buttons.add(actionButton(ReviewKeys.CLOSE, this::onClose));
        }
        int buttonWidth = BUTTON_PADDING + buttons.stream()
                .mapToInt(button -> font.width(button.getMessage())).max().orElse(0);
        int perRow = Math.max(1, Math.min(buttons.size(), (width - 2 * BUTTON_GAP + BUTTON_GAP) / (buttonWidth + BUTTON_GAP)));
        int rows = (buttons.size() + perRow - 1) / perRow;
        // Rows of the same length where possible, like 3 and 3 rather than 5 and 1.
        perRow = (buttons.size() + rows - 1) / rows;
        buttonWidth = Math.min(buttonWidth, (width - 2 * BUTTON_GAP - BUTTON_GAP * (perRow - 1)) / perRow);
        for (int index = 0; index < buttons.size(); index++) {
            int row = index / perRow;
            int inRow = Math.min(perRow, buttons.size() - row * perRow);
            int rowWidth = inRow * buttonWidth + (inRow - 1) * BUTTON_GAP;
            int x = (width - rowWidth) / 2 + (index % perRow) * (buttonWidth + BUTTON_GAP);
            int y = height - (rows - row) * (BUTTON_HEIGHT + BUTTON_GAP) - 4;
            buttonsTop = Math.min(buttonsTop, y);
            buttons.get(index).setRectangle(buttonWidth, BUTTON_HEIGHT, x, y);
            addRenderableWidget(buttons.get(index));
        }
    }

    /** A button labeled with the action's name, which does what its key does, and gives the typing bar focus back. */
    private Button actionButton(ReviewKeys.Binding binding, Runnable press) {
        return actionButton(Component.translatable(binding.mapping().getName()), press);
    }

    private Button revealButton() {
        Button button = actionButton(ReviewKeys.revealLabel(shownAnswer != null), this::revealAnswer);
        button.active = !readingShown;
        return button;
    }

    private Button actionButton(Component label, Runnable press) {
        return Button.builder(label, button -> {
            if (!composing) {
                press.run();
            }
            if (input != null && input.isVisible()) {
                setFocused(input);
            }
        }).build();
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(input);
    }

    /**
     * While the keyboard's kanji conversion has text that isn't confirmed, every key belongs to the conversion.
     * Minecraft hands every key to the screen anyway, so the menu ignores them: an Enter that confirms the conversion
     * doesn't submit, an Esc that cancels it doesn't close the menu, and a Backspace doesn't delete confirmed text.
     *
     * <p>Don't know only acts while the typing bar is empty, so its default key, Delete, still deletes typed text and
     * fixing a typo never fails the review. After a fail, only a new press of Answer or Close, not a held key's
     * repeat, closes the menu, and not before the answer has been shown for {@link #FAIL_SHOWN_MILLIS}.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean repeat = !heldKeys.add(event.key());
        if (composing) {
            return true;
        }
        if (failed) {
            if (ReviewKeys.SHOW.matches(event)) {
                if (!repeat) revealAnswer();
            } else if (!repeat && failShownLongEnough() && (ReviewKeys.CLOSE.matches(event) || isAnswerKey(event))) {
                onClose();
            }
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
        if ((level == 0 || shownAnswer != null) && ReviewKeys.SHOW.matches(event)) {
            if (!repeat) revealAnswer();
            return true;
        }
        if (ReviewKeys.DONT_KNOW.matches(event) && input.getValue().isEmpty()) {
            action(ReviewAction.Action.DONT_KNOW);
            return true;
        }
        if (ReviewKeys.CLOSE.matches(event)) {
            onClose();
            return true;
        }
        if (isAnswerKey(event)) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Both Enter keys answer while the Answer binding is the default, as on other Minecraft screens. */
    private static boolean isAnswerKey(KeyEvent event) {
        return ReviewKeys.ANSWER.matches(event) || (ReviewKeys.ANSWER.mapping().isDefault() && event.isConfirmation());
    }

    private boolean failShownLongEnough() {
        return Util.getMillis() - failedAtMillis >= FAIL_SHOWN_MILLIS;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        heldKeys.remove(event.key());
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (composing) return true;
        if (failed) {
            if (ReviewKeys.SHOW.matches(event)) {
                revealAnswer();
                return true;
            }
            if (failShownLongEnough() && (ReviewKeys.CLOSE.matches(event) || ReviewKeys.ANSWER.matches(event))) {
                onClose();
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (ReviewKeys.KNOWN.matches(event)) { action(ReviewAction.Action.KNOWN); return true; }
        if (ReviewKeys.IGNORE.matches(event)) { action(ReviewAction.Action.IGNORE); return true; }
        if ((level == 0 || shownAnswer != null) && ReviewKeys.SHOW.matches(event)) {
            revealAnswer(); return true;
        }
        if (ReviewKeys.DONT_KNOW.matches(event) && input.getValue().isEmpty()) {
            action(ReviewAction.Action.DONT_KNOW); return true;
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

    /** The server reveals the name first; another deliberate press reveals its reading locally. */
    private void revealAnswer() {
        if (shownAnswer == null) {
            if (level == 0 && !failed) action(ReviewAction.Action.SHOW_ANSWER);
        } else if (!readingShown) {
            readingShown = true;
            refreshButtons();
            updateHint();
        }
    }

    void showAnswer(ReviewAnswered.Answer answer) {
        input.setValue("");
        wrong = false;
        shownAnswer = answer;
        refreshButtons();
        updateHint();
    }

    private void submit() {
        String typed = input.getValue();
        if (ReviewRules.isAttempt(typed)) {
            ClientPlayNetworking.send(new SubmitAnswer(reviewId, typed));
        }
    }

    /** The server started the review, at this level and with these attempts left. */
    void started(ReviewStart.Started started) {
        this.level = started.level();
        this.attemptsLeft = started.attemptsLeft();
        refreshButtons();
        updateHint();
    }

    /** The buttons reflect the level, a fail and which part of the answer has been revealed. */
    private void refreshButtons() {
        if (OboecraftClient.clientSettings().clickableButtons()) {
            rebuildWidgets();
        }
    }

    private void updateHint() {
        Component hint = failed ? ReviewKeys.closeHint(shownAnswer != null && !readingShown)
                : ReviewKeys.hint(level == 0, shownAnswer != null, readingShown);
        hintLines = font.split(hint, Math.max(100, width - 24));
        // Reserve both answer lines from the first reveal, so revealing the reading doesn't shift the typing bar.
        int messageLines = shownAnswer != null ? 2 : (wrong || level > 0 && attemptsLeft == 1 ? 1 : 0);
        if (failed) messageLines++;
        int contentHeight = INPUT_HEIGHT + 8 + hintLines.size() * 12
                + (messageLines == 0 ? 0 : 12 + messageLines * 12);
        if (input != null) input.setY(Math.max(0, Math.min(height / 2, buttonsTop - 8 - contentHeight)));
    }

    /** The server said the answer was wrong. The typing bar is cleared, and the review goes on with one attempt. */
    void answeredWrong() {
        input.setValue("");
        wrong = true;
        attemptsLeft = ReviewRules.ATTEMPTS - SrsState.Leveled.MAX_WRONG_ATTEMPTS;
        updateHint();
    }

    /**
     * The review failed, and the server has punished it. The review is over, so the menu only shows the answer until
     * the player closes it, and closing it tells the server nothing.
     */
    void failed(ReviewAnswered.Answer answer) {
        endedByServer = true;
        failed = true;
        failedAtMillis = Util.getMillis();
        wrong = false;
        shownAnswer = answer;
        readingShown = false;
        input.setValue("");
        input.setEditable(false);
        input.setVisible(false);
        refreshButtons();
        updateHint();
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
        int top = input == null ? height / 2 : input.getY();
        // Keep the image's bottom above the level label, so growing it never covers the answer field or hint.
        float itemSize = Math.min(16f * OboecraftClient.clientSettings().reviewItemScalePercent() / 100f,
                Math.max(0, top - 36));
        if (itemSize > 0) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX - itemSize / 2, top - 28 - itemSize);
            graphics.pose().scale(itemSize / 16f);
            graphics.fakeItem(icon, 0, 0);
            graphics.pose().popMatrix();
        }
        graphics.centeredText(font, Component.translatable("oboecraft.answer_menu.level", level), centerX, top - 20, -1);
        int hintY = top + INPUT_HEIGHT + 8;
        for (var line : hintLines) {
            graphics.text(font, line, centerX - font.width(line) / 2, hintY, 0xFFA0A0A0);
            hintY += 12;
        }
        int messageY = hintY + 12;
        if (failed) {
            graphics.centeredText(font, FAILED, centerX, messageY, -1);
            messageY += 12;
        }
        if (shownAnswer != null) {
            graphics.centeredText(font, Component.literal(shownAnswer.japaneseName()),
                    centerX, messageY, -1);
            if (readingShown) {
                graphics.centeredText(font, Component.literal(shownAnswer.reading()), centerX, messageY + 12, 0xFFA0A0A0);
            }
        }
        if (wrong) {
            graphics.centeredText(font, WRONG, centerX, messageY, -1);
        } else if (!failed && level > 0 && attemptsLeft == 1) {
            graphics.centeredText(font, ATTEMPT_LEFT, centerX, messageY, -1);
        }
    }
}
