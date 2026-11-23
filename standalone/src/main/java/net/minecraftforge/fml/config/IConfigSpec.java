package net.minecraftforge.fml.config;
import com.electronwill.nightconfig.core.*;
public interface IConfigSpec<T extends IConfigSpec<T>> extends UnmodifiableConfig {
    @SuppressWarnings("unchecked")
    default T self() {
        return (T)this;
    }
    void acceptConfig(CommentedConfig config);
    boolean isCorrecting();
    boolean isCorrect(CommentedConfig config);
    int correct(CommentedConfig config);
    void afterReload();
}
