package dev.kizuna.bs3d.pocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;

/** 在真实 PlayerInventory 与 PlayerScreenHandler 上验证口袋容量；Mixin 由 fabric-loader-junit 加载。 */
class PocketRulesTest {
    private static final int FULL_STACK = 64;
    private static final int LEGACY_SLOT = PocketSlots.AVAILABLE_SLOTS;
    private static final int LAST_LEGACY_SLOT = PocketSlots.VANILLA_MAIN_SLOTS - 1;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void insertStackFillsOnlyHotbarAndPocketRow() throws ReflectiveOperationException {
        // 连续拾取整组物品：前 18 格依次填满，第 19 组原样留在调用方手里。
        var player = TestPlayer.create();
        var inventory = player.getInventory();
        for (int slot = 0; slot < PocketSlots.AVAILABLE_SLOTS; slot++) {
            assertTrue(inventory.insertStack(new ItemStack(Items.STONE, FULL_STACK)));
        }
        var overflow = new ItemStack(Items.STONE, FULL_STACK);
        assertFalse(inventory.insertStack(overflow));
        assertEquals(FULL_STACK, overflow.getCount());
        var damaged = new ItemStack(Items.IRON_SWORD);
        damaged.setDamage(1);
        assertFalse(inventory.insertStack(damaged));
        assertFalse(damaged.isEmpty());
        assertEquals(-1, inventory.getEmptySlot());
        assertFalse(inventory.insertStack(LEGACY_SLOT, new ItemStack(Items.STONE)));
        assertLegacyEmpty(inventory);
    }

    @Test
    void fullPocketDropsOfferedItemsAndKeepsOffhandMerge() throws ReflectiveOperationException {
        // /give 与合成格归还走原版 offerOrDrop：口袋满时掉落；副手同类物品仍按原版优先合并。
        var player = TestPlayer.create();
        var inventory = player.getInventory();
        inventory.setStack(PlayerInventory.OFF_HAND_SLOT, new ItemStack(Items.STONE, 10));
        fillAvailable(inventory, Items.DIRT);
        var stone = new ItemStack(Items.STONE, 20);
        assertTrue(inventory.insertStack(stone));
        assertEquals(30, inventory.getStack(PlayerInventory.OFF_HAND_SLOT).getCount());
        inventory.offerOrDrop(new ItemStack(Items.COBBLESTONE, 5));
        assertEquals(1, player.dropped.size());
        assertEquals(5, player.dropped.get(0).getCount());
        assertLegacyEmpty(inventory);
    }

    @Test
    void forgedClicksOnRemovedSlotsChangeNothing() throws ReflectiveOperationException {
        // 玩家菜单 9–35 号与库存槽号一致；伪造的拿取、Shift、数字键、丢出与复制点击都被入口拒绝。
        var player = TestPlayer.create();
        var handler = player.playerScreenHandler;
        var inventory = player.getInventory();
        assertEquals(LEGACY_SLOT, handler.getSlot(LEGACY_SLOT).getIndex());
        inventory.setStack(LEGACY_SLOT, new ItemStack(Items.DIAMOND, 3));
        var actions = List.of(SlotActionType.PICKUP, SlotActionType.QUICK_MOVE, SlotActionType.SWAP,
                SlotActionType.THROW, SlotActionType.CLONE, SlotActionType.PICKUP_ALL);
        for (SlotActionType action : actions) {
            handler.onSlotClick(LEGACY_SLOT, 0, action, player);
            assertEquals(3, inventory.getStack(LEGACY_SLOT).getCount());
            assertTrue(handler.getCursorStack().isEmpty());
        }
        handler.setCursorStack(new ItemStack(Items.EMERALD, 5));
        handler.onSlotClick(LAST_LEGACY_SLOT, 0, SlotActionType.PICKUP, player);
        assertTrue(inventory.getStack(LAST_LEGACY_SLOT).isEmpty());
        assertEquals(5, handler.getCursorStack().getCount());
        assertTrue(PocketClicks.blocked(handler, LEGACY_SLOT));
        assertFalse(PocketClicks.blocked(handler, PlayerScreenHandler.INVENTORY_START));
        assertFalse(PocketClicks.blocked(handler, PlayerScreenHandler.OFFHAND_ID));
        assertFalse(PocketClicks.blocked(handler, ScreenHandler.EMPTY_SPACE_SLOT_INDEX));
    }

    @Test
    void dragDistributionSkipsRemovedSlots() throws ReflectiveOperationException {
        // 左键拖动分配经过 18 号槽时该槽不加入分配，物品平分到其余两个口袋格。
        var player = TestPlayer.create();
        var handler = player.playerScreenHandler;
        var inventory = player.getInventory();
        int first = PlayerScreenHandler.INVENTORY_START;
        int second = first + 1;
        handler.setCursorStack(new ItemStack(Items.EMERALD, 10));
        handler.onSlotClick(ScreenHandler.EMPTY_SPACE_SLOT_INDEX, ScreenHandler.packQuickCraftData(0, 0),
                SlotActionType.QUICK_CRAFT, player);
        for (int slot : List.of(first, LEGACY_SLOT, second)) {
            handler.onSlotClick(slot, ScreenHandler.packQuickCraftData(1, 0), SlotActionType.QUICK_CRAFT, player);
        }
        handler.onSlotClick(ScreenHandler.EMPTY_SPACE_SLOT_INDEX, ScreenHandler.packQuickCraftData(2, 0),
                SlotActionType.QUICK_CRAFT, player);
        assertEquals(5, inventory.getStack(first).getCount());
        assertEquals(5, inventory.getStack(second).getCount());
        assertTrue(handler.getCursorStack().isEmpty());
        assertLegacyEmpty(inventory);
    }

    @Test
    void shiftClickMovesOnlyIntoPocketRow() throws ReflectiveOperationException {
        // 快捷栏 Shift 的原版目标是 9–35，现只能进入 9–17；口袋行满时物品留在快捷栏。
        var player = TestPlayer.create();
        var handler = player.playerScreenHandler;
        var inventory = player.getInventory();
        inventory.setStack(0, new ItemStack(Items.STONE, 5));
        handler.onSlotClick(PlayerScreenHandler.HOTBAR_START, 0, SlotActionType.QUICK_MOVE, player);
        assertEquals(5, inventory.getStack(PlayerScreenHandler.INVENTORY_START).getCount());
        assertTrue(inventory.getStack(0).isEmpty());
        for (int slot = PocketSlots.HOTBAR_SLOTS; slot < PocketSlots.AVAILABLE_SLOTS; slot++) {
            inventory.setStack(slot, new ItemStack(Items.DIRT, FULL_STACK));
        }
        inventory.setStack(1, new ItemStack(Items.GOLD_INGOT, 7));
        handler.onSlotClick(PlayerScreenHandler.HOTBAR_START + 1, 0, SlotActionType.QUICK_MOVE, player);
        assertEquals(7, inventory.getStack(1).getCount());
        assertLegacyEmpty(inventory);
    }

    @Test
    void migrationReturnsLegacyItemsWithoutDropping() throws ReflectiveOperationException {
        // 旧存档 18–35 号的物品回到空出的口袋格并合并；全部放下时不提示也不掉落。
        var player = TestPlayer.create();
        var inventory = player.getInventory();
        inventory.setStack(LEGACY_SLOT, new ItemStack(Items.DIAMOND, 3));
        inventory.setStack(LAST_LEGACY_SLOT, new ItemStack(Items.DIAMOND, 4));
        PocketMigration.clean(player);
        assertEquals(7, inventory.getStack(0).getCount());
        assertLegacyEmpty(inventory);
        assertTrue(player.dropped.isEmpty());
        assertTrue(player.overlays.isEmpty());
    }

    private static void fillAvailable(PlayerInventory inventory, Item item) {
        for (int slot = 0; slot < PocketSlots.AVAILABLE_SLOTS; slot++) {
            inventory.setStack(slot, new ItemStack(item, FULL_STACK));
        }
    }

    private static void assertLegacyEmpty(PlayerInventory inventory) {
        for (int slot = LEGACY_SLOT; slot < PocketSlots.VANILLA_MAIN_SLOTS; slot++) {
            assertTrue(inventory.getStack(slot).isEmpty(), "旧槽 " + slot + " 应为空");
        }
    }
}
