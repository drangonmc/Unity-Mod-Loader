# Unity 0.2.0 Beta · Minecraft Java 26.2

本版使用 Java 25。混装验收包含 65 个有 Java 代码的作者模组，另有 Puzzles Lib 和 7 个 Fabric API 模块。作者模组中，Fabric 46 个、Forge 14 个、NeoForge 5 个。来源、Modrinth 版本 ID 和原始文件的 SHA-512 均可复核。[混装清单](performance-pack.json)

## 功能范围

| 能力 | 本版实现和验收方式 |
|---|---|
| 配置 | Forge 与 NeoForge 的类型化 TOML 配置、按世界加载 SERVER 配置、进入游戏前下发快照、主线程热重载和退出清理。两台独立客户端分别从 23 更新到 71、从 31 更新到 59。越界的 120 被拒绝，客户端本地文件的哈希保持一致。 |
| 实体 | Forge、NeoForge 属性事件和 Fabric 默认属性注册，实体渲染器和模型层注册。两个自定义实体的最大生命值在服务端分别为 47 和 53。两个客户端都实际烘焙了自定义模型层。 |
| 生物与地物 | Forge 与 NeoForge 的数据包 `add_spawns`、`add_features` 进入按世界加载的注册表，经 Fabric Biome API 应用。测试从平原的实际生成表读取权重 17，再检查新生成的 9 个区块中的标记方块。出生位置谓词分别验证了允许和拒绝路径。 |
| 掉落与重载 | 原始 Fabric Loot API 模块执行表修改。Forge 与 NeoForge 的全局掉落修改器从各自的数据包列表加载，支持作者注册的 MapCodec、表 ID 条件和 NeoForge 的 `add_table`。服务器执行 `/reload` 后通过原版 `/loot` 和背包计数检查结果。 |
| 原版扩展 | 方块爆炸抗性调用携带位置与世界的扩展方法。猪灵交易判断调用物品的货币判定。验收覆盖自定义方块和按数量区分的自定义货币。 |
| 附件 | Fabric 和 NeoForge 共用实体、方块实体与区块的附件存储。持久附件通过作者 Codec 随存档写入，区块转换和玩家重生有独立的生命周期处理。 |
| 运输 | Forge 物品接口能访问 Fabric 的有槽库存，Forge 流体接口能访问 Fabric 储罐，Forge 能量接口能访问已注册的 NeoForge 方块能源能力。验证面规则、模拟、嵌套回滚、实际提交、毫桶换算和设备移除后的句柄失效。 |
| Mixin | 独立运行时内置 MixinExtras 0.5.3。预检或转换失败时，控制台和实例的 `logs/unity-mod-error.txt` 给出中文说明、已知的 JAR、缺失符号或 Mixin 配置。底层异常保留在游戏日志中。 |

65 个作者模组用于检验同服运行。本版新增 API 的具体计数由针对公开 SDK 编译的测试模组产生。三平台测试模组单独计数，性能测试会移除它们。[双客户端与重启记录](../evidence/0.2.0-Beta/candidate65.json)

## 准备测试包

基础样本安装器仍提供经过玩法回归的 21 个模组。要复现本版的大包，在 Unity 项目目录运行下列命令。服务端目录有 73 个 JAR，客户端目录有 71 个。两个仅供专服的 Forge 命令模组已经从客户端清单排除。

```powershell
python tools/performance_samples.py --side server
python tools/performance_samples.py --side client --source cache/0.2.0-Beta/performance-samples
```

生成的目录分别是 `cache/0.2.0-Beta/performance-samples` 和 `cache/0.2.0-Beta/performance-client-samples`。已有本地文件可以增加 `--offline` 只校验缓存。使用 `--dest` 可以指定独立实例的模组目录。

## 复核性能

在同一台主机上依次运行原版、空载 Unity、Unity 混装三组专服。三个实例复制同一份原版存档，使用相同服务端堆参数、视距和模拟距离。三个组都由两个带相同操作探针的 Unity OpenGL 客户端并行步行，之后传送回同一起点。原版组运行 Mojang 专服，混装组的客户端另装清单中的客户端模组。观察进程通过本机 JMX 读取 Minecraft 的 `tickTimes` 环形缓冲区，每个取样窗口的 100 个槽位均须刷新。每组使用 1,200 个 Tick 计算最近秩 P50、P95、P99，并记录 JVM 堆和进程工作集。启动时间从创建进程算到服务端首次记录 Done。每组使用新的 JVM，操作系统文件缓存沿用主机当时的状态。两个客户端与专服运行在同一台 Windows 主机上，因此数值包含本机进程竞争。

```powershell
python tools/performance_samples.py
python standalone/tests/performance.py --tag release65 --walk --samples cache/0.2.0-Beta/performance-samples --eula-from instance/eula.txt --windows 12
```

配置文件、缓存、世界和客户端目录均隔离。源 JAR 下载前后以锁定的 SHA-512 核对。两人步行测试覆盖的是已生成区域内的活动，尚未测量多人同时生成新区块、密集机器运转或 GPU 渲染回调的单独成本。[原版结果](../evidence/0.2.0-Beta/release65-vanilla.json) · [空载 Unity 结果](../evidence/0.2.0-Beta/release65-bare.json) · [65 模组结果](../evidence/0.2.0-Beta/release65-mixed.json)

<!-- release measurements -->
### 本次发行候选实测

| 同存档双人步行 | 启动（秒） | P50 MSPT | P95 MSPT | P99 MSPT | 取样期最高工作集（MiB） |
|---|---:|---:|---:|---:|---:|
| 原版专服 | 6.72 | 1.040 | 2.186 | 4.050 | 1138 |
| 空载 Unity | 7.50 | 1.085 | 2.479 | 4.524 | 1136 |
| Unity 与 65 个作者模组 | 9.92 | 1.110 | 2.671 | 4.335 | 1351 |

每种配置运行一轮。工作集为取样期观测峰值，原始 CSV 保留了各窗口的堆使用量和每个 Tick 的耗时。
<!-- end measurements -->

### 回调和资源重载的开销

在同一 JDK 上，使用相同的有状态监听器，分别测直接数组分发、Unity Core 信号及 Forge 分发。每个监听器数量先预热 10 轮，再交错执行 20 个批次，每批分发 30 万次并核对结果。8 个监听器时，Core 和 Forge 的批次中位数分别为 9.75 和 10.20 纳秒／次分发。这个微基准包含 JIT 内联影响，直接数组的耗时也随优化变化，不能把两列之差当作固定的运行时税率。[原始 CPU 样本](../evidence/0.2.0-Beta/callback-costs.json)

客户端在注册阶段将实体与方块实体的渲染器工厂交给原版注册表，模型层在资源重载时烘焙。日常渲染沿用原版调度。服务端配置使用文件变更通知，常规 Tick 每隔 20 Tick 才检查待处理事件。跨平台流体提交遇到分数毫桶时会先回滚，再提交可完整表示的数量，因此这条边界路径会多执行一次查询和事务。[渲染器注册](adapters/client14/src/mc/unity/client/Renderers.java) · [配置监听](src/main/java/net/neoforged/fml/config/ConfigWatcher.java) · [流体适配](adapters/transfer/src/mc/unity/adapter/transfer/FabricFluids.java)

## 当前兼容边界

- 数据包生物群系修改目前实现增加地物与新增生物生成项。自定义修改器序列化器、复杂条件与完整 Forge/NeoForge 世界生成 API 仍需逐个扩展。生态适配依赖的原始 Fabric API 模块随示例安装器准备。
- 附件已覆盖服务端对象和存档。自动向跟踪客户端同步附件、世界级附件和读取其他加载器的历史附件存档格式仍待验收。
- 物流互操作按上表所列方向验证。Fabric 流体按 81 滴折合 1 毫桶，只提交整数毫桶。带额外 Fabric 数据组件的流体、无槽虚拟物品网络、反向查询和其他能源标准仍需专门适配。
- 选择列表以外的作者版本仍需验证。已观察到 Bellows 可通过启动检查，但区块加载期间会调用 MC 26.2 缺少的 `ValueInput.contains(String)`。生态筛选把这个版本排除在性能结论之外。另一个负例 Disable Portal Checks 的中文报告直接列出了原始 JAR、Mixin 配置与缺失目标。[诊断原文](../evidence/0.2.0-Beta/mod-diagnostic.txt)

API 验收探针使用 Forge、NeoForge 和 Fabric 发布的 26.2 二进制签名编译。NeoForge 的设计文档可用于理解具有默认值和序列化器的附件，以及通过有序列表加载全局掉落修改器的机制。[附件设计](https://docs.neoforged.net/docs/1.21.11/datastorage/attachments/) · [全局掉落修改器设计](https://docs.neoforged.net/docs/1.21.11/resources/server/loottables/glm/)
