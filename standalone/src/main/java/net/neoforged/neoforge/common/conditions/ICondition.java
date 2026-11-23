package net.neoforged.neoforge.common.conditions;
import com.mojang.serialization.MapCodec;
public interface ICondition {
    interface IContext {}
    boolean test(IContext context);
    MapCodec<? extends ICondition> codec();
}
