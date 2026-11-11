package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.tsukumogami.Tsukumogami;
import io.github.willdalh.oboecraft.fabric.tsukumogami.TsukumogamiType;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The tsukumogami in a real client: it arrives at night for a player with overdue items, everyone is sent its orbiting
 * items, it winds up and hits its owner, a review stops its attack, and it is drawn with its model, its glowing eyes,
 * its ring of items and each of its animations. The screenshots show them.
 */
public class TsukumogamiClientGameTest implements FabricClientGameTest {
    /** The Minecraft items of {@link #OVERDUE_IDS}, in the same order. */
    private static final List<Item> OVERDUE = List.of(
            Items.OAK_LOG, Items.STONE, Items.TORCH, Items.APPLE, Items.IRON_INGOT, Items.BREAD);
    private static final List<ItemId> OVERDUE_IDS = List.of(new ItemId("block.minecraft.oak_log"),
            new ItemId("block.minecraft.stone"), new ItemId("block.minecraft.torch"), new ItemId("item.minecraft.apple"),
            new ItemId("item.minecraft.iron_ingot"), new ItemId("item.minecraft.bread"));

    /** Thresholds the six overdue items are above, so a pass and then two more bring them down to the fade one. */
    private static final Settings SETTINGS = Settings.DEFAULTS.withTsukumogamiThresholds(5, 3);

    /** How far in front of the player it is held for the screenshots, in blocks. */
    private static final double DISTANCE_IN_FRONT = 3.4;
    /** The player's pitch that shows all of it from {@link #DISTANCE_IN_FRONT}, in degrees. */
    private static final float LOOKING_AHEAD = -4;
    /** The player's pitch that shows its mask when it stands over them to attack, in degrees. */
    private static final float LOOKING_UP = -30;

    @Override
    public void runTest(ClientGameTestContext context) {
        ProgressFolder.delete();
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            // Past the wait after joining, at midnight, with six overdue items.
            OboecraftMod.clock().setSource(InstantSource.offset(InstantSource.system(), Duration.ofMinutes(1)));
            singleplayer.getServer().runCommand("time set midnight");
            singleplayer.getServer().runOnServer(server -> {
                OboecraftMod.serverProgress().useSettings(SETTINGS);
                ServerPlayer player = player(server);
                for (int i = 0; i < OVERDUE.size(); i++) {
                    player.getInventory().setItem(i, new ItemStack(OVERDUE.get(i)));
                    OboecraftMod.serverProgress().update(player, OVERDUE_IDS.get(i), new SrsState.Leveled(
                            1, Optional.of(OboecraftMod.clock().instant().minusSeconds(60))));
                }
            });
            // It arrives within the next check, and everyone is sent its orbiting items, never the owner's stacks.
            context.waitFor(client -> tsukumogamiOnClient(client)
                    .filter(tsukumogami -> tsukumogami.orbitingItems().equals(OVERDUE_IDS)).isPresent(), 100);

            standInFrontOfTheCamera(singleplayer);
            context.waitTicks(20);
            context.takeScreenshot("tsukumogami");

            // Let go, it walks up to its owner, who looks up at it, winds up for a second and hits them.
            singleplayer.getServer().runOnServer(server -> {
                look(player(server), LOOKING_UP);
                existing(server).setNoAi(false);
            });
            context.waitFor(client -> tsukumogamiOnClient(client)
                    .filter(tsukumogami -> tsukumogami.motion().attackWeight(1) > 0).isPresent(), 200);
            context.waitTicks(Tsukumogami.WIND_UP_TICKS - 4);
            check(context.computeOnClient(client -> client.player.getHealth() == client.player.getMaxHealth()),
                    "the owner is unhurt during the wind-up");
            context.takeScreenshot("tsukumogami-wind-up");
            context.waitFor(client -> client.player.getHealth() < client.player.getMaxHealth(), 20);
            context.takeScreenshot("tsukumogami-hit");

            // Danger interrupts the next wind-up without putting it into the review's watching pose.
            context.waitFor(client -> tsukumogamiOnClient(client)
                    .filter(tsukumogami -> tsukumogami.motion().attackSeconds(1) > 0
                            && tsukumogami.motion().attackSeconds(1) < 0.4F).isPresent(), 200);
            int threatId = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer owner = player(server);
                var threat = EntityTypes.ZOMBIE.create(server.overworld(), EntitySpawnReason.EVENT);
                threat.setNoAi(true);
                threat.snapTo(owner.position().add(10, 0, 0));
                threat.setTarget(owner);
                server.overworld().addFreshEntity(threat);
                return threat.getId();
            });
            context.waitFor(client -> tsukumogamiOnClient(client).filter(Tsukumogami::waitsForSafety).isPresent(), 40);
            float healthInDanger = context.computeOnClient(client -> client.player.getHealth());
            context.waitTicks(Tsukumogami.ATTACK_ANIMATION_TICKS);
            check(context.computeOnClient(client -> tsukumogamiOnClient(client).orElseThrow().motion()
                    .attackWeight(1) == 0), "danger settles the cancelled attack animation");
            check(context.computeOnClient(client -> tsukumogamiOnClient(client).orElseThrow().motion()
                    .watchWeight(1) == 0), "danger does not use the review watching pose");
            check(context.computeOnClient(client -> client.player.getHealth() >= healthInDanger),
                    "the owner isn't hit during danger");
            context.takeScreenshot("tsukumogami-waiting-for-safety");
            singleplayer.getServer().runOnServer(server -> {
                server.overworld().getEntity(threatId).discard();
                OboecraftMod.clock().setSource(InstantSource.offset(InstantSource.system(), Duration.ofMinutes(2)));
            });
            context.waitFor(client -> tsukumogamiOnClient(client)
                    .filter(tsukumogami -> !tsukumogami.waitsForSafety()).isPresent(), 40);

            // A review that starts during the next wind-up stops the attack, and it watches its owner.
            context.waitFor(client -> tsukumogamiOnClient(client)
                    .filter(tsukumogami -> tsukumogami.motion().attackSeconds(1) > 0.5F).isPresent(), 200);
            singleplayer.getServer().runOnServer(server ->
                    OboecraftMod.serverTsukumogami().reviewStarted(player(server)));
            context.waitFor(client -> tsukumogamiOnClient(client).filter(Tsukumogami::standsStill).isPresent(), 40);
            float healthAtReview = context.computeOnClient(client -> client.player.getHealth());
            context.waitTicks(Tsukumogami.ATTACK_ANIMATION_TICKS);
            check(context.computeOnClient(client -> tsukumogamiOnClient(client).orElseThrow().motion()
                    .attackWeight(1) == 0), "the attack's animation stops for a review");
            check(context.computeOnClient(client -> client.player.getHealth() >= healthAtReview),
                    "the owner isn't hit during a review");
            context.takeScreenshot("tsukumogami-watching");
            singleplayer.getServer().runOnServer(server ->
                    OboecraftMod.serverTsukumogami().reviewEnded(player(server)));
            standInFrontOfTheCamera(singleplayer);

            // A pass knocks one item out of the ring.
            singleplayer.getServer().runOnServer(server -> OboecraftMod.serverProgress().update(player(server),
                    OVERDUE_IDS.getFirst(), new SrsState.Leveled(2, Optional.of(
                            OboecraftMod.clock().instant().plus(Duration.ofHours(1))))));
            context.waitFor(client -> tsukumogamiOnClient(client)
                    .filter(tsukumogami -> tsukumogami.orbitingItems().size() == OVERDUE.size() - 1).isPresent(), 40);
            context.waitTicks(8);
            context.takeScreenshot("tsukumogami-item-knocked-out");

            // Down to the fade threshold, it fades out on the spot.
            singleplayer.getServer().runOnServer(server -> {
                for (ItemId item : OVERDUE_IDS.subList(1, 3)) {
                    OboecraftMod.serverProgress().update(player(server), item, new SrsState.Leveled(2, Optional.of(
                            OboecraftMod.clock().instant().plus(Duration.ofHours(1)))));
                }
                existing(server).setNoAi(false);
            });
            context.waitFor(client -> tsukumogamiOnClient(client).filter(Tsukumogami::isFading).isPresent(), 40);
            context.waitTicks(Tsukumogami.FADE_TICKS / 2);
            context.takeScreenshot("tsukumogami-fading");
            context.waitFor(client -> tsukumogamiOnClient(client).isEmpty(), 100);
        } finally {
            OboecraftMod.clock().setSource(InstantSource.system());
            OboecraftMod.serverProgress().useSettings(Settings.DEFAULTS);
            ProgressFolder.delete();
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static Tsukumogami existing(MinecraftServer server) {
        return OboecraftMod.serverTsukumogami().existingOf(player(server)).orElseThrow();
    }

    private static Optional<Tsukumogami> tsukumogamiOnClient(Minecraft client) {
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Tsukumogami tsukumogami) {
                return Optional.of(tsukumogami);
            }
        }
        return Optional.empty();
    }

    /** Holds it still a few blocks in front of the player, facing them, so the screenshots show all of it close up. */
    private static void standInFrontOfTheCamera(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = player(server);
            look(player, LOOKING_AHEAD);
            Tsukumogami tsukumogami = existing(server);
            tsukumogami.setNoAi(true);
            Vec3 front = player.position().add(0, 0, DISTANCE_IN_FRONT);
            tsukumogami.snapTo(front.x, front.y, front.z, 180, 0);
            tsukumogami.setYHeadRot(180);
            tsukumogami.setYBodyRot(180);
            check(tsukumogami.getType() == TsukumogamiType.TYPE, "the type of the tsukumogami");
        });
        singleplayer.getConnection().waitForClientboundPackets();
    }

    /** Turns the player south, where the tsukumogami is put, looking up or down by {@code pitch} degrees. */
    private static void look(ServerPlayer player, float pitch) {
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0, pitch);
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
