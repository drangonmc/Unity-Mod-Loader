package qa.neocontent;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
@Mod("qa_neo_content")
public final class NeoParent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("qa_neo_content");
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("qa_neo_content");
    public static final DeferredBlock<Counter> COUNTER = BLOCKS.registerBlock("counter", Counter::new);
    public static final DeferredItem<BlockItem> COUNTER_ITEM = ITEMS.registerSimpleBlockItem(COUNTER);
    static {
        for (String mode : new String[] {"pass", "fail", "commit"})
            ITEMS.registerItem(mode, props -> new Writer(props, mode));
    }
    private static String sequence = "";
    private static volatile int last;
    public static final class LocalEvent extends Event implements ICancellableEvent {}
    public NeoParent(IEventBus bus) {
        if (qa.lib.Version.secret() != 13 || qa.lib.Version.marker != 13)
            throw new IllegalStateException("Wrong nested NeoForge library");
        qa.lib.Version.marker = 14;
        BLOCKS.register(bus);
        ITEMS.register(bus);
        Consumer<LocalEvent> high = e -> {
            sequence += "H";
            e.setCanceled(true);
        };
        Consumer<LocalEvent> monitor =
            System.getProperty("java.version") != null ? NeoParent::observe : e -> sequence += "unexpected";
        bus.addListener(EventPriority.HIGH, high);
        bus.addListener(EventPriority.LOW, (LocalEvent e) -> sequence += "SKIPPED");
        bus.addListener(EventPriority.LOWEST, true, monitor);
        bus.post(new LocalEvent());
        if (!sequence.equals("HM"))
            throw new IllegalStateException("Listener priority or receiveCanceled: " + sequence);
        Consumer<RegisterPayloadHandlersEvent> network = NeoParent::register;
        bus.addListener(network);
        System.out.println("QA_NESTED_NEO " + qa.lib.Version.secret() + " " + sequence);
    }
    private static void observe(LocalEvent e) {
        if (e.isCanceled())
            sequence += "M";
    }
    private record Ping(int value) implements CustomPacketPayload {
        static final Type<Ping> ID = new Type<>(Identifier.parse("qa_neo_content:ping"));
        public Type<Ping> type() {
            return ID;
        }
    }
    private record Pong(int value) implements CustomPacketPayload {
        static final Type<Pong> ID = new Type<>(Identifier.parse("qa_neo_content:pong"));
        public Type<Pong> type() {
            return ID;
        }
    }
    private static void register(RegisterPayloadHandlersEvent e) {
        var reg = e.registrar("1.1-test");
        StreamCodec<RegistryFriendlyByteBuf, Ping> p =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, Ping::value, Ping::new);
        StreamCodec<RegistryFriendlyByteBuf, Pong> q =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, Pong::value, Pong::new);
        reg.playToServer(Ping.ID, p, (m, c) -> {
            System.out.println("QA_NEO_C2S " + c.player().getName().getString() + " " + m.value() + " "
                + Thread.currentThread().getName());
            c.reply(new Pong(m.value()));
        });
        reg.playToClient(Pong.ID, q, (m, c) -> {
            last = m.value();
            System.out.println("QA_NEO_S2C " + c.player().getName().getString() + " " + m.value() + " "
                + Thread.currentThread().getName());
        });
    }
    public static void ask(int value) {
        last = -1;
        ClientPacketDistributor.sendToServer(new Ping(value));
    }
    public static int last() {
        return last;
    }
    public static final class Counter extends Block {
        public Counter(BlockBehaviour.Properties props) {
            super(props);
        }
        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            super.onPlace(state, level, pos, old, moved);
            if (!level.isClientSide())
                System.out.println("QA_COUNTER_PLACE " + pos.toShortString());
        }
    }
    static final class Writer extends Item {
        final String mode;
        Writer(Properties p, String m) {
            super(p);
            mode = m;
        }
        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            if (!ctx.getLevel().isClientSide()) {
                var pos = ctx.getClickedPos().east();
                ctx.getLevel().setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                ctx.getLevel().setBlock(pos, COUNTER.get().defaultBlockState(), 3);
                ctx.getItemInHand().shrink(1);
                System.out.println("QA_WRITE " + mode + " " + pos.toShortString());
            }
            return switch (mode) {
                case "pass" -> InteractionResult.PASS;
                case "fail" -> InteractionResult.FAIL;
                default -> InteractionResult.SUCCESS;
            };
        }
    }
}
