package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.core.tsukumogami.Night;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
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
    private static final StackLocation FIRST_SLOT = new StackLocation.InventorySlot(0);
    /** The Minecraft items of {@link #ITEMS}, in the same order. */
    private static final List<Item> MINECRAFT_ITEMS = List.of(
            Items.OAK_LOG, Items.STONE, Items.TORCH, Items.APPLE, Items.IRON_INGOT, Items.BREAD, Items.DIRT);
    private static final List<ItemId> ITEMS = List.of(new ItemId("block.minecraft.oak_log"),
            new ItemId("block.minecraft.stone"), new ItemId("block.minecraft.torch"),
            new ItemId("item.minecraft.apple"), new ItemId("item.minecraft.iron_ingot"),
            new ItemId("item.minecraft.bread"), new ItemId("block.minecraft.dirt"));

    /** A test at midnight, with the clock past the wait after joining, that puts both back when it is done. */
    private interface Scene {
        void run(ServerPlayer player, MinecraftServer server);
    }

    private static void atMidnight(GameTestHelper helper, Scene scene) {
        MinecraftServer server = helper.getLevel().getServer();
        OboecraftMod.clock().setSource(InstantSource.fixed(START));
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
    public void neverAppearsDuringTheDayForLessonsOrWhenTurnedOff(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            for (int i = 0; i < 6; i++) {
                player.getInventory().setItem(i, new ItemStack(MINECRAFT_ITEMS.get(i), 4));
            }
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "with only level 0 items");

            carryOverdue(player, 6);
            OverworldTime.set(server, NOON);
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "at noon");

            OverworldTime.set(server, MIDNIGHT);
            OboecraftMod.serverProgress().useSettings(Settings.DEFAULTS.withTsukumogamiThreshold(0));
            tsukumogami().checkPlayers(server);
            assertNone(helper, player, "with the threshold at 0");
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
    public void aPassBelowTheThresholdMakesItFadeOutAndWait(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);

            // A real review of the oak log, which ends with a pass and checks the player at once.
            OboecraftMod.serverReviews().start(player, 1, FIRST_SLOT);
            OboecraftMod.serverReviews().answer(player, 1, "オークの原木");
            helper.assertTrue(tsukumogami.isFading(), "it should fade out right after the pass");
            assertNone(helper, player, "after the pass");

            // Locked again the same night, it waits before it comes back, and arriving doesn't shorten the wait.
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
    public void itLeavesAtDawn(GameTestHelper helper) {
        atMidnight(helper, (player, server) -> {
            carryOverdue(player, 5);
            tsukumogami().checkPlayers(server);
            Tsukumogami tsukumogami = existing(helper, player);
            OverworldTime.set(server, Night.END);
            tsukumogami().checkPlayers(server);
            helper.assertTrue(tsukumogami.isFading(), "it should fade out at dawn");
            assertNone(helper, player, "at dawn");
        });
    }

    @GameTest
    public void killedItIsGoneUntilTheNextNightAndDropsNothing(GameTestHelper helper) {
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
            OverworldTime.set(server, MIDNIGHT + Night.DAY_LENGTH);
            tsukumogami().checkPlayers(server);
            existing(helper, player);
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

            OboecraftMod.serverProgress().useSettings(Settings.DEFAULTS.withTsukumogamiReviewFreeze(Duration.ZERO));
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
                assertNone(helper, again, "after joining again the night it was killed");
            } finally {
                TestPlayers.leave(again);
            }
        } finally {
            OverworldTime.set(server, timeBefore);
            OboecraftMod.clock().setSource(InstantSource.system());
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
