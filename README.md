# Unity 0.2.7 Beta · Minecraft Java 26.2

Unity 使用独立内核，Java 25 为运行环境。专服由 `mc.unity.bootstrap.Main` 启动。本页列出已通过实机验收的兼容子集。

## 安装

1. 解压完整 Standalone ZIP，在独立测试目录运行 `Start-Unity.cmd`。客户端先安装原版 26.2，再用 `Install-Unity-Client.cmd` 安装 Unity 配置。
2. 将下面两个 NeoForge 作者原包放到服务端及每个客户端的 `mods/`。发行包附有版本标识、下载地址和 SHA-512，见 `standalone/tests/reference/machines-26.2.json`。
3. 将 WorldEdit 7.4.6、WorldGuard 7.0.19 的 Bukkit 作者 JAR 和随包的 `versions/0.2.7-Beta/UnityEditRegions.jar` 放入 `standalone-instance/plugins/`。两个上游插件的校验值在 `standalone/tests/reference/protection-originals.json`。为获准复制容器内容的编辑者配置 WorldEdit 命令权限和 `worldedit.setnbt`。

| NeoForge 原包 | 实测玩法 | 用于存档核对的字段 |
| --- | --- | --- |
| AdvancedFurnaceY 1.0.2 | 高级熔炉用煤将粗铁烧成铁锭。连接铜漏斗后，侧面燃料槽收到 2 个煤。 | 注册 ID、朝向、机器名称、`lit_total_time`、槽号、物品数量和自定义物品组件 |
| Copper Hopper 0.24.1 | 铜漏斗按过滤条件输送 4 个绿宝石，留下 1 个过滤样品，输入端的 3 个钻石保持原样。 | 方块 ID、独立的方块实体 ID、朝向、机器名称、过滤槽物品和自定义物品组件 |

作者版本：[AdvancedFurnaceY](https://modrinth.com/mod/advancedfurnacey/version/D0goCfZL) · [Copper Hopper，由 pcal.net 发布](https://modrinth.com/mod/copper-hopper/version/4JMEDg1g)

## WorldEdit 区域编辑和重启读档

Paper 26.2 build 121 和 Unity 使用相同的原版箱子、插件原包、区域组件和权限做对照。两个真实客户端分别在首次启动和重启后进服，核对方块、双方背包和血量。Unity 的机器用例另外装入两个候选模组。按作者样本 JAR 计，服务端共 79 个、每个客户端 77 个，另有官方 Fabric API 与验收见证模组。

| 操作 | 实际结果 |
| --- | --- |
| 箱子对照 | 跨界设置六格方块，区内三格保持原样。区外箱子粘贴后有 11 个带自定义数据的绿宝石，撤销恢复 3 个钻石，重做再恢复绿宝石。 |
| 编辑者 A 从区外复制机器 | 将向东的机器粘贴到区内，原来的向北机器保留 7 个青金石与原始数据。粘贴到区外后，目标由向西、3 个钻石变成向东、11 个绿宝石。撤销和重做均恢复了对应朝向、槽位、名称与组件。 |
| 编辑者 B 从区内复制机器 | B 有读取剪贴板内容的命令权限。粘贴到获准写入的空地后，两端读到向北的副本、7 个青金石和原始标记。B 撤销后该处变为空气，重做后机器与内容恢复。 |
| 专服重启 | 从同一世界读回两台机器的源和目标、区内原件及区外副本。两个客户端分别打开区外机器的作者菜单，读取槽位与组件。服务端同时核对区内原件以及所有目标的方块实体 ID 与 NBT。两位玩家的背包和 20 点血量与重启前一致。 |

上述区域启用 WorldGuard 的 `build: deny`，两位非 OP 编辑者均有 WorldEdit 读取和复制的权限。区域内的写入保护由 UnityEditRegions 执行。对于需要限制读取剪贴板的部署，还需单独配置并验收读取权限。

## NeoForge 新增能力

- `RegisterMenuScreensEvent` 在客户端将模组菜单工厂接入实际屏幕注册表，检测重复注册并在关闭时释放本会话添加的工厂。
- 支持 NeoForge 的方块实体构造重载、有效方块集合和仅管理员可写 NBT 标志。WorldEdit 通过游戏注册键和原生方块属性转换状态。Bukkit 插件可以用注册键识别本服的模组物品与方块。
- 物品与物品栈的 `getBurnTime`、`EventHooks.getItemBurnTime` 和 `FurnaceFuelBurnTimeEvent` 接入了熔炉燃料检查。由上游 SDK 编译的见证模组将带标记的黏土球燃料时间设为 240 tick，测试验证了监听优先级、取消语义和原版熔炉实际产出金锭。

验证范围限于锁定的两个机器版本及本次混装。其他机器的能量、流体、跨区块多方块结构及离线编辑仍需各自验证。可复查的发行散列、两端结果和运行日志摘录在 `evidence/0.2.7-Beta/`。
