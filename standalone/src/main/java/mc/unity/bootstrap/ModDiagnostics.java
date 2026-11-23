package mc.unity.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Keep the original exception intact, with a short report alongside the normal game logs. */
public final class ModDiagnostics {
    public static void failure(Path game, String phase, Throwable cause, Map<String, String> mixins) {
        if (game == null)
            return;
        var text = new StringBuilder("Unity 模组加载失败\n阶段 ").append(phase).append('\n');
        var messages = new LinkedHashSet<String>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var e = cause; e != null && seen.add(e); e = e.getCause()) {
            String message = e.getMessage();
            if (message != null && !message.isBlank())
                messages.add(message);
        }
        String details = String.join("\n", messages);
        if (phase.startsWith("Mixin")) {
            text.append(
                "字节码转换失败。请核对以下配置所属模组与当前 MC 版本，并检查修改同一目标的其他模组。\n");
            var hits = mixins.entrySet().stream().filter(e -> details.contains(e.getKey())).toList();
            if (hits.isEmpty() && !mixins.isEmpty())
                text.append("异常未指明责任 JAR。已启用的 Mixin 配置如下。\n");
            for (var e : hits.isEmpty() ? mixins.entrySet() : hits)
                text.append(e.getKey()).append(" → ").append(e.getValue()).append('\n');
        } else if (details.contains("Unsupported platform")) {
            text.append("以下原始 JAR 引用了当前适配层缺少的 API。请核对模组的平台与游戏版本。\n");
        } else
            text.append("请核对下列模组文件、依赖和版本要求。\n");
        text.append(details.isBlank() ? cause.toString() : details);
        String report = text.toString();
        System.err.println("[Unity] " + report);
        try {
            Path dir = Files.createDirectories(game.resolve("logs"));
            Path stage = Files.createTempFile(dir, ".unity-diagnostic-", ".tmp");
            try {
                Files.writeString(stage, report, StandardCharsets.UTF_8);
                Files.move(stage, dir.resolve("unity-mod-error.txt"), StandardCopyOption.ATOMIC_MOVE,
                           StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(stage);
            }
        } catch (IOException | SecurityException io) {
            cause.addSuppressed(io);
        }
    }
}
