import java.util.*;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.electronwill.nightconfig.core.CommentedConfig;
class ConfigTypes {
    enum Choice { FIRST,SECOND }
    public static void main(String[] args){
        var builder=new ModConfigSpec.Builder();
        builder.define("list",List.of("initial"));builder.define("choice",Choice.FIRST);
        builder.define("number",Integer.valueOf(3));var spec=builder.build();
        var config=CommentedConfig.inMemory();config.set("list",new ArrayList<>(List.of("custom")));
        config.set("choice","SECOND");config.set("number",12L);
        if(!spec.isCorrect(config))throw new AssertionError("Valid TOML decoded types rejected");
        config.set("number",12.7d);
        if(spec.isCorrect(config))throw new AssertionError("Non-integral TOML coerced into an integer");
        spec.correct(config);if(!config.get("list").equals(List.of("custom")) || !(config.get("number") instanceof Integer))throw new AssertionError("correction");
        System.out.println("PASS TOML list, enum and exact numeric conversion");
    }
}
