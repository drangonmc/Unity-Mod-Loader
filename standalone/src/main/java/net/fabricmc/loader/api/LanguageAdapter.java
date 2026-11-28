package net.fabricmc.loader.api;
public interface LanguageAdapter {
    static LanguageAdapter getDefault(){return mc.unity.adapter.fabric.LoaderView.DEFAULT_ADAPTER;}
    <T>T create(ModContainer owner,String definition,Class<T> type) throws LanguageAdapterException;
}
