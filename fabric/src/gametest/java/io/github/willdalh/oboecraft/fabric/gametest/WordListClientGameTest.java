package io.github.willdalh.oboecraft.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.OboecraftClient;
import io.github.willdalh.oboecraft.fabric.client.WordListScreen;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * The word list opens from a button on the survival inventory, through a real client in singleplayer. The inventory
 * stays open behind it, so the crafting grid and the stack on the cursor are where they were when it goes back.
 */
public class WordListClientGameTest implements FabricClientGameTest {
    private static final ItemId OAK_LOG = new ItemId("block.minecraft.oak_log");
    private static final int PRIMARY = 0;
    private static final int SECONDARY = 1;
    /** The button is right of the recipe book button, which is 20 wide, with a gap of 2. */
    private static final int RIGHT_OF_RECIPE_BOOK = 22;

    @Override
    public void runTest(ClientGameTestContext context) {
        ProgressFolder.delete();
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.waitFor(client -> OboecraftClient.progress().hasProgress(client.player));
            buttonFollowsTheRecipeBook(context);
            craftingGridSurvivesTheWordList(context, singleplayer);
            inventoryKeyClosesBoth(context, singleplayer);
            buttonNeedsProgress(context);
            noButtonOnTheCreativeInventory(context, singleplayer);
        }
    }

    private static void buttonFollowsTheRecipeBook(ClientGameTestContext context) {
        openInventory(context);
        context.takeScreenshot("word-list-button");
        assertNextToRecipeBook(context, "with the recipe book closed");
        context.runOnClient(client -> recipeBookButton(client).onPress(new net.minecraft.client.input.KeyEvent(
                InputConstants.KEY_RETURN, 0, 0)));
        context.waitTick();
        assertNextToRecipeBook(context, "with the recipe book open");
        context.takeScreenshot("word-list-button-recipe-book");
        context.runOnClient(client -> recipeBookButton(client).onPress(new net.minecraft.client.input.KeyEvent(
                InputConstants.KEY_RETURN, 0, 0)));
        context.waitTick();
        assertNextToRecipeBook(context, "after closing the recipe book");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(null);
    }

    /**
     * A log in the crafting grid and two on the cursor stay there on both sides while the word list is open and after
     * Close goes back, and the crafting result stays shown: the inventory is never closed on the server.
     */
    private static void craftingGridSurvivesTheWordList(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = player(server);
            OboecraftMod.serverProgress().update(player, OAK_LOG, new SrsState.Known());
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));
        });
        singleplayer.getConnection().waitForClientboundPackets();
        openInventory(context);
        click(context, InventoryMenu.USE_ROW_SLOT_START, PRIMARY);
        click(context, InventoryMenu.CRAFT_SLOT_START, SECONDARY);
        singleplayer.getConnection().waitForServerboundPackets();
        singleplayer.getConnection().waitForClientboundPackets();
        assertCraftingKept(context, singleplayer, "before the word list");

        WordListButtons.click(context);
        context.waitForScreen(WordListScreen.class);
        singleplayer.getConnection().waitForServerboundPackets();
        context.takeScreenshot("word-list-from-inventory");
        assertCraftingKept(context, singleplayer, "while the word list is open");

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(InventoryScreen.class);
        singleplayer.getConnection().waitForServerboundPackets();
        assertCraftingKept(context, singleplayer, "after going back to the inventory");
        if (context.computeOnClient(client -> WordListButtons.find(client.gui.screen()).isEmpty())) {
            throw new AssertionError("The inventory should have the button again after going back");
        }
    }

    /** The inventory key in the word list closes the inventory too, which the server hears about, as with vanilla. */
    private static void inventoryKeyClosesBoth(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        WordListButtons.click(context);
        context.waitForScreen(WordListScreen.class);
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitForScreen(null);
        singleplayer.getConnection().waitForServerboundPackets();
        singleplayer.getConnection().waitForClientboundPackets();
        check(singleplayer, player -> player.inventoryMenu.getSlot(InventoryMenu.CRAFT_SLOT_START).getItem().isEmpty()
                        && player.inventoryMenu.getCarried().isEmpty()
                        && player.getInventory().countItem(Items.OAK_LOG) == 3,
                "closing the inventory puts the logs back into it");
        singleplayer.getServer().runOnServer(server -> player(server).getInventory().clearContent());
        singleplayer.getConnection().waitForClientboundPackets();
    }

    /**
     * Without progress, the inventory has no button. When progress arrives with the inventory open, the button
     * appears, and when progress goes away with the word list open, it goes back to the inventory without the button.
     */
    private static void buttonNeedsProgress(ClientGameTestContext context) {
        Progress progress = context.computeOnClient(client -> OboecraftClient.progress().progress());
        Settings settings = context.computeOnClient(client -> OboecraftClient.progress().settings());
        context.runOnClient(client -> OboecraftClient.progress().clear());
        openInventory(context);
        context.waitTick();
        assertButton(context, false, "without progress");

        context.runOnClient(client -> OboecraftClient.progress().sync(
                client.getConnection(), progress, settings, OboecraftClient.progress().now()));
        context.waitTicks(2);
        assertButton(context, true, "once progress arrives");

        WordListButtons.click(context);
        context.waitForScreen(WordListScreen.class);
        context.runOnClient(client -> OboecraftClient.progress().clear());
        context.waitForScreen(InventoryScreen.class);
        context.waitTicks(2);
        assertButton(context, false, "after progress goes away");

        context.runOnClient(client -> OboecraftClient.progress().sync(
                client.getConnection(), progress, settings, OboecraftClient.progress().now()));
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(null);
    }

    private static void noButtonOnTheCreativeInventory(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> player(server).setGameMode(GameType.CREATIVE));
        singleplayer.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.player.hasInfiniteMaterials());
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitForScreen(CreativeModeInventoryScreen.class);
        context.waitTicks(2);
        assertButton(context, false, "on the creative inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitForScreen(null);
        singleplayer.getServer().runOnServer(server -> player(server).setGameMode(GameType.SURVIVAL));
        singleplayer.getConnection().waitForClientboundPackets();
    }

    private static void openInventory(ClientGameTestContext context) {
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitForScreen(InventoryScreen.class);
    }

    private static void assertNextToRecipeBook(ClientGameTestContext context, String when) {
        context.runOnClient(client -> {
            Button recipeBook = recipeBookButton(client);
            Button wordList = WordListButtons.find(client.gui.screen())
                    .orElseThrow(() -> new AssertionError("The inventory should have the button " + when));
            if (wordList.getX() != recipeBook.getX() + RIGHT_OF_RECIPE_BOOK || wordList.getY() != recipeBook.getY()
                    || wordList.getWidth() != 20 || wordList.getHeight() != 18) {
                throw new AssertionError("The button should be right of the recipe book button " + when);
            }
        });
    }

    private static void assertButton(ClientGameTestContext context, boolean shown, String when) {
        if (context.computeOnClient(client -> WordListButtons.find(client.gui.screen()).isPresent()) != shown) {
            throw new AssertionError("The button should " + (shown ? "" : "not ") + "be there " + when);
        }
    }

    /** One log in the crafting grid with planks as the result, and two on the cursor, on the client and the server. */
    private static void assertCraftingKept(
            ClientGameTestContext context, TestSingleplayerContext singleplayer, String when) {
        if (!context.computeOnClient(client -> craftingKept(client.player))) {
            throw new AssertionError("The client should keep the crafting grid and cursor " + when);
        }
        check(singleplayer, WordListClientGameTest::craftingKept, "the server keeps the crafting grid and cursor " + when);
    }

    private static boolean craftingKept(Player player) {
        InventoryMenu menu = player.inventoryMenu;
        return player.containerMenu == menu
                && menu.getSlot(InventoryMenu.CRAFT_SLOT_START).getItem().is(Items.OAK_LOG)
                && menu.getSlot(InventoryMenu.CRAFT_SLOT_START).getItem().getCount() == 1
                && menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.OAK_PLANKS)
                && menu.getCarried().is(Items.OAK_LOG) && menu.getCarried().getCount() == 2;
    }

    private static ImageButton recipeBookButton(Minecraft client) {
        return client.gui.screen().children().stream().filter(ImageButton.class::isInstance)
                .map(ImageButton.class::cast).findFirst().orElseThrow();
    }

    private static void click(ClientGameTestContext context, int slot, int button) {
        context.runOnClient(client -> client.gameMode.handleContainerInput(
                client.player.containerMenu.containerId, slot, button, ContainerInput.PICKUP, client.player));
    }

    private static void check(
            TestSingleplayerContext singleplayer, Function<ServerPlayer, Boolean> condition, String what) {
        if (!singleplayer.getServer().computeOnServer(server -> condition.apply(player(server)))) {
            throw new AssertionError("Expected that " + what);
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}
