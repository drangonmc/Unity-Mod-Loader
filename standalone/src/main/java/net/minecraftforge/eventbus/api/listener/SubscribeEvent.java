package net.minecraftforge.eventbus.api.listener;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
    byte priority() default 0;
    boolean alwaysCancelling() default false;
}
