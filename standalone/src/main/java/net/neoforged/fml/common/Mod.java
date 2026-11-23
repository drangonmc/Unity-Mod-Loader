package net.neoforged.fml.common;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();
    net.neoforged.api.distmarker.Dist[] dist() default {
        net.neoforged.api.distmarker.Dist.CLIENT, net.neoforged.api.distmarker.Dist.DEDICATED_SERVER};
}
