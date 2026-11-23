package qa.config;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Final-priority test hook. Existing third-party registrations are counted; all extra channels use the
 * public SDK.
 */
final class LoginCapacity {
    record Marker(Type<Marker> type) implements CustomPacketPayload {}
    static void bind() {
        if (!java.nio.file.Files.isRegularFile(java.nio.file.Path.of("config/qa22-capacity")))
            return;
        try {
            var signal = Class.forName("mc.unity.core.Events").getField("PLAY_REGISTRATION").get(null);
            signal.getClass()
                .getMethod("listen", byte.class, Consumer.class)
                .invoke(signal, Byte.MIN_VALUE, (Consumer<Object>)ignored -> fill());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
    private static void fill() {
        try {
            var f = Class.forName("mc.unity.core.PlayNetwork").getDeclaredField("registrations");
            f.setAccessible(true);
            int existing = 0;
            for(var binding:((Map<?,?>)f.get(null)).values()){
                var optional = binding.getClass().getDeclaredMethod("optional");
                optional.setAccessible(true);
                if ((boolean)optional.invoke(binding))
                    existing++;
            }
            var api = new PayloadRegistrar("capacity").optional();
            for (int i = existing; i < 256; i++) {
                var type =
                    new CustomPacketPayload.Type<Marker>(Identifier.parse("qa_server_config:capacity/" + i));
                api.playToClient(type, StreamCodec.unit(new Marker(type)), (value, context) -> {});
            }
            System.out.println("QA22 CAPACITY declared=256 previous=" + existing);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
