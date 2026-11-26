package qa.reference;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import mc.unity.api.UnityMod;
import mc.unity.core.*;
import java.nio.file.Path;

public class SaveProbe implements UnityMod {
    private record Key(Identifier id,Codec<Integer> codec) implements Attachments.Key<Integer> {}
    private static final Key LIMITED=new Key(Identifier.parse("reference:limited"),Codec.intRange(0,9));
    private static final Key OTHER=new Key(Identifier.parse("reference:other"),Codec.INT);
    private static final BlockPos POS=new BlockPos(3,100,3);
    public void initialize(Path data) {
        Attachments.register(LIMITED); Attachments.register(OTHER);
        Events.SERVER_STOPPING.listen(s->{throw new IllegalStateException("reference shutdown listener");});
        Events.SERVER_STOPPING.listen(s->{
            try { java.nio.file.Files.writeString(data.resolve("listener-ran"), "after exception"); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            System.out.println("REFERENCE remaining listener");
        });
        Events.LEVEL_UNLOAD.listen(level->{throw new IllegalStateException("reference unload listener");});
    }
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String cmd : new String[]{"refseed","refupdate","refread","refexit"}) {
            dispatcher.register(Commands.literal(cmd).executes(c->{
                var level=c.getSource().getLevel();
                var owner=(Attachments.Owner)level.getChunkAt(POS);
                switch(cmd) {
                case "refseed" -> {
                    Attachments.set(owner,LIMITED,7); Attachments.set(owner,OTHER,10);
                    level.setBlockAndUpdate(POS,Blocks.IRON_BLOCK.defaultBlockState());
                }
                case "refupdate" -> {
                    Attachments.set(owner,LIMITED,12); Attachments.set(owner,OTHER,14);
                    level.setBlockAndUpdate(POS,Blocks.GOLD_BLOCK.defaultBlockState());
                }
                case "refread" -> System.out.println("REFERENCE read "+Attachments.find(owner,LIMITED)+" "+
                    Attachments.find(owner,OTHER)+" "+level.getBlockState(POS).is(Blocks.GOLD_BLOCK));
                case "refexit" -> new Thread(()->System.exit(0),"reference orderly JVM exit").start();
                }
                System.out.println("REFERENCE command "+cmd);
                return 1;
            }));
        }
    }
}
