package net.minecraftforge.api.distmarker;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Repeatable(OnlyIns.class)
public @interface OnlyIn {
    Dist value();
    Class<?> _interface() default Object.class;
}
