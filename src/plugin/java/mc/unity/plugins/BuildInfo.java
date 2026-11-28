package mc.unity.plugins;

import io.papermc.paper.ServerBuildInfo;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.key.Key;

public final class BuildInfo implements ServerBuildInfo {
    public Key brandId() {
        return Key.key("unity", "server");
    }
    public boolean isBrandCompatible(Key key) {
        return brandId().equals(key);
    }
    public String brandName() {
        return "Unity";
    }
    public String minecraftVersionId() {
        return "26.2";
    }
    public String minecraftVersionName() {
        return "26.2";
    }
    public OptionalInt buildNumber() {
        return OptionalInt.empty();
    }
    public Instant buildTime() {
        try {
            return java.nio.file.Files
                .getLastModifiedTime(java.nio.file.Path.of(
                    BuildInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI()))
                .toInstant();
        } catch (Exception e) {
            throw new IllegalStateException("Reading Unity build timestamp", e);
        }
    }
    public Optional<String> gitBranch() {
        return Optional.empty();
    }
    public Optional<String> gitCommit() {
        return Optional.empty();
    }
    public String asString(StringRepresentation representation) {
        return "Unity 0.3.1-Beta (MC 26.2)";
    }
}
