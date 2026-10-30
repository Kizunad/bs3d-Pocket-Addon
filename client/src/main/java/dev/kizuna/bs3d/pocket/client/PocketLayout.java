package dev.kizuna.bs3d.pocket.client;

import dev.kizuna.backpack3d.client.InteriorRenderer.Rect;
import dev.kizuna.bs3d.vanilla.client.StationLayout;

/** GUI 逻辑像素中的紧凑 E 页：人物与内景左右并列，口袋行与快捷栏居中贴底。 */
public record PocketLayout(StationLayout scenes, Rect menu, Rect crafting, Rect recipeButton) {
    private static final int BUTTON_WIDTH_PIXELS = 20;
    private static final int BUTTON_HEIGHT_PIXELS = 18;
    private static final int BUTTON_GAP_PIXELS = 4;
    private static final int MIN_SCENE_WIDTH_PIXELS = 112;
    private static final int MIN_SCENE_HEIGHT_PIXELS = 144;
    private static final int SCENE_COUNT = 2;
    // 左右外边距加两个场景之间的间隔。
    private static final int SCENE_MARGINS = SCENE_COUNT + 1;

    public static PocketLayout calculate(int width, int height) {
        // 底部菜单保持原版格子尺寸，其上方剩余高度由两个场景平分宽度使用。
        int margin = StationLayout.MARGIN_PIXELS;
        int menuLeft = (width - PocketPage.WIDTH_PIXELS) / 2;
        int menuTop = height - PocketPage.COMPACT_HEIGHT_PIXELS - margin;
        var menu = new Rect(menuLeft, menuTop, PocketPage.WIDTH_PIXELS, PocketPage.COMPACT_HEIGHT_PIXELS);
        int sceneTop = StationLayout.HEADER_PIXELS;
        int sceneWidth = Math.max(0, (width - margin * SCENE_MARGINS) / SCENE_COUNT);
        int sceneHeight = Math.max(0, menuTop - margin - sceneTop);
        var player = new Rect(margin, sceneTop, sceneWidth, sceneHeight);
        var interior = new Rect(width - margin - sceneWidth, sceneTop, sceneWidth, sceneHeight);
        boolean visible = sceneWidth >= MIN_SCENE_WIDTH_PIXELS && sceneHeight >= MIN_SCENE_HEIGHT_PIXELS;
        var scenes = new StationLayout(player, interior, visible);
        return new PocketLayout(scenes, menu, crafting(interior), recipeButton(menu));
    }

    private static Rect crafting(Rect interior) {
        // 2×2 合成居中放在内景区域，展开容器内景时整块让位。
        int width = PocketPage.CRAFTING_WIDTH_PIXELS;
        int height = PocketPage.CRAFTING_HEIGHT_PIXELS;
        int left = interior.x() + (interior.width() - width) / 2;
        int top = interior.y() + (interior.height() - height) / 2;
        return new Rect(left, top, width, height);
    }

    private static Rect recipeButton(Rect menu) {
        // 配方书按钮贴在底部菜单左侧，不压住人物场景与快捷栏。
        int left = menu.x() - BUTTON_GAP_PIXELS - BUTTON_WIDTH_PIXELS;
        int top = menu.y() + (menu.height() - BUTTON_HEIGHT_PIXELS) / 2;
        return new Rect(left, top, BUTTON_WIDTH_PIXELS, BUTTON_HEIGHT_PIXELS);
    }

    public boolean inside(double x, double y, boolean craftingShown) {
        // 合成面板在原版背景之外，点击其中的槽位不能被原版当作丢出光标物品。
        if (menu.contains(x, y)) {
            return true;
        }
        return craftingShown && crafting.contains(x, y);
    }
}
