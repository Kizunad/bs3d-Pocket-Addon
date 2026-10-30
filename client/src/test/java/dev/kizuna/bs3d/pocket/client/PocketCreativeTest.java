package dev.kizuna.bs3d.pocket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PocketCreativeTest {
    private static final int ARMOR_Y = 6;
    private static final int POCKET_Y = 54;
    private static final int REMOVED_ROW_Y = 72;
    private static final int HOTBAR_Y = 112;

    @Test
    void compactKeepsPocketRowHotbarAndDeleteSlot() {
        var compact = PocketCreative.COMPACT;
        assertEquals(52, compact.height());
        assertEquals(6, POCKET_Y + compact.offset(POCKET_Y));
        assertEquals(28, HOTBAR_Y + compact.offset(HOTBAR_Y));
        assertEquals(0, compact.offset(REMOVED_ROW_Y));
    }

    @Test
    void fallbackKeepsVanillaUpperHalf() {
        var fallback = PocketCreative.FALLBACK;
        assertEquals(100, fallback.height());
        assertEquals(0, fallback.offset(ARMOR_Y));
        assertEquals(0, fallback.offset(POCKET_Y));
        assertEquals(76, HOTBAR_Y + fallback.offset(HOTBAR_Y));
    }
}
