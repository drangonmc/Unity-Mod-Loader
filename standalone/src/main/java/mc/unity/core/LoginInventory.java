package mc.unity.core;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;

/** Bounded diagnostic disclosure, requested only after a rejected login. It cannot authorize login. */
public record LoginInventory(Map<String, String> owners, Map<String, String> registries, boolean incomplete)
    implements CustomQueryAnswerPayload {
    static final String MAGIC = "unity-diff-1";
    private static final int LIMIT = 192;
    static LoginInventory snapshot(Map<String, String> owners, Map<String, String> registries) {
        boolean truncated = owners.size() > LIMIT || registries.size() > LIMIT ||
                            owners.keySet().stream().anyMatch(n -> n.length() > 192) ||
                            registries.keySet().stream().anyMatch(n -> n.length() > 192);
        return new LoginInventory(limited(owners), limited(registries), truncated);
    }
    private static Map<String, String> limited(Map<String, String> values) {
        var out = new TreeMap<String, String>();
        for (var e : new TreeMap<>(values).entrySet()) {
            if (out.size() == LIMIT)
                break;
            if (e.getKey().length() > 192)
                continue;
            out.put(e.getKey(), e.getValue().substring(0, Math.min(128, e.getValue().length())));
        }
        return Collections.unmodifiableMap(out);
    }
    public void write(FriendlyByteBuf b) {
        b.writeUtf(MAGIC, 64);
        b.writeBoolean(incomplete);
        write(b, owners);
        write(b, registries);
    }
    private static void write(FriendlyByteBuf b, Map<String, String> values) {
        b.writeVarInt(values.size());
        values.forEach((k, v) -> {
            b.writeUtf(k, 192);
            b.writeUtf(v, 128);
        });
    }
    static LoginInventory read(FriendlyByteBuf b) {
        if (b.readableBytes() > 262144)
            throw new IllegalArgumentException("Oversize diagnostic response");
        boolean truncated = b.readBoolean();
        var mods = readMap(b);
        var regs = readMap(b);
        if (b.isReadable())
            throw new IllegalArgumentException("Trailing diagnostic data");
        return new LoginInventory(mods, regs, truncated);
    }
    private static Map<String, String> readMap(FriendlyByteBuf b) {
        var out = new TreeMap<String, String>();
        int n = b.readVarInt();
        if (n < 0 || n > LIMIT)
            throw new IllegalArgumentException("Invalid diagnostic list");
        for (int i = 0; i < n; i++) {
            var k = b.readUtf(192);
            var v = b.readUtf(128);
            if (!k.matches("[a-z0-9_:/.-]+") || v.chars().anyMatch(Character::isISOControl) ||
                out.putIfAbsent(k, v) != null)
                throw new IllegalArgumentException("Invalid diagnostic member");
        }
        return out;
    }
    String difference(LoginInventory client) {
        var issues = new ArrayList<String>();
        owners.forEach((id, ver) -> {
            var other = client.owners.get(id);
            if (other == null && !client.incomplete)
                issues.add("客户端缺少 " + id + " " + ver);
            else if (other != null && !ver.equals(other))
                issues.add(id + " 服务端 " + ver + "，客户端 " + other);
        });
        if (!incomplete)
            client.owners.keySet()
                .stream()
                .filter(k -> !owners.containsKey(k))
                .forEach(k -> issues.add("客户端多出 " + k));
        var keys = new TreeSet<>(registries.keySet());
        keys.addAll(client.registries.keySet());
        for (String k : keys)
            if (!Objects.equals(registries.get(k), client.registries.get(k)))
                issues.add("注册表差异 " + k);
        if (incomplete || client.incomplete)
            issues.add("诊断清单已截断");
        if (issues.isEmpty())
            issues.add("加载器、依赖或必需网络通道不一致");
        return "Unity: content registry mismatch — " + String.join("；", issues.stream().limit(12).toList()) +
            (issues.size() > 12 ? "（另有 " + (issues.size() - 12) + " 项）" : "");
    }
}
