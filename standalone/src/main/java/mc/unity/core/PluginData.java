package mc.unity.core;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;

/** Persistent plugin tags share the existing entity attachment serialization and respawn path. */
public final class PluginData {
    public static final Attachments.Key<CompoundTag> KEY=new Attachments.Key<>(){
        public Identifier id(){return Identifier.fromNamespaceAndPath("unity","bukkit_pdc");}
        public com.mojang.serialization.Codec<CompoundTag> codec(){return CompoundTag.CODEC;}
        public boolean copyOnDeath(){return true;}
    };
    private PluginData(){}
}
