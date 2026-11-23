package net.minecraft.world.item;
import java.util.*;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.flag.FeatureFlagSet;
/** Mutable, ordered entries for one vanilla creative build. Both output views are committed together. */
public class UnityTabContents implements CreativeModeTab.Output {
    protected final CreativeModeTab.ItemDisplayParameters context;
    protected final List<ItemStack> display, search;
    public UnityTabContents(CreativeModeTab.ItemDisplayParameters context, List<ItemStack> display,
                            List<ItemStack> search) {
        this.context = context;
        this.display = display;
        this.search = search;
    }
    public UnityTabContents(UnityTabContents shared) {
        this(shared.context, shared.display, shared.search);
    }
    private static final java.lang.reflect.Field TAB = field("tabContents", Collection.class),
                                                 SEARCH = field("searchTabContents", Set.class);
    private static java.lang.reflect.Field field(String name, Class<?> expected) {
        try {
            var owner = Class.forName(CreativeModeTab.class.getName() + "$ItemDisplayBuilder", false,
                                      CreativeModeTab.class.getClassLoader());
            var f = owner.getDeclaredField(name);
            if (f.getType() != expected || java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                throw new NoSuchFieldException(owner.getName() + "." + name + ": " + expected.getName());
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
    public static void verifyRuntime() { /* Trigger field resolution during registry bootstrap. */ }
    public static void edit(Object raw, CreativeModeTab.ItemDisplayParameters context,
                            java.util.function.Consumer<UnityTabContents> build) {
        try {
            var tab = (Collection<ItemStack>)TAB.get(raw);
            var search = (Collection<ItemStack>)SEARCH.get(raw);
            var view = new UnityTabContents(context, new ArrayList<>(tab), new ArrayList<>(search));
            build.accept(view);
            tab.clear();
            tab.addAll(view.display);
            search.clear();
            search.addAll(view.search);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unexpected 26.2 creative output", e);
        }
    }
    public CreativeModeTab.ItemDisplayParameters getContext() {
        return context;
    }
    public FeatureFlagSet getEnabledFeatures() {
        return context.enabledFeatures();
    }
    public boolean shouldShowOpRestrictedItems() {
        return context.hasPermissions();
    }
    public List<ItemStack> getDisplayStacks() {
        return display;
    }
    public List<ItemStack> getSearchTabStacks() {
        return search;
    }
    public void accept(ItemStack s, CreativeModeTab.TabVisibility visibility) {
        insert(null, List.of(s), true, visibility);
    }
    public void insertAfter(ItemLike anchor, ItemLike... values) {
        insert(new ItemStack(anchor), Arrays.stream(values).map(ItemStack::new).toList(), true,
               CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
    public void insertBefore(ItemLike anchor, ItemLike... values) {
        insert(new ItemStack(anchor), Arrays.stream(values).map(ItemStack::new).toList(), false,
               CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
    public void insertAfter(ItemStack anchor, ItemStack value, CreativeModeTab.TabVisibility visibility) {
        insert(anchor, List.of(value), true, visibility);
    }
    public void insertBefore(ItemStack anchor, ItemStack value, CreativeModeTab.TabVisibility visibility) {
        insert(anchor, List.of(value), false, visibility);
    }
    private void insert(ItemStack anchor, List<ItemStack> values, boolean after,
                        CreativeModeTab.TabVisibility visibility) {
        if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY)
            insert(display, anchor, values, after);
        if (visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY)
            insert(search, anchor, values, after);
    }
    private void insert(List<ItemStack> list, ItemStack anchor, List<ItemStack> values, boolean after) {
        int i = list.size();
        if (anchor != null)
            for (int j = 0; j < list.size(); j++)
                if (ItemStack.isSameItemSameComponents(list.get(j), anchor)) {
                    i = j + (after ? 1 : 0);
                    break;
                }
        for (var value : values) {
            if (value.getCount() != 1)
                throw new IllegalArgumentException("Creative stack count must be one");
            if (value.getItem().isEnabled(context.enabledFeatures()) &&
                list.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, value)))
                list.add(i++, value);
        }
    }
}
