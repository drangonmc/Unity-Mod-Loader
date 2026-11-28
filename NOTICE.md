# Unity 0.3.1 Beta 的第三方组件

Unity 自有代码使用根目录 `LICENSE` 中的 MIT 许可证。下表列出加载器及 Bukkit 插件域随附的主要上游实现。各组件保留其原作者的版权声明和许可证。

| 随附组件 | 许可证与来源 |
| --- | --- |
| NeoForge 26.2.0.93 游戏事件、伤害容器和传输 API | LGPL-2.1-only。`standalone/lib/unity-neo-transfer-26.2.0.93.jar` 内含 `META-INF/LICENSE-NeoForge.txt`、`META-INF/README-LICENSE-NeoForge.md` 和对应的 `META-INF/unity-sources/`。主加载器保留同一份源码，许可证归档于 `META-INF/licenses/unity-neo-transfer-26.2.0.93.jar/`。提取清单、输入摘要和重建入口见 `standalone/adapters/neo-transfer/`。[NeoForge 源码](https://github.com/neoforged/NeoForge) |
| Paper API 26.2 build 121、Bukkit/Spigot 继承部分 | Paper 声明项目继承 GPL v3，部分贡献者另行提供 MIT 授权。按来源保留适用条款。[Paper 项目许可证](https://github.com/PaperMC/Paper/blob/main/LICENSE.md) |
| Adventure API 与文本序列化器 5.2.0 | MIT。[Adventure 项目](https://github.com/PaperMC/adventure) |
| Apache Maven 3.9.6、Maven Resolver 1.9.18、HttpComponents、Commons Codec 1.16.0 | Apache License 2.0。各 Maven 组件的原包保留在插件域中，包括组件自带的 LICENSE 与 NOTICE。[Maven Resolver](https://maven.apache.org/resolver/) |
| Eclipse Sisu Inject 0.9.0.M2 | 此版本原包的 `about.html` 指定 EPL 1.0。组件位于 `standalone/vendor/bukkit/org.eclipse.sisu.inject-0.9.0.M2.jar`。[Eclipse Sisu](https://eclipse.dev/sisu/) |
| JSON.simple 1.1.1、SnakeYAML 2.2、JCL over SLF4J 1.7.36 | 此处发布的三个构件的内嵌 POM 均列出 Apache License 2.0。 |
| Fabric API、SpongePowered Mixin、MixinExtras | Fabric API 使用 Apache License 2.0，Mixin 与 MixinExtras 使用 MIT 各自的授权。随包版本及内容摘要见 `standalone/vendor/` 和 `standalone/lib/SHA256SUMS`。[Fabric API](https://github.com/FabricMC/fabric) · [Mixin](https://github.com/SpongePowered/Mixin) · [MixinExtras](https://github.com/LlamaLad7/MixinExtras) |

Bukkit 插件宿主按原包加载 `META-INF/unity/bukkit/` 中的库。构件清单与 SHA-256 在 `standalone/vendor/bukkit/SHA256SUMS`。编译和重建入口是 `standalone/Build.java`。验收使用的机器模组、事件模组和第三方插件的下载标识、版本与散列记录在对应的测试清单中。
