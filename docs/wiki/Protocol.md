# 规则与接入点

本附属不注册网络通道，也不定义 Protobuf 消息。槽位权限建立在原版协议和 Vanilla-Addon 既有事务上，由服务器执行；客户端只调整显示。

## 服务器规则

被移除的槽位以**实际库存槽号**判定：`slot.container` 为 `Inventory` 且槽号位于 18–35。不同菜单的槽位编号不同，不能把菜单槽号当作库存槽号。

| 入口 | 处理 |
| --- | --- |
| `Inventory.getFreeSlot` / `getSlotWithRemainingSpace` / `findSlotMatchingItem` / `findSlotMatchingCraftingIngredient` | 只扫描 0–17，选中格和副手的原版合并优先级不变 |
| `Inventory.add(int, ItemStack)` | 指定 18–35 视为放不下，调用者保留原栈 |
| `AbstractContainerMenu.clicked`（`ServerboundContainerClickPacket`） | 点击指向被移除槽位时整次拒绝，光标和槽位都不变 |
| `AbstractContainerMenu.moveItemStackTo`（Shift 搬运、Vanilla-Addon 自动取回） | 合并阶段与空格阶段都跳过被移除槽位 |
| `Slot.mayPlace` / `Slot.mayPickup` | 被移除槽位一律返回 false，覆盖 BS3D 与第三方的直接调用 |
| `ServerboundSetCreativeModeSlotPacket` | 指向被移除槽位时拒绝，并整页重发玩家菜单以撤销客户端预测 |

Vanilla-Addon 的拖放事务（`bs3d_vanilla:action`）最终都走原版点击或 `moveItemStackTo`，因此同样受上述规则约束；事务失败时按 Vanilla-Addon 的规则恢复整份库存。

旧槽整理调用 BS3D `InventoryAccess.tryStoreCarried(ServerPlayer, ItemStack)`：整堆放入随身容器或不放，失败（包括存档写入异常）时物品留在手中，随后掉在脚边。

## 只读常量

`dev.kizuna.bs3d.pocket.api.PocketSlots`：

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `HOTBAR_SLOTS` | 9 | 快捷栏格数 |
| `VISIBLE_MAIN_SLOTS` | 9 | 贴身口袋格数，即可见主背包格数 |
| `VISIBLE_MAIN_ROWS` | 1 | 可见主背包行数 |
| `AVAILABLE_SLOTS` | 18 | 0–17 可用 |
| `VANILLA_MAIN_SLOTS` | 36 | 原版主背包结构，存档不变 |

`PocketSlots.unavailable(int inventorySlot)` 判断库存槽号是否属于被移除的 18–35；盔甲与副手不计入。Weights 等附属可以读取这些常量；没有运行时配置。

## Vanilla-Addon 接入点

- `PlayerInventoryRows.setVisibleRows(int)`：玩家主背包可见行数（1–3）。本附属在客户端初始化时设为 1；Vanilla-Addon 已适配的页面据此隐藏多余行，并把快捷栏接到最后一行下方。
- `StationHost.stationLayout()`：页面可提供自己的场景区域。紧凑 E 页把人物放在左上、内景放在右上。
- `StationHost.stationSlotVisible(Slot)`：页面决定哪些原版槽位参与绘制、悬停和点击。紧凑 E 页隐藏装备槽，内景展开时隐藏合成格；创造「物品栏」页签隐藏盔甲、副手和合成格。
- `StationHost.stationMenuSlot(Slot)`：把页面槽换成服务器菜单里的真实槽。创造页的页面槽都是包装槽，本附属据此按真实库存槽号排版和隐藏；删除格没有真实槽，保持原版。
- 创造模式「物品栏」页签的三维场景由 Vanilla-Addon 接入，本附属只排版窗口：场景启用时裁成口袋行、快捷栏和删除格，否则退回原版上半页。
- `SlotPosition`：只改槽位坐标，不替换带装备与绑定诅咒检查的原版槽对象。

## 存档

存档仍是原版 36 格主背包，附属不写额外字段。卸载后 18–35 号槽恢复可用；安装期间整理到随身容器或地上的物品不会自动搬回。
