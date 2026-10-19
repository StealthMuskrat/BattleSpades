// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import net.battlespades.player.*;
import net.battlespades.runtime.OwnerSequence;

/** Owner-resumed asynchronous command dispatch, canonical aliases, permissions and secret-safe logging. */
public final class CommandRegistry {
    public record Context(PlayerLife player,List<String> args,String raw){public Context{args=List.copyOf(args);}}
    public record Command(String name,List<String> aliases,boolean admin,boolean hidden,String usage,String description,Function<Context,CompletionStage<Void>> handler){public Command{aliases=List.copyOf(aliases);Objects.requireNonNull(handler);}}
    private final LinkedHashMap<String,Command> commands=new LinkedHashMap<>();private final StandardMatchDomain match;private final java.util.function.BiConsumer<PlayerLife,String> notice;private final OwnerSequence sequence;private final boolean log;
    public CommandRegistry(StandardMatchDomain match,java.util.function.BiConsumer<PlayerLife,String> notice,Executor resume,boolean log){this.match=Objects.requireNonNull(match);this.notice=Objects.requireNonNull(notice);sequence=new OwnerSequence(resume);this.log=log;}
    public void register(Command command){sequence.owned();commands.put(command.name.toLowerCase(Locale.ROOT),command);for(var alias:command.aliases)commands.put(alias.toLowerCase(Locale.ROOT),command);}
    public Command get(String name){sequence.owned();return commands.get(name.toLowerCase(Locale.ROOT));}
    public List<Command> all(){sequence.owned();return List.copyOf(new LinkedHashSet<>(commands.values()));}
    public CompletionStage<Void> execute(PlayerLife p,String message){
        sequence.owned();String stripped=NameUnicode.strip(message);if(stripped.isEmpty())return CompletableFuture.completedFuture(null);int end=0;while(end<stripped.length()&&!Character.isWhitespace(stripped.charAt(end)))end++;String name=stripped.substring(0,end).toLowerCase(Locale.ROOT),raw=NameUnicode.strip(stripped.substring(end));var command=get(name);
        if(command==null){notice.accept(p,"Unknown command: /"+name);return CompletableFuture.completedFuture(null);}if(command.admin&&!match.admin(p)){notice.accept(p,"You don't have permission to use this command.");return CompletableFuture.completedFuture(null);}var context=new Context(p,raw.isEmpty()?List.of():Arrays.asList(raw.split("(?U)\\s+")),raw);
        return sequence.isolate(()->sequence.run(List.<java.util.function.Supplier<CompletionStage<Void>>>of(()->command.handler.apply(context),()->{if(log)System.getLogger("commands").log(System.Logger.Level.INFO,"Command: {0} used /{1} {2}",p.name(),name,Set.of("admin","claimhost").contains(command.name)?"<redacted>":raw);return CompletableFuture.completedFuture(null);}).iterator()),failure->{System.getLogger("commands").log(System.Logger.Level.ERROR,"Command failed: {0} ({1})",name,failure.getClass().getSimpleName());notice.accept(p,"Command failed; see server log");});
    }
}
