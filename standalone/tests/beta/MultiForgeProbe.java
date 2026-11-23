package qa.forgeconfig;
import java.io.*;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
@Mod("qa_archive_mod")
public final class MultiForgeProbe {
    public MultiForgeProbe(FMLJavaModLoadingContext context) throws IOException {
        try (var stream = MultiForgeProbe.class.getClassLoader().getResourceAsStream("qa22/layer.txt")) {
            String data = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (VersionLayer.value() != 25 || !data.equals("java25") ||
                !context.getActiveNamespace().equals("qa_archive_mod"))
                throw new IllegalStateException("Wrong multi-release layer or Forge owner");
        }
        System.out.println("QA22 FORGE MULTIMOD java=25 resource=java25 owner=qa_archive_mod");
    }
}
class VersionLayer {
    static int value() {
        return 0;
    }
}
