package io.github.willdalh.oboecraft.core.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PunishmentTest {
    private static final Settings SETTINGS = Settings.DEFAULTS;
    private static final ItemId PLANKS = new ItemId("block.minecraft.oak_planks");
    private static final ItemId DIAMOND = new ItemId("item.minecraft.diamond");
    private static final ItemId STICK = new ItemId("item.minecraft.stick");
    private static final ItemId SHULKER_SHELL = new ItemId("item.minecraft.shulker_shell");
    private static final ItemId CHEST = new ItemId("block.minecraft.chest");
    private static final ItemId BUNDLE = new ItemId("item.minecraft.bundle");
    private static final ItemId RED_DYE = new ItemId("item.minecraft.red_dye");

    @Test
    void theRemovalRateIsHalfAtLevelOneAndFifteenPercentAtLevelEight() {
        assertEquals(50, Punishment.removalPercent(1, SETTINGS));
        assertEquals(45, Punishment.removalPercent(2, SETTINGS));
        assertEquals(15, Punishment.removalPercent(8, SETTINGS));
    }

    @Test
    void theRemovalRateNeverDropsBelowZero() {
        Settings bigFactor = SETTINGS.withPercentageFactor(30);

        assertEquals(20, Punishment.removalPercent(2, bigFactor));
        assertEquals(0, Punishment.removalPercent(3, bigFactor));
        assertEquals(0, Punishment.removalPercent(8, bigFactor));
        assertEquals(50, Punishment.removalPercent(8, SETTINGS.withPercentageFactor(0)));
        assertEquals(0, Punishment.removalPercent(Integer.MAX_VALUE, SETTINGS));
    }

    @Test
    void aLessonIsNeverPunished() {
        assertThrows(IllegalArgumentException.class, () -> Punishment.removalPercent(0, SETTINGS));
    }

    @Test
    void failingAtLevelOneRemovesHalfOfTheItem() {
        assertEquals(25, Punishment.removedCount(PLANKS, 50, 1, SETTINGS));
    }

    @Test
    void theRemovedCountIsRoundedUp() {
        assertEquals(1, Punishment.removedCount(PLANKS, 1, 1, SETTINGS));
        assertEquals(26, Punishment.removedCount(PLANKS, 51, 1, SETTINGS));
        assertEquals(8, Punishment.removedCount(PLANKS, 50, 8, SETTINGS));
        assertEquals(0, Punishment.removedCount(PLANKS, 0, 1, SETTINGS));
    }

    @Test
    void aZeroRateRemovesNothing() {
        assertEquals(0, Punishment.removedCount(PLANKS, 64, 8, SETTINGS.withPercentageFactor(10)));
    }

    @Test
    void aProtectedItemIsNeverRemoved() {
        assertEquals(0, Punishment.removedCount(BUNDLE, 10, 1, SETTINGS));
        assertEquals(0, Punishment.removedCount(new ItemId("block.minecraft.dragon_egg"), 1, 1, SETTINGS));
    }

    @Test
    void theDragonEggShulkerBoxesAndBundlesAreProtected() {
        assertTrue(ProtectedItems.isProtected(new ItemId("block.minecraft.dragon_egg")));
        assertTrue(ProtectedItems.isProtected(new ItemId("block.minecraft.shulker_box")));
        assertTrue(ProtectedItems.isProtected(new ItemId("block.minecraft.light_blue_shulker_box")));
        assertTrue(ProtectedItems.isProtected(BUNDLE));
        assertTrue(ProtectedItems.isProtected(new ItemId("item.minecraft.black_bundle")));
        assertFalse(ProtectedItems.isProtected(SHULKER_SHELL));
        assertFalse(ProtectedItems.isProtected(CHEST));
        assertEquals(35, ProtectedItems.IDS.size());
    }

    @Test
    void ingredientsLoseTheirPartOfTheRecipe() {
        Recipe pickaxe = new Recipe(Map.of(DIAMOND, 3, STICK, 2), 5);

        Map<ItemId, Integer> removed =
                Punishment.removedIngredients(pickaxe, Map.of(DIAMOND, 50, STICK, 100), 1, SETTINGS);

        assertEquals(Map.of(DIAMOND, 15, STICK, 20), removed);
    }

    @Test
    void ingredientCountsAreRoundedUp() {
        Recipe pickaxe = new Recipe(Map.of(DIAMOND, 3, STICK, 2), 5);

        Map<ItemId, Integer> removed =
                Punishment.removedIngredients(pickaxe, Map.of(DIAMOND, 3, STICK, 1), 1, SETTINGS);

        assertEquals(Map.of(DIAMOND, 1, STICK, 1), removed);
    }

    @Test
    void ingredientsNotInTheCurrentViewAreLeftOut() {
        Recipe pickaxe = new Recipe(Map.of(DIAMOND, 3, STICK, 2), 5);

        assertEquals(Map.of(DIAMOND, 15),
                Punishment.removedIngredients(pickaxe, Map.of(DIAMOND, 50, PLANKS, 64), 1, SETTINGS));
    }

    @Test
    void aProtectedIngredientIsKeptAndTheOthersLoseOnlyTheirPart() {
        Recipe redBundle = new Recipe(Map.of(BUNDLE, 1, RED_DYE, 1), 2);

        assertEquals(Map.of(RED_DYE, 3),
                Punishment.removedIngredients(redBundle, Map.of(BUNDLE, 4, RED_DYE, 12), 1, SETTINGS));
    }

    @Test
    void ingredientsThatArentItemsCountTowardsTheirPart() {
        Recipe withAnotherModsIngredient = new Recipe(Map.of(DIAMOND, 1), 4);

        assertEquals(Map.of(DIAMOND, 5),
                Punishment.removedIngredients(withAnotherModsIngredient, Map.of(DIAMOND, 40), 1, SETTINGS));
    }

    @Test
    void aRecipeChecksItsCounts() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(Map.of(DIAMOND, 3, STICK, 2), 4));
        assertThrows(IllegalArgumentException.class, () -> new Recipe(Map.of(DIAMOND, 0), 1));
        assertEquals(Map.of(), Punishment.removedIngredients(new Recipe(Map.of(), 0), Map.of(), 1, SETTINGS));
    }
}
