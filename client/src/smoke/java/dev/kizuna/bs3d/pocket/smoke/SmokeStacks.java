package dev.kizuna.bs3d.pocket.smoke;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import dev.kizuna.bs3d.vanilla.client.VanillaClient;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** 数量核对只读服务端同步到客户端的库存、随身容器与地面实体，不依赖界面文字。 */
final class SmokeStacks {
    private static final int GROUND_RADIUS = 16;

    private SmokeStacks() {
        // 静态只读查询。
    }

    static int count(Inventory inventory, Item item, int from, int to) {
        int total = 0;
        for (int slot = from; slot < to; slot++) {
            var stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static int empty(Inventory inventory, int from, int to) {
        int total = 0;
        for (int slot = from; slot < to; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                total++;
            }
        }
        return total;
    }

    static int firstEmpty(Inventory inventory, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        throw new IllegalStateException("口袋行没有空位");
    }

    static boolean removedEmpty(Inventory inventory) {
        // 被移除的 18–35 号槽在任何阶段都不能持有物品。
        int removed = PocketSlots.VANILLA_MAIN_SLOTS - PocketSlots.AVAILABLE_SLOTS;
        return empty(inventory, PocketSlots.AVAILABLE_SLOTS, PocketSlots.VANILLA_MAIN_SLOTS) == removed;
    }

    static int carried(Item item) {
        // 随身容器内容来自 bs3d 权威快照。
        var volume = VanillaClient.instance().volume();
        int total = 0;
        for (var entry : volume.state().getItemsList()) {
            var stack = volume.stack(entry.getId());
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static Map<Item, Integer> inventory(Inventory inventory) {
        // 主背包、盔甲与副手全部计入，死亡掉落按物品种类核对。
        Map<Item, Integer> totals = new HashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            add(totals, inventory.getItem(slot));
        }
        return totals;
    }

    static Map<Item, Integer> ground(Minecraft client) {
        // 平台范围内全部高度的掉落物；夹具平台足以接住整理与死亡时随机抛出的物品。
        var world = client.level;
        var area = new AABB(-GROUND_RADIUS, world.getMinBuildHeight(), -GROUND_RADIUS,
                GROUND_RADIUS, world.getMaxBuildHeight(), GROUND_RADIUS);
        Map<Item, Integer> totals = new HashMap<>();
        for (ItemEntity entity : world.getEntitiesOfClass(ItemEntity.class, area, entity -> true)) {
            add(totals, entity.getItem());
        }
        return totals;
    }

    static Map<Item, Integer> sum(Map<Item, Integer> first, Map<Item, Integer> second) {
        Map<Item, Integer> totals = new HashMap<>(first);
        second.forEach((item, count) -> totals.merge(item, count, Integer::sum));
        return totals;
    }

    private static void add(Map<Item, Integer> totals, ItemStack stack) {
        if (!stack.isEmpty()) {
            totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
    }
}
