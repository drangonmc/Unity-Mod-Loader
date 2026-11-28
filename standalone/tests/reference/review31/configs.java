import java.util.*;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.electronwill.nightconfig.core.CommentedConfig;
class ConfigTypes {
    enum Choice { FIRST { public int order(){return 1;} },SECOND { public int order(){return 2;} }; public abstract int order(); }
    public static void main(String[] args){
        var builder=new ModConfigSpec.Builder();
        builder.define("list",List.of("initial"));builder.define("choice",Choice.FIRST);
        builder.define("fraction",Float.valueOf(1));builder.define("number",Integer.valueOf(3));var spec=builder.build();
        var config=CommentedConfig.inMemory();config.set("list",new ArrayList<>(List.of("custom")));
        config.set("choice","SECOND");config.set("number",12L);config.set("fraction",1d);
        if(!spec.isCorrect(config))throw new AssertionError("Valid TOML decoded types rejected");
        config.set("choice",Choice.SECOND);if(!spec.isCorrect(config))throw new AssertionError("Enum constant body");
        config.set("fraction",1e300);if(spec.isCorrect(config))throw new AssertionError("Float overflow");
        config.set("fraction",3.0f);config.set("number",12.7d);
        if(spec.isCorrect(config))throw new AssertionError("Non-integral TOML coerced into an integer");
        spec.correct(config);if(!config.get("list").equals(List.of("custom")) || !(config.get("number") instanceof Integer))throw new AssertionError("correction");
        System.out.println("PASS TOML list, enum and exact numeric conversion");
    }
}
