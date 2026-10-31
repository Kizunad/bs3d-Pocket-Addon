package dev.kizuna.bs3d.pocket.server;

import dev.kizuna.backpack3d.server.InventoryAccess;
import dev.kizuna.bs3d.pocket.api.PocketSlots;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** 从旧存档或外部写入收回不可用槽位，最终剩余物品进入真实世界。 */
public final class PocketMigration {
    public static final String OVERFLOW_KEY = "bs3d_pocket.overflow";
    private static final Logger LOGGER = LoggerFactory.getLogger("bs3d_pocket");

    private PocketMigration() {
        // 整理由服务器主线程执行，不保存跨 tick 的物品副本。
    }

    public static void clean(ServerPlayer player) {
        // 死亡掉落由原版完成；旁观者无法拾取脚边物品，切回其他模式后再整理。
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        var inventory = player.getInventory();
        int moved = 0;
        int dropped = 0;
        for (int slot = PocketSlots.AVAILABLE_SLOTS; slot < PocketSlots.VANILLA_MAIN_SLOTS; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                continue;
            }
            ItemStack stack = inventory.removeItemNoUpdate(slot);
            moved += stack.getCount();
            dropped += relocate(player, stack);
        }
        if (moved == 0) {
            return;
        }
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        if (dropped > 0) {
            player.displayClientMessage(Component.translatable(OVERFLOW_KEY), true /* overlay */);
        }
        LOGGER.info("整理玩家 {} 的旧背包槽：迁出 {} 件，掉落 {} 件", player.getStringUUID(), moved, dropped);
    }

    private static int relocate(ServerPlayer player, ItemStack stack) {
        // 依次放入口袋、随身容器，剩余数量掉在脚边并返回。
        var inventory = player.getInventory();
        if (hasRoom(inventory, stack)) {
            inventory.add(stack);
        }
        if (stack.isEmpty() || storeCarried(player, stack)) {
            return 0;
        }
        int dropped = stack.getCount();
        player.drop(stack, true /* throwRandomly */, false /* retainOwnership */);
        return dropped;
    }

    private static boolean hasRoom(Inventory inventory, ItemStack stack) {
        // 原版创造模式在一件都放不下时会清空物品，先确认存在空间再插入。
        return inventory.getSlotWithRemainingSpace(stack) >= 0 || inventory.getFreeSlot() >= 0;
    }

    private static boolean storeCarried(ServerPlayer player, ItemStack stack) {
        // 复用 BS3D 容量与嵌套验证；磁盘失败时物品仍在手中，随后安全掉落。
        try {
            return InventoryAccess.tryStoreCarried(player, stack);
        } catch (IOException failure) {
            LOGGER.error("口袋溢出物品无法存入随身容器", failure);
            return false;
        }
    }
}
