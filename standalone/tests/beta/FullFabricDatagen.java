package qa.fabric;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.*;
import net.minecraft.data.*;
import com.google.gson.JsonObject;

public final class FullFabricDatagen implements DataGeneratorEntrypoint {
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        generator.createPack().addProvider(
            (FabricDataGenerator.Pack.Factory<DataProvider>)output -> new DataProvider() {
                public String getName() {
                    return "Unity full API acceptance";
                }
                public CompletableFuture<?> run(CachedOutput cache) {
                    var result = new JsonObject();
                    result.addProperty("owner", generator.getModId());
                    return DataProvider.saveStable(
                        cache, result, output.getOutputFolder().resolve("data/qa23_full/audit.json"));
                }
            });
    }
}
