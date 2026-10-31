package dev.kizuna.bs3d.pocket.smoke;

import dev.kizuna.backpack3d.client.WornPreview;
import dev.kizuna.bs3d.vanilla.client.StationHost;
import dev.kizuna.bs3d.vanilla.client.VanillaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 玩家 E 页的四条真实输入链路：内景到快捷栏、快捷栏到手、模型盔甲到口袋、2×2 产物到口袋。 */
final class EPageProbe {
    private static final int SETTLE_TICKS = 20;
    private static final int LAST_STEP = 9;
    private static final int LOG_SLOT = 2;
    private static final int HELMET_TARGET = InventoryMenu.INV_SLOT_START;
    private static final int PLANKS_TARGET = InventoryMenu.INV_SLOT_START + 1;
    private static final int PLANKS_PER_LOG = 4;
    private static final int CLOSE_BUTTON_OFFSET = 8;
    private static final float MODEL_SEARCH_DEGREES = 15;
    private final String bag;
    private final Evidence evidence = new Evidence();
    private int step;
    private int ticks;
    private long logsRevision;

    EPageProbe(String bag) {
        this.bag = bag;
    }

    boolean tick(Minecraft client) {
        // 每步先等服务端权威库存，再发出下一次真实输入；返回 true 表示全部链路完成。
        ticks++;
        if (step == 0) {
            advance(open(client));
            return false;
        }
        var screen = ready(client);
        if (screen == null) {
            return false;
        }
        advance(input(client, screen));
        return step > LAST_STEP;
    }

    private boolean input(Minecraft client, AbstractContainerScreen<?> screen) {
        return switch (step) {
            case 1 -> inspect(client, screen);
            case 2 -> openBag(screen);
            case 3 -> takeLogs(screen);
            case 4 -> toHand(client, screen);
            case 5 -> takeHelmet(client, screen);
            case 6 -> closeBag(client, screen);
            case 7 -> fillGrid(screen);
            case 8 -> craft(screen);
            case 9 -> finish(client, screen);
            default -> throw new IllegalStateException("未知 E 页验收步骤");
        };
    }

    private void advance(boolean done) {
        if (done) {
            step++;
            ticks = 0;
        }
    }

    private static boolean open(Minecraft client) {
        // 无页面时按物品栏键，由客户端 tick 按原版流程打开 E 页。
        if (client.screen != null) {
            return false;
        }
        SmokeInput.pressKey(client.options.keyInventory);
        return true;
    }

    private static AbstractContainerScreen<?> ready(Minecraft client) {
        // 只在 E 页、附属状态就绪且没有等待中的转移时推进。
        if (!(client.screen instanceof InventoryScreen screen)) {
            return null;
        }
        var addon = VanillaClient.instance();
        if (!addon.ready(screen) || addon.busy()) {
            return null;
        }
        return screen;
    }

    private boolean inspect(Minecraft client, AbstractContainerScreen<?> screen) {
        // 左模型、右合成、下两行的几何先断言，再截图留证。
        if (ticks < SETTLE_TICKS || !SmokeInput.panel(screen).enabled()) {
            return false;
        }
        PageChecks.compactPage(screen);
        return evidence.captured(client, "pocket-e-page.png");
    }

    private boolean openBag(AbstractContainerScreen<?> screen) {
        // 点击人物身上的草包模型打开内景；背面时旋转人物继续寻找。
        int[] point = SmokeInput.modelPoint(screen, bag);
        if (point == null) {
            SmokeInput.read(SmokeInput.panel(screen), "player", WornPreview.class).rotate(MODEL_SEARCH_DEGREES, 0);
            return false;
        }
        SmokeInput.click(screen, point);
        return true;
    }

    private boolean takeLogs(AbstractContainerScreen<?> screen) {
        // 链路一：内景原木拖到快捷栏第三格。
        int[] item = SmokeInput.itemPoint(screen, bag);
        if (item == null) {
            return false;
        }
        var target = screen.getMenu().getSlot(InventoryMenu.USE_ROW_SLOT_START + LOG_SLOT);
        logsRevision = VanillaClient.instance().volume().state().getRevision();
        SmokeInput.drag(screen, item, SmokeInput.slotPoint(screen, target));
        return true;
    }

    private boolean toHand(Minecraft client, AbstractContainerScreen<?> screen) {
        // 链路二：快捷栏铁锭拖到人物模型的副手。
        var inventory = client.player.getInventory();
        if (!holds(inventory.getItem(LOG_SLOT), Items.OAK_LOG, PocketFixture.LOG_COUNT)) {
            return false;
        }
        // 原版槽位与背包快照分别同步；修订号推进前发出的请求会因旧修订号被服务端拒绝。
        if (VanillaClient.instance().volume().state().getRevision() == logsRevision) {
            return false;
        }
        if (!evidence.captured(client, "pocket-interior-to-hotbar.png")) {
            return false;
        }
        int[] hand = SmokeInput.bodyPoint(screen, Inventory.SLOT_OFFHAND);
        SmokeInput.require(hand != null, "人物模型没有可命中的副手");
        var source = screen.getMenu().getSlot(InventoryMenu.USE_ROW_SLOT_START + PocketFixture.INGOT_SLOT);
        SmokeInput.drag(screen, SmokeInput.slotPoint(screen, source), hand);
        return true;
    }

    private boolean takeHelmet(Minecraft client, AbstractContainerScreen<?> screen) {
        // 链路三：从模型头部取下头盔，放进口袋行第一格。
        LocalPlayer player = client.player;
        if (!holds(player.getOffhandItem(), Items.IRON_INGOT, PocketFixture.INGOT_COUNT)) {
            return false;
        }
        SmokeInput.require(player.getInventory().getItem(PocketFixture.INGOT_SLOT).isEmpty(), "快捷栏来源未清空");
        if (!evidence.captured(client, "pocket-hotbar-to-hand.png")) {
            return false;
        }
        int[] head = SmokeInput.bodyPoint(screen, PocketFixture.HEAD_SLOT);
        SmokeInput.require(head != null, "人物模型没有可命中的头盔");
        var target = screen.getMenu().getSlot(HELMET_TARGET);
        SmokeInput.drag(screen, head, SmokeInput.slotPoint(screen, target));
        return true;
    }

    private boolean closeBag(Minecraft client, AbstractContainerScreen<?> screen) {
        // 头盔进入口袋后关闭内景，合成面板回到右侧。
        var inventory = client.player.getInventory();
        if (!holds(inventory.getItem(HELMET_TARGET), Items.IRON_HELMET, 1)) {
            return false;
        }
        SmokeInput.require(inventory.getItem(PocketFixture.HEAD_SLOT).isEmpty(), "头盔仍在头部");
        if (!evidence.captured(client, "pocket-armor-to-pocket.png")) {
            return false;
        }
        var area = ((StationHost) screen).stationLayout().interior();
        SmokeInput.click(screen, new int[] {area.right() - CLOSE_BUTTON_OFFSET, area.y() + CLOSE_BUTTON_OFFSET});
        return true;
    }

    private boolean fillGrid(AbstractContainerScreen<?> screen) {
        // 内景关闭后合成格重新可见，原木从快捷栏拖进 2×2 第一格。
        if (SmokeInput.panel(screen).interiorOpen()) {
            return false;
        }
        var handler = screen.getMenu();
        var input = handler.getSlot(InventoryMenu.CRAFT_SLOT_START);
        SmokeInput.require(PageChecks.shown(screen, input), "内景关闭后合成格没有出现");
        var source = handler.getSlot(InventoryMenu.USE_ROW_SLOT_START + LOG_SLOT);
        SmokeInput.drag(screen, SmokeInput.slotPoint(screen, source), SmokeInput.slotPoint(screen, input));
        return true;
    }

    private boolean craft(AbstractContainerScreen<?> screen) {
        // 链路四：服务端算出木板后，把产物拖进口袋行第二格。
        var handler = screen.getMenu();
        if (!holds(handler.getSlot(InventoryMenu.CRAFT_SLOT_START).getItem(),
                Items.OAK_LOG, PocketFixture.LOG_COUNT)) {
            return false;
        }
        var result = handler.getSlot(InventoryMenu.RESULT_SLOT);
        if (!holds(result.getItem(), Items.OAK_PLANKS, PLANKS_PER_LOG)) {
            return false;
        }
        var target = handler.getSlot(PLANKS_TARGET);
        SmokeInput.drag(screen, SmokeInput.slotPoint(screen, result), SmokeInput.slotPoint(screen, target));
        return true;
    }

    private boolean finish(Minecraft client, AbstractContainerScreen<?> screen) {
        // 产物到达且只扣一块原木后截图，按物品栏键关闭页面。
        if (!holds(client.player.getInventory().getItem(PLANKS_TARGET), Items.OAK_PLANKS, PLANKS_PER_LOG)) {
            return false;
        }
        var input = screen.getMenu().getSlot(InventoryMenu.CRAFT_SLOT_START).getItem();
        SmokeInput.require(input.getCount() == PocketFixture.LOG_COUNT - 1, "合成没有正确扣料");
        if (!evidence.captured(client, "pocket-craft-to-pocket.png")) {
            return false;
        }
        SmokeInput.closeWithKey(screen, client.options.keyInventory);
        return true;
    }

    static boolean holds(ItemStack stack, Item item, int count) {
        return stack.is(item) && stack.getCount() == count;
    }
}
