# Unity 0.3.3 Beta

Unity 使用独立加载内核运行 Minecraft Java 26.2，需要 Java 25。这个版本验证了两种机器在 WorldGuard 和 Residence 同服时使用放置者的权限。[GitHub 发行页](https://github.com/drangonmc/Unity-Mod-Loader/releases/tag/0.3.3-Beta)

## 安装

解压 Standalone ZIP 后运行 `Start-Unity.cmd` 启动专服。客户端先安装原版 26.2，再运行 `Install-Unity-Client.cmd` 创建 Unity 配置。模组放入各自游戏目录的 `mods/`，Bukkit 插件放入 `standalone-instance/plugins/`。作者插件和商业插件请从作者处获取。WorldGuard 使用 WorldEdit，Residence 使用 CMILib。WorldEdit 的区域编辑保护还可以安装发行包里的 `UnityEditRegions.jar`。

## 机器领地兼容清单

| 作者原包 | 验证的操作 | 观测结果 |
| --- | --- | --- |
| Quite Quarry 项目的 QuietQuarry 1.1.0，26.2 | 玩家从客户端放置采矿核心，机器消耗 FE 逐格采矿 | 一次加入 800 FE，只挖走获准位置的铁块和铜块，各产出 1 个。三处拒绝位置的金块、绿宝石块和青金石块保持原状。在同一存档的副本中换 VolumeB 实际放置核心，原先拒绝 VolumeA 开采的同一金块、绿宝石块均可开采，重叠区的青金石仍保留。两次归属均已从关闭后的存档读回。 |
| Logistics Drones 1.1.0，石质无人机 | 两名玩家各自部署无人机，使用原模组遥控器和原生 AI 搬运 | VolumeA 从公共源箱送出 12 个绿宝石。VolumeB 在自己获准的区域搬运 5 个钻石，目的箱从 4 个增至 9 个。越权取货和目标箱拒收时，源箱、目的箱、机上货物总数均保持原值。 |
| WorldGuard 7.0.19 + WorldEdit 7.4.6，Residence 6.0.4.2 + CMILib 1.6.1.4 | 原始插件共存，按目标坐标评估采矿、取货和交货，两套规则均需允许 | 同一观察插件在独立 Paper 和 Unity 上得到相同的 12 组双人判定。实际客户端挖掘和开箱复核了公共区、单独保护区与重叠区。玩家下线后，已部署无人机在重启实例中继续受其 UUID 对应的区域规则约束。 |

采矿授权发生在作者代码计算并写入掉落、扣电和破坏之前。无人机在抽取前检查源箱和目标箱，实际交货时再次检查目标。缺少归属或已安装的策略提供者出错时拒绝操作。当前运行时钩子覆盖表内的作者机器入口。其他模组直接写世界、转运物品的路径需要单独验证。[机器与保护插件原包清单](evidence/0.3.3-Beta/machine-compatibility.json) · [Unity 双端记录](evidence/0.3.3-Beta/unity/result.json) · [Paper 对照记录](evidence/0.3.3-Beta/paper/result.json)

## 存档和客户端

Unity 专服加载 88 个作者顶层模组文件，每个实际客户端加载 86 个，另有 14 个作者插件。见证程序单独计数。两台隐藏图形客户端在回环离线账号环境中进服，重启后再次读取容器、玩家和机器。原版 NBT 读取器另行核对了关闭后的采矿核心、两台无人机的作者和 Unity 归属、箱子及两名玩家的背包。正常停服测得 Unity 为 34.046 s, 50.157 s，Paper 为 3.156 s, 1.984 s。开发期间一次多实例并行存盘触发了 60 秒 watchdog，快照显示服务端正在做 Windows 剩余空间检查。此处时长来自保留原版 watchdog 的串行发行验收。[世界存档](evidence/0.3.3-Beta/unity/world.json) · [玩家存档](evidence/0.3.3-Beta/unity/players.json) · [第二名放置者的同坐标存档](evidence/0.3.3-Beta/other-owner/world.json)

NeoForge 的方块实体和世界附件支持同步。双端核对了世界值 3303、箱子附件值 3305，以及玩家切换到下界后自身值 3302 和下界值 3304 的读回。两服均验证了玩家进入下界门前取消 `PlayerPortalEvent` 能使玩家留在原世界。Bukkit 世界 UUID 保存在 `uid.dat`。物品 Lore 可取出、修改再写回。Unity 接受旧式 `keepInventory` 名称，并拒绝以 0 级附魔覆盖既有附魔。本次 Paper 26.2 build 121 对旧名称抛出异常，对忽略等级限制的 0 级写入返回成功且读回等级 0。

本版覆盖的是上述游戏操作。Tiny Pipes 部分可选配方仍有已知解析告警。正版在线聊天签名、十人以上并发和长期堆内存趋势属于 0.4.0 公测前的独立验收。[验收程序](standalone/tests/reference/review33/accept33.py) · [第三方组件声明](NOTICE.md)
