package qa.reference;
import java.nio.file.Path;
import mc.unity.api.ClientTickMod;
import mc.unity.core.PlayNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;

public final class OrderProbe implements ClientTickMod {
    private record Mark(int slot) implements CustomPacketPayload {
        static final Type<Mark> TYPE=new Type<>(Identifier.parse("reference:order"));
        public Type<Mark> type(){ return TYPE; }
    }
    private PlayNetwork.Context previousContext;
    private net.minecraft.server.level.ServerPlayer previousPlayer;
    public void initialize(Path data) {
        StreamCodec<RegistryFriendlyByteBuf,Mark> codec=StreamCodec.composite(ByteBufCodecs.VAR_INT,Mark::slot,Mark::new);
        PlayNetwork.add(Mark.TYPE.id(),1,PacketFlow.SERVERBOUND,Mark.TYPE,codec,(packet,ctx)->{
            var current=ctx.sender();
            if (!ctx.executor().isSameThread() || current.getInventory().getSelectedSlot()!=packet.slot())
                throw new IllegalStateException("Unexpected held slot or callback thread");
            if (previousContext!=null && (previousPlayer==current || previousContext.sender()!=current))
                throw new IllegalStateException("Expected the current player after respawn");
            previousContext=ctx; previousPlayer=current;
            System.out.println("REFERENCE ordered slot "+packet.slot()+" currentPlayer=true");
        },true);
    }
    private Object lastLocalPlayer;
    private int turn;
    public void clientTick() {
        var game=net.minecraft.client.Minecraft.getInstance();var p=game.player;
        if (p==null || p==lastLocalPlayer || p.tickCount<25 || p.isDeadOrDying() || game.getConnection()==null) return;
        lastLocalPlayer=p;
        int slot=++turn==1 ? 3 : 5;
        p.getInventory().setSelectedSlot(slot);
        game.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        PlayNetwork.sendToServer(new Mark(slot));
    }
}
