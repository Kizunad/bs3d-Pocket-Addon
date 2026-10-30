package dev.kizuna.bs3d.pocket.api;

import net.minecraft.entity.player.PlayerInventory;

/** 可选附属的固定容量；快捷栏和原版存档结构保持独立。 */
public final class PocketSlots {
    public static final int HOTBAR_SLOTS = 9;
    public static final int VISIBLE_MAIN_SLOTS = 9;
    public static final int VISIBLE_MAIN_ROWS = VISIBLE_MAIN_SLOTS / HOTBAR_SLOTS;
    public static final int AVAILABLE_SLOTS = HOTBAR_SLOTS + VISIBLE_MAIN_SLOTS;
    public static final int VANILLA_MAIN_SLOTS = PlayerInventory.MAIN_SIZE;

    private PocketSlots() {
        // 公开只读容量，不提供运行时配置或第二份库存。
    }

    public static boolean unavailable(int inventorySlot) {
        // 盔甲与副手位于主背包之后，不能误判为被移除的主背包槽。
        return inventorySlot >= AVAILABLE_SLOTS && inventorySlot < VANILLA_MAIN_SLOTS;
    }
}
