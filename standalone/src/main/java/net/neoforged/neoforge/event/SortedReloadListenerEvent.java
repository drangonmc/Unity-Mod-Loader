package net.neoforged.neoforge.event;
import java.util.*;
import java.util.function.Function;
import com.google.common.graph.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.bus.api.Event;
public abstract class SortedReloadListenerEvent extends Event {
    public interface NameLookup extends Function<PreparableReloadListener, Identifier> {}
    private final Map<Identifier, PreparableReloadListener> registry = new LinkedHashMap<>();
    private final Map<PreparableReloadListener, Identifier> names = new IdentityHashMap<>();
    private final MutableGraph<PreparableReloadListener> graph = GraphBuilder.directed().build();
    private final PreparableReloadListener last;
    protected SortedReloadListenerEvent(List<PreparableReloadListener> vanilla, NameLookup lookup) {
        PreparableReloadListener previous = null;
        for (var listener : vanilla) {
            addListener(lookup.apply(listener), listener);
            if (previous != null)
                graph.putEdge(previous, listener);
            previous = listener;
        }
        last = Objects.requireNonNull(previous);
    }
    public void addListener(Identifier id, PreparableReloadListener listener) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(listener);
        if (registry.containsKey(id) || names.containsKey(listener))
            throw new IllegalArgumentException("Duplicate reload listener " + id);
        registry.put(id, listener);
        names.put(listener, id);
        graph.addNode(listener);
    }
    public void addDependency(Identifier before, Identifier after) {
        graph.putEdge(Objects.requireNonNull(registry.get(before), before.toString()),
                      Objects.requireNonNull(registry.get(after), after.toString()));
    }
    public Graph<PreparableReloadListener> getGraph() { return ImmutableGraph.copyOf(graph); }
    public Map<Identifier, PreparableReloadListener> getRegistry() {
        return Collections.unmodifiableMap(registry);
    }
    public NameLookup getNameLookup() { return names::get; }
    public PreparableReloadListener getLastVanillaListener() { return last; }
    public List<PreparableReloadListener> unityOrder() {
        var done = new LinkedHashSet<PreparableReloadListener>();
        while (done.size() < registry.size()) {
            var next = registry.values()
                           .stream()
                           .filter(x -> !done.contains(x) && done.containsAll(graph.predecessors(x)))
                           .findFirst()
                           .orElseThrow(
                               () -> new IllegalStateException("Reload listener cycle " + registry.keySet()));
            done.add(next);
        }
        return List.copyOf(done);
    }
}
