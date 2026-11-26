package qa.libb;
public class Facade extends org.bukkit.plugin.java.JavaPlugin {
    public static qa.libc.Types.Value value() {
        return new qa.libc.Types.Value();
    }
}
