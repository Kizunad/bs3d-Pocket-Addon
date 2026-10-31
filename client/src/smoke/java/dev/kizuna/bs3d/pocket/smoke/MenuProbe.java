package dev.kizuna.bs3d.pocket.smoke;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import dev.kizuna.bs3d.vanilla.client.StationHost;
import dev.kizuna.bs3d.vanilla.client.VanillaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** 箱子页两行与 Shift 搬运；创造物品栏页签的三维人物穿脱、本地光标穿戴与伪造创造写入，都以服务端回写为准。 */
final class MenuProbe {
    private static final int SETTLE_TICKS = 20;
    private static final int LAST_STEP = 13;
    private static final int HELMET_SOURCE = InventoryMenu.INV_SLOT_START;
    private static final int CHEST_SLOT = 0;
    private static final int FORGED_SLOT = PocketSlots.AVAILABLE_SLOTS + PocketSlots.HOTBAR_SLOTS;
    private static final int TAB_CENTER_X = 13;
    private static final int TAB_CENTER_Y = 16;
    private final Evidence evidence = new Evidence();
    private int step;
    private int ticks;
    private int control;
    private int source = HELMET_SOURCE;
    private int pocket;

    boolean tick(Minecraft client) {
        // 返回 true 表示两类页面都已验收并回到生存模式。
        ticks++;
        boolean done = switch (step) {
            case 0 -> openChest(client);
            case 1 -> chest(client);
            case 2 -> chestMoved(client);
            case 3 -> openCreative(client);
            case 4 -> selectTab(client);
            case 5 -> creative(client);
            case 6 -> worn(client);
            case 7 -> takenOff(client);
            case 8 -> carried(client);
            case 9 -> worn(client);
            case 10 -> returned(client);
            case 11 -> forged(client);
            case 12 -> closeCreative(client);
            case 13 -> client.gameMode.getPlayerMode() == GameType.SURVIVAL;
            default -> throw new IllegalStateException("未知页面验收步骤");
        };
        if (done) {
            step++;
            ticks = 0;
        }
        return step > LAST_STEP;
    }

    private static boolean openChest(Minecraft client) {
        // 26.2 起原版包按 tick 处理、聊天命令直接进主线程，E 页的关闭包可能晚于命令，把新开的箱子页关掉。
        // 服务端处理关闭时才把 2×2 里剩下的原木退回背包，看到原木回来再发命令。
        if (client.gui.screen() != null) {
            return false;
        }
        var inventory = client.player.getInventory();
        if (SmokeStacks.count(inventory, Items.OAK_LOG, 0, PocketSlots.VANILLA_MAIN_SLOTS) != PocketFixture.LOG_COUNT - 1) {
            return false;
        }
        SmokeInput.require(SmokeStacks.removedEmpty(inventory), "合成格退回的原木进入了被移除的槽位");
        return command(client, "pocket-fixture chest");
    }

    private boolean chest(Minecraft client) {
        // 真实箱子页面先断言玩家区两行，再用原版 Shift 点击把石头搬进玩家库存。
        if (!(client.gui.screen() instanceof ContainerScreen screen)) {
            return false;
        }
        if (!VanillaClient.instance().ready(screen) || ticks < SETTLE_TICKS) {
            return false;
        }
        var handler = screen.getMenu();
        SmokeInput.require(handler.getSlot(CHEST_SLOT).getItem().is(Items.STONE), "箱子夹具没有石头");
        PageChecks.chestPage(screen);
        if (!evidence.captured(client, "pocket-chest.png")) {
            return false;
        }
        client.gameMode.handleContainerInput(handler.containerId, CHEST_SLOT, 0, ContainerInput.QUICK_MOVE, client.player);
        return true;
    }

    private boolean chestMoved(Minecraft client) {
        // 等服务端回写稳定后检查：石头只能落在口袋行或快捷栏。
        if (!(client.gui.screen() instanceof ContainerScreen screen)) {
            return false;
        }
        if (!screen.getMenu().getSlot(CHEST_SLOT).getItem().isEmpty() || ticks < SETTLE_TICKS) {
            return false;
        }
        var inventory = client.player.getInventory();
        int stone = SmokeStacks.count(inventory, Items.STONE, 0, PocketSlots.AVAILABLE_SLOTS);
        SmokeInput.require(stone == PocketFixture.STONE_COUNT, "Shift 搬运没有进入口袋行或快捷栏");
        SmokeInput.require(SmokeStacks.removedEmpty(inventory), "Shift 搬运进入了被移除的槽位");
        SmokeInput.closeWithKey(screen, client.options.keyInventory);
        return command(client, "gamemode creative");
    }

    private static boolean openCreative(Minecraft client) {
        // 创造模式生效且页面已关闭后按物品栏键，由原版打开创造物品栏。
        if (client.gameMode.getPlayerMode() != GameType.CREATIVE || client.gui.screen() != null) {
            return false;
        }
        SmokeInput.pressKey(client.options.keyInventory);
        return true;
    }

    private boolean selectTab(Minecraft client) {
        // 点击物品栏页签图标中心，走原版页签切换。
        if (!(client.gui.screen() instanceof CreativeModeInventoryScreen screen) || ticks < SETTLE_TICKS) {
            return false;
        }
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.stream()
                .filter(group -> group.getType() == CreativeModeTab.Type.INVENTORY)
                .findFirst()
                .orElseThrow();
        var host = (StationHost) screen;
        int x = host.stationLeft() + SmokeInput.invokeInt(screen, "getTabX", CreativeModeTab.class, tab) + TAB_CENTER_X;
        int y = host.stationTop() + SmokeInput.invokeInt(screen, "getTabY", CreativeModeTab.class, tab) + TAB_CENTER_Y;
        SmokeInput.click(screen, new int[] {x, y});
        return true;
    }

    private boolean creative(Minecraft client) {
        // 三维人物启用后断言紧凑布局，再把 E 页放进口袋行的头盔拖到人物头部。
        var screen = creativeScreen(client);
        if (screen == null || ticks < SETTLE_TICKS) {
            return false;
        }
        if (!SmokeInput.panel(screen).enabled()) {
            return false;
        }
        PageChecks.creativePage(screen, SmokeInput.read(screen, "imageHeight", Integer.class));
        var source = PageChecks.creativeSlot(screen, HELMET_SOURCE);
        SmokeInput.require(source.getItem().is(Items.IRON_HELMET), "口袋行没有 E 页取下的头盔");
        int[] head = SmokeInput.bodyPoint(screen, PocketFixture.HEAD_SLOT);
        SmokeInput.require(head != null, "创造页人物模型没有可命中的头部");
        SmokeInput.drag(screen, SmokeInput.slotPoint(screen, source), head);
        return true;
    }

    private boolean worn(Minecraft client) {
        // 头盔穿上、光标为空且来源清空后，用事务从人物头部取下，放进口袋行第一个空格；
        // 本地光标穿戴之后再取下一次，事务成功即说明服务端已收到原版创造写入。
        var screen = creativeScreen(client);
        var inventory = client.player.getInventory();
        if (screen == null || !EPageProbe.holds(inventory.getItem(PocketFixture.HEAD_SLOT), Items.IRON_HELMET, 1)) {
            return false;
        }
        if (!screen.getMenu().getCarried().isEmpty() || ticks < SETTLE_TICKS) {
            return false;
        }
        SmokeInput.require(inventory.getItem(source).isEmpty(), "口袋行来源未清空");
        pocket = SmokeStacks.firstEmpty(inventory, PocketSlots.HOTBAR_SLOTS, PocketSlots.AVAILABLE_SLOTS);
        int[] head = SmokeInput.bodyPoint(screen, PocketFixture.HEAD_SLOT);
        SmokeInput.require(head != null, "创造页人物模型没有可命中的头盔");
        var target = PageChecks.creativeSlot(screen, pocket);
        SmokeInput.drag(screen, head, SmokeInput.slotPoint(screen, target));
        return true;
    }

    private boolean takenOff(Minecraft client) {
        // 头盔回到口袋格后截图，再原地点一下口袋格，由原版创造页把头盔拿到本地光标上。
        var screen = creativeScreen(client);
        var inventory = client.player.getInventory();
        if (screen == null || !EPageProbe.holds(inventory.getItem(pocket), Items.IRON_HELMET, 1)) {
            return false;
        }
        SmokeInput.require(inventory.getItem(PocketFixture.HEAD_SLOT).isEmpty(), "头盔仍在头部");
        if (!evidence.captured(client, "pocket-creative.png")) {
            return false;
        }
        source = pocket;
        SmokeInput.click(screen, SmokeInput.slotPoint(screen, PageChecks.creativeSlot(screen, source)));
        return true;
    }

    private boolean carried(Minecraft client) {
        // 本地光标拿着头盔时点人物头部，走原版创造写入穿上。
        var screen = creativeScreen(client);
        if (screen == null || !EPageProbe.holds(screen.getMenu().getCarried(), Items.IRON_HELMET, 1)) {
            return false;
        }
        SmokeInput.require(client.player.getInventory().getItem(source).isEmpty(), "点击口袋格没有拿起头盔");
        int[] head = SmokeInput.bodyPoint(screen, PocketFixture.HEAD_SLOT);
        SmokeInput.require(head != null, "创造页人物模型没有可命中的头部");
        SmokeInput.click(screen, head);
        return true;
    }

    private boolean returned(Minecraft client) {
        // 头盔再次回到口袋格后，发送一个合法对照写入和伪造的旧槽写入。
        var screen = creativeScreen(client);
        var inventory = client.player.getInventory();
        if (screen == null || !EPageProbe.holds(inventory.getItem(pocket), Items.IRON_HELMET, 1)) {
            return false;
        }
        SmokeInput.require(inventory.getItem(PocketFixture.HEAD_SLOT).isEmpty(), "头盔仍在头部");
        control = SmokeStacks.firstEmpty(inventory, PocketSlots.HOTBAR_SLOTS, PocketSlots.AVAILABLE_SLOTS);
        sendThenForge(client, new ItemStack(Items.DIAMOND));
        return true;
    }

    private static CreativeModeInventoryScreen creativeScreen(Minecraft client) {
        // 只在物品栏页签、附属状态就绪且没有等待中的转移时推进。
        if (!(client.gui.screen() instanceof CreativeModeInventoryScreen screen)) {
            return null;
        }
        var addon = VanillaClient.instance();
        if (!screen.isInventoryOpen() || !addon.ready(screen)) {
            return null;
        }
        if (addon.busy() || addon.volume().busy()) {
            return null;
        }
        return screen;
    }

    private boolean forged(Minecraft client) {
        // 对照钻石随整页重发回到客户端，说明两个包都已处理；旧槽仍为空才算拒绝成功。
        var inventory = client.player.getInventory();
        if (!inventory.getItem(control).is(Items.DIAMOND) || ticks < SETTLE_TICKS) {
            return false;
        }
        SmokeInput.require(SmokeStacks.removedEmpty(inventory), "服务端接受了伪造的旧槽写入");
        sendThenForge(client, ItemStack.EMPTY);
        return true;
    }

    private boolean closeCreative(Minecraft client) {
        // 对照物品清除且再次整页重发后，按物品栏键关闭页面并回到生存模式。
        var inventory = client.player.getInventory();
        if (!inventory.getItem(control).isEmpty()) {
            return false;
        }
        SmokeInput.require(SmokeStacks.removedEmpty(inventory), "服务端接受了伪造的旧槽写入");
        if (!(client.gui.screen() instanceof CreativeModeInventoryScreen screen)) {
            throw new IllegalStateException("创造物品栏提前关闭");
        }
        SmokeInput.closeWithKey(screen, client.options.keyInventory);
        return command(client, "gamemode survival");
    }

    private void sendThenForge(Minecraft client, ItemStack stack) {
        // 26.2 服务端接受创造写入后把远端槽记为新值、不再回发（真实页面已在本地预测）。
        // 两个包在服务端同一队列按序处理：先写对照格，再写旧槽；旧槽被拒时附属整页重发玩家菜单，
        // 客户端看到对照格的新内容，即说明两个包都已处理。
        send(client, control, stack);
        send(client, FORGED_SLOT, new ItemStack(Items.DIAMOND));
    }

    private static void send(Minecraft client, int slot, ItemStack stack) {
        // 直接发送创造写入包，绕过客户端页面的槽位过滤，检验服务端权威。
        client.getConnection().send(new ServerboundSetCreativeModeSlotPacket(slot, stack));
    }

    private static boolean command(Minecraft client, String command) {
        client.getConnection().sendCommand(command);
        return true;
    }
}
