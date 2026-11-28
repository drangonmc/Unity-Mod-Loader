package mc.unity.adapter.neoforge;

import java.util.*;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import mc.unity.core.PlayNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.neoforged.neoforge.network.IContainerFactory;

public final class MenuData {
    private static final int MAX_BYTES=1<<16;
    private record Data(int container,Identifier menu,byte[] bytes) implements CustomPacketPayload {
        static final Type<Data> TYPE=new Type<>(Identifier.fromNamespaceAndPath("unity","menu_data"));
        public Type<Data> type(){return TYPE;}
    }
    private static final ThreadLocal<Consumer<RegistryFriendlyByteBuf>> WRITER=new ThreadLocal<>();
    private static final Map<Player,Data> received=new WeakHashMap<>();
    private static boolean registered;
    public static synchronized void checkFactory(Object factory){
        if(registered || !(factory instanceof IContainerFactory<?>))return;
        StreamCodec<RegistryFriendlyByteBuf,Data> codec=StreamCodec.of((buf,d)->{
            buf.writeVarInt(d.container);buf.writeIdentifier(d.menu);buf.writeByteArray(d.bytes);
        },buf->new Data(buf.readVarInt(),buf.readIdentifier(),buf.readByteArray(MAX_BYTES)));
        PlayNetwork.add(Data.TYPE.id(),1,PacketFlow.CLIENTBOUND,Data.TYPE,codec,(d,ctx)->{
            synchronized(received){received.put(PlayNetwork.localPlayer(),d);}
        },true);
        registered=true;
    }
    public static synchronized void close(){WRITER.remove();registered=false;synchronized(received){received.clear();}}
    public static OptionalInt open(Player p,MenuProvider provider,Consumer<RegistryFriendlyByteBuf> writer) {
        if(!(p instanceof ServerPlayer sp))return OptionalInt.empty();
        var previous=WRITER.get();WRITER.set(Objects.requireNonNull(writer));
        try{return sp.openMenu(provider);}
        finally{if(previous==null)WRITER.remove();else WRITER.set(previous);}
    }
    public static OptionalInt open(Player p,MenuProvider provider,BlockPos pos){return open(p,provider,b->b.writeBlockPos(pos));}
    public static void beforeOpen(ClientboundOpenScreenPacket packet,ServerPlayer p) {
        var writer=WRITER.get();if(writer==null)return;
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(256,MAX_BYTES),p.registryAccess());
        try{
            writer.accept(buffer);
            byte[] data=new byte[buffer.readableBytes()];buffer.readBytes(data);
            var info=new Data(packet.getContainerId(),BuiltInRegistries.MENU.getKey(packet.getType()),data);
            p.connection.send(new ClientboundCustomPayloadPacket(info));
        }finally{buffer.release();}
    }
    public static AbstractContainerMenu create(Object factory,MenuType<?> type,int id,Inventory inv) {
        var f=(IContainerFactory<?>)factory;
        Data d;
        synchronized(received){d=received.remove(inv.player);}
        if(d==null || d.container!=id || !d.menu.equals(BuiltInRegistries.MENU.getKey(type)))return f.create(id,inv,null);
        var buf=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(d.bytes),inv.player.registryAccess());
        try{return f.create(id,inv,buf);}finally{buf.release();}
    }
}
