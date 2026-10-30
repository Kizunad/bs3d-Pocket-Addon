package dev.kizuna.bs3d.pocket.client;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import dev.kizuna.bs3d.pocket.client.TextureSlices.Slice;
import dev.kizuna.bs3d.pocket.server.PocketClicks;
import dev.kizuna.bs3d.vanilla.client.PlayerInventoryRows;
import dev.kizuna.bs3d.vanilla.client.PlayerInventoryView;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import java.util.List;

/** 创造模式「物品栏」页签：三维场景启用时只留口袋行、快捷栏与删除格，否则保留原版上半页。 */
public final class PocketCreative {
    public static final int WIDTH_PIXELS = 195;
    private static final int TEXTURE_HEIGHT_PIXELS = 136;
    private static final int TOP_BORDER_PIXELS = 5;
    private static final int MAIN_FRAME_Y = 53;
    private static final int VISIBLE_MAIN_PIXELS =
            PocketSlots.VISIBLE_MAIN_ROWS * PlayerInventoryView.SLOT_PITCH_PIXELS;
    private static final int HOTBAR_SECTION_Y =
            MAIN_FRAME_Y + PlayerInventoryRows.VANILLA_ROWS * PlayerInventoryView.SLOT_PITCH_PIXELS;
    private static final int HOTBAR_SECTION_PIXELS = TEXTURE_HEIGHT_PIXELS - HOTBAR_SECTION_Y;
    public static final TextureSlices COMPACT = new TextureSlices(WIDTH_PIXELS, List.of(
            new Slice(0, TOP_BORDER_PIXELS, 0),
            new Slice(MAIN_FRAME_Y, VISIBLE_MAIN_PIXELS, TOP_BORDER_PIXELS),
            new Slice(HOTBAR_SECTION_Y, HOTBAR_SECTION_PIXELS, TOP_BORDER_PIXELS + VISIBLE_MAIN_PIXELS)));
    public static final TextureSlices FALLBACK = new TextureSlices(WIDTH_PIXELS, List.of(
            new Slice(0, MAIN_FRAME_Y + VISIBLE_MAIN_PIXELS, 0),
            new Slice(HOTBAR_SECTION_Y, HOTBAR_SECTION_PIXELS, MAIN_FRAME_Y + VISIBLE_MAIN_PIXELS)));
    public static final int COMPACT_HEIGHT_PIXELS = COMPACT.height();

    private PocketCreative() {
        // 创造写入与删除格仍由原版创造页处理。
    }

    public static boolean fallbackVisible(Slot menuSlot) {
        // 退回布局保留原版上半页，只有 18–35 号槽视为不存在。
        return !PocketClicks.blocked(menuSlot);
    }

    public static boolean compactVisible(Slot menuSlot) {
        // 紧凑布局只留口袋行与快捷栏，盔甲、副手与合成格由三维人物承担。
        if (!fallbackVisible(menuSlot)) {
            return false;
        }
        if (!(menuSlot.inventory instanceof PlayerInventory)) {
            return false;
        }
        return menuSlot.getIndex() < PlayerInventory.MAIN_SIZE;
    }
}
