# Unity 0.3.4 Beta

Unity 是 Minecraft Java 26.2 的独立加载器，运行环境为 Java 25。本地发行包内只有一个 JAR，专服和客户端共用。

## 安装插件和模组

1. 解压完整 ZIP，将 Bukkit、Spigot 或 Paper 插件的作者原始 JAR 放入 **与 `Start-Unity.cmd` 同级的 `plugins/`**，再启动或重启服务器。插件作者声明的其他前置插件也放在这里。
2. 需要 WorldGuard 区域限制 WorldEdit 编辑时，将 WorldEdit 和 WorldGuard 的原包装入 `plugins/`。Unity 会自动接入每次编辑实际写入的坐标，覆盖填充、粘贴和历史操作。`/unitymark place`、`/unitymark count` 随加载器提供。
3. 专服的 Forge、NeoForge、Fabric 模组放入 `standalone-instance/mods/`。客户端先安装原版 26.2，再运行 `Install-Unity-Client.cmd`，选择生成的 Unity 启动配置并在对应游戏目录的 `mods/` 安装所需模组。直接用 `java -jar unity-loader-0.3.4-Beta.jar` 启动专服时，使用当前实例的 `plugins/` 和 `mods/`。

## 从 0.3.3 升级

原有 `standalone-instance/plugins/` 继续参与插件发现。根目录和旧目录中的插件使用同一依赖顺序。两个目录有同名且字节相同的插件时只加载一份；同名但内容不同会在启动时报错。把 JAR 挪到新目录后，已有的旧配置和数据目录仍可使用。原版 UnityEditRegions 旧副本会被识别；World Marker 使用原来的计数存档键。客户端安装程序只安装主加载器，图形验收组件仅供开发测试。

## 本版验证的兼容范围

| 平台 | 本版新增或验证的行为 |
| --- | --- |
| Forge | `LevelEvent.Load/Unload` 接入专服世界和客户端世界切换，`LevelEvent.Save` 在原版区块与实体保存路径完成后触发。使用 Forge SDK 编译的监听器及 More Berry Uses 原包验证。 |
| NeoForge | NeoBus 派发上游 `LevelEvent.Load/Unload/Save` 类。使用 NeoForge SDK 编译的监听器及 Advanced Furnace 原包验证。 |
| Fabric | 轻量模式支持 `ServerChunkEvents.CHUNK_LOAD`、`CHUNK_GENERATE` 和 `CHUNK_UNLOAD`，加载事件携带新区块标记。使用官方 SDK 编译的回调及 Bedrock Crafter 原包验证。完整 Fabric API 模式沿用上游模块。 |
| Bukkit/Paper | WorldEdit 7.4.6 + WorldGuard 7.0.19。同一选区横跨边界时，保护侧三格保持原样，允许侧三格变更；撤销、重做和带内容的箱子复制已用两台实际客户端核对。 |

另已验证客户端切换维度、拦截末地传送、禁止创建末地平台和末地目标重定向。兼容范围以所列 API 和用例为准。完整作者插件依赖仍应按作者说明安装。

上版混装世界的副本另用 88 个作者模组和 14 个作者插件验证离线归属。在仅 Residence 拒绝的取货点，无人机货舱为 0，源箱保留 6 个绿宝石，目的箱保留 12 个。

开发测试使用本机回环离线账号和真实游戏客户端。该机第一次停服时，主世界保存耗时 53.7 秒。通过 JVM 参数 `-Dmc.unity.shutdownDiagnostics=true` 可记录每个世界和每个插件的耗时。
