package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.AnswerScreen;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * The answer menu over an open menu, through a real client in singleplayer: it opens over the menu's screen, the menu
 * stays open on the server, and closing the answer menu goes back to the menu's screen as it was.
 */
public class MenuClientGameTest implements FabricClientGameTest {
    private static final ItemId OAK_PLANKS = new ItemId("block.minecraft.oak_planks");
    /** The menu slot of the first hotbar slot in a chest menu with three rows. */
    private static final int CHEST_HOTBAR_SLOT = 54;
    private static final int PRIMARY = 0;
    private static final int SECONDARY = 1;

    @Override
    public void runTest(ClientGameTestContext context) {
        SingleplayerProgress.delete();
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.waitFor(client -> OboecraftClient.progress().hasProgress(client.player));
            takingALockedStackFromAChest(context, singleplayer);
            takingALockedCraftingResult(context, singleplayer);
        }
    }

    private static void takingALockedStackFromAChest(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = player(server);
            BlockPos pos = player.blockPosition().offset(2, 0, 0);
            player.level().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            ChestBlockEntity chest = (ChestBlockEntity) player.level().getBlockEntity(pos);
            chest.setItem(0, new ItemStack(Items.OAK_LOG, 4));
            player.openMenu(chest);
        });
        context.waitForScreen(ContainerScreen.class);

        // Taking the locked oak logs opens the answer menu over the chest, and the chest stays open on the server.
        click(context, 0, PRIMARY, ContainerInput.PICKUP);
        context.waitForScreen(AnswerScreen.class);
        singleplayer.getConnection().waitForServerboundPackets();
        context.takeScreenshot("answer-menu-over-chest");
        check(singleplayer, player -> player.containerMenu != player.inventoryMenu, "the chest is open on the server");

        // Closing the answer menu goes back to the chest, with the oak logs still in it.
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(ContainerScreen.class);
        singleplayer.getConnection().waitForServerboundPackets();
        check(singleplayer, player -> player.containerMenu.getSlot(0).getItem().getCount() == 4,
                "the oak logs are in the chest after closing the answer menu");

        // After a pass, the answer menu goes back to the chest, and the oak logs can be taken.
        click(context, 0, PRIMARY, ContainerInput.PICKUP);
        context.waitForScreen(AnswerScreen.class);
        context.getInput().typeChars("オークの原木");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitForScreen(ContainerScreen.class);
        singleplayer.getConnection().waitForClientboundPackets();
        click(context, 0, PRIMARY, ContainerInput.PICKUP);
        singleplayer.getConnection().waitForServerboundPackets();
        check(singleplayer, player -> player.containerMenu.getCarried().getCount() == 4,
                "the player carries the oak logs after the pass");
        click(context, CHEST_HOTBAR_SLOT, PRIMARY, ContainerInput.PICKUP);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(null);
    }

    private static void takingALockedCraftingResult(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = player(server);
            player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 4));
            OboecraftMod.serverProgress().update(player, OAK_PLANKS, new SrsState.Leveled(
                    1, Optional.of(OboecraftMod.clock().instant().plus(Duration.ofDays(1)))));
        });
        singleplayer.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
        context.waitForScreen(InventoryScreen.class);

        // One plank in each slot of the crafting grid makes a crafting table, which is locked.
        int planks = InventoryMenu.USE_ROW_SLOT_START + 1;
        click(context, planks, PRIMARY, ContainerInput.PICKUP);
        for (int slot = InventoryMenu.CRAFT_SLOT_START; slot < InventoryMenu.CRAFT_SLOT_END; slot++) {
            click(context, slot, SECONDARY, ContainerInput.PICKUP);
        }
        singleplayer.getConnection().waitForServerboundPackets();
        singleplayer.getConnection().waitForClientboundPackets();
        if (!context.computeOnClient(MenuClientGameTest::resultIsCraftingTable)) {
            throw new AssertionError("The crafting grid should make a crafting table");
        }

        // Taking the locked crafting table opens the answer menu. Closing it goes back to the inventory, where the
        // crafting table is still the result: opening the answer menu didn't clear the result slot.
        click(context, InventoryMenu.RESULT_SLOT, PRIMARY, ContainerInput.PICKUP);
        context.waitForScreen(AnswerScreen.class);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(InventoryScreen.class);
        if (!context.computeOnClient(MenuClientGameTest::resultIsCraftingTable)) {
            throw new AssertionError("The crafting table should still be the result after the answer menu");
        }
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(null);
    }

    private static boolean resultIsCraftingTable(Minecraft client) {
        return client.player.inventoryMenu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.CRAFTING_TABLE);
    }

    /** Clicks a slot of the open menu, as the menu's screen does it. */
    private static void click(ClientGameTestContext context, int slot, int button, ContainerInput input) {
        context.runOnClient(client -> client.gameMode.handleContainerInput(
                client.player.containerMenu.containerId, slot, button, input, client.player));
    }

    private static void check(
            TestSingleplayerContext singleplayer, Function<ServerPlayer, Boolean> condition, String what) {
        if (!singleplayer.getServer().computeOnServer(server -> condition.apply(player(server)))) {
            throw new AssertionError("Expected that " + what);
        }
    }

    private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}
