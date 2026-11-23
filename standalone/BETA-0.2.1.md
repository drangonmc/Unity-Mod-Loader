# Unity 0.2.1 Beta · Minecraft Java 26.2

本版在独立加载器上接入 NeoForge 和 Fabric 的配置阶段网络。服务端可以在玩家进入世界前发送模组规则，等客户端确认后，再由原版配置任务队列继续登录。

## 配置阶段 API

| 平台 | 本版路径 |
|---|---|
| NeoForge | `PayloadRegistrar.configurationToClient`、`configurationToServer`、`configurationBidirectional` 和同时用于两个阶段的 `common*` 注册。支持按方向提供处理器、客户端延迟注册、可选通道和版本匹配。 |
| NeoForge 任务 | 在 `RegisterConfigurationTasksEvent` 注册原版 `ConfigurationTask` 或 `ICustomConfigurationTask`。接收处理器通过 `IPayloadContext.reply` 回复，可用 `enqueueWork` 切到游戏线程，再用 `finishCurrentTask` 完成当前模组任务。 |
| Fabric | `PayloadTypeRegistry.clientboundConfiguration()` 与 `serverboundConfiguration()` 分别注册编解码器。`ServerConfigurationNetworking` 与 `ClientConfigurationNetworking` 提供全局及连接级接收器、能力查询、发送和应答。支持配置连接事件及 `addTask`、`completeTask`。 |

能力协商按连接、方向和版本记录接收器。在远程连接上，客户端与服务器通过两次基础能力确认往返交换通道列表和连接级接收器，再启动依赖这些通道的任务。模组各自的请求与应答会另外产生往返。用户定义的任务由模组显式完成。[NeoForge 双向任务示例](tests/beta/NeoPhase.java) · [Fabric 服务端示例](tests/beta/FabricPhase.java) · [Fabric 客户端示例](tests/beta/FabricPhaseClient.java)

Unity 自身的通道协商有 60 秒超时。单个连接每个方向最多登记 256 个配置类型。普通自定义包的内容上限为客户端发往服务端 32,767 字节、反向 1,048,576 字节。

配置包与 Play 包可以使用相同的标识符和不同的编解码器。配置阶段没有玩家对象，处理器应通过上下文访问连接和任务。Fabric 的 `registerLarge` 分片和完整 Play 网络 API 仍在本版验收范围之外。跨平台模组的公开 API 签名以随测试锁定的 26.2 SDK 为准。[SDK 基线](tests/worlds/fabric-references.json) · [NeoForge 配置任务说明](https://docs.neoforged.net/docs/networking/configuration-tasks/)

## 存档和储罐修复

- 一个附件的作者 Codec 解析旧值失败时，加载器记录附件 ID，将原始 NBT 留在所属对象上，并继续解码其余键。读取默认值后再次保存，会保留旧 NBT。模组主动写入新值或删除该键时会替换或清除旧值。
- 有槽 Fabric 储罐按槽直接索引。普通多储罐保留同一底层存储的一份视图快照，按 Tick、事务和存储版本失效。对于既不提供槽位接口也不提供版本号的存储，同一 Tick 内从外部改变布局，最迟在下一 Tick 重新枚举。储罐有效性通过可回滚的实际插入探测。仅提供只读视图的聚合存储，按目标视图在模拟期间的实际变化判断。访问已移除的方块实体或在专服主线程以外查询会返回空。
- SERVER 配置接收、断开与重置使用同一把锁。初始配置、玩家登录时的补发和热重载广播均跳过集成服本地内存连接。远程玩家仍正常接收配置。
- 注入接口时由 ASM 签名读写器保留原有泛型签名，并添加原始接口的 JVM 二进制名。Java 25 反射测试覆盖了嵌套接口、名称中含字面 `$` 的顶层接口和原有泛型父类。使用参数化外部类的签名与这里的原始嵌套接口是不同情形。[JVM 签名语法](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.9.1)
- 生物群系适配沿用经过哈希校验的 Fabric Biome API `18.0.6+c7bd5b8e9c`。适配器在启动时验证内部字段 `dynamicRegistries` 的名称和 `RegistryAccess` 类型，模块升级时需要重跑新生成区块和生物生成表测试。

<!-- release measurements -->
## 性能对照

| 同存档双人步行 | 启动（秒） | P50 MSPT | P95 MSPT | P99 MSPT | 取样期最高工作集（MiB） |
|---|---:|---:|---:|---:|---:|
| 原版专服 | 6.20 | 0.984 | 2.311 | 3.649 | 1131 |
| 空载 Unity | 6.94 | 1.040 | 2.282 | 4.262 | 1138 |
| Unity 与 65 个作者模组 | 9.24 | 1.052 | 2.361 | 3.833 | 1349 |

每种配置运行一轮。工作集为取样期观测峰值，原始 CSV 保留了各窗口的堆使用量和每个 Tick 的耗时。
<!-- end measurements -->

## 验收范围

兼容回归使用 65 个作者原始模组和 8 个外部依赖。专门的握手、储罐过滤与旧数据测试使用独立针对公开 SDK 编译的探针。两台隐藏的真实客户端同时进服，在重启后以原身份重连。记录按玩家匹配两套平台的挑战和应答，并检查它们出现在服务端加入世界日志之前。[专服、双客户端和重启记录](../evidence/0.2.1-Beta/candidate65.json) · [作者 JAR 清单](performance-pack.json)

单人测试在同一真实客户端中创建世界 A、创建世界 B、再打开 A。传输探针检查内存连接上确有配置阶段的应用包，同时核对 SERVER 配置的重复发包次数。性能对照使用同一份原版存档、相同堆参数与视距，每组两个真实客户端步行 1,200 个取样 Tick。性能组使用作者模组包，移除了专门的协议探针。MSPT 描述进入世界后的服务端 Tick，基础能力协商的登录延迟尚未单独计时。[单人记录](../evidence/0.2.1-Beta/integrated.json) · [65 模组性能原始数据](../evidence/0.2.1-Beta/release65-mixed.json)

| 检查项 | 实机结果 |
|---|---|
| 两名玩家首次进入并在重启后重连 | 共 4 个远程会话，每个会话各完成一次 Fabric 和一次 NeoForge 应答。服务端日志中的 8 次应答均早于对应玩家加入世界。 |
| 损坏附件 | 更换 Codec 后重新打开旧存档。4 个旧值可回退到默认值，原始 NBT 在重新序列化时仍可读，邻接的健康键和两份背包内容一致。 |
| 过滤和缩容 | 32 个储罐的完整读取与模拟校验只枚举一次。空岩浆槽拒绝水，模拟结束后仍为空。缩减到 1 槽，刷新一次便能安全查询已消失的下标。 |
| 单人连接 | A、B、A 三次进入共产生 6 个被探针观察到的 NeoForge 配置请求与应答包，SERVER TOML 快照包为 0。修改 A 的配置后服务端确实触发了重载。 |
| Java 25 反射 | 注入后，原泛型父类和接口的类型参数、嵌套接口和带字面 `$` 的顶层接口均能正确读取。 |

## 安装

解压后运行 `Start-Unity.cmd` 启动专服。客户端需先安装原版 26.2，再运行 `Install-Unity-Client.cmd` 并选择对应的 Unity 配置。需要复现大包时，可分别使用服务端和客户端清单。

```powershell
python tools/performance_samples.py --side server
python tools/performance_samples.py --side client --source cache/0.2.1-Beta/performance-samples
```
