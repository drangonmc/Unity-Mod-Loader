package net.neoforged.neoforge.client.event;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.bus.api.Event;
public class RegisterKeyMappingsEvent extends Event implements IModBusEvent {
    private final Options options;
    private final Map<Identifier,KeyMapping.Category> categories=new TreeMap<>();
    private final List<KeyMapping> additions=new ArrayList<>();
    public RegisterKeyMappingsEvent(Options options){this.options=options;}
    public void register(KeyMapping mapping){additions.add(Objects.requireNonNull(mapping));registerCategory(mapping.getCategory());}
    public void registerCategory(KeyMapping.Category category){categories.putIfAbsent(category.id(),category);}
    public void sortAndStoreCategories(List<KeyMapping.Category> target){for(var cat:categories.values()) if(!target.contains(cat))target.add(cat);}
    public static KeyMapping[] collect(KeyMapping[] existing, Options options) {
        var event=new RegisterKeyMappingsEvent(options);
        for(var info:ModList.get().getMods()) ModList.get().getModContainerById(info.getModId()).orElseThrow().getEventBus().post(event);
        if (!event.categories.isEmpty()) {
            try {
                var order=java.lang.invoke.MethodHandles.privateLookupIn(KeyMapping.Category.class,java.lang.invoke.MethodHandles.lookup())
                    .findStaticVarHandle(KeyMapping.Category.class,"SORT_ORDER",List.class);
                @SuppressWarnings("unchecked") var actual=(List<KeyMapping.Category>)order.get();
                event.sortAndStoreCategories(actual);
            } catch(ReflectiveOperationException e) {throw new IllegalStateException("Key mapping categories",e);}
        }
        if(event.additions.isEmpty())return existing;
        var merged=new ArrayList<>(Arrays.asList(existing));merged.addAll(event.additions);
        return merged.toArray(KeyMapping[]::new);
    }
}
