package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The tsukumogami in a real client: it arrives at night for a player with overdue items, everyone is sent its orbiting
 * items, and it is drawn with its model, its glowing eyes, its ring of items, its wind-up and its fade-out. The
 * screenshots show each of them.
 */
public class TsukumogamiClientGameTest implements FabricClientGameTest {
    /** The Minecraft items of {@link #OVERDUE_IDS}, in the same order. */
    private static final List<Item> OVERDUE = List.of(
            Items.OAK_LOG, Items.STONE, Items.TORCH, Items.APPLE, Items.IRON_INGOT, Items.BREAD);
    private static final List<ItemId> OVERDUE_IDS = List.of(new ItemId("block.minecraft.oak_log"),
            new ItemId("block.minecraft.stone"), new ItemId("block.minecraft.torch"), new ItemId("item.minecraft.apple"),
            new ItemId("item.minecraft.iron_ingot"), new ItemId("item.minecraft.bread"));

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

            singleplayer.getServer().runOnServer(server -> OboecraftMod.serverTsukumogami().existingOf(player(server))
                    .ifPresent(tsukumogami -> server.overworld().broadcastEntityEvent(
                            tsukumogami, Tsukumogami.WIND_UP_EVENT)));
            context.waitTicks(15);
            context.takeScreenshot("tsukumogami-wind-up");

            // A pass knocks one item out of the ring.
            singleplayer.getServer().runOnServer(server -> OboecraftMod.serverProgress().update(player(server),
                    OVERDUE_IDS.getFirst(), new SrsState.Leveled(2, Optional.of(
                            OboecraftMod.clock().instant().plus(Duration.ofHours(1))))));
            context.waitFor(client -> tsukumogamiOnClient(client)
                    .filter(tsukumogami -> tsukumogami.orbitingItems().size() == OVERDUE.size() - 1).isPresent(), 40);
            context.waitTicks(8);
            context.takeScreenshot("tsukumogami-item-knocked-out");

            // Below the threshold, it fades out on the spot.
            singleplayer.getServer().runOnServer(server -> {
                for (ItemId item : OVERDUE_IDS.subList(1, 3)) {
                    OboecraftMod.serverProgress().update(player(server), item, new SrsState.Leveled(2, Optional.of(
                            OboecraftMod.clock().instant().plus(Duration.ofHours(1)))));
                }
                OboecraftMod.serverTsukumogami().existingOf(player(server)).ifPresent(tsukumogami ->
                        tsukumogami.setNoAi(false));
            });
            context.waitFor(client -> tsukumogamiOnClient(client).filter(Tsukumogami::isFading).isPresent(), 40);
            context.waitTicks(15);
            context.takeScreenshot("tsukumogami-fading");
            context.waitFor(client -> tsukumogamiOnClient(client).isEmpty(), 100);
        } finally {
            OboecraftMod.clock().setSource(InstantSource.system());
            ProgressFolder.delete();
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static Optional<Tsukumogami> tsukumogamiOnClient(Minecraft client) {
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Tsukumogami tsukumogami) {
                return Optional.of(tsukumogami);
            }
        }
        return Optional.empty();
    }

    /** Holds it still four blocks in front of the player, facing them, so the screenshots show it close up. */
    private static void standInFrontOfTheCamera(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = player(server);
            player.setXRot(10);
            player.setYRot(0);
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0, 10);
            Tsukumogami tsukumogami = OboecraftMod.serverTsukumogami().existingOf(player).orElseThrow();
            tsukumogami.setNoAi(true);
            Vec3 front = player.position().add(0, 0, 4);
            tsukumogami.snapTo(front.x, front.y, front.z, 180, 0);
            tsukumogami.setYHeadRot(180);
            tsukumogami.setYBodyRot(180);
            check(tsukumogami.getType() == TsukumogamiType.TYPE, "the type of the tsukumogami");
        });
        singleplayer.getConnection().waitForClientboundPackets();
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
