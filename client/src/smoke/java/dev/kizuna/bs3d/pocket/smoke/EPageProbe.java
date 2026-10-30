package dev.kizuna.bs3d.pocket.smoke;

import dev.kizuna.backpack3d.client.WornPreview;
import dev.kizuna.bs3d.vanilla.client.StationHost;
import dev.kizuna.bs3d.vanilla.client.VanillaClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;

/** 玩家 E 页的四条真实输入链路：内景到快捷栏、快捷栏到手、模型盔甲到口袋、2×2 产物到口袋。 */
final class EPageProbe {
    private static final int SETTLE_TICKS = 20;
    private static final int LAST_STEP = 9;
    private static final int LOG_SLOT = 2;
    private static final int HELMET_TARGET = PlayerScreenHandler.INVENTORY_START;
    private static final int PLANKS_TARGET = PlayerScreenHandler.INVENTORY_START + 1;
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

    boolean tick(MinecraftClient client) {
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

    private boolean input(MinecraftClient client, HandledScreen<?> screen) {
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

    private static boolean open(MinecraftClient client) {
        // 无页面时按物品栏键，由客户端 tick 按原版流程打开 E 页。
        if (client.currentScreen != null) {
            return false;
        }
        SmokeInput.pressKey(client.options.inventoryKey);
        return true;
    }

    private static HandledScreen<?> ready(MinecraftClient client) {
        // 只在 E 页、附属状态就绪且没有等待中的转移时推进。
        if (!(client.currentScreen instanceof InventoryScreen screen)) {
            return null;
        }
        var addon = VanillaClient.instance();
        if (!addon.ready(screen) || addon.busy()) {
            return null;
        }
        return screen;
    }

    private boolean inspect(MinecraftClient client, HandledScreen<?> screen) {
        // 左模型、右合成、下两行的几何先断言，再截图留证。
        if (ticks < SETTLE_TICKS || !SmokeInput.panel(screen).enabled()) {
            return false;
        }
        PageChecks.compactPage(screen);
        return evidence.captured(client, "pocket-e-page.png");
    }

    private boolean openBag(HandledScreen<?> screen) {
        // 点击人物身上的草包模型打开内景；背面时旋转人物继续寻找。
        int[] point = SmokeInput.modelPoint(screen, bag);
        if (point == null) {
            SmokeInput.read(SmokeInput.panel(screen), "player", WornPreview.class).rotate(MODEL_SEARCH_DEGREES, 0);
            return false;
        }
        SmokeInput.click(screen, point);
        return true;
    }

    private boolean takeLogs(HandledScreen<?> screen) {
        // 链路一：内景原木拖到快捷栏第三格。
        int[] item = SmokeInput.itemPoint(screen, bag);
        if (item == null) {
            return false;
        }
        var target = screen.getScreenHandler().getSlot(PlayerScreenHandler.HOTBAR_START + LOG_SLOT);
        logsRevision = VanillaClient.instance().volume().state().getRevision();
        SmokeInput.drag(screen, item, SmokeInput.slotPoint(screen, target));
        return true;
    }

    private boolean toHand(MinecraftClient client, HandledScreen<?> screen) {
        // 链路二：快捷栏铁锭拖到人物模型的副手。
        var inventory = client.player.getInventory();
        if (!holds(inventory.getStack(LOG_SLOT), Items.OAK_LOG, PocketFixture.LOG_COUNT)) {
            return false;
        }
        // 原版槽位与背包快照分别同步；修订号推进前发出的请求会因旧修订号被服务端拒绝。
        if (VanillaClient.instance().volume().state().getRevision() == logsRevision) {
            return false;
        }
        if (!evidence.captured(client, "pocket-interior-to-hotbar.png")) {
            return false;
        }
        int[] hand = SmokeInput.bodyPoint(screen, PlayerInventory.OFF_HAND_SLOT);
        SmokeInput.require(hand != null, "人物模型没有可命中的副手");
        var source = screen.getScreenHandler().getSlot(PlayerScreenHandler.HOTBAR_START + PocketFixture.INGOT_SLOT);
        SmokeInput.drag(screen, SmokeInput.slotPoint(screen, source), hand);
        return true;
    }

    private boolean takeHelmet(MinecraftClient client, HandledScreen<?> screen) {
        // 链路三：从模型头部取下头盔，放进口袋行第一格。
        ClientPlayerEntity player = client.player;
        if (!holds(player.getOffHandStack(), Items.IRON_INGOT, PocketFixture.INGOT_COUNT)) {
            return false;
        }
        SmokeInput.require(player.getInventory().getStack(PocketFixture.INGOT_SLOT).isEmpty(), "快捷栏来源未清空");
        if (!evidence.captured(client, "pocket-hotbar-to-hand.png")) {
            return false;
        }
        int[] head = SmokeInput.bodyPoint(screen, PocketFixture.HEAD_SLOT);
        SmokeInput.require(head != null, "人物模型没有可命中的头盔");
        var target = screen.getScreenHandler().getSlot(HELMET_TARGET);
        SmokeInput.drag(screen, head, SmokeInput.slotPoint(screen, target));
        return true;
    }

    private boolean closeBag(MinecraftClient client, HandledScreen<?> screen) {
        // 头盔进入口袋后关闭内景，合成面板回到右侧。
        var inventory = client.player.getInventory();
        if (!holds(inventory.getStack(HELMET_TARGET), Items.IRON_HELMET, 1)) {
            return false;
        }
        SmokeInput.require(inventory.getStack(PocketFixture.HEAD_SLOT).isEmpty(), "头盔仍在头部");
        if (!evidence.captured(client, "pocket-armor-to-pocket.png")) {
            return false;
        }
        var area = ((StationHost) screen).stationLayout().interior();
        SmokeInput.click(screen, new int[] {area.right() - CLOSE_BUTTON_OFFSET, area.y() + CLOSE_BUTTON_OFFSET});
        return true;
    }

    private boolean fillGrid(HandledScreen<?> screen) {
        // 内景关闭后合成格重新可见，原木从快捷栏拖进 2×2 第一格。
        if (SmokeInput.panel(screen).interiorOpen()) {
            return false;
        }
        var handler = screen.getScreenHandler();
        var input = handler.getSlot(PlayerScreenHandler.CRAFTING_INPUT_START);
        SmokeInput.require(PageChecks.shown(screen, input), "内景关闭后合成格没有出现");
        var source = handler.getSlot(PlayerScreenHandler.HOTBAR_START + LOG_SLOT);
        SmokeInput.drag(screen, SmokeInput.slotPoint(screen, source), SmokeInput.slotPoint(screen, input));
        return true;
    }

    private boolean craft(HandledScreen<?> screen) {
        // 链路四：服务端算出木板后，把产物拖进口袋行第二格。
        var handler = screen.getScreenHandler();
        if (!holds(handler.getSlot(PlayerScreenHandler.CRAFTING_INPUT_START).getStack(),
                Items.OAK_LOG, PocketFixture.LOG_COUNT)) {
            return false;
        }
        var result = handler.getSlot(PlayerScreenHandler.CRAFTING_RESULT_ID);
        if (!holds(result.getStack(), Items.OAK_PLANKS, PLANKS_PER_LOG)) {
            return false;
        }
        var target = handler.getSlot(PLANKS_TARGET);
        SmokeInput.drag(screen, SmokeInput.slotPoint(screen, result), SmokeInput.slotPoint(screen, target));
        return true;
    }

    private boolean finish(MinecraftClient client, HandledScreen<?> screen) {
        // 产物到达且只扣一块原木后截图，按物品栏键关闭页面。
        if (!holds(client.player.getInventory().getStack(PLANKS_TARGET), Items.OAK_PLANKS, PLANKS_PER_LOG)) {
            return false;
        }
        var input = screen.getScreenHandler().getSlot(PlayerScreenHandler.CRAFTING_INPUT_START).getStack();
        SmokeInput.require(input.getCount() == PocketFixture.LOG_COUNT - 1, "合成没有正确扣料");
        if (!evidence.captured(client, "pocket-craft-to-pocket.png")) {
            return false;
        }
        SmokeInput.closeWithKey(screen, client.options.inventoryKey);
        return true;
    }

    static boolean holds(ItemStack stack, Item item, int count) {
        return stack.isOf(item) && stack.getCount() == count;
    }
}
