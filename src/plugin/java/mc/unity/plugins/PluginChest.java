package mc.unity.plugins;

import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.InventoryType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;

public interface PluginChest extends org.bukkit.inventory.Inventory {
    SimpleMenuProvider unityMenu();
    static PluginChest create(Host host,InventoryHolder holder,int slots,Component title) {
        if (slots<9 || slots>54 || slots%9!=0) throw new IllegalArgumentException("Chest size must be a multiple of 9 up to 54");
        int rows=slots/9;
        var type=List.of(MenuType.GENERIC_9x1,MenuType.GENERIC_9x2,MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4,MenuType.GENERIC_9x5,MenuType.GENERIC_9x6).get(rows-1);
        var contents=new SimpleContainer(slots);
        var menu=new SimpleMenuProvider((id,inv,who)->new ChestMenu(type,id,inv,contents,rows),title);
        return Views.of(PluginChest.class,title.getString(),contents,(m,a)->switch(m){
            case "unityMenu"->menu;
            case "getSize"->slots;
            case "getType"->InventoryType.CHEST;
            case "getHolder"->holder;
            case "isEmpty"->contents.isEmpty();
            case "getMaxStackSize"->contents.getMaxStackSize();
            case "getItem"->{var stack=contents.getItem((int)a[0]);yield stack.isEmpty()?null:BlockViews.Stack.mirror(stack,contents::setChanged);}
            case "setItem"->{contents.setItem((int)a[0],BlockViews.Stack.nativeCopy((ItemStack)a[1]));yield null;}
            case "getContents","getStorageContents"->{
                var result=new ItemStack[slots];
                for(int i=0;i<slots;i++){var stack=contents.getItem(i);if(!stack.isEmpty())result[i]=new BlockViews.Stack(stack);}
                yield result;
            }
            case "setContents","setStorageContents"->{
                ItemStack[] next=(ItemStack[])a[0];
                if(next.length>slots)throw new IllegalArgumentException("Overflow");
                for(int i=0;i<slots;i++)contents.setItem(i,i<next.length?BlockViews.Stack.nativeCopy(next[i]):net.minecraft.world.item.ItemStack.EMPTY);
                yield null;
            }
            case "clear"->{if(a.length==0)contents.clearContent();else contents.setItem((int)a[0],net.minecraft.world.item.ItemStack.EMPTY);yield null;}
            case "getViewers"->host.game.getPlayerList().getPlayers().stream().filter(p->p.containerMenu.slots.stream().anyMatch(s->s.container==contents)).map(p->host.views.player(p)).toList();
            default->throw Views.unsupported("Custom chest inventory",m);
        });
    }
}
