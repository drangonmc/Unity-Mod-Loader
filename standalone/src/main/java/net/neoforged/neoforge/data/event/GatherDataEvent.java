package net.neoforged.neoforge.data.event;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.IModBusEvent;
/** Data generation listeners may register during normal game startup. */
public abstract class GatherDataEvent extends Event implements IModBusEvent {
    private final DataGenerator generator;
    private final ModContainer owner;
    private CompletableFuture<HolderLookup.Provider> registriesWithModdedEntries;
    protected GatherDataEvent(ModContainer owner, DataGenerator generator,
                              CompletableFuture<HolderLookup.Provider> lookup) {
        this.owner = Objects.requireNonNull(owner);
        this.generator = Objects.requireNonNull(generator);
        registriesWithModdedEntries = Objects.requireNonNull(lookup);
    }
    public ModContainer getModContainer() { return owner; }
    public DataGenerator getGenerator() { return generator; }
    public CompletableFuture<HolderLookup.Provider> getLookupProvider() { return registriesWithModdedEntries; }
    public static final class Client extends GatherDataEvent {
        public Client(ModContainer owner, DataGenerator generator,
                      CompletableFuture<HolderLookup.Provider> lookup) {
            super(owner, generator, lookup);
        }
    }
    public static final class Server extends GatherDataEvent {
        public Server(ModContainer owner, DataGenerator generator,
                      CompletableFuture<HolderLookup.Provider> lookup) {
            super(owner, generator, lookup);
        }
    }
}
