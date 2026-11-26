# Unity 0.2.6 Beta · Minecraft Java 26.2

Unity 使用独立内核，服务端入口为 `mc.unity.bootstrap.Main`，运行需要 Java 25。本版支持下表中经过验证的子集。首次试用请使用测试世界，保留原有存档备份。

## 安装

1. 解压完整的 Standalone ZIP。服务端运行 `Start-Unity.cmd`，按 Minecraft 的流程处理 EULA。客户端先安装原版 26.2，再运行 `Install-Unity-Client.cmd`，在启动器中选择生成的 Unity 配置。
2. 将作者发布的 WorldEdit 7.4.6 和 WorldGuard 7.0.19 Bukkit JAR 放入 `standalone-instance/plugins/`。需要让 WorldGuard 的区域规则约束 WorldEdit 批量写入时，将随包的 `versions/0.2.6-Beta/UnityEditRegions.jar` 也复制到这个目录。三份插件都安装在服务端。
3. 为需要复制箱子内容的编辑者授予 WorldEdit 的编辑命令权限和 `worldedit.setnbt`。区域保护组件逐个检查目标坐标的 WorldGuard build 规则，并尊重 WorldGuard 授予该玩家的区域绕过权限。验收使用了普通非 OP 编辑者。

## 已验证的编辑行为

Paper 26.2 build 121 和 Unity 使用相同的 WorldEdit、WorldGuard、区域保护组件和测试权限。两个隐藏窗口均运行真正的 Minecraft 图形客户端。Unity 服务端加载 77 个锁定样本，两名客户端各 75 个，另外安装官方完整 Fabric API 和 NeoForge 测试模组。

| 场景 | 两端和服务器的核对结果 |
| --- | --- |
| 跨界 `//set`、`//undo`、`//redo` | 六格中，区域内三格始终为平滑石。区域外三格变为金块，撤销还原平滑石，重做后恢复金块。 |
| 区域内粘贴 | 对有 7 个青金石的受保护箱子执行粘贴，数量和槽位保持原样，重启后仍能读回。 |
| 区域外箱子复制粘贴 | 源箱第 4 格有 11 个绿宝石，并带 `minecraft:custom_data`。目标箱同一格原有 3 个钻石。粘贴、撤销和重做依次得到绿宝石、钻石、绿宝石。两个客户端分别打开箱子检查物品和数量，服务端核对完整组件；重启后再次检查源箱与目标箱。 |
| 玩家存档 | 玩家 A 的 3 个黑曜石、玩家 B 的 5 个燧石、双方 UUID 和 20 点血量在重启后与服务端一致。 |

## NeoForge API

本版增加 `@SubscribeEvent`、按物理端筛选的 `@EventBusSubscriber`、模组和游戏事件总线的自动分发、`IEventBus.register/unregister`、`ServerTickEvent.Pre/Post` 与加载上下文扩展点。使用上游 NeoForge SDK 编译的独立测试模组，在混装服首次启动和重启时均验证了扩展点读取、监听优先级、服务端线程和注销行为。

本轮涵盖方块编辑和箱子方块实体。实体剪贴板、跨维度编辑、离线编辑者、任意模组机器数据及整个 WorldGuard 保护事件集仍需分别验收。单项 NeoForge 总线支持也取决于 Unity 是否接入了对应的游戏事件。

完整对照数据和运行散列随包保存在 `evidence/0.2.6-Beta/`，每个平台的 `observations.txt` 保留客户端和服务端的关键观测。
