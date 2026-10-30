package dev.kizuna.bs3d.pocket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kizuna.backpack3d.client.InteriorRenderer.Rect;
import dev.kizuna.bs3d.vanilla.client.StationLayout;
import org.junit.jupiter.api.Test;

class PocketLayoutTest {
    @Test
    void usualGuiScalePlacesScenesAboveTwoBottomRows() {
        var layout = PocketLayout.calculate(640, 360);
        var player = layout.scenes().player();
        var interior = layout.scenes().interior();
        assertTrue(layout.scenes().visible());
        assertEquals(new Rect(232, 298, 176, 54), layout.menu());
        assertEquals(layout.menu().y() - StationLayout.MARGIN_PIXELS, player.bottom());
        assertEquals(player.width(), interior.width());
        assertTrue(player.right() < interior.x());
        assertTrue(interior.right() <= 640);
    }

    @Test
    void craftingSitsInsideInteriorAndButtonBesideMenu() {
        var layout = PocketLayout.calculate(640, 360);
        var interior = layout.scenes().interior();
        var crafting = layout.crafting();
        var button = layout.recipeButton();
        assertTrue(crafting.x() >= interior.x());
        assertTrue(crafting.right() <= interior.right());
        assertTrue(crafting.y() >= interior.y() + StationLayout.HEADER_PIXELS);
        assertTrue(crafting.bottom() <= interior.bottom());
        assertTrue(button.right() <= layout.menu().x());
        assertTrue(button.y() >= layout.menu().y());
        assertTrue(button.bottom() <= layout.menu().bottom());
    }

    @Test
    void smallWindowFallsBackToVanillaPage() {
        assertTrue(PocketLayout.calculate(427, 240).scenes().visible());
        assertFalse(PocketLayout.calculate(320, 180).scenes().visible());
        assertFalse(PocketLayout.calculate(240, 360).scenes().visible());
    }

    @Test
    void craftingPanelIsInsideOnlyWhileShown() {
        var layout = PocketLayout.calculate(640, 360);
        var crafting = layout.crafting();
        var player = layout.scenes().player();
        assertTrue(layout.inside(crafting.x() + 1, crafting.y() + 1, true /* craftingShown */));
        assertFalse(layout.inside(crafting.x() + 1, crafting.y() + 1, false /* craftingShown */));
        assertTrue(layout.inside(layout.menu().x() + 1, layout.menu().y() + 1, false /* craftingShown */));
        assertFalse(layout.inside(player.x() + 1, player.y() + 1, true /* craftingShown */));
    }
}
