package mc.unity.plugins;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.tree.*;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.function.Function;

/** Rebind a Paper command tree's callbacks to their API source while retaining its argument parsers. */
final class PaperTree {
    @SuppressWarnings({"rawtypes","unchecked"})
    static CommandNode<net.minecraft.commands.CommandSourceStack> convert(CommandNode<CommandSourceStack> source,String label,
            Function<net.minecraft.commands.CommandSourceStack,CommandSourceStack> view){
        ArgumentBuilder<net.minecraft.commands.CommandSourceStack,?> node;
        if(source instanceof LiteralCommandNode<?>)node=LiteralArgumentBuilder.literal(label);
        else if(source instanceof ArgumentCommandNode arg){
            RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack,Object> argument=RequiredArgumentBuilder.argument(source.getName(),arg.getType());
            var suggestions=arg.getCustomSuggestions();
            if(suggestions!=null)argument.suggests((ctx,b)->suggestions.getSuggestions(((CommandContext)ctx).copyFor(view.apply(ctx.getSource())),b));
            node=argument;
        }else throw new IllegalArgumentException("Unsupported Paper node "+source.getName());
        node.requires(s->source.getRequirement().test(view.apply(s)));
        if(source.getCommand()!=null)node.executes(ctx->source.getCommand().run(((CommandContext)ctx).copyFor(view.apply(ctx.getSource()))));
        if(source.getRedirect()!=null)throw new IllegalArgumentException("Unsupported redirected Paper command node "+source.getName());
        for(var child:source.getChildren())node.then(convert(child,child.getName(),view));
        return node.build();
    }
}
