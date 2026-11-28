package mc.unity.bootstrap;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.lang.reflect.Proxy;

/** Reopen the menu channel twice in one patched game class domain. */
public final class MenuSessions {
    public static void main(String[] args) throws Exception {
        Path engine=Path.of(args[0]), cache=Path.of(args[1]);
        var bundle=VanillaBundle.prepare(cache,cache.resolve("mojang-server-26.2.jar"));
        var urls=new ArrayList<URL>();
        urls.add(engine.toUri().toURL());
        urls.add(bundle.game().toUri().toURL());
        for (Path lib:bundle.libraries()) urls.add(lib.toUri().toURL());
        try(var game=new GameLoader(urls.toArray(URL[]::new),bundle.game())) {
            GamePatches.install(game,bundle.game());
            Thread.currentThread().setContextClassLoader(game);
            var factory=game.loadClass("net.neoforged.neoforge.network.IContainerFactory");
            var probe=Proxy.newProxyInstance(game,new Class<?>[]{factory},(o,m,a)->null);
            var menu=game.loadClass("mc.unity.adapter.neoforge.MenuData").getMethod("checkFactory",Object.class);
            var net=game.loadClass("mc.unity.core.PlayNetwork");
            var manifest=net.getMethod("manifest");
            var platforms=game.loadClass("mc.unity.runtime.Platforms");
            for(int session=1;session<=2;session++){
                try(var runtime=(AutoCloseable)platforms.getConstructor().newInstance()){
                    menu.invoke(null,probe);menu.invoke(null,probe);
                    var ids=(java.util.List<?>)manifest.invoke(null);
                    if (!ids.equals(java.util.List.of("unity:menu_data 1 CLIENTBOUND unity:menu_data"))) throw new AssertionError(ids);
                }
            }
            if (!((java.util.List<?>)manifest.invoke(null)).isEmpty()) throw new AssertionError("leaked channel after close");
            System.out.println("PASS two Platforms sessions register and clear unity:menu_data");
        }
    }
}
