package net.neoforged.fml.i18n;
import java.text.MessageFormat;
import java.util.function.Supplier;
import net.minecraft.locale.Language;
public final class FMLTranslations {
    public static String parseMessageWithFallback(String key, Supplier<String> fallback, Object... args) {
        var language = Language.getInstance();
        String text = language.has(key) ? language.getOrDefault(key) : fallback.get();
        return args.length == 0 ? text : MessageFormat.format(text, args);
    }
}
