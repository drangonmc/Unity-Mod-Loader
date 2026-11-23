package net.neoforged.neoforge.common.util;
import net.minecraft.world.level.storage.*;
public interface ValueIOSerializable {
    void serialize(ValueOutput out);
    void deserialize(ValueInput in);
}
