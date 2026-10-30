package dev.kizuna.bs3d.pocket.smoke;

import dev.kizuna.backpack3d.client.InteriorRenderer.Rect;
import dev.kizuna.bs3d.pocket.api.PocketSlots;
import dev.kizuna.bs3d.pocket.client.PocketCreative;
import dev.kizuna.bs3d.pocket.client.PocketLayout;
import dev.kizuna.bs3d.vanilla.client.PlayerInventoryRows;
import dev.kizuna.bs3d.vanilla.client.PlayerInventoryView;
import dev.kizuna.bs3d.vanilla.client.StationHost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;

/** 页面几何断言读取原版槽位的实际坐标与可见性，与绘制、悬停和点击共用同一判断。 */
final class PageChecks {
    private static final int SLOT_PIXELS = 16;
    private static final int HOTBAR_GAP_PIXELS = 4;
    private static final int CREATIVE_POCKET_Y = 6;
    private static final int CREATIVE_HOTBAR_Y =
            CREATIVE_POCKET_Y + PlayerInventoryView.SLOT_PITCH_PIXELS + HOTBAR_GAP_PIXELS;
    private static final int DELETE_SLOTS = 1;
    private static final int HIDDEN_ROW = -1;

    private PageChecks() {
        // 只读断言，不修改页面或库存。
    }

    static boolean shown(HandledScreen<?> screen, Slot slot) {
        return PlayerInventoryView.visible(screen, slot);
    }

    static void compactPage(HandledScreen<?> screen) {
        // 左模型、右合成、下方口袋行加快捷栏；盔甲、副手与被移除两行不可见。
        var layout = PocketLayout.calculate(screen.width, screen.height);
        var host = (StationHost) screen;
        SmokeInput.require(layout.scenes().visible(), "E 页没有进入紧凑布局");
        SmokeInput.require(host.stationLeft() == layout.menu().x(), "菜单原点没有移到底部");
        SmokeInput.require(host.stationTop() == layout.menu().y(), "菜单原点没有移到底部");
        SmokeInput.require(layout.scenes().player().right() <= layout.crafting().x(), "合成面板没有位于人物右侧");
        var inventory = MinecraftClient.getInstance().player.getInventory();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory != inventory) {
                requireInside(screen, slot, layout.crafting(), "合成格没有显示在右侧面板");
            } else if (slot.getIndex() < PocketSlots.AVAILABLE_SLOTS) {
                requireInside(screen, slot, layout.menu(), "口袋行或快捷栏没有显示在底部");
            } else {
                SmokeInput.require(!shown(screen, slot), "被移除的主背包、盔甲或副手仍显示为二维槽位");
            }
        }
        requireHotbarBelow(screen, inventory);
    }

    static void chestPage(HandledScreen<?> screen) {
        // 已适配工作站的玩家区只剩口袋行，快捷栏紧贴其下。
        SmokeInput.require(PlayerInventoryRows.visibleRows() == PocketSlots.VISIBLE_MAIN_ROWS, "玩家区行数没有登记");
        var inventory = MinecraftClient.getInstance().player.getInventory();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory != inventory || slot.getIndex() >= PocketSlots.VANILLA_MAIN_SLOTS) {
                continue;
            }
            boolean pocket = slot.getIndex() < PocketSlots.AVAILABLE_SLOTS;
            SmokeInput.require(shown(screen, slot) == pocket, "箱子页面玩家区不是两行");
        }
        requireHotbarBelow(screen, inventory);
    }

    static void creativePage(HandledScreen<?> screen, int backgroundHeight) {
        // 三维人物与内景位于创造窗口两侧，窗口只剩口袋行、快捷栏与删除格；盔甲、副手与被移除两行不可见。
        var host = (StationHost) screen;
        var layout = host.stationLayout();
        SmokeInput.require(SmokeInput.panel(screen).enabled(), "创造物品栏页签没有启用三维人物");
        SmokeInput.require(layout.player().right() <= host.stationLeft(), "人物场景遮住了创造窗口");
        SmokeInput.require(layout.interior().x() >= host.stationLeft() + host.stationWidth(), "内景遮住了创造窗口");
        SmokeInput.require(backgroundHeight == PocketCreative.COMPACT_HEIGHT_PIXELS, "创造物品栏页签高度错误");
        int hotbarRow = 0;
        int pocketRow = 0;
        for (Slot slot : screen.getScreenHandler().slots) {
            int row = creativeRow(host.stationMenuSlot(slot));
            if (row == HIDDEN_ROW) {
                SmokeInput.require(!shown(screen, slot), "创造物品栏仍显示盔甲、副手、合成或被移除的主背包");
                continue;
            }
            SmokeInput.require(shown(screen, slot), "创造物品栏口袋行、快捷栏或删除格不可见");
            SmokeInput.require(slot.y == row, "创造物品栏口袋行、快捷栏或删除格位置错误");
            if (row == CREATIVE_HOTBAR_Y) {
                hotbarRow++;
            } else {
                pocketRow++;
            }
        }
        SmokeInput.require(hotbarRow == PocketSlots.HOTBAR_SLOTS + DELETE_SLOTS, "创造物品栏快捷栏或删除格不完整");
        SmokeInput.require(pocketRow == PocketSlots.VISIBLE_MAIN_SLOTS, "创造物品栏口袋行不完整");
    }

    static Slot creativeSlot(HandledScreen<?> screen, int inventorySlot) {
        // 创造页的页面槽包着玩家菜单的真实槽，按真实槽的库存槽号查找。
        var host = (StationHost) screen;
        var inventory = MinecraftClient.getInstance().player.getInventory();
        for (Slot slot : screen.getScreenHandler().slots) {
            Slot menuSlot = host.stationMenuSlot(slot);
            if (menuSlot == null || menuSlot.inventory != inventory) {
                continue;
            }
            if (menuSlot.getIndex() == inventorySlot) {
                return slot;
            }
        }
        throw new IllegalStateException("创造物品栏没有库存槽 " + inventorySlot);
    }

    private static int creativeRow(Slot menuSlot) {
        // 删除格没有对应真实槽，与快捷栏同一行；口袋行与快捷栏之外的真实槽都不应显示。
        if (menuSlot == null) {
            return CREATIVE_HOTBAR_Y;
        }
        if (menuSlot.inventory != MinecraftClient.getInstance().player.getInventory()) {
            return HIDDEN_ROW;
        }
        if (menuSlot.getIndex() < PocketSlots.HOTBAR_SLOTS) {
            return CREATIVE_HOTBAR_Y;
        }
        if (menuSlot.getIndex() < PocketSlots.AVAILABLE_SLOTS) {
            return CREATIVE_POCKET_Y;
        }
        return HIDDEN_ROW;
    }

    private static void requireInside(HandledScreen<?> screen, Slot slot, Rect area, String reason) {
        // 槽位整格都要落在所属面板内，并且对绘制与命中可见。
        int[] center = SmokeInput.slotPoint(screen, slot);
        int half = SLOT_PIXELS / 2;
        SmokeInput.require(shown(screen, slot), reason);
        SmokeInput.require(area.contains(center[0] - half, center[1] - half), reason);
        SmokeInput.require(area.contains(center[0] + half - 1, center[1] + half - 1), reason);
    }

    private static void requireHotbarBelow(HandledScreen<?> screen, PlayerInventory inventory) {
        // 快捷栏位于唯一一行口袋下方，中间保留原版四像素间隔。
        int pocketY = slotY(screen, inventory, PocketSlots.HOTBAR_SLOTS);
        int hotbarY = slotY(screen, inventory, 0);
        int expected = pocketY + PlayerInventoryView.SLOT_PITCH_PIXELS + HOTBAR_GAP_PIXELS;
        SmokeInput.require(hotbarY == expected, "快捷栏没有紧贴口袋行");
    }

    private static int slotY(HandledScreen<?> screen, PlayerInventory inventory, int index) {
        return screen.getScreenHandler().slots.stream()
                .filter(slot -> slot.inventory == inventory && slot.getIndex() == index)
                .findFirst()
                .orElseThrow()
                .y;
    }
}
