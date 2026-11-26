package mc.unity.plugins;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.help.*;

/** Session-local help topics and factories for the commands actually registered on this host. */
final class PluginHelp implements HelpMap {
    private final Map<String, Command> commands;
    private final Map<String, HelpTopic> topics=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<Class<?>, HelpTopicFactory<?>> factories=new LinkedHashMap<>();
    private final Set<Command> indexed=Collections.newSetFromMap(new IdentityHashMap<>());

    PluginHelp(Map<String, Command> commands) { this.commands=commands; }
    public synchronized void addTopic(HelpTopic topic) { topics.putIfAbsent(topic.getName(),topic); }
    public synchronized void clear() { topics.clear(); indexed.clear(); }
    public synchronized HelpTopic getHelpTopic(String name) { index();return topics.get(name); }
    public synchronized Collection<HelpTopic> getHelpTopics() { index();return List.copyOf(topics.values()); }
    public List<String> getIgnoredPlugins() { return List.of(); }
    public synchronized void registerHelpTopicFactory(Class<?> type,HelpTopicFactory<?> factory) {
        if(!Command.class.isAssignableFrom(type) && !CommandExecutor.class.isAssignableFrom(type))
            throw new IllegalArgumentException("Expected a Command or CommandExecutor class");
        factories.put(type,Objects.requireNonNull(factory));
        for(var command:commands.values()) {
            if(type.isInstance(command) || (command instanceof PluginCommand pc && type.isInstance(pc.getExecutor()))) {
                if(indexed.remove(command))topics.remove("/"+command.getLabel());
            }
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private void index() {
        for(var command:List.copyOf(commands.values())) {
            if(!indexed.add(command))continue;
            HelpTopicFactory selected=null;
            for(var entry:factories.entrySet()) {
                if(entry.getKey().isInstance(command) || (command instanceof PluginCommand pc && entry.getKey().isInstance(pc.getExecutor()))) {
                    selected=entry.getValue();break;
                }
            }
            var topic=selected==null ? new GenericCommandHelpTopic(command) : selected.createTopic(command);
            if(topic!=null)addTopic(topic);
        }
    }
}
