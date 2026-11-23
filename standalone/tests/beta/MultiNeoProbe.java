package qa.config;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
@Mod("qa_archive_neo")
public final class MultiNeoProbe {
    public MultiNeoProbe(ModContainer container) {
        var info = container.getModInfo();
        if (!info.getDisplayName().equals("A \"quoted\" name") ||
            !info.getDescription().contains("[[accessTransformers]]\nfile = \"absent.cfg\"") ||
            !info.getModId().equals("qa_archive_neo"))
            throw new IllegalStateException("Incorrect NeoForge TOML or container");
        System.out.println("QA22 NEO MULTIMOD owner=qa_archive_neo escaped=true multiline=true");
    }
}
