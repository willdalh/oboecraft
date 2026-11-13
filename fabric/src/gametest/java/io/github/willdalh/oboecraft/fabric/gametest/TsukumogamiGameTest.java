package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.core.tsukumogami.TsukumogamiRules;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.network.ReviewAction;
import io.github.willdalh.oboecraft.fabric.tsukumogami.ServerTsukumogami;
import io.github.willdalh.oboecraft.fabric.tsukumogami.Tsukumogami;
import io.github.willdalh.oboecraft.fabric.tsukumogami.TsukumogamiSounds;
import io.github.willdalh.oboecraft.fabric.tsukumogami.TsukumogamiType;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The tsukumogami on the server: when it appears and leaves, its health, the damage it takes, the review freeze,
 * sleeping and what ends it. Each test runs in one tick, with the clock and the Overworld's time set and put back.
 */
public class TsukumogamiGameTest {
    private static final Instant START = Instant.parse("2026-11-09T22:00:00Z");
    /** After the wait that starts when the player joins. */
    private static final Instant ARRIVED = START.plus(TsukumogamiRules.ARRIVAL_DELAY);
    private static final long MIDNIGHT = 18_000;
    private static final long NOON = 6_000;
    /**
     * The settings most tests use: it appears at 5 overdue items and fades at 4, so the threshold is one count. The
     * gap between the two thresholds has tests of its own.
     */
    private static final Settings SETTINGS = Settings.DEFAULTS.withTsukumogamiThresholds(5, 4);
    /** Thresholds with a gap between them, which the seven items can cross both ways. */
    private static final Settings GAP_SETTINGS = Settings.DEFAULTS.withTsukumogamiThresholds(5, 2);
    private static final StackLocation FIRST_SLOT = new StackLocation.InventorySlot(0);
    /**
     * How far from the player a threat stands when only being in range matters. The player stands 1.5 blocks into the
     * 8×8×8 test structure, so this keeps the threat inside it. The runner loads only the structure's chunks before a
     * test, and a mob outside them can be missing from the threat check.
     */
    private static final double NEARBY_THREAT_DISTANCE = 5;
    /** The Minecraft items of {@link #ITEMS}, in the same order. */
    private static final List<Item> MINECRAFT_ITEMS = List.of(
            Items.OAK_LOG, Items.STONE, Items.TORCH, Items.APPLE, Items.IRON_INGOT, Items.BREAD, Items.DIRT);
    private static final List<ItemId> ITEMS = List.of(new ItemId("block.minecraft.oak_log"),
            new ItemId("block.minecraft.stone"), new ItemId("block.minecraft.torch"),
            new ItemId("item.minecraft.apple"), new ItemId("item.minecraft.iron_ingot"),
            new ItemId("item.minecraft.bread"), new ItemId("block.minecraft.dirt"));

    /**
     * A test at midnight with {@link #SETTINGS}, with the clock past the wait after joining, that puts the clock, the
     * time and the settings back when it is done.
     */
    private interface Scene {
        void run(ServerPlayer player, MinecraftServer server);
    }

    private static void atMidnight(GameTestHelper helper, Scene scene) {
        MinecraftServer server = helper.getLevel().getServer();
        OboecraftMod.clock().setSource(InstantSource.fixed(START));
        OboecraftMod.serverProgress().useSettings(SETTINGS);
        long timeBefore = OverworldTime.set(server, MIDNIGHT);
        ServerPlayer player = TestPlayers.join(helper, UUID.randomUUID());
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
            OboecraftMod.clock().setSource(InstantSource.fixed(ARRIVED));
            scene.run(player, server);
        } finally {
            TestPlayers.leave(player);
            OverworldTime.set(server, timeBefore);
            OboecraftMod.clock().setSource(InstantSource.system());
            OboecraftMod.serverProgress().useSettings(Settings.DEFAULTS);
        }
        helper.succeed();
    }

    /** Puts the first {@code count} items in the player's inventory, overdue at level 1. */
    private static void carryOverdue(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            player.getInventory().setItem(i, new ItemStack(MINECRAFT_ITEMS.get(i), 4));
            setOverdue(player, ITEMS.get(i));
        }
    }

    private static void setOverdue(ServerPlayer player, ItemId item) {
        OboecraftMod.serverProgress().update(player, item,
                new SrsState.Leveled(1, Optional.of(OboecraftMod.clock().instant().minusSeconds(1))));
    }

    private static void unlock(ServerPlayer player, ItemId item) {
        OboecraftMod.serverProgress().update(player, item,
                new SrsState.Leveled(2, Optional.of(OboecraftMod.clock().instant().plus(Duration.ofHours(1)))));
    }

    private static ServerTsukumogami tsukumogami() {
        return OboecraftMod.serverTsukumogami();
    }

    private static Tsukumogami existing(GameTestHelper helper, ServerPlayer player) {
        Optional<Tsukumogami> tsukumogami = tsukumogami().existingOf(player);
        helper.assertTrue(tsukumogami.isPresent(), "the player's tsukumogami should exist");
        return tsukumogami.get();
    }

    private static void assertNone(GameTestHelper helper, ServerPlayer player, String when) {
        helper.assertValueEqual(tsukumogami().existingOf(player), Optional.empty(), "tsukumogami " + when);
    }

    private static void later(Duration duration) {
        OboecraftMod.clock().setSource(InstantSource.fixed(OboecraftMod.clock().instant().plus(duration)));
    }

    @GameTest
    public void appearsAtNightWhenTheOverdueCountReachesTheThreshold(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 4);
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "with 4 overdue items");

            // The fifth overdue item is the threshold. More of the same item don't count.
            player.getInventory().setItem(10, new ItemStack(Items.OAK_LOG, 64));
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "with 4 overdue items in two stacks");
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            helper.assertValueEqual(tsukumogami.owner(), Optional.of(player), "owner");
            helper.assertValueEqual(tsukumogami.orbitingItems(), ITEMS.subList(0, 5), "orbiting items");
            helper.assertValueEqual(tsukumogami.getMaxHealth(), 20.0F, "max health at the threshold");
            helper.assertValueEqual(tsukumogami.getHealth(), 20.0F, "health");
            double distance = tsukumogami.position().subtract(player.position()).horizontalDistance();
            helper.assertTrue(distance >= 15.5 && distance <= 24.5, "it should arrive 16 to 24 blocks away, not "
                    + distance);

            // Each player has at most one.
            tsukumogami().checkPlayers(server);
            helper.assertValueEqual(tsukumogami().existingOf(player), Optional.of(tsukumogami), "the same one");
        });
    }

    @GameTest
    public void appearsDuringTheDayButNotForLessonsOrWhenTurnedOff(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            for (int i = 0; i < 6; i++) {
                player.getInventory().setItem(i, new ItemStack(MINECRAFT_ITEMS.get(i), 4));
            }
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "with only level 0 items");

            OverworldTime.set(server, NOON);
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "with only level 0 items at noon");
            carryOverdue(player, 6);
            tsukumogami().checkPlayers(server);
            Tsukumogami daytime = existing(helper, player);
            helper.assertTrue(server.overworld().canSeeSky(daytime.blockPosition()), "it arrives in open sunlight");
            ticks(daytime, 40);
            helper.assertTrue(daytime.isAlive() && !daytime.isFading() && !daytime.isOnFire(),
                    "it remains active in sunlight");
            tsukumogami().checkPlayers(server);
            helper.assertValueEqual(existing(helper, player), daytime, "one tsukumogami during the day");

            OboecraftMod.serverProgress().useSettings(SETTINGS.withTsukumogamiThresholds(0, 0));
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "with the appear threshold at 0");
        });
    }

    @GameTest
    public void wornItemsDontCountButTheOffhandDoes(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            ItemStack fifth = player.getInventory().removeItemNoUpdate(4);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            setOverdue(player, new ItemId("block.minecraft.carved_pumpkin"));
            helper.assertTrue(!fifth.isEmpty(), "the fifth item was carried");
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "with the fifth overdue item worn");

            // The offhand counts.
            player.getInventory().setItem(Inventory.SLOT_OFFHAND, fifth);
            tsukumogami().checkPlayers(server);
            existing(helper, player);
        });
    }

    @GameTest
    public void itsHealthFollowsTheOverdueCountAndKeepsItsFraction(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 7);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            helper.assertValueEqual(tsukumogami.getMaxHealth(), 32.0F, "max health with 7 overdue items");

            tsukumogami.hurtServer(server.overworld(), player.damageSources().playerAttack(player), 16);
            helper.assertValueEqual(tsukumogami.getHealth(), 16.0F, "health after the owner's hit");

            unlock(player, ITEMS.get(6));
            tsukumogami().checkPlayers(server);
            helper.assertValueEqual(tsukumogami.getMaxHealth(), 26.0F, "max health with 6 overdue items");
            helper.assertValueEqual(tsukumogami.getHealth(), 13.0F, "half of its health, as before the pass");
            helper.assertValueEqual(tsukumogami.orbitingItems(), ITEMS.subList(0, 6), "orbiting items");
        });
    }

    @GameTest
    public void aPassDownToTheFadeThresholdMakesItFadeOutAndWait(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            OverworldTime.set(server, NOON);
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);

            // A real review of the oak log, which ends with a pass and checks the player at once.
            OboecraftMod.serverReviews().start(player, 1, FIRST_SLOT);
            OboecraftMod.serverReviews().answer(player, 1, "オークの原木");
            helper.assertTrue(tsukumogami.isFading(), "it should fade out right after the pass");
            assertNone(helper, player, "after the pass");

            // Locked again, it waits before it comes back, and arriving doesn't shorten the wait.
            setOverdue(player, ITEMS.getFirst());
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "right after it faded");
            tsukumogami().arrived(player);
            later(TsukumogamiRules.ARRIVAL_DELAY);
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "an arrival's wait after it faded");
            later(TsukumogamiRules.RETURN_DELAY.minus(TsukumogamiRules.ARRIVAL_DELAY));
            tsukumogami().checkPlayers(server);
            existing(helper, player);
        });
    }

    @GameTest
    public void itStaysUntilReviewsBringTheCountDownToTheFadeThreshold(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            OboecraftMod.serverProgress().useSettings(GAP_SETTINGS);
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);

            // Below the appear threshold but above the fade threshold, it stays, at its base health.
            unlock(player, ITEMS.get(4));
            unlock(player, ITEMS.get(3));
            tsukumogami().checkPlayers(server);
            helper.assertValueEqual(existing(helper, player), tsukumogami, "the same tsukumogami with 3 overdue items");
            helper.assertTrue(!tsukumogami.isFading(), "it shouldn't fade out above the fade threshold");
            helper.assertValueEqual(tsukumogami.getMaxHealth(), 20.0F, "max health below the appear threshold");

            unlock(player, ITEMS.get(2));
            tsukumogami().checkPlayers(server);
            helper.assertTrue(tsukumogami.isFading(), "it should fade out at the fade threshold");
            assertNone(helper, player, "at the fade threshold");

            // Once it has faded, it needs the appear threshold again, not only a count above the fade threshold.
            carryOverdue(player, 4);
            later(TsukumogamiRules.RETURN_DELAY);
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "with 4 overdue items after it faded");
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            existing(helper, player);
        });
    }

    @GameTest
    public void aHauntedPlayerBelowTheAppearThresholdIsFollowedAgainAfterCreativeMode(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            OboecraftMod.serverProgress().useSettings(GAP_SETTINGS);
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami first = existing(helper, player);
            unlock(player, ITEMS.get(4));
            tsukumogami().checkPlayers(server);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(first.isRemoved(), "creative mode should remove it");

            // Only reviews get rid of it, so it comes back with 4 overdue items, below the appear threshold.
            player.setGameMode(GameType.SURVIVAL);
            later(TsukumogamiRules.ARRIVAL_DELAY);
            tsukumogami().checkPlayers(server);
            existing(helper, player);
        });
    }

    @GameTest
    public void aFailDoesntWeakenIt(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            OboecraftMod.serverReviews().start(player, 1, FIRST_SLOT);
            OboecraftMod.serverReviews().action(player, 1,
                    ReviewAction.Action.DONT_KNOW);
            helper.assertValueEqual(existing(helper, player).orbitingItems().size(), 5, "orbiting items after a fail");
        });
    }

    @GameTest
    public void itSurvivesSunrise(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            OverworldTime.set(server, 23_460);
            tsukumogami().checkPlayers(server);
            helper.assertTrue(!tsukumogami.isFading(), "it should remain active at dawn");
            helper.assertValueEqual(existing(helper, player), tsukumogami, "same entity after sunrise");
        });
    }

    @GameTest
    public void skyTimeChangesDoNotConsumeKillReliefAndItDropsNothing(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            tsukumogami.hurtServer(server.overworld(), player.damageSources().playerAttack(player), 1000);
            helper.assertTrue(tsukumogami.isDeadOrDying(), "the owner's hit should kill it");
            AABB around = tsukumogami.getBoundingBox().inflate(4);
            helper.assertValueEqual(server.overworld().getEntitiesOfClass(ItemEntity.class, around).size(), 0,
                    "dropped items");
            helper.assertValueEqual(server.overworld().getEntitiesOfClass(ExperienceOrb.class, around).size(), 0,
                    "dropped experience");

            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "later the same night");
            OverworldTime.set(server, MIDNIGHT + 24_000);
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "after skipping a sky day");
            OverworldTime.set(server, NOON);
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "after moving sky time backward");

            ServerLevelData data = (ServerLevelData) server.overworld().getLevelData();
            long killedAt = server.overworld().getGameTime();
            try {
                data.setGameTime(killedAt + TsukumogamiRules.KILL_RELIEF_TICKS - 1);
                tsukumogami().checkPlayers(server);
                assertNone(helper, player, "one tick before kill relief expires");
                data.setGameTime(killedAt + TsukumogamiRules.KILL_RELIEF_TICKS);
                tsukumogami().checkPlayers(server);
                Tsukumogami returned = existing(helper, player);
                // Killing at noon gets the same full relief as killing at midnight.
                returned.hurtServer(server.overworld(), player.damageSources().playerAttack(player), 1000);
                data.setGameTime(killedAt + 2 * TsukumogamiRules.KILL_RELIEF_TICKS - 1);
                tsukumogami().checkPlayers(server);
                assertNone(helper, player, "one tick before daytime kill relief expires");
                data.setGameTime(killedAt + 2 * TsukumogamiRules.KILL_RELIEF_TICKS);
                tsukumogami().checkPlayers(server);
                existing(helper, player);
            } finally {
                data.setGameTime(killedAt);
            }
        });
    }

    @GameTest
    public void onlyItsOwnerCanHurtIt(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            ServerPlayer friend = TestPlayers.join(helper, UUID.randomUUID());
            try {
                var level = server.overworld();
                helper.assertTrue(!tsukumogami.hurtServer(level, friend.damageSources().playerAttack(friend), 5),
                        "another player's hit should do nothing");
                helper.assertTrue(!tsukumogami.hurtServer(level, level.damageSources().lava(), 5), "lava");
                helper.assertTrue(!tsukumogami.hurtServer(level, level.damageSources().fall(), 5), "falling");
                helper.assertTrue(!tsukumogami.hurtServer(level, level.damageSources().drown(), 5), "drowning");
                helper.assertTrue(!tsukumogami.hurtServer(level, level.damageSources().cactus(), 5), "cactus");
                helper.assertValueEqual(tsukumogami.getHealth(), 20.0F, "health after the others' damage");
                helper.assertTrue(tsukumogami.hurtServer(level, player.damageSources().playerAttack(player), 5),
                        "the owner's hit");
                helper.assertTrue(!tsukumogami.canBeSeenAsEnemy(), "other mobs should ignore it");

                // Fading out, nothing hurts it, but /kill still works.
                unlock(player, ITEMS.getFirst());
                tsukumogami().checkPlayers(server);
                helper.assertTrue(tsukumogami.isFading(), "it should fade out");
                helper.assertTrue(!tsukumogami.hurtServer(level, player.damageSources().playerAttack(player), 5),
                        "the owner's hit while it fades");
                helper.assertTrue(tsukumogami.hurtServer(level, level.damageSources().genericKill(), 1000), "/kill");
            } finally {
                TestPlayers.leave(friend);
            }
        });
    }

    @GameTest
    public void onlyItsOwnerHearsTheBellWhereItArrives(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            ServerPlayer friend = TestPlayers.join(helper, UUID.randomUUID());
            try {
                friend.snapTo(player.position());
                TestPlayers.takeSentPackets(player);
                TestPlayers.takeSentPackets(friend);
                carryOverdue(player, 5);
                tsukumogami().checkPlayers(server);
                Tsukumogami tsukumogami = existing(helper, player);

                List<ClientboundSoundPacket> ownerBells = soundsSentTo(player, TsukumogamiSounds.ARRIVE);
                helper.assertValueEqual(ownerBells.size(), 1, "bells its owner hears");
                // Compared as the packet sends positions, which far from the world's center are whole blocks.
                ClientboundSoundPacket bell = ownerBells.getFirst();
                ClientboundSoundPacket atItsPosition = new ClientboundSoundPacket(bell.getSound(), bell.getSource(),
                        tsukumogami.getX(), tsukumogami.getY(), tsukumogami.getZ(), 1.0F, 1.0F, 0);
                helper.assertValueEqual(List.of(bell.getX(), bell.getY(), bell.getZ()),
                        List.of(atItsPosition.getX(), atItsPosition.getY(), atItsPosition.getZ()),
                        "where the bell rings");
                helper.assertValueEqual(soundsSentTo(friend, TsukumogamiSounds.ARRIVE), List.of(),
                        "bells another player nearby hears");
            } finally {
                TestPlayers.leave(friend);
            }
        });
    }

    @GameTest
    public void itsStrikeThudsAtTheEndOfTheWindUpUnlessAReviewStopsIt(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            tsukumogami.snapTo(player.getX(), player.getY(), player.getZ() + 1, 180, 0);
            TestPlayers.takeSentPackets(player);

            tsukumogami.tick();
            helper.assertValueEqual(soundsSentTo(player, TsukumogamiSounds.ATTACK).size(), 1, "wind-ups started");
            ticks(tsukumogami, Tsukumogami.WIND_UP_TICKS - 1);
            helper.assertValueEqual(soundsSentTo(player, TsukumogamiSounds.HIT), List.of(),
                    "strikes before the wind-up ends");
            tsukumogami.tick();
            helper.assertValueEqual(soundsSentTo(player, TsukumogamiSounds.HIT).size(), 1,
                    "strikes when the wind-up ends");

            // The next wind-up starts after the cooldown, and a review that starts during it stops the strike.
            ticks(tsukumogami, Tsukumogami.ATTACK_COOLDOWN_TICKS);
            helper.assertValueEqual(soundsSentTo(player, TsukumogamiSounds.ATTACK).size(), 1,
                    "wind-ups started after the cooldown");
            OboecraftMod.serverReviews().start(player, 1, FIRST_SLOT);
            ticks(tsukumogami, Tsukumogami.WIND_UP_TICKS + 5);
            helper.assertValueEqual(soundsSentTo(player, TsukumogamiSounds.HIT), List.of(),
                    "strikes after a review stopped the wind-up");
        });
    }

    @GameTest
    public void itStandsWhereItIsWhileItStraightensUpAfterTheHit(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            tsukumogami.snapTo(player.getX(), player.getY(), player.getZ() + 1, 180, 0);
            ticks(tsukumogami, 1 + Tsukumogami.WIND_UP_TICKS);

            // Its owner backs away at the hit. It follows only once it has straightened up.
            player.snapTo(player.getX(), player.getY(), player.getZ() - 5);
            ticks(tsukumogami, Tsukumogami.RECOVERY_TICKS - 1);
            helper.assertTrue(tsukumogami.getNavigation().isDone(), "it stands where it is while it straightens up");
            tsukumogami.tick();
            helper.assertTrue(tsukumogami.getNavigation().isInProgress(), "it follows its owner again after that");
        });
    }

    @GameTest
    public void itGetsAsCloseAsItCanToAnOwnerItCantReachWithoutSearchingEveryTick(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            // Its owner stands on a pillar it can't climb, out of its reach.
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(4, y, 4), Blocks.STONE);
            }
            player.snapTo(helper.absoluteVec(new Vec3(4.5, 4, 4.5)));
            tsukumogami.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 0.5)));
            int searchesBefore = PathSearches.of(tsukumogami);

            // A search for an owner it can't reach is the most expensive kind, and it did one on every tick before.
            int walkTicks = 60;
            ticks(tsukumogami, walkTicks);
            double distance = tsukumogami.position().subtract(player.position()).horizontalDistance();
            helper.assertTrue(distance < 3, "it should walk as close as it can to the pillar, not stay " + distance
                    + " blocks away");
            int searches = PathSearches.of(tsukumogami) - searchesBefore;
            helper.assertTrue(searches >= 1, "it should look for a path to its owner");
            helper.assertTrue(searches <= walkTicks / Tsukumogami.PATH_INTERVAL_TICKS + 1,
                    "it should look for a path at most every " + Tsukumogami.PATH_INTERVAL_TICKS + " ticks, not "
                            + searches + " times in " + walkTicks + " ticks");

            // Where it stands, a search would fall short again until one of them moves. 40 more ticks stay below the
            // 120 it needs to count as stuck and keep up, which would move it.
            ticks(tsukumogami, 40);
            helper.assertValueEqual(PathSearches.of(tsukumogami) - searchesBefore, searches,
                    "searches while it and its owner stand still");
        });
    }

    @GameTest
    public void threatsCancelPendingStrikesAndRestartTheSafetyGrace(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami spirit = existing(helper, player);
            spirit.snapTo(player.getX(), player.getY(), player.getZ() + 1, 180, 0);
            TestPlayers.takeSentPackets(player);
            spirit.tick();
            helper.assertValueEqual(soundsSentTo(player, TsukumogamiSounds.ATTACK).size(), 1, "pending strike");
            float health = player.getHealth();
            Mob threat = threat(player, EntityTypes.ZOMBIE, NEARBY_THREAT_DISTANCE);
            try {
                ticks(spirit, Tsukumogami.WIND_UP_TICKS + 1);
                helper.assertTrue(spirit.waitsForSafety(), "danger suspends attacks");
                helper.assertValueEqual(player.getHealth(), health, "health after the cancelled strike");
                helper.assertValueEqual(soundsSentTo(player, TsukumogamiSounds.HIT), List.of(), "cancelled hit");
                helper.assertTrue(spirit.bossBarName().getString().contains("(5) — Waiting until you’re safe"),
                        "the boss bar explains danger alongside the count");
                threat.setTarget(null);
                later(Duration.ofMillis(4_999));
                spirit.tick();
                helper.assertTrue(spirit.waitsForSafety(), "the full grace period is protected");
                threat.setTarget(player);
                spirit.tick();
                threat.discard();
                later(Duration.ofMillis(4_999));
                spirit.tick();
                helper.assertTrue(spirit.waitsForSafety(), "a new threat restarts the grace period");
                later(Duration.ofMillis(1));
                spirit.tick();
                helper.assertTrue(!spirit.waitsForSafety(), "attacks resume at five safe seconds");
                helper.assertTrue(!spirit.bossBarName().getString().contains("Waiting"), "normal boss bar returns");
                ticks(spirit, Tsukumogami.WIND_UP_TICKS - 1);
                helper.assertValueEqual(player.getHealth(), health, "resuming starts a fresh full wind-up");
                spirit.tick();
                helper.assertTrue(player.getHealth() < health, "the fresh strike deals damage");
            } finally {
                threat.discard();
            }
        });
    }

    // Keep every threat position inside the structure, whose chunks the runner loads before starting the test.
    @GameTest(structure = "oboecraft-gametest:threat_range")
    public void onlyLivingMobsTargetingTheOwnerWithinThirtyTwoBlocksCount(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami spirit = existing(helper, player);
            Mob threat = threat(player, EntityTypes.WOLF, 32.01);
            ServerPlayer friend = TestPlayers.join(helper, UUID.randomUUID());
            try {
                spirit.tick();
                helper.assertTrue(!spirit.waitsForSafety(), "a target beyond 32 blocks does not count");
                threat.snapTo(player.position().add(24, 0, 24));
                spirit.tick();
                helper.assertTrue(!spirit.waitsForSafety(), "range is spherical, not a bounding box");
                threat.snapTo(player.position().add(32, 0, 0));
                threat.setTarget(friend);
                spirit.tick();
                helper.assertTrue(!spirit.waitsForSafety(), "targeting another player does not count");
                threat.setTarget(null);
                spirit.tick();
                helper.assertTrue(!spirit.waitsForSafety(), "an idle nearby mob does not count");
                // A targeting neutral mob counts through a wall, even when it cannot move.
                for (int y = 0; y < 3; y++) {
                    player.level().setBlockAndUpdate(player.blockPosition().offset(1, y, 0),
                            Blocks.STONE.defaultBlockState());
                }
                helper.assertTrue(!threat.hasLineOfSight(player), "the wall blocks sight of the owner");
                threat.setTarget(player);
                spirit.tick();
                helper.assertTrue(spirit.waitsForSafety(), "a targeting neutral mob at exactly 32 blocks counts");
                threat.setHealth(0);
                later(TsukumogamiRules.SAFE_AFTER_THREAT);
                spirit.tick();
                helper.assertTrue(!spirit.waitsForSafety(), "a dead mob does not renew the grace period");
            } finally {
                threat.discard();
                TestPlayers.leave(friend);
                for (int y = 0; y < 3; y++) {
                    player.level().setBlockAndUpdate(player.blockPosition().offset(1, y, 0),
                            Blocks.AIR.defaultBlockState());
                }
            }
        });
    }

    @GameTest
    public void dangerAllowsFollowingAndReviewsRegardlessOfTheReviewFreeze(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            carryOverdue(player, 6);
            tsukumogami().checkPlayers(server);
            Tsukumogami spirit = existing(helper, player);
            Mob threat = threat(player, EntityTypes.ZOMBIE, NEARBY_THREAT_DISTANCE);
            try {
                spirit.snapTo(player.position().add(50, 0, 0));
                spirit.tick();
                helper.assertTrue(spirit.waitsForSafety() && !spirit.standsStill(), "danger does not freeze movement");
                helper.assertTrue(spirit.distanceToSqr(player) < 32 * 32, "it still keeps up with its owner");
                OboecraftMod.serverReviews().start(player, 1, FIRST_SLOT);
                spirit.tick();
                helper.assertTrue(spirit.standsStill(), "a review still freezes movement");
                later(SETTINGS.tsukumogamiReviewFreeze());
                spirit.tick();
                helper.assertTrue(spirit.waitsForSafety() && !spirit.standsStill(), "danger outlasts the review freeze");
                OboecraftMod.serverProgress().useSettings(SETTINGS.withTsukumogamiReviewFreeze(Duration.ZERO));
                spirit.snapTo(player.position().add(0, 0, 1));
                float health = player.getHealth();
                ticks(spirit, Tsukumogami.WIND_UP_TICKS + 1);
                helper.assertTrue(spirit.waitsForSafety(), "disabling the review freeze does not disable protection");
                helper.assertValueEqual(player.getHealth(), health, "no damage with the freeze disabled");
                OboecraftMod.serverReviews().answer(player, 1, "オークの原木");
                helper.assertValueEqual(spirit.orbitingItems().size(), 5, "a review can pass during danger");
                helper.assertTrue(spirit.bossBarName().getString().contains("(5) — Waiting until you’re safe"),
                        "a review updates the count without losing the waiting message");
            } finally {
                threat.discard();
            }
        });
    }

    @GameTest
    public void dangerLetsItFollowEvenDuringAttackRecovery(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami spirit = existing(helper, player);
            spirit.snapTo(player.position().add(0, 0, 1));
            ticks(spirit, Tsukumogami.WIND_UP_TICKS + 1);
            Mob threat = threat(player, EntityTypes.ZOMBIE, NEARBY_THREAT_DISTANCE);
            try {
                spirit.snapTo(player.position().add(0, 0, 4));
                spirit.tick();
                helper.assertTrue(spirit.waitsForSafety(), "danger protects during recovery");
                helper.assertTrue(spirit.getNavigation().isInProgress(), "danger lets it follow during recovery");
            } finally {
                threat.discard();
            }
        });
    }

    private static Mob threat(ServerPlayer player, EntityType<? extends Mob> type, double distance) {
        Mob mob = type.create(player.level(), EntitySpawnReason.EVENT);
        mob.setNoAi(true);
        mob.snapTo(player.position().add(distance, 0, 0));
        mob.setTarget(player);
        player.level().addFreshEntity(mob);
        return mob;
    }

    private static void ticks(Tsukumogami tsukumogami, int count) {
        for (int i = 0; i < count; i++) {
            tsukumogami.tick();
        }
    }

    /** The sounds of the event the server sent the player since they were last asked about. */
    private static List<ClientboundSoundPacket> soundsSentTo(ServerPlayer player, SoundEvent event) {
        return TestPlayers.takeSentPackets(player).stream()
                .filter(packet -> packet instanceof ClientboundSoundPacket sound && sound.getSound().value() == event)
                .map(ClientboundSoundPacket.class::cast)
                .toList();
    }

    @GameTest
    public void itStandsStillDuringAReviewForAtMostTheFreeze(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            helper.assertTrue(!tsukumogami().standsStill(player), "it moves without a review");

            OboecraftMod.serverReviews().start(player, 1, FIRST_SLOT);
            helper.assertTrue(tsukumogami().standsStill(player), "it stands still during a review");
            later(Duration.ofSeconds(59));
            helper.assertTrue(tsukumogami().standsStill(player), "it stands still for the freeze");
            later(Duration.ofSeconds(1));
            helper.assertTrue(!tsukumogami().standsStill(player), "it moves again after the freeze");

            // A new review starts a new freeze, and it stays still for 2 seconds after a review ends.
            OboecraftMod.serverReviews().start(player, 2, FIRST_SLOT);
            helper.assertTrue(tsukumogami().standsStill(player), "a new review starts a new freeze");
            OboecraftMod.serverReviews().close(player, 2);
            helper.assertTrue(tsukumogami().standsStill(player), "it stays still just after the review");
            later(TsukumogamiRules.STILL_AFTER_REVIEW);
            helper.assertTrue(!tsukumogami().standsStill(player), "it moves again 2 seconds after the review");

            OboecraftMod.serverProgress().useSettings(SETTINGS.withTsukumogamiReviewFreeze(Duration.ZERO));
            OboecraftMod.serverReviews().start(player, 3, FIRST_SLOT);
            helper.assertTrue(!tsukumogami().standsStill(player), "a freeze of 0 never stands still");
        });
    }

    @GameTest
    public void itsOwnerCantSleepWhileItExistsOrIsComing(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            var bed = helper.absolutePos(BlockPos.ZERO);
            carryOverdue(player, 4);
            helper.assertTrue(EntitySleepEvents.ALLOW_SLEEPING.invoker().allowSleep(player, bed) == null,
                    "the player may sleep below the threshold");

            // At the threshold, but still waiting after the player arrived: sleeping would skip the night.
            carryOverdue(player, 5);
            tsukumogami().arrived(player);
            helper.assertTrue(EntitySleepEvents.ALLOW_SLEEPING.invoker().allowSleep(player, bed) != null,
                    "the player can't sleep while it waits to arrive");

            // An owner who got into bed anyway is woken when it arrives.
            player.startSleeping(bed);
            later(TsukumogamiRules.ARRIVAL_DELAY);
            tsukumogami().checkPlayers(server);
            existing(helper, player);
            helper.assertTrue(!player.isSleeping(), "its owner should be woken when it arrives");
            helper.assertTrue(EntitySleepEvents.ALLOW_SLEEPING.invoker().allowSleep(player, bed) != null,
                    "its owner can't sleep");
        });
    }

    @GameTest
    public void itsBossBarGoesWithItEvenWhenItsChunkUnloads(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            helper.assertValueEqual(tsukumogami.bossBarViewers(), List.of(player), "who sees its boss bar");
            tsukumogami.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertValueEqual(tsukumogami.bossBarViewers(), List.of(), "who sees it after it unloaded");
        });
    }

    @GameTest
    public void creativeModeDeathAndPeacefulRemoveIt(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            OverworldTime.set(server, NOON);
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami first = existing(helper, player);
            player.setGameMode(GameType.ADVENTURE);
            tsukumogami().checkPlayers(server);
            helper.assertValueEqual(tsukumogami().existingOf(player), Optional.of(first), "in adventure mode");
            helper.assertTrue(!first.isFading(), "changing to adventure mode shouldn't make it fade out");
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(first.isRemoved(), "creative mode should remove it");

            // Back in survival mode, it waits before it appears again.
            player.setGameMode(GameType.SURVIVAL);
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "right after returning to survival mode");
            later(TsukumogamiRules.ARRIVAL_DELAY);
            tsukumogami().checkPlayers(server);
            Tsukumogami second = existing(helper, player);

            Difficulty difficulty = server.overworld().getDifficulty();
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                tsukumogami().checkPlayers(server);
                helper.assertTrue(second.isRemoved(), "Peaceful should remove it");
            } finally {
                server.setDifficulty(difficulty, true);
            }

            tsukumogami().checkPlayers(server);
            Tsukumogami third = existing(helper, player);
            // Test players can't be hurt, since no client loaded the world for them.
            player.die(player.damageSources().genericKill());
            helper.assertTrue(third.isRemoved(), "its owner's death should remove it");
        });
    }

    @GameTest
    public void leavingRemovesItButKeepsThatItWasKilled(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = UUID.randomUUID();
        OboecraftMod.clock().setSource(InstantSource.fixed(START));
        OboecraftMod.serverProgress().useSettings(SETTINGS);
        long timeBefore = OverworldTime.set(server, MIDNIGHT);
        try {
            ServerPlayer player = TestPlayers.join(helper, id);
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
            OboecraftMod.clock().setSource(InstantSource.fixed(ARRIVED));
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            tsukumogami.hurtServer(server.overworld(), player.damageSources().playerAttack(player), 1000);
            TestPlayers.leave(player);

            ServerPlayer again = TestPlayers.join(helper, id);
            try {
                again.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
                later(TsukumogamiRules.ARRIVAL_DELAY);
                carryOverdue(again, 5);
                tsukumogami().checkPlayers(server);
                assertNone(helper, again, "after joining again during kill relief");
            } finally {
                TestPlayers.leave(again);
            }
        } finally {
            OverworldTime.set(server, timeBefore);
            OboecraftMod.clock().setSource(InstantSource.system());
            OboecraftMod.serverProgress().useSettings(Settings.DEFAULTS);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void aTsukumogamiWithoutAnOwnerRemovesItself(GameTestHelper helper) {
        Tsukumogami ownerless = TsukumogamiType.TYPE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(ownerless != null, "a tsukumogami can be made");
        ownerless.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        helper.getLevel().addFreshEntity(ownerless);
        helper.succeedWhen(() -> helper.assertTrue(ownerless.isRemoved(), "it should remove itself"));
    }
}
