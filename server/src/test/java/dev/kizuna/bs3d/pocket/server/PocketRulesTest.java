package dev.kizuna.bs3d.pocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;

/** 在真实 Inventory 与 InventoryMenu 上验证口袋容量；Mixin 由 fabric-loader-junit 加载。 */
class PocketRulesTest {
    private static final int FULL_STACK = 64;
    private static final int LEGACY_SLOT = PocketSlots.AVAILABLE_SLOTS;
    private static final int LAST_LEGACY_SLOT = PocketSlots.VANILLA_MAIN_SLOTS - 1;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void insertStackFillsOnlyHotbarAndPocketRow() throws ReflectiveOperationException {
        // 连续拾取整组物品：前 18 格依次填满，第 19 组原样留在调用方手里。
        var player = TestPlayer.create();
        var inventory = player.getInventory();
        for (int slot = 0; slot < PocketSlots.AVAILABLE_SLOTS; slot++) {
            assertTrue(inventory.add(new ItemStack(Items.STONE, FULL_STACK)));
        }
        var overflow = new ItemStack(Items.STONE, FULL_STACK);
        assertFalse(inventory.add(overflow));
        assertEquals(FULL_STACK, overflow.getCount());
        var damaged = new ItemStack(Items.IRON_SWORD);
        damaged.setDamageValue(1);
        assertFalse(inventory.add(damaged));
        assertFalse(damaged.isEmpty());
        assertEquals(-1, inventory.getFreeSlot());
        assertFalse(inventory.add(LEGACY_SLOT, new ItemStack(Items.STONE)));
        assertLegacyEmpty(inventory);
    }

    @Test
    void fullPocketDropsOfferedItemsAndKeepsOffhandMerge() throws ReflectiveOperationException {
        // /give 与合成格归还走原版 placeItemBackInInventory：口袋满时掉落；副手同类物品仍按原版优先合并。
        var player = TestPlayer.create();
        var inventory = player.getInventory();
        inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.STONE, 10));
        fillAvailable(inventory, Items.DIRT);
        var stone = new ItemStack(Items.STONE, 20);
        assertTrue(inventory.add(stone));
        assertEquals(30, inventory.getItem(Inventory.SLOT_OFFHAND).getCount());
        inventory.placeItemBackInInventory(new ItemStack(Items.COBBLESTONE, 5));
        assertEquals(1, player.dropped.size());
        assertEquals(5, player.dropped.get(0).getCount());
        assertLegacyEmpty(inventory);
    }

    @Test
    void forgedClicksOnRemovedSlotsChangeNothing() throws ReflectiveOperationException {
        // 玩家菜单 9–35 号与库存槽号一致；伪造的拿取、Shift、数字键、丢出与复制点击都被入口拒绝。
        var player = TestPlayer.create();
        var handler = player.inventoryMenu;
        var inventory = player.getInventory();
        assertEquals(LEGACY_SLOT, handler.getSlot(LEGACY_SLOT).getContainerSlot());
        inventory.setItem(LEGACY_SLOT, new ItemStack(Items.DIAMOND, 3));
        var actions = List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP,
                ClickType.THROW, ClickType.CLONE, ClickType.PICKUP_ALL);
        for (ClickType action : actions) {
            handler.clicked(LEGACY_SLOT, 0, action, player);
            assertEquals(3, inventory.getItem(LEGACY_SLOT).getCount());
            assertTrue(handler.getCarried().isEmpty());
        }
        handler.setCarried(new ItemStack(Items.EMERALD, 5));
        handler.clicked(LAST_LEGACY_SLOT, 0, ClickType.PICKUP, player);
        assertTrue(inventory.getItem(LAST_LEGACY_SLOT).isEmpty());
        assertEquals(5, handler.getCarried().getCount());
        assertTrue(PocketClicks.blocked(handler, LEGACY_SLOT));
        assertFalse(PocketClicks.blocked(handler, InventoryMenu.INV_SLOT_START));
        assertFalse(PocketClicks.blocked(handler, InventoryMenu.SHIELD_SLOT));
        assertFalse(PocketClicks.blocked(handler, AbstractContainerMenu.SLOT_CLICKED_OUTSIDE));
    }

    @Test
    void dragDistributionSkipsRemovedSlots() throws ReflectiveOperationException {
        // 左键拖动分配经过 18 号槽时该槽不加入分配，物品平分到其余两个口袋格。
        var player = TestPlayer.create();
        var handler = player.inventoryMenu;
        var inventory = player.getInventory();
        int first = InventoryMenu.INV_SLOT_START;
        int second = first + 1;
        handler.setCarried(new ItemStack(Items.EMERALD, 10));
        handler.clicked(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, AbstractContainerMenu.getQuickcraftMask(0, 0),
                ClickType.QUICK_CRAFT, player);
        for (int slot : List.of(first, LEGACY_SLOT, second)) {
            handler.clicked(slot, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
        }
        handler.clicked(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, AbstractContainerMenu.getQuickcraftMask(2, 0),
                ClickType.QUICK_CRAFT, player);
        assertEquals(5, inventory.getItem(first).getCount());
        assertEquals(5, inventory.getItem(second).getCount());
        assertTrue(handler.getCarried().isEmpty());
        assertLegacyEmpty(inventory);
    }

    @Test
    void shiftClickMovesOnlyIntoPocketRow() throws ReflectiveOperationException {
        // 快捷栏 Shift 的原版目标是 9–35，现只能进入 9–17；口袋行满时物品留在快捷栏。
        var player = TestPlayer.create();
        var handler = player.inventoryMenu;
        var inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.STONE, 5));
        handler.clicked(InventoryMenu.USE_ROW_SLOT_START, 0, ClickType.QUICK_MOVE, player);
        assertEquals(5, inventory.getItem(InventoryMenu.INV_SLOT_START).getCount());
        assertTrue(inventory.getItem(0).isEmpty());
        for (int slot = PocketSlots.HOTBAR_SLOTS; slot < PocketSlots.AVAILABLE_SLOTS; slot++) {
            inventory.setItem(slot, new ItemStack(Items.DIRT, FULL_STACK));
        }
        inventory.setItem(1, new ItemStack(Items.GOLD_INGOT, 7));
        handler.clicked(InventoryMenu.USE_ROW_SLOT_START + 1, 0, ClickType.QUICK_MOVE, player);
        assertEquals(7, inventory.getItem(1).getCount());
        assertLegacyEmpty(inventory);
    }

    @Test
    void migrationReturnsLegacyItemsWithoutDropping() throws ReflectiveOperationException {
        // 旧存档 18–35 号的物品回到空出的口袋格并合并；全部放下时不提示也不掉落。
        var player = TestPlayer.create();
        var inventory = player.getInventory();
        inventory.setItem(LEGACY_SLOT, new ItemStack(Items.DIAMOND, 3));
        inventory.setItem(LAST_LEGACY_SLOT, new ItemStack(Items.DIAMOND, 4));
        PocketMigration.clean(player);
        assertEquals(7, inventory.getItem(0).getCount());
        assertLegacyEmpty(inventory);
        assertTrue(player.dropped.isEmpty());
        assertTrue(player.overlays.isEmpty());
    }

    private static void fillAvailable(Inventory inventory, Item item) {
        for (int slot = 0; slot < PocketSlots.AVAILABLE_SLOTS; slot++) {
            inventory.setItem(slot, new ItemStack(item, FULL_STACK));
        }
    }

    private static void assertLegacyEmpty(Inventory inventory) {
        for (int slot = LEGACY_SLOT; slot < PocketSlots.VANILLA_MAIN_SLOTS; slot++) {
            assertTrue(inventory.getItem(slot).isEmpty(), "旧槽 " + slot + " 应为空");
        }
    }
}
