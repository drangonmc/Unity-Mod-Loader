package net.neoforged.neoforge.attachment;

import io.netty.buffer.Unpooled;
import java.util.*;
import mc.unity.core.Attachments;
import mc.unity.core.PlayNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class AttachmentWire {
    private static final int MAX=1<<18;
    private static final Map<Entity,Set<UUID>> tracking=new WeakHashMap<>();
    private static volatile boolean active;
    private record Data(Identifier dimension,boolean chunk,long target,Identifier key,boolean present,byte[] bytes) implements CustomPacketPayload {
        static final Type<Data> TYPE=new Type<>(Identifier.parse("unity:attachments"));
        public Type<Data> type(){return TYPE;}
    }
    static synchronized void install(){
        if(active)return;
        StreamCodec<RegistryFriendlyByteBuf,Data> codec=StreamCodec.of((b,v)->{
            b.writeIdentifier(v.dimension);b.writeBoolean(v.chunk);b.writeLong(v.target);b.writeIdentifier(v.key);b.writeBoolean(v.present);b.writeByteArray(v.bytes);
        },b->new Data(b.readIdentifier(),b.readBoolean(),b.readLong(),b.readIdentifier(),b.readBoolean(),b.readByteArray(MAX)));
        PlayNetwork.add(Data.TYPE.id(),1,PacketFlow.CLIENTBOUND,Data.TYPE,codec,(d,ctx)->receive(d),true);active=true;
    }
    public static synchronized void close(){active=false;synchronized(tracking){tracking.clear();}}
    public static void pair(Entity entity,ServerPlayer player){
        if(!active)return;
        synchronized(tracking){tracking.computeIfAbsent(entity,e->new HashSet<>()).add(player.getUUID());}
        initial((IAttachmentHolder)entity,player);
    }
    public static void unpair(Entity entity,ServerPlayer player){
        if(!active)return;synchronized(tracking){var peers=tracking.get(entity);if(peers!=null){peers.remove(player.getUUID());if(peers.isEmpty())tracking.remove(entity);}}
    }
    public static void chunkSent(LevelChunk chunk,ServerPlayer player){if(active)initial((IAttachmentHolder)(Object)chunk,player);}
    public static void self(ServerPlayer player){if(active)initial((IAttachmentHolder)player,player);}
    private static void initial(IAttachmentHolder holder,ServerPlayer p){
        for(var entry:Attachments.presentKeys(holder))if(entry instanceof AttachmentType<?> type && type.syncHandler!=null)send(holder,type,List.of(p),true);
    }
    static void changed(IAttachmentHolder holder,AttachmentType<?> type){
        if(!active || type.syncHandler==null)return;
        if(holder instanceof Entity e && e.level() instanceof ServerLevel level){
            if(!level.getServer().isSameThread()){level.getServer().execute(()->{if(!e.isRemoved())changed(holder,type);});return;}
            Set<UUID> ids;
            synchronized(tracking){ids=new HashSet<>(tracking.getOrDefault(e,Set.of()));}
            if(e instanceof ServerPlayer p && p.connection!=null && level.getServer().getPlayerList().getPlayer(p.getUUID())==p)ids.add(p.getUUID());
            send(holder,type,ids.stream().map(level.getServer().getPlayerList()::getPlayer).filter(Objects::nonNull).toList(),false);
        }else if(holder instanceof LevelChunk chunk && chunk.getLevel() instanceof ServerLevel level){
            if(!level.getServer().isSameThread()){level.getServer().execute(()->{if(level.getChunkSource().getChunkNow(chunk.getPos().x(),chunk.getPos().z())==chunk)changed(holder,type);});return;}
            send(holder,type,level.getChunkSource().chunkMap.getPlayers(chunk.getPos(),false),false);
        }
    }
    private static <T> void send(IAttachmentHolder holder,AttachmentType<T> type,List<ServerPlayer> players,boolean initial){
        var level=holder instanceof Entity e?e.level():((LevelChunk)(Object)holder).getLevel();
        var targets=players.stream().filter(p->p.level()==level && type.syncHandler.sendToPlayer(holder,p)).toList();
        if(targets.isEmpty())return;
        var value=Attachments.find(holder,type);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(128,MAX),level.registryAccess());
        try{
            if(value!=null)type.syncHandler.write(buffer,value,initial);
            byte[] bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);
            boolean chunk=holder instanceof LevelChunk;
            long id=chunk?((LevelChunk)(Object)holder).getPos().pack():((Entity)holder).getId();
            var packet=new ClientboundCustomPayloadPacket(new Data(level.dimension().identifier(),chunk,id,type.id(),value!=null,bytes));
            for(var to:targets)to.connection.send(packet);
        }finally{buffer.release();}
    }
    private static void receive(Data d){
        var p=PlayNetwork.localPlayer();var level=p.level();if(!level.dimension().identifier().equals(d.dimension))return;
        Object target=d.chunk?level.getChunkSource().getChunkNow(ChunkPos.getX(d.target),ChunkPos.getZ(d.target)):level.getEntity((int)d.target);
        if(!(target instanceof IAttachmentHolder holder))return;
        if(!(Attachments.lookup(d.key) instanceof AttachmentType<?> type) || type.syncHandler==null)throw new IllegalArgumentException("Unknown synced attachment "+d.key);
        var b=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(d.bytes),level.registryAccess());
        try{apply(holder,type,b,d.present);}finally{b.release();}
    }
    private static <T> void apply(IAttachmentHolder holder,AttachmentType<T> type,RegistryFriendlyByteBuf b,boolean present){
        var next=present?type.syncHandler.read(holder,b,Attachments.find(holder,type)):null;
        if(b.isReadable())throw new IllegalArgumentException("Unread attachment data "+type.id());
        Attachments.set(holder,type,next);
    }
}
