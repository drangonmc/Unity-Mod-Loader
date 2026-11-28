package mc.unity.plugins;

import org.bukkit.block.data.type.Beehive;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BeehiveFacade implements Beehive {
    protected final Beehive view;
    protected BeehiveFacade(Beehive view, BlockState initial) { this.view = view; }
    static Beehive create(Beehive view, BlockState initial) {
        try {
            return (Beehive) Class.forName("org.bukkit.craftbukkit.block.impl.CraftBeehive")
                .getConstructor(Beehive.class,BlockState.class).newInstance(view,initial);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Creating beehive data",e); }
    }
    @Override public boolean equals(Object other){return this==other || view.equals(other);}
    @Override public int hashCode(){return view.hashCode();}
    @Override public String toString(){return view.toString();}
    @Override public Beehive clone() { return (Beehive) view.clone(); }
}
