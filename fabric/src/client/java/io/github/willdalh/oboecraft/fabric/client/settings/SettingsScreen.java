package io.github.willdalh.oboecraft.fabric.client.settings;

import io.github.willdalh.oboecraft.core.settings.DurationText;
import io.github.willdalh.oboecraft.core.settings.LevelsText;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.settings.ServerSettingsFile;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The mod's settings screen, which Mod Menu opens. The client settings are the player's own. The server settings are
 * the ones in this computer's settings file, which singleplayer worlds and worlds opened to LAN use, and can only be
 * changed while the player isn't on another server. On another server, the screen shows that server's settings and
 * can't change them. A settings file that can't be read or isn't valid can't be changed here either, so the screen
 * never saves the defaults over the owner's file.
 */
public final class SettingsScreen extends Screen {
    private static final int FIELD_WIDTH = 260;
    private static final int GAP = 4;
    private static final int CLIENT_TOP = 20;
    /** The server settings' header, which an error after a failed save replaces, so it is next to the fields. */
    private static final int SERVER_TOP = 64;
    /** The space left of and right of an error, which can be wider than the fields. */
    private static final int ERROR_MARGIN = 8;
    /** A label and its field. */
    private static final int ROW_HEIGHT = 30;
    private static final int LABEL_COLOR = 0xFFA0A0A0;

    private final @Nullable Screen parent;
    private final boolean serverEditable;
    /** The server settings' header, which says whose settings they are or why they can't be changed. */
    private final Component serverHeader;
    private ClientSettings clientSettings;
    private Settings serverSettings;
    private @Nullable CycleButton<Boolean> clickableButtons;
    private @Nullable ReviewItemScaleSlider reviewItemScale;
    private @Nullable EditBox intervals;
    private @Nullable EditBox demotions;
    private @Nullable EditBox banLength;
    private @Nullable EditBox percentageFactor;
    private @Nullable EditBox tsukumogamiThreshold;
    private @Nullable EditBox tsukumogamiReviewFreeze;
    private @Nullable CycleButton<Boolean> dropCountsAsInteraction;
    private Component error = Component.empty();

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("oboecraft.settings.title"));
        this.parent = parent;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        boolean onThisComputer = minecraft.getConnection() == null || minecraft.hasSingleplayerServer();
        clientSettings = OboecraftClient.clientSettings();
        Optional<Settings> fileSettings = onThisComputer
                ? ServerSettingsFile.readIfValid(ServerSettingsFile.path())
                : Optional.empty();
        serverEditable = fileSettings.isPresent();
        serverSettings = onThisComputer
                ? fileSettings.orElse(Settings.DEFAULTS)
                : OboecraftClient.progress().settings();
        serverHeader = Component.translatable(!onThisComputer ? "oboecraft.settings.server_remote"
                : serverEditable ? "oboecraft.settings.server" : "oboecraft.settings.server_invalid");
    }

    @Override
    protected void init() {
        int x = (width - FIELD_WIDTH) / 2;
        int halfWidth = (FIELD_WIDTH - GAP) / 2;
        clickableButtons = addRenderableWidget(CycleButton.onOffBuilder(clientSettings.clickableButtons())
                .create(x, CLIENT_TOP, FIELD_WIDTH, 20, Component.translatable("oboecraft.settings.clickable_buttons")));
        int scale = reviewItemScale == null ? clientSettings.reviewItemScalePercent() : reviewItemScale.scalePercent();
        reviewItemScale = addRenderableWidget(new ReviewItemScaleSlider(x, CLIENT_TOP + 22, FIELD_WIDTH, scale));
        int y = SERVER_TOP + 10;
        intervals = field(x, y, FIELD_WIDTH, LevelsText.intervals(serverSettings.levels()),
                "oboecraft.settings.intervals");
        y += ROW_HEIGHT;
        demotions = field(x, y, FIELD_WIDTH, LevelsText.demotions(serverSettings.levels()),
                "oboecraft.settings.demotions");
        y += ROW_HEIGHT;
        banLength = field(x, y, halfWidth, DurationText.format(serverSettings.banLength()),
                "oboecraft.settings.ban_length");
        percentageFactor = field(x + halfWidth + GAP, y, halfWidth, Integer.toString(serverSettings.percentageFactor()),
                "oboecraft.settings.percentage_factor");
        y += ROW_HEIGHT;
        tsukumogamiThreshold = field(x, y, halfWidth, Integer.toString(serverSettings.tsukumogamiThreshold()),
                "oboecraft.settings.tsukumogami_threshold");
        tsukumogamiThreshold.setTooltip(
                Tooltip.create(Component.translatable("oboecraft.settings.tsukumogami_threshold.tooltip")));
        tsukumogamiReviewFreeze = field(x + halfWidth + GAP, y, halfWidth,
                DurationText.format(serverSettings.tsukumogamiReviewFreeze()),
                "oboecraft.settings.tsukumogami_review_freeze");
        tsukumogamiReviewFreeze.setTooltip(
                Tooltip.create(Component.translatable("oboecraft.settings.tsukumogami_review_freeze.tooltip")));
        y += ROW_HEIGHT;
        dropCountsAsInteraction = addRenderableWidget(CycleButton.onOffBuilder(serverSettings.dropCountsAsInteraction())
                .create(x, y, FIELD_WIDTH, 20, Component.translatable("oboecraft.settings.drop")));
        dropCountsAsInteraction.active = serverEditable;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                .bounds(x, height - 24, halfWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(x + halfWidth + GAP, height - 24, halfWidth, 20).build());
    }

    /** A text field for a server setting, with its label above it, which can't be typed in on another server. */
    private EditBox field(int x, int labelY, int fieldWidth, String value, String label) {
        EditBox box = new EditBox(font, x, labelY + 10, fieldWidth, 20, Component.translatable(label));
        box.setMaxLength(Settings.MAX_LEVELS * 12);
        box.setValue(value);
        box.setEditable(serverEditable);
        return addRenderableWidget(box);
    }

    /**
     * Saves both kinds of settings and closes the screen. Server settings that aren't valid aren't saved: the screen
     * stays open and says what is wrong. Saved server settings are used at once by a singleplayer world or a world
     * opened to LAN that is running. The client settings file is only written when they changed, so an invalid file
     * the player didn't touch here is left as it is.
     */
    private void save() {
        Settings newServerSettings = serverSettings;
        if (serverEditable) {
            try {
                newServerSettings = new Settings(LevelsText.parse(intervals.getValue(), demotions.getValue()),
                        DurationText.parse(banLength.getValue()),
                        parseWholeNumber(percentageFactor.getValue(), "percentage points"),
                        dropCountsAsInteraction.getValue(),
                        parseWholeNumber(tsukumogamiThreshold.getValue(), "overdue items"),
                        DurationText.parse(tsukumogamiReviewFreeze.getValue()));
            } catch (IllegalArgumentException e) {
                error = Component.literal(e.getMessage()).withStyle(ChatFormatting.RED);
                return;
            }
        }
        ClientSettings newClientSettings = new ClientSettings(clickableButtons.getValue(), reviewItemScale.scalePercent());
        try {
            if (!newClientSettings.equals(clientSettings)) {
                ClientSettingsFile.write(ClientSettingsFile.path(), newClientSettings);
            }
            if (serverEditable) {
                ServerSettingsFile.write(ServerSettingsFile.path(), newServerSettings);
            }
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't save the settings", e);
            error = Component.translatable("oboecraft.settings.save_failed").withStyle(ChatFormatting.RED);
            return;
        }
        OboecraftClient.useClientSettings(newClientSettings);
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (serverEditable && server != null) {
            Settings applied = newServerSettings;
            server.execute(() -> OboecraftMod.serverProgress().useSettings(applied));
        }
        onClose();
    }

    /** A whole number typed in a field, where {@code unit} says what it counts for the error message. */
    private static int parseWholeNumber(String text, String unit) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + text.trim() + "\" isn't a whole number of " + unit, e);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int x = (width - FIELD_WIDTH) / 2;
        graphics.centeredText(font, title, width / 2, 8, -1);
        if (error.getString().isEmpty()) {
            graphics.text(font, serverHeader, x, SERVER_TOP, -1);
        } else {
            int errorY = SERVER_TOP;
            for (var line : font.split(error, width - 2 * ERROR_MARGIN)) {
                graphics.centeredText(font, line, width / 2, errorY, -1);
                errorY += 10;
            }
        }
        for (EditBox box : List.of(intervals, demotions, banLength, percentageFactor, tsukumogamiThreshold,
                tsukumogamiReviewFreeze)) {
            graphics.text(font, box.getMessage(), box.getX(), box.getY() - 10, LABEL_COLOR);
        }
    }

    /** The error shown after a save that failed, empty if none. Client game tests read it. */
    public Component error() {
        return error;
    }
}
