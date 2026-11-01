# 验证记录

## 2026-11-02：验收夹具改用灵草囊

BS3D 删除草包、背篓、收纳箱，验收夹具的随身容器由草包改为 BS3D 自带的 Bong 灵草囊（`bs3d:herb_pouch`，仍挂腰部右前），下文旧记录中的「草包」即当时的夹具容器。正式代码不变。

Java 25 `test --rerun build compileSmokeJava :client:compileSmokeJava` 通过：客户端 **6 项**、服务端 **6 项**，无失败或跳过，日志为 `build/tabclean-test-build.log`。真实 Fabric 专服与 Xvfb 客户端两种模式通过，`build/smoke/70f1e06a3352-dedicated/client/pocket-ok.txt` 与 `build/smoke/70f1e06a3352-integrated/client/pocket-ok.txt` 的覆盖项与 2026-11-01 记录逐字相同，日志为 `build/tabclean-smoke.log`。

## 2026-11-01：迁移到 Minecraft 26.2 / Java 25

运行环境改为 Minecraft 26.2、Fabric Loader 0.20.1、Fabric API 0.161.0+26.2、owo 0.13.1+26.2、Java 25，构建使用 Gradle 9.8.0、Fabric Loom 1.19.3。功能、槽位规则与存档格式不变，配套 Framework、BS3D 与 Vanilla-Addon 为同日迁移的 26.2 版本。

跟随 26.2 调整的接入点：

- 原版 `Inventory.findSlotMatchingUnusedItem` 改名为 `findSlotMatchingCraftingIngredient`，寻位限制随之改挂，四个寻位入口仍只扫描 0–17。
- 26.2 的界面先提取后绘制。E 页与创造页的排版改在 `extractBackground` 开头（每帧最先提取的部分），背景裁接改为包住其中的纹理绘制与二维人物绘制。
- 页面高度 `imageHeight` 改为 final，改由访问器写入。
- E 页的配方书按钮改由父类私有方法加入。附属在初始化之后从子控件中取出唯一的图片按钮；退回布局时，页面左边缘经 Vanilla-Addon 的配方书访问器计算。
- 点击窗口外的判断 `hasClickedOutside` 移到 `AbstractRecipeBookScreen` 声明，E 页改为覆写该方法。
- 26.2 的文字绘制会跳过 alpha 为 0 的颜色，紧凑 E 页标题色改为带不透明 alpha 的同一灰色。

Java 25 `test build :client:compileSmokeJava :server:compileSmokeJava` 通过：服务端 **6 项**、客户端 **6 项**，共 **12 项测试**，与迁移前相同，无失败或跳过。26.2 的物品默认组件在服务器加载数据时才绑定，服务端测试改为按原版内置注册表补绑一次。两端安装产物为 `jar` 任务输出，均不含 smoke 夹具或 refmap；专服 JAR 不含客户端或 owo 类。

真实 Fabric 专服与 Xvfb（llvmpipe 软件渲染）客户端联调两种模式均通过，覆盖项与迁移前相同，日志同为「迁出 1152 件，掉落 704 件」：

- `build/smoke/bf3333eaf73e-dedicated/client/pocket-ok.txt`
- `build/smoke/d1ef2667ba72-integrated/client/pocket-ok.txt`

箱子页玩家区只有口袋行加快捷栏，因此也覆盖了 Vanilla-Addon 可见行数小于 3 时的背景两段裁剪。

联调探针随 26.2 时序做了三处调整，产品代码未改：

- 26.2 每轮先 tick 再绘制，tick 内截图读到的是上一帧。探针改为记下帧号，等世界再绘制一帧后截图，并等文件写完再发下一次输入。
- 原版包在服务端按 tick 排队处理，聊天命令则直接进入主线程。E 页关闭后，先等服务端把合成格剩余的原木退回背包，再发开箱子的命令；否则晚到的关闭包会把新开的箱子页关掉。
- 服务端接受创造写入后不再回发该槽（真实页面已在本地预测）。伪造写入改为排在对照写入之后：伪造写入被拒时附属整页重发玩家菜单，客户端看到对照格变化，即说明两个包都已处理。清除对照物品时也按此确认。

九张截图与 2026-10-31 的 1.20.1 截图（`build/smoke/30f30cfc0d3c-dedicated/client/screenshots/`）对比，页面布局、人物、内景和槽位内容一致。差别有四处：

- 天空多了云层；
- 平台在 26.2 下有光照，1.20.1 截图中为全黑；
- 旧存档截图里，一块随机抛出的鹅卵石离镜头很近；
- 死亡页底部的快捷栏已清空，因为截图改在死亡后的下一帧。

README 截图仍为 1.20.1 版本，布局相同，未替换。未在 Windows 实例上验证。

## 2026-10-30：首个源码实现

当前为未发布源码，API 未冻结。配套 BS3D 业务协议 **v7**、Vanilla-Addon 业务协议 **v2**，二者为同日未提交的源码，已包含本附属依赖的接入点：BS3D 的 `InventoryAccess.tryStoreCarried`，Vanilla-Addon 的 `PlayerInventoryRows`、`StationHost.stationLayout`、`StationHost.stationSlotVisible` 与 `SlotPosition`。

Java 17 离线 `test build` 通过。服务端 **6 项**、客户端 **4 项**，共 **10 项测试**，无失败或跳过。配套 BS3D 的 **130 项**测试和 Vanilla-Addon 的 **5 项**测试也通过。

服务端测试使用真实的 `Inventory` 与 `InventoryMenu`：

- `Inventory.add` 只填 0–17，第 19 组物品原样留给调用者，指定 18 号槽插入失败。
- 口袋满时 `placeItemBackInInventory` 掉在脚边，副手同类物品仍按原版优先合并。
- 伪造的拿取、Shift、数字键、丢出、复制点击指向 18–35 时，槽位和光标都不变。
- 拖动分配跳过被移除槽位。
- 快捷栏 Shift 搬运只进口袋行，口袋行满时物品留在快捷栏。
- 旧槽物品能全部放回口袋时不掉落、不提示。

客户端测试覆盖紧凑 E 页的人物、合成和内景区域，配方书按钮位置，以及窗口过小时退回原版布局。

## 2026-10-30：创造页三维人物

创造模式「物品栏」页签改为与 E 页同一做法：左侧 Vanilla-Addon 的三维人物，窗口只留口袋行、快捷栏和删除格；窗口过小或库存尚未同步时退回原版上半页。Vanilla-Addon 为此新增 `StationHost.stationSupported`、`stationActive`、`stationMenuSlot`、`stationSharedCursor`、`stationClick` 五个接入点和创造页混入，业务协议仍为 **v2**。本附属只排版窗口，并按 `stationMenuSlot` 换出的真实槽决定哪些槽显示。

Java 17 离线 `test build :client:compileSmokeJava :server:compileSmokeJava` 通过：服务端 **6 项**、客户端 **6 项**，共 **12 项测试**，无失败或跳过。新增的 2 项检查紧凑与退回两种背景切片的高度和槽位位移。Vanilla-Addon 的 **5 项**测试也通过。

## 真实客户端联调

最终 BS3D、Vanilla-Addon 生产 JAR 与本附属一起，在真实 Fabric 专服和 Xvfb 单机下都通过联调：

- `build/smoke/1f136c58db92-dedicated/client/pocket-ok.txt`
- `build/smoke/1f136c58db92-integrated/client/pocket-ok.txt`

两种模式都依次验证了以下内容：

1. **E 页**：按 E 打开。人物在左，2×2 合成在右，底部只有口袋行和快捷栏，二维盔甲与副手槽不显示。随后在真实客户端依次完成并截图四条链路：
   - 身上草包的内景物品拖到快捷栏；
   - 快捷栏物品拖到模型的手上；
   - 头盔从模型上取下，放进口袋格；
   - 2×2 合成取出木板，放进口袋格。
2. **箱子**：玩家区为口袋行加快捷栏。原版 Shift 搬运落在 0–17，18–35 保持为空。
3. **创造物品栏页签**：三维场景启用，背景高 52 像素；人物与内景都在窗口两侧之外，口袋行与快捷栏各一行，删除格在快捷栏右侧，盔甲、副手与合成格不显示。随后依次验证：
   - 口袋行的头盔拖到模型头部，再从头部拖回口袋格，两次都走事务，然后截图；
   - 原地点一下口袋格，由原版创造页把头盔拿到本地光标上；再点模型头部，由原版创造写入穿上；随后用事务从头部取下放回口袋格，事务成功说明服务端确已写入；
   - 伪造的 27 号槽写入被拒绝；同一时刻写入口袋格的对照物品正常回显。
4. **旧存档**：夹具在断线保存前，把 18 组鹅卵石写进 18–35 号槽。重新登录后，口袋空位、随身草包和脚边三处共 1152 件，一件不少。口袋先被填满，动作栏提示恰好一次，日志恰好一行。
5. **死亡**：死亡后地面物品等于死亡前的库存加地面。随身草包及其内容按 BS3D 规则保留，重生后 18–35 仍为空。

两种模式的日志各记录一行：

```text
整理玩家 <UUID> 的旧背包槽：迁出 1152 件，掉落 704 件
```

截图保存在对应目录的 `client/screenshots/`，README 使用其中专服一轮的 E 页、箱子、创造页签和旧存档截图。构建日志与联调日志在远程会话仓库：`handoff/logs/bs3d-pocket-creative-test-build.log`、`handoff/logs/bs3d-pocket-creative-smoke.log`；首个源码实现的一轮（`ac000641e4e5`）见 `handoff/logs/bs3d-pocket-test-build.log`、`handoff/logs/bs3d-pocket-smoke.log`。两端生产 JAR 都不含 smoke 夹具，专服 JAR 不含客户端类。

## 验证边界

工作台、熔炉等其他 Vanilla-Addon 页面与箱子共用同一套玩家区裁剪，但这次只联调了箱子。创造页只联调了头盔一条链路：手部、副手、身上容器与内景在创造页的拖放与 E 页共用同一事务，从物品类别页签拿起物品再点模型与「点口袋格拿起」共用同一本地光标路径，都没有单独联调；窗口过小时的退回布局只由单元测试覆盖。第三方菜单只验证了服务端规则，没有在真实第三方模组里联调。紧凑 E 页的状态效果列表可能被遮挡，见[已知限制](Interaction.md#已知限制)。

本次未启动 Windows Native，也没有发布 Release、Maven 制品或独立 GitHub Wiki。

## 重跑联调

`scripts/smoke.py` 每次在新的隔离目录里启动 Linux Xvfb 客户端，以及本地专服或单机内置服务器，不启动 Windows Native。夹具只放在 `smoke` 源集里，生产 JAR 不包含夹具入口。

```bash
python3 scripts/smoke.py --mode both
```

`--mode dedicated` / `integrated` 选择环境，`--port` 修改专服验收端口，默认 25587，端口被占用时直接失败。脚本只回收自己创建的进程组，不连接或关闭已有的游戏和服务器。日志、截图与通过标记都在 `build/smoke/` 下。运行期间不要在同一工作区再执行构建，以免重写正在加载的类文件。
