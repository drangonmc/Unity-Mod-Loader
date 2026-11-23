"""Rebuild the phase table from the frozen first-error baseline and actual two-client class traces."""
import collections
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/"evidence/0.2.2-Beta"
def main():
    raw=EVIDENCE/"puzzles-57-original.log"
    runtime=json.loads((EVIDENCE/"beta-mixed-gameplay.json").read_bytes())
    specs=json.loads((ROOT/"standalone/beta-gameplay.json").read_bytes())["originals"]
    library=next(x for x in specs if x["id"]=="XkWV0WSp")
    consumer=next(x for x in specs if x["id"]=="VNgfrtBA")
    original=ROOT/"cache/0.2.2-Beta/samples"/library["filename"]
    assert hashlib.sha512(original.read_bytes()).hexdigest()==library["sha512"]
    with zipfile.ZipFile(original) as z: classes=set(z.namelist())
    fingerprint=hashlib.sha256(raw.read_bytes()).hexdigest()
    if fingerprint != "ce7f4145978cb0ebed05b5e3204913658042dd3e76370e9ba887960e5db7c5da":
        raise ValueError("Different preflight source: re-review the phase assignments")
    rows=[]
    for line in raw.read_text("utf8").splitlines():
        match=re.fullmatch(r"  Unsupported platform (type|member): (.+) in (\S+)",line)
        if not match:continue
        kind,symbol,owner=match.groups()
        assert owner+".class" in classes
        index=len(rows)+1
        phase="数据生成" if index<=4 else "客户端" if 13<=index<=36 or index in (49,56,57) else "公共／专服"
        states={side:any(run["classAudits"][side]["classes"].get(owner.replace("/","."))==library["filename"] for run in runtime["runs"])
                for side in ("server","A","B")}
        notes={
            1:"生成声音资源",2:"构造器可注册工厂；其产物只在数据生成时使用",3:"生成内置动态注册表数据",4:"生成器所用的资源管理器",
            13:"客户端入口中的开发模式分支",17:"Unity 客户端服务提供器继承作者代理；按能力接入",
            19:"0.1.4 提供客户端渲染工厂注册；世界探针覆盖新实体工厂",
            43:"0.1.4 提供基础数据注册表声明，测试含 NeoForge 自定义注册表",
            26:"已提供真实键位注册事件及 Options 注入点；本样本未声明新键位",
            32:"当前引用来自客户端资源包上下文；AddPackFindersEvent 类型也可用于服务端",
            34:"客户端事件由 Unity 提供器按已支持的回调接入",
            37:"公共代理可被两端加载；查询当前服务端不是本次玩法所需调用",
            38:"已提供游戏模式判定；正常启动时跳过作者的数据生成工作",
            39:"Unity 构造 SPI 调用作者的 onConstructMod；额外重载钩子未作为通用实现",
            40:"查询模组版本的通用元数据接口，未作为本次通过项",
            41:"已实现 optional 注册、双向处理器及按连接握手；两名玩家与专服都有匹配及未匹配通道的对照",
            49:"由 NeoForgeClientProxy 创建的客户端注册器，属于配置阶段 S2C；与 play 握手分开",
            51:"作者原生 NeoForge 事件工厂的注册表修改通知；Unity 走选定的服务提供器",
            56:"泛型父类绑定 ClientConfigurationPacketListenerImpl，接收者是客户端",
            57:"提供可供 Mixin 访问的品牌字段；未对开发模式品牌编辑作玩法验收"}
        note=notes.get(index, "客户端专用配置、渲染或事件适配；本次未单独验证该接口" if phase=="客户端" else "通用扩展，超出 Block Runner 本次实测调用面")
        rows.append(dict(index=index,phase=phase,kind=kind,symbol=symbol,owner=owner,definedDuringRuns=states,assessment=note))
    counts=dict(collections.Counter(r["phase"] for r in rows))
    assert counts=={"数据生成":4,"客户端":27,"公共／专服":26} and len(rows)==57
    report={"version":"0.2.2-Beta","originalLogSHA256":hashlib.sha256(raw.read_bytes()).hexdigest(),
            "puzzlesLibSHA512":library["sha512"],"blockRunnerSHA512":consumer["sha512"],"loaderSHA256":runtime["loader"],
            "counts":counts,"rows":rows}
    (EVIDENCE/"puzzles-57-phases.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n","utf8")
    md=["# Puzzles Lib 26.2.5 的 57 项原始预检缺口","",
        "公共／专服 26 项，客户端 27 项，数据生成 4 项。每项按首个报错的作者引用类归类；公共端包括专服和图形客户端。两项客户端配置网络引用通过客户端代理的构造代码和接收连接类型确认。","","JVM 栏表示实测进程定义了引用该符号的作者类。类中可含未执行的方法，具体行为见最后一栏。原包散列、每项完整类名和两次运行的来源依据见同名 JSON 及 beta-mixed-gameplay.json。","",
        "| 序号 | 引用阶段 | 原始类型或成员 | 作者类 | 已定义该类的 JVM | 验证范围 |","|---:|---|---|---|---|---|"]
    for r in rows:
        sides="、".join(k for k,v in r["definedDuringRuns"].items() if v) or "未观察到"
        md.append(f'| {r["index"]} | {r["phase"]} | `{r["symbol"]}` | `{r["owner"].rsplit("/",1)[-1]}` | {sides} | {r["assessment"]} |')
    (EVIDENCE/"puzzles-57-phases.md").write_text("\n".join(md)+"\n","utf8")
if __name__=="__main__":main()
