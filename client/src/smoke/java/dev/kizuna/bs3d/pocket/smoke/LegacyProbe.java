package dev.kizuna.bs3d.pocket.smoke;

import dev.kizuna.bs3d.pocket.api.PocketSlots;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.TranslatableTextContent;
import java.util.Map;

/** 旧存档整理与死亡掉落：数量以口袋、随身容器和地面实体合计核对，任何一件都不能消失。 */
final class LegacyProbe {
    private static final int SETTLE_TICKS = 40;
    private static final int LEGACY_TOTAL = PocketFixture.LEGACY_STACKS * PocketFixture.FULL_STACK;
    private static final int POCKET_TOTAL = PocketFixture.LEGACY_ROOM * PocketFixture.FULL_STACK;
    private static final int LAST_DEATH_STEP = 3;
    private static final String RESPAWN_KEY = "deathScreen.respawn";
    private final Evidence evidence = new Evidence();
    private boolean armed;
    private int ticks;
    private int deathStep;
    private int carriedBeforeDeath;
    private Map<Item, Integer> beforeDeath;

    boolean arm(MinecraftClient client) {
        // 口袋只留三个空格，确认同步后再下线，让夹具在保存前写入旧槽位。
        if (!armed) {
            client.getNetworkHandler().sendChatCommand("pocket-fixture legacy");
            armed = true;
            return false;
        }
        var inventory = client.player.getInventory();
        return SmokeStacks.empty(inventory, 0, PocketSlots.AVAILABLE_SLOTS) == PocketFixture.LEGACY_ROOM;
    }

    boolean migrated(MinecraftClient client, int overlays) {
        // 重登后鹅卵石依次进入口袋、随身容器、脚边，合计不变且提示只出现一次。
        if (client.currentScreen != null) {
            // 地形加载页关闭前实体可能已同步，等回到游戏画面再截图核对。
            ticks = 0;
            return false;
        }
        var inventory = client.player.getInventory();
        int pocket = SmokeStacks.count(inventory, Items.COBBLESTONE, 0, PocketSlots.AVAILABLE_SLOTS);
        int carried = SmokeStacks.carried(Items.COBBLESTONE);
        int ground = SmokeStacks.ground(client).getOrDefault(Items.COBBLESTONE, 0);
        if (pocket + carried + ground != LEGACY_TOTAL || overlays == 0) {
            ticks = 0;
            return false;
        }
        if (ticks++ == 0) {
            // 动作栏提示只停留数秒，数量首次对齐时立即截图。
            SmokeInput.screenshot(client, "pocket-legacy.png");
        }
        if (ticks < SETTLE_TICKS) {
            return false;
        }
        SmokeInput.require(SmokeStacks.removedEmpty(inventory), "旧槽位物品没有迁出");
        SmokeInput.require(pocket == POCKET_TOTAL, "口袋空位没有先被填满");
        SmokeInput.require(carried > 0, "随身容器没有接收口袋放不下的物品");
        SmokeInput.require(ground > 0, "随身容器放不下的物品没有掉在脚边");
        SmokeInput.require(overlays == 1, "迁移提示没有恰好出现一次");
        return true;
    }

    boolean death(MinecraftClient client) {
        // 返回 true 表示死亡掉落与重生检查完成。
        boolean done = switch (deathStep) {
            case 0 -> kill(client);
            case 1 -> dropped(client);
            case 2 -> respawn(client);
            case 3 -> respawned(client);
            default -> throw new IllegalStateException("未知死亡验收步骤");
        };
        if (done) {
            deathStep++;
            ticks = 0;
        }
        return deathStep > LAST_DEATH_STEP;
    }

    private boolean kill(MinecraftClient client) {
        // 记录库存与地面合计；随身容器按 bs3d 规则死亡保留，不计入掉落。
        var inventory = SmokeStacks.inventory(client.player.getInventory());
        beforeDeath = SmokeStacks.sum(inventory, SmokeStacks.ground(client));
        carriedBeforeDeath = SmokeStacks.carried(Items.COBBLESTONE);
        client.getNetworkHandler().sendChatCommand("kill");
        return true;
    }

    private boolean dropped(MinecraftClient client) {
        // 死亡页出现后，地面每种物品的数量都要等于死亡前库存加地面。
        if (!(client.currentScreen instanceof DeathScreen)) {
            return false;
        }
        if (!SmokeStacks.ground(client).equals(beforeDeath)) {
            return false;
        }
        return evidence.captured(client, "pocket-death.png");
    }

    private static boolean respawn(MinecraftClient client) {
        // 等原版重生按钮启用后点击它。
        if (!(client.currentScreen instanceof DeathScreen screen)) {
            throw new IllegalStateException("死亡页提前关闭");
        }
        var button = screen.children().stream()
                .filter(ButtonWidget.class::isInstance)
                .map(ButtonWidget.class::cast)
                .filter(LegacyProbe::respawnButton)
                .findFirst()
                .orElseThrow();
        if (!button.active) {
            return false;
        }
        int x = button.getX() + button.getWidth() / 2;
        int y = button.getY() + button.getHeight() / 2;
        SmokeInput.click(screen, new int[] {x, y});
        return true;
    }

    private static boolean respawnButton(ButtonWidget button) {
        if (button.getMessage().getContent() instanceof TranslatableTextContent content) {
            return content.getKey().equals(RESPAWN_KEY);
        }
        return false;
    }

    private boolean respawned(MinecraftClient client) {
        // 重生后旧槽仍为空，随身草包与其中的鹅卵石原样保留。
        if (!client.player.isAlive() || client.currentScreen instanceof DeathScreen) {
            return false;
        }
        if (SmokeStacks.carried(Items.COBBLESTONE) != carriedBeforeDeath) {
            return false;
        }
        SmokeInput.require(SmokeStacks.removedEmpty(client.player.getInventory()), "重生后旧槽位持有物品");
        return true;
    }
}
