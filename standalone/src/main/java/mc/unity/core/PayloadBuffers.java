package mc.unity.core;

import java.util.function.Consumer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** Cap the mod's writable packet body before invoking its codec. Commit the header and body together. */
final class PayloadBuffers {
    static void write(FriendlyByteBuf destination, Identifier id, int limit,
                      Consumer<FriendlyByteBuf> encode) {
        ByteBuf body = destination.alloc().buffer(Math.min(128, limit), limit);
        try {
            FriendlyByteBuf view = destination instanceof RegistryFriendlyByteBuf game
                                       ? new RegistryFriendlyByteBuf(body, game.registryAccess())
                                       : new FriendlyByteBuf(body);
            try {
                encode.accept(view);
            } catch (IndexOutOfBoundsException invalid) {
                throw new IllegalArgumentException("Invalid payload write " + id + " (limit " + limit + ")",
                                                   invalid);
            }
            int mark = destination.writerIndex();
            try {
                destination.writeIdentifier(id);
                destination.writeBytes(body, 0, body.writerIndex());
            } catch (RuntimeException | Error failure) {
                destination.writerIndex(mark);
                throw failure;
            }
        } finally {
            body.release();
        }
    }
}
