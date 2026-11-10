package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.network.ReviewAction;
import io.github.willdalh.oboecraft.fabric.network.UploadProgress;
import io.github.willdalh.oboecraft.fabric.network.ProgressUnavailable;
import io.github.willdalh.oboecraft.fabric.progress.ProgressFiles;
import io.github.willdalh.oboecraft.fabric.progress.ProgressMigration;
import io.github.willdalh.oboecraft.fabric.progress.ProgressStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class LanguageProgressGameTest {
    private static final ItemId OAK = new ItemId("block.minecraft.oak_log");
    private static final Progress KNOWN = Progress.EMPTY.with(OAK, new SrsState.Known());
    private static final Progress IGNORED = Progress.EMPTY.with(OAK, new SrsState.Ignored());

    @GameTest public void twoPlayersUseDifferentAnswersAndGroupingOnOneServer(GameTestHelper helper) {
        var german = TestPlayers.join(helper, UUID.randomUUID());
        var japanese = TestPlayers.join(helper, UUID.randomUUID());
        var progress = OboecraftMod.serverProgress();
        var reviews = OboecraftMod.serverReviews();
        try {
            progress.start(german, LearningLanguage.GERMAN, Progress.EMPTY);
            german.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
            japanese.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
            reviews.start(german, 1, new StackLocation.InventorySlot(0));
            reviews.start(japanese, 2, new StackLocation.InventorySlot(0));
            reviews.answer(japanese, 2, "Eichenstamm");
            helper.assertTrue(reviews.isReviewing(japanese), "a German answer doesn't pass in Japanese");
            reviews.answer(german, 1, "eichenstamm");
            helper.assertFalse(reviews.isReviewing(german), "the German answer passes in German");
            helper.assertValueEqual(progress.state(japanese, OAK), SrsState.Leveled.NEW, "Japanese state is untouched");
            reviews.answer(japanese, 2, "オークの原木");
            helper.assertFalse(reviews.isReviewing(japanese), "Japanese answer passes");

            german.getInventory().setItem(1, new ItemStack(Items.BRICK));
            german.getInventory().setItem(2, new ItemStack(Items.BRICKS));
            japanese.getInventory().setItem(1, new ItemStack(Items.BRICK));
            japanese.getInventory().setItem(2, new ItemStack(Items.BRICKS));
            reviews.start(german, 3, new StackLocation.InventorySlot(1));
            reviews.action(german, 3, ReviewAction.Action.KNOWN);
            reviews.start(german, 4, new StackLocation.InventorySlot(2));
            helper.assertTrue(reviews.isReviewing(german), "German brick and bricks are separate items");
            reviews.start(japanese, 5, new StackLocation.InventorySlot(1));
            reviews.action(japanese, 5, ReviewAction.Action.KNOWN);
            reviews.start(japanese, 6, new StackLocation.InventorySlot(2));
            helper.assertFalse(reviews.isReviewing(japanese), "Japanese brick and bricks share progress");
        } finally {
            TestPlayers.leave(german);
            TestPlayers.leave(japanese);
        }
        helper.succeed();
    }

    @GameTest public void eachLanguageHasItsOwnFileAndLock(GameTestHelper helper) throws Exception {
        Path folder = Files.createTempDirectory("oboecraft-languages");
        UUID player = UUID.randomUUID();
        ProgressStore german = new ProgressStore(folder, Duration.ofHours(1), () -> { });
        ProgressStore dutch = new ProgressStore(folder, Duration.ofHours(1), () -> { });
        var first = new Connection(PacketFlow.CLIENTBOUND);
        var second = new Connection(PacketFlow.CLIENTBOUND);
        try {
            List<CustomPacketPayload> sent = new ArrayList<>();
            german.start(first, player, LearningLanguage.GERMAN, sent::add).join();
            dutch.start(second, player, LearningLanguage.DUTCH, sent::add).join();
            helper.assertValueEqual(sent, List.of(new UploadProgress("de_de", Progress.EMPTY),
                    new UploadProgress("nl_nl", Progress.EMPTY)), "different languages can be used at once");
            german.changed(first, KNOWN);
            dutch.changed(second, IGNORED);
            german.end(first).join();
            dutch.end(second).join();
            helper.assertValueEqual(ProgressFiles.read(folder.resolve("de_de/" + player + ".json")),
                    new ProgressFiles.ReadResult.Loaded(KNOWN), "German progress");
            helper.assertValueEqual(ProgressFiles.read(folder.resolve("nl_nl/" + player + ".json")),
                    new ProgressFiles.ReadResult.Loaded(IGNORED), "Dutch progress");
            helper.assertFalse(Files.exists(folder.resolve("ja_jp/" + player + ".json")), "Japanese is untouched");
        } finally { german.close(); dutch.close(); }
        helper.succeed();
    }

    @GameTest public void migrationPreservesTheOriginalAndBrokenFilesAndBlocksDowngrades(GameTestHelper helper) throws Exception {
        Path folder = Files.createTempDirectory("oboecraft-migration");
        UUID player = UUID.randomUUID();
        Path old = folder.resolve(player + ".json");
        Path current = folder.resolve("ja_jp").resolve(old.getFileName());
        ProgressFiles.write(old, KNOWN);
        byte[] broken = {(byte) 0xff, 0, 42};
        Files.write(old.resolveSibling(old.getFileName() + ".broken"), broken);
        helper.assertTrue(ProgressMigration.migrate(folder, player), "migrated");
        helper.assertValueEqual(ProgressFiles.read(current), new ProgressFiles.ReadResult.Loaded(KNOWN), "progress moved");
        helper.assertValueEqual(ProgressFiles.read(old), new ProgressFiles.ReadResult.TooNew(ProgressMigration.STUB_VERSION), "downgrade stub");
        helper.assertTrue(java.util.Arrays.equals(broken, Files.readAllBytes(current.resolveSibling(current.getFileName() + ".broken"))), "broken bytes preserved");
        helper.assertTrue(ProgressMigration.migrate(folder, player), "repeat is harmless");
        helper.assertValueEqual(ProgressFiles.read(current), new ProgressFiles.ReadResult.Loaded(KNOWN), "progress kept");
        helper.succeed();
    }

    @GameTest public void migrationKeepsTheNewerFileAndArchivesTheOtherInBothDirections(GameTestHelper helper) throws Exception {
        for (boolean oldIsNewer : List.of(false, true)) {
            Path folder = Files.createTempDirectory("oboecraft-conflict");
            UUID player = UUID.randomUUID();
            Path old = folder.resolve(player + ".json");
            Path current = folder.resolve("ja_jp").resolve(old.getFileName());
            ProgressFiles.write(old, KNOWN);
            ProgressFiles.write(current, IGNORED);
            Files.setLastModifiedTime(old, FileTime.fromMillis(oldIsNewer ? 2000 : 1000));
            Files.setLastModifiedTime(current, FileTime.fromMillis(oldIsNewer ? 1000 : 2000));
            ProgressMigration.migrate(folder, player);
            helper.assertValueEqual(ProgressFiles.read(current), new ProgressFiles.ReadResult.Loaded(oldIsNewer ? KNOWN : IGNORED), "newer wins");
            helper.assertValueEqual(ProgressFiles.read(current.resolveSibling(current.getFileName() + ".broken")),
                    new ProgressFiles.ReadResult.Loaded(oldIsNewer ? IGNORED : KNOWN), "other preserved");
        }
        helper.succeed();
    }

    @GameTest public void aLegacyLockDefersMigrationWithoutChangingEitherFile(GameTestHelper helper) throws Exception {
        Path folder = Files.createTempDirectory("oboecraft-migration-lock");
        UUID player = UUID.randomUUID();
        Path old = folder.resolve(player + ".json");
        ProgressFiles.write(old, KNOWN);
        try (var channel = java.nio.channels.FileChannel.open(old.resolveSibling(old.getFileName() + ".lock"),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            helper.assertFalse(ProgressMigration.migrate(folder, player), "legacy game owns the file");
            helper.assertValueEqual(ProgressFiles.read(old), new ProgressFiles.ReadResult.Loaded(KNOWN), "legacy untouched");
            helper.assertFalse(Files.exists(folder.resolve("ja_jp").resolve(old.getFileName())), "no destination written");
        }
        helper.assertTrue(ProgressMigration.migrate(folder, player), "migration retries after the lock is released");
        helper.succeed();
    }

    @GameTest public void failedMigrationKeepsTheOriginalAndCanBeRetried(GameTestHelper helper) throws Exception {
        Path folder = Files.createTempDirectory("oboecraft-migration-retry");
        UUID player = UUID.randomUUID();
        Path old = folder.resolve(player + ".json");
        Path current = folder.resolve("ja_jp").resolve(old.getFileName());
        ProgressFiles.write(old, KNOWN);
        Path blocked = current.resolveSibling(current.getFileName() + ".tmp");
        Files.createDirectories(blocked);
        Files.writeString(blocked.resolve("blocker"), "can't replace a nonempty directory");
        boolean failed = false;
        try { ProgressMigration.migrate(folder, player); }
        catch (java.io.IOException expected) { failed = true; }
        helper.assertTrue(failed, "destination failure reported");
        helper.assertValueEqual(ProgressFiles.read(old), new ProgressFiles.ReadResult.Loaded(KNOWN), "original survives");
        helper.assertFalse(Files.exists(current), "no half file");
        Files.delete(blocked.resolve("blocker"));
        Files.delete(blocked);
        helper.assertTrue(ProgressMigration.migrate(folder, player), "retry succeeds");
        helper.assertValueEqual(ProgressFiles.read(current), new ProgressFiles.ReadResult.Loaded(KNOWN), "complete file");
        helper.succeed();
    }

    @GameTest public void startupMigratesAccountsAndKeepsEveryBrokenBackup(GameTestHelper helper) throws Exception {
        Path folder = Files.createTempDirectory("oboecraft-migration-startup");
        UUID player = UUID.randomUUID();
        Path old = folder.resolve(player + ".json");
        Path current = folder.resolve("ja_jp").resolve(old.getFileName());
        ProgressFiles.write(old, KNOWN);
        Files.createDirectories(current.getParent());
        Files.writeString(current.resolveSibling(current.getFileName() + ".broken"), "existing backup");
        Files.writeString(old.resolveSibling(old.getFileName() + ".broken"), "old broken");
        Files.writeString(old.resolveSibling(old.getFileName() + ".broken.1"), "old numbered broken");
        ProgressStore store = new ProgressStore(folder, Duration.ofHours(1), () -> { });
        store.close(); // Drains startup migration even without joining a world.
        helper.assertValueEqual(ProgressFiles.read(current), new ProgressFiles.ReadResult.Loaded(KNOWN), "startup migrated");
        try (var files = Files.list(current.getParent())) {
            var contents = new java.util.HashSet<String>();
            for (Path file : files.filter(path -> path.getFileName().toString().contains(".broken")).toList()) {
                contents.add(Files.readString(file));
            }
            helper.assertValueEqual(contents, java.util.Set.of("existing backup", "old broken", "old numbered broken"), "all backups kept");
        }
        helper.succeed();
    }

    @GameTest public void reconfiguringOneConnectionKeepsItsLanguageUntilTheNextConnection(GameTestHelper helper) throws Exception {
        Path folder = Files.createTempDirectory("oboecraft-language-session");
        UUID player = UUID.randomUUID();
        var connection = new Connection(PacketFlow.CLIENTBOUND);
        ProgressStore store = new ProgressStore(folder, Duration.ofHours(1), () -> { });
        try {
            List<CustomPacketPayload> sent = new ArrayList<>();
            store.start(connection, player, LearningLanguage.GERMAN, sent::add).join();
            store.changed(connection, KNOWN);
            store.start(connection, player, LearningLanguage.DUTCH, sent::add).join();
            helper.assertValueEqual(sent.getLast(), new UploadProgress("de_de", KNOWN), "connection retains language");
            store.end(connection).join();
            store.start(new Connection(PacketFlow.CLIENTBOUND), player, LearningLanguage.DUTCH, sent::add).join();
            helper.assertValueEqual(sent.getLast(), new UploadProgress("nl_nl", Progress.EMPTY), "new connection chooses language");
        } finally { store.close(); }
        helper.succeed();
    }

    @GameTest public void aMaxVersionFileWithoutTheExactMarkerIsStillRefused(GameTestHelper helper) throws Exception {
        for (String marker : List.of("", ", \"movedTo\": \"ja_jp/another-account.json\"")) {
            Path folder = Files.createTempDirectory("oboecraft-migration-newer");
            UUID player = UUID.randomUUID();
            Path old = folder.resolve(player + ".json");
            String text = "{\"version\": " + ProgressMigration.STUB_VERSION + marker + "}";
            Files.writeString(old, text);
            ProgressStore store = new ProgressStore(folder, Duration.ofHours(1), () -> { });
            try {
                List<CustomPacketPayload> sent = new ArrayList<>();
                store.start(new Connection(PacketFlow.CLIENTBOUND), player, LearningLanguage.JAPANESE, sent::add).join();
                helper.assertValueEqual(sent, List.of(new ProgressUnavailable(
                        ProgressUnavailable.Reason.TOO_NEW)), "unsupported file refuses play");
                helper.assertValueEqual(Files.readString(folder.resolve("ja_jp").resolve(old.getFileName())), text, "all original bytes kept");
            } finally { store.close(); }
        }
        helper.succeed();
    }
}
