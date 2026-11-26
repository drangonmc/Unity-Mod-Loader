package qa.fabric;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;

public final class InteropStorageProbe implements ModInitializer {
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((commands, context, side) -> {
            for (boolean write : new boolean[] {true, false})
                commands.register(
                    Commands.literal(write ? "qa24_attach_write" : "qa24_attach_read").executes(c -> {
                        var level = c.getSource().getLevel();
                        var block = level.getBlockEntity(new BlockPos(7, 201, 8));
                        var fabric = (AttachmentTarget)block;
                        var neo = (IAttachmentHolder)block;
                        if (write) {
                            fabric.setAttached(FabricEcologyProbe.NOTE, "fabric-only");
                            neo.setData(qa.config.ServerConfigProbe.SCORE, 42);
                            block.setChanged();
                        }
                        var tag = block.saveWithFullMetadata(level.registryAccess());
                        var a = tag.getCompoundOrEmpty("fabric:attachments");
                        var b = tag.getCompoundOrEmpty("unity:attachments");
                        if (!"fabric-only".equals(fabric.getAttached(FabricEcologyProbe.NOTE)) ||
                            neo.getData(qa.config.ServerConfigProbe.SCORE) != 42 ||
                            !a.contains("qa_fabric_ecology:note") || a.contains("qa_server_config:score") ||
                            !b.contains("qa_server_config:score") || b.contains("qa_fabric_ecology:note"))
                            throw new IllegalStateException("Attachment stores " + tag);
                        System.out.println("QA24 STORES " + (write ? "WRITE" : "READ") +
                                           " fabric:attachments=fabric-only unity:attachments=42");
                        return 1;
                    }));
        });
    }
}
