package dev.kizuna.bs3d.pocket.server;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** 用实际库存槽号验证点击，不能把不同菜单的槽号当作 Inventory 槽号。 */
public final class PocketClicks {
    private PocketClicks() {
        // 纯权限查询供原版菜单、附属拖放与网络入口共用。
    }

    public static boolean blocked(Slot slot) {
        // 方块库存、合成输入及装备规则继续由原版 Slot 负责。
        return slot.container instanceof Inventory && PocketSlots.unavailable(slot.getContainerSlot());
    }

    public static boolean blocked(AbstractContainerMenu handler, int slot) {
        // 原版的负数槽号保留丢弃及拖动阶段语义，越界包交由原版拒绝。
        return slot >= 0 && slot < handler.slots.size() && blocked(handler.getSlot(slot));
    }
}
