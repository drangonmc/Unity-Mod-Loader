package mc.unity.core;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import java.util.*;
/** Preserve vanilla positions. The extra tabs use both vanilla rows and page with Page Up/Down. */
public final class TabPages {
    private record Layout(List<List<CreativeModeTab>> pages, Map<CreativeModeTab, Integer> positions) {}
    private static Layout layout;
    private static int page;
    private static Layout current() {
        if (layout != null)
            return layout;
        var vanilla = new ArrayList<CreativeModeTab>();
        var extra = new ArrayList<CreativeModeTab>();
        var pos = new IdentityHashMap<CreativeModeTab, Integer>();
        for (var t : CreativeModeTabs.tabs()) {
            if (BuiltInRegistries.CREATIVE_MODE_TAB.getKey(t).getNamespace().equals("minecraft"))
                vanilla.add(t);
            else {
                pos.put(t, extra.size());
                extra.add(t);
            }
        }
        var pages = new ArrayList<List<CreativeModeTab>>();
        pages.add(List.copyOf(vanilla));
        var pinned = vanilla.stream().filter(TabPages::pinned).toList();
        for (int i = 0; i < extra.size(); i += 12) {
            var tabs = new ArrayList<>(extra.subList(i, Math.min(extra.size(), i + 12)));
            tabs.addAll(pinned);
            pages.add(List.copyOf(tabs));
        }
        layout = new Layout(List.copyOf(pages), pos);
        page = Math.min(page, pages.size() - 1);
        return layout;
    }
    private static boolean pinned(CreativeModeTab tab) {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).getNamespace().equals("minecraft")
            && (tab.getType() == CreativeModeTab.Type.SEARCH || tab.getType() == CreativeModeTab.Type.INVENTORY);
    }
    public static void selected(CreativeModeTab t) {
        int i = current().positions.getOrDefault(t, -1);
        if (!pinned(t)) page = i < 0 ? 0 : 1 + i / 12;
    }
    public static List<CreativeModeTab> visible() {
        return current().pages.get(page);
    }
    public static int column(CreativeModeTab t) {
        int i = current().positions.getOrDefault(t, -1);
        return i < 0 ? t.column() : i % 6;
    }
    public static CreativeModeTab.Row row(CreativeModeTab t) {
        int i = current().positions.getOrDefault(t, -1);
        return i < 0 ? t.row() : i % 12 < 6 ? CreativeModeTab.Row.TOP : CreativeModeTab.Row.BOTTOM;
    }
    public static CreativeModeTab turn(int key) {
        if (key != 266 && key != 267)
            return null;
        int n = current().pages.size();
        if (n == 1)
            return null;
        page = Math.floorMod(page + (key == 267 ? 1 : -1), n);
        return visible().getFirst();
    }
    public static void invalidate() {
        layout = null;
    }
    public static void reset() {
        page = 0;
        invalidate();
    }
}
