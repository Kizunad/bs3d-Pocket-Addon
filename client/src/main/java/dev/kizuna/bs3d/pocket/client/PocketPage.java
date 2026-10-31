package dev.kizuna.bs3d.pocket.client;

import dev.kizuna.backpack3d.client.InteriorRenderer.Rect;
import dev.kizuna.bs3d.pocket.api.PocketSlots;
import dev.kizuna.bs3d.pocket.client.TextureSlices.Position;
import dev.kizuna.bs3d.pocket.client.TextureSlices.Slice;
import dev.kizuna.bs3d.vanilla.client.PlayerInventoryRows;
import dev.kizuna.bs3d.vanilla.client.PlayerInventoryView;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/** 原版玩家菜单的槽对象保持不变，只按纹理切片平移坐标并裁接原版背景。 */
public final class PocketPage {
    public static final int WIDTH_PIXELS = 176;
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");
    private static final int TEXTURE_HEIGHT_PIXELS = 166;
    private static final int TOP_BORDER_PIXELS = 7;
    private static final int MAIN_FRAME_Y = 83;
    private static final int VISIBLE_MAIN_PIXELS =
            PocketSlots.VISIBLE_MAIN_ROWS * PlayerInventoryView.SLOT_PITCH_PIXELS;
    private static final int HOTBAR_SECTION_Y =
            MAIN_FRAME_Y + PlayerInventoryRows.VANILLA_ROWS * PlayerInventoryView.SLOT_PITCH_PIXELS;
    private static final int HOTBAR_SECTION_PIXELS = TEXTURE_HEIGHT_PIXELS - HOTBAR_SECTION_Y;
    public static final int COMPACT_HEIGHT_PIXELS = TOP_BORDER_PIXELS + VISIBLE_MAIN_PIXELS + HOTBAR_SECTION_PIXELS;
    public static final int FALLBACK_HEIGHT_PIXELS = MAIN_FRAME_Y + VISIBLE_MAIN_PIXELS + HOTBAR_SECTION_PIXELS;
    private static final TextureSlices COMPACT = new TextureSlices(WIDTH_PIXELS, List.of(
            new Slice(0, TOP_BORDER_PIXELS, 0),
            new Slice(MAIN_FRAME_Y, VISIBLE_MAIN_PIXELS, TOP_BORDER_PIXELS),
            new Slice(HOTBAR_SECTION_Y, HOTBAR_SECTION_PIXELS, TOP_BORDER_PIXELS + VISIBLE_MAIN_PIXELS)));
    private static final TextureSlices FALLBACK = new TextureSlices(WIDTH_PIXELS, List.of(
            new Slice(0, MAIN_FRAME_Y + VISIBLE_MAIN_PIXELS, 0),
            new Slice(HOTBAR_SECTION_Y, HOTBAR_SECTION_PIXELS, MAIN_FRAME_Y + VISIBLE_MAIN_PIXELS)));
    private static final int CRAFTING_BLOCK_U = 97;
    private static final int CRAFTING_BLOCK_V = 17;
    private static final int CRAFTING_BLOCK_WIDTH_PIXELS = 74;
    private static final int CRAFTING_BLOCK_HEIGHT_PIXELS = 36;
    private static final int PANEL_PADDING_PIXELS = 7;
    public static final int CRAFTING_WIDTH_PIXELS = PANEL_PADDING_PIXELS * 2 + CRAFTING_BLOCK_WIDTH_PIXELS;
    public static final int CRAFTING_HEIGHT_PIXELS =
            CRAFTING_BLOCK_V + CRAFTING_BLOCK_HEIGHT_PIXELS + PANEL_PADDING_PIXELS;
    private static final int CRAFTING_ORIGIN_X = CRAFTING_BLOCK_U - PANEL_PADDING_PIXELS;
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0x404040;
    private static final int WINDOW_CORNER_PIXELS = 4;
    private static final int WINDOW_RIGHT_U = WIDTH_PIXELS - WINDOW_CORNER_PIXELS;
    private static final int WINDOW_BOTTOM_V = TEXTURE_HEIGHT_PIXELS - WINDOW_CORNER_PIXELS;
    private static final int WINDOW_COLOR = 0xFFC6C6C6;

    private PocketPage() {
        // 库存、合成结果与装备校验仍由原版 AbstractContainerMenu 持有。
    }

    public static void arrangeCompact(InventoryMenu handler, PocketLayout layout) {
        // 口袋行与快捷栏随纹理切片平移，合成格随合成面板整体平移；装备槽由人物模型代替。
        Rect menu = layout.menu();
        Rect crafting = layout.crafting();
        int craftingX = crafting.x() - CRAFTING_ORIGIN_X - menu.x();
        int craftingY = crafting.y() - menu.y();
        for (Slot slot : handler.slots) {
            if (slot.container instanceof Inventory) {
                COMPACT.arrange(slot);
            } else {
                Position vanilla = TextureSlices.vanilla(slot);
                TextureSlices.move(slot, vanilla.x() + craftingX, vanilla.y() + craftingY);
            }
        }
    }

    public static void arrangeFallback(InventoryMenu handler) {
        // 退回布局保留原版上半页，只把快捷栏接到可见口袋行下方。
        for (Slot slot : handler.slots) {
            FALLBACK.arrange(slot);
        }
    }

    public static boolean compactVisible(Slot slot, boolean interiorOpen) {
        // 盔甲与副手位于主背包之后，改由人物模型展示；合成格随合成面板在内景展开时让位。
        if (slot.container instanceof Inventory) {
            return slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
        }
        return !interiorOpen;
    }

    public static void drawCompact(GuiGraphics context, PocketLayout layout, Component title, boolean interiorOpen) {
        // 底部只拼接口袋行与快捷栏；合成面板沿用原版窗口边框、合成格纹理和标题位置。
        Rect menu = layout.menu();
        COMPACT.draw(context, TEXTURE, menu.x(), menu.y());
        if (interiorOpen) {
            return;
        }
        Rect area = layout.crafting();
        drawWindow(context, area);
        int blockLeft = area.x() - CRAFTING_ORIGIN_X + CRAFTING_BLOCK_U;
        int top = area.y();
        context.blit(TEXTURE, blockLeft, top + CRAFTING_BLOCK_V,
                CRAFTING_BLOCK_U, CRAFTING_BLOCK_V, CRAFTING_BLOCK_WIDTH_PIXELS, CRAFTING_BLOCK_HEIGHT_PIXELS);
        var font = Minecraft.getInstance().font;
        context.drawString(font, title, blockLeft, top + TITLE_Y, TITLE_COLOR, false /* shadow */);
    }

    public static void drawFallback(GuiGraphics context, ResourceLocation texture, int left, int top) {
        // 二维人物和盔甲区保持原样，被移除的两行主背包不再绘制。
        FALLBACK.draw(context, texture, left, top);
    }

    private static void drawWindow(GuiGraphics context, Rect area) {
        // 四角取原版窗口纹理，边缘沿原版边框延伸，中间填原版底色。
        int corner = WINDOW_CORNER_PIXELS;
        int left = area.x();
        int top = area.y();
        int innerLeft = left + corner;
        int innerTop = top + corner;
        int right = area.right() - corner;
        int bottom = area.bottom() - corner;
        int innerWidth = area.width() - corner * 2;
        int innerHeight = area.height() - corner * 2;
        context.fill(innerLeft, innerTop, right, bottom, WINDOW_COLOR);
        context.blit(TEXTURE, left, top, 0, 0, corner, corner);
        context.blit(TEXTURE, innerLeft, top, corner, 0, innerWidth, corner);
        context.blit(TEXTURE, right, top, WINDOW_RIGHT_U, 0, corner, corner);
        context.blit(TEXTURE, left, innerTop, 0, corner, corner, innerHeight);
        context.blit(TEXTURE, right, innerTop, WINDOW_RIGHT_U, corner, corner, innerHeight);
        context.blit(TEXTURE, left, bottom, 0, WINDOW_BOTTOM_V, corner, corner);
        context.blit(TEXTURE, innerLeft, bottom, corner, WINDOW_BOTTOM_V, innerWidth, corner);
        context.blit(TEXTURE, right, bottom, WINDOW_RIGHT_U, WINDOW_BOTTOM_V, corner, corner);
    }
}
