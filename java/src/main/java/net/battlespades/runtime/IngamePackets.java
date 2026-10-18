// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.runtime;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Source tick-start packet FIFO: detach a fixed batch, release shares, then recheck identities after every await. */
public final class IngamePackets<C> {
    public record Settings(int capacity,int perConnection,int budget){
        public Settings{if(capacity<1||perConnection<1||perConnection>capacity||budget<1)throw new IllegalArgumentException("Packet queue limits");}
        public static Settings defaults(int capacity,int budget){return new Settings(capacity,Math.min(capacity,256),budget);}
    }
    public record Pending<C>(C connection,byte[] data){
        public Pending{data=data.clone();}
        @Override public byte[] data(){return data.clone();}
    }
    public List<Pending<C>> snapshot(){sequence.owned();return rows.stream().map(row->new Pending<>(row.connection(),row.data())).toList();}
    private record Row<C>(C connection,byte[] data){}
    private final OwnerSequence sequence;
    private final Settings settings;
    private final Predicate<C> current;
    private final BiFunction<C,byte[],CompletionStage<Void>> receive;
    private final Consumer<Throwable> failure;
    private final Deque<Row<C>> rows=new ArrayDeque<>();
    private final IdentityHashMap<C,Integer> shares=new IdentityHashMap<>();
    private long dropped;
    private boolean draining;
    public IngamePackets(Settings settings,Predicate<C> current,BiFunction<C,byte[],CompletionStage<Void>> receive,Consumer<Throwable> failure,Executor resume){
        this.settings=Objects.requireNonNull(settings);this.current=Objects.requireNonNull(current);this.receive=Objects.requireNonNull(receive);this.failure=Objects.requireNonNull(failure);sequence=new OwnerSequence(resume);
    }
    public boolean offer(C connection,byte[] data){
        sequence.owned();Objects.requireNonNull(connection);Objects.requireNonNull(data);int count=shares.getOrDefault(connection,0);
        if(count>=settings.perConnection()||rows.size()>=settings.capacity()){dropped++;return false;}
        rows.addLast(new Row<>(connection,data.clone()));shares.put(connection,count+1);return true;
    }
    public boolean draining(){sequence.owned();return draining;}
    public int size(){sequence.owned();return rows.size();}public int share(C c){sequence.owned();return shares.getOrDefault(c,0);}public long dropped(){sequence.owned();return dropped;}
    public void clear(){sequence.owned();rows.clear();shares.clear();}
    public void forget(C c){sequence.owned();rows.removeIf(row->row.connection()==c);shares.remove(c);}
    public CompletionStage<Void> drain(){
        sequence.owned();if(draining)throw new IllegalStateException("Packet drain already suspended");draining=true;
        var batch=new ArrayList<Row<C>>();int count=Math.min(rows.size(),settings.budget());
        for(int i=0;i<count;i++){var row=rows.removeFirst();batch.add(row);shares.compute(row.connection(),(key,n)->n==null||n<=1?null:n-1);}
        var operations=batch.stream().<Supplier<CompletionStage<Void>>>map(row->()->{
            if(!current.test(row.connection()))return CompletableFuture.completedFuture(null);
            return sequence.isolate(()->receive.apply(row.connection(),row.data()),failure);
        }).iterator();
        var result=new CompletableFuture<Void>();sequence.run(operations).whenComplete((unused,error)->sequence.resume(()->{draining=false;if(error==null)result.complete(null);else result.completeExceptionally(OwnerSequence.cause(error));},result));
        return result.minimalCompletionStage();
    }
}
