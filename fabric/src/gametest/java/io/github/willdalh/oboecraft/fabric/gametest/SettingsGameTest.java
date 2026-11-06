package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.network.ReviewAction;
import io.github.willdalh.oboecraft.fabric.progress.ServerProgress;
import io.github.willdalh.oboecraft.fabric.settings.ServerSettingsFile;
import io.github.willdalh.oboecraft.json.SettingsJson;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The server settings file, and server settings other than the defaults used by the rules on the server. Each test
 * that changes the server's settings puts the defaults back before it ends, within the same tick, so no other test
 * sees them.
 */
public class SettingsGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");

    private static ServerProgress progress() {
        return OboecraftMod.serverProgress();
    }

    private static Path temporaryFile() {
        try {
            return Files.createTempDirectory("oboecraft-settings").resolve("server.json");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GameTest
    public void aMissingSettingsFileIsWrittenWithTheDefaults(GameTestHelper helper) throws IOException {
        Path file = temporaryFile();

        helper.assertValueEqual(ServerSettingsFile.load(file), Settings.DEFAULTS, "settings");
        helper.assertValueEqual(SettingsJson.parse(JsonParser.parseString(Files.readString(file))), Settings.DEFAULTS,
                "settings in the written file");
        helper.succeed();
    }

    @GameTest
    public void anInvalidSettingsFileIsKeptAndTheDefaultsAreUsed(GameTestHelper helper) throws IOException {
        Path file = temporaryFile();
        String invalid = SettingsJson.format(Settings.DEFAULTS).replace("\"10m\"", "\"10 minutes\"");
        Files.writeString(file, invalid, StandardCharsets.UTF_8);

        helper.assertValueEqual(ServerSettingsFile.load(file), Settings.DEFAULTS, "settings");
        helper.assertValueEqual(Files.readString(file), invalid, "the invalid file, untouched");
        helper.succeed();
    }

    @GameTest
    public void savedSettingsAreReadBack(GameTestHelper helper) throws IOException {
        Path file = temporaryFile();
        Settings custom = Settings.DEFAULTS.withBanLength(Duration.ofSeconds(30)).withPercentageFactor(10);

        ServerSettingsFile.write(file, custom);
        helper.assertValueEqual(ServerSettingsFile.load(file), custom, "settings");
        helper.succeed();
    }

    @GameTest
    public void theDropSettingStopsDroppingALockedStack(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG, 4));
            progress().useSettings(Settings.DEFAULTS.withDropCountsAsInteraction(true));
            player.drop(false);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 4, "oak logs left after dropping");
        } finally {
            progress().useSettings(Settings.DEFAULTS);
            TestPlayers.leave(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aFailBansForTheBanLengthSetting(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG, 4));
            Instant now = Instant.parse("2026-11-06T12:00:00Z");
            OboecraftMod.clock().setSource(InstantSource.fixed(now));
            progress().update(player, OAK_LOG, new SrsState.Leveled(1, Optional.of(now)));
            progress().useSettings(Settings.DEFAULTS.withBanLength(Duration.ofSeconds(30)));
            OboecraftMod.serverReviews().start(player, 1, new StackLocation.InventorySlot(0));
            OboecraftMod.serverReviews().action(player, 1, ReviewAction.Action.DONT_KNOW);
            SrsState state = progress().state(player, OAK_LOG);
            helper.assertTrue(state instanceof SrsState.Leveled leveled
                            && leveled.bannedUntil().equals(Optional.of(now.plusSeconds(30))),
                    "the oak log should be banned for 30 seconds, not " + state);
        } finally {
            progress().useSettings(Settings.DEFAULTS);
            OboecraftMod.clock().setSource(InstantSource.system());
            TestPlayers.leave(player);
        }
        helper.succeed();
    }
}
