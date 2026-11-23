# Unity 0.1.3 Beta · Minecraft Java 26.2

独立专服共装入 21 个作者模组，沿用 19 个原始样本，加入 Fabric 的 Immersive Ores 26.2-0.3 和 NeoForge 的 Block Runner 26.2.0。额外依赖为 Puzzles Lib 26.2.5 与三个 Fabric API 模块。PlayerAbilityLib 1.16.0 和 MultiLoader Data Extensions 26.2.0 直接取自作者原包。这些模组和玩家侧依赖按作者发行文件的 SHA-512 校验。[原件和模块清单](beta-gameplay.json) · [Immersive Ores](https://modrinth.com/mod/immersive-ores/version/fjZyYXGJ) · [Block Runner](https://modrinth.com/mod/block-runner/version/VNgfrtBA) · [Puzzles Lib](https://modrinth.com/mod/puzzles-lib/version/XkWV0WSp)

## 双客户端实测

Windows x64 上两个独立的 Minecraft 图形进程使用不同本机测试身份，连接同一个回环专服。每位玩家在相同起点和朝向按住前进键 38 个游戏刻，松键 12 刻后分别读取客户端坐标及服务端玩家 NBT。草径略低于石头，距离取水平方向。首次启动和重启后均执行同一组对照。

| 玩家 | 石头行走距离 | 草径行走距离 | 草径／石头 |
|---|---:|---:|---:|
| UnityMinerA | 8.198889 格 | 11.068501 格 | 1.3500 |
| UnityMinerB | 8.198889 格 | 11.068501 格 | 1.3500 |

客户端和服务端得到相同的起止距离。玩家 A 使用原版模组物品振金矿锤从正面破坏 3×3 石墙，九个目标位置都变为空气，矿锤损耗为 9。玩家 B 放置一块振金块后，持有数量由 3 变为 2。两人退出、刷盘并关闭所有游戏进程后，重新登入同一世界，核对了 UUID、双方背包、矿墙和放置的方块。玩家 A 还在创造栏中实际点选了模组标签、搜索和生存背包。QA 保留真正的 GLFW 窗口、OpenGL 渲染与原版多人游戏连接，窗口在启动和重启时都保持不可见。正常玩家启动方式沿用 Minecraft 默认行为。[完整双端结果和类来源](../evidence/0.1.3-Beta/beta-mixed-gameplay.json) · [实机截图](../evidence/0.1.3-Beta/beta-mixed-world.png)

## Puzzles Lib 的平台接入

原来 57 项按作者引用类的实际用途划分为公共／专服 26 项、客户端 27 项、数据生成 4 项。公共类也能在客户端运行。两项配置协议代码虽然位于公共目录，其具体连接及创建它们的代理都属于客户端。报告逐条列出原始符号、所属类、物理端定义记录和验证范围。[逐项对照](../evidence/0.1.3-Beta/puzzles-57-phases.md) · [机器可读结果](../evidence/0.1.3-Beta/puzzles-57-phases.json)

Unity 通过 Puzzles Lib 原生的 Java ServiceLoader 接口提供公共端和客户端适配器，运行两个作者发布的模组入口。适配器调度构造、公共设置、客户端设置和加载完成事件，并把物品提示回调接入客户端。方块速度值来自作者的数据包，注册和查询经过原包内的 MultiLoader Data Extensions，移动属性计算由 Block Runner 自身的 Mixin 和处理类执行。独立游戏 JVM 只使用 Unity 引导和已锁定的依赖。官方 NeoForge 开发 JAR 用于适配器的编译。

可选 play 载荷按连接、版本和方向协商。实测每位玩家在双方均能查询到 blockrunner:handshake 和 puzzleslib:handshake，未注册通道返回 false。增加了资源包隐藏属性、客户端键位事件和配置屏工厂的二进制接口。配置阶段自定义网络、数据生成工作流及 Puzzles Lib 的全部可选扩展均超出本批次实测范围。

宿主适配只对锁定的 Puzzles Lib 二进制选择。其未选用的平台实现和可选方法允许延迟链接，按原件 SHA、引用类和具体符号限定。其他模组保留原有的 ABI 预检。模组覆盖未接入的构造回调会在构造阶段报错；原库代码真正执行到已记录且缺失的接口时会报告具体调用。[适配器源码](adapters/puzzles/src/mc/unity/adapter/puzzles) · [协商和失败路径回归](../evidence/0.1.3-Beta/puzzles-adapter-checks.log)

## 本机复验

准备 Java 25、独立的原版 26.2 测试启动器目录、官方服务端 JAR 和本机已同意的测试 EULA。下列命令从项目根目录执行，测试在临时目录创建世界。`--cache-only` 仅准备锁定的样本缓存。发行包的双击启动与安装方式见 [README](../README.md)。

```powershell
python tools/unity.py samples --cache-only
python tools/unity.py build --client-home cache/0.1.3-Beta/client-home
python tests/check.py
python standalone/tests/beta_gameplay.py --client-home cache/0.1.3-Beta/client-home --bundle cache/0.1.3-Beta/mojang-server-26.2.jar --samples cache/0.1.3-Beta/samples --eula-from instance/eula.txt
```

仅使用官方专服输入重建，也得到了与双端构建完全相同的加载器和服务端示例 JAR。

适配器单独重建可运行 `standalone/adapters/puzzles/build.py --help`。编译器校验 Puzzles Lib、NeoForge 及事件总线参考件的哈希，输出可与 `standalone/lib/unity-puzzles-platform-26.2.5.jar` 对照；发行包还包含独立加载器和两个 Unity 示例的源码。
