// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.runtime;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Serial nonblocking continuations, resumed on the gameplay owner. Immediate stages use no recursive drain. */
public final class OwnerSequence {
    private final Thread owner=Thread.currentThread();
    private final Executor resume;
    public OwnerSequence(Executor resume){this.resume=Objects.requireNonNull(resume);}
    public void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Sequence outside gameplay owner");}
    public static Throwable cause(Throwable error){while(error instanceof CompletionException||error instanceof ExecutionException){if(error.getCause()==null)break;error=error.getCause();}return error;}
    public void resume(Runnable operation,CompletableFuture<?> result){
        Runnable guarded=()->{try{owned();operation.run();}catch(Throwable error){result.completeExceptionally(error);}};
        if(Thread.currentThread()==owner)guarded.run();else try{resume.execute(guarded);}catch(RuntimeException error){result.completeExceptionally(error);}
    }
    public CompletionStage<Void> run(Iterator<? extends Supplier<CompletionStage<Void>>> operations){
        owned();var done=new CompletableFuture<Void>();
        class Drain {
            void advance(){
                try{
                    owned();while(operations.hasNext()){
                        var stage=Objects.requireNonNull(operations.next().get());var future=stage.toCompletableFuture();
                        if(future.isDone()){future.join();continue;}
                        stage.whenComplete((unused,failure)->resume(()->{if(failure!=null)done.completeExceptionally(cause(failure));else advance();},done));return;
                    }done.complete(null);
                }catch(Throwable failure){done.completeExceptionally(cause(failure));}
            }
        }
        new Drain().advance();return done.minimalCompletionStage();
    }
    /** Isolate ordinary handler exceptions; cancellation and fatal errors abort the frame. */
    public CompletionStage<Void> isolate(Supplier<CompletionStage<Void>> operation,java.util.function.Consumer<Throwable> failure){
        owned();var done=new CompletableFuture<Void>();
        try{Objects.requireNonNull(operation.get()).whenComplete((unused,error)->resume(()->finish(error,failure,done),done));}
        catch(RuntimeException error){finish(error,failure,done);}return done.minimalCompletionStage();
    }
    private static void finish(Throwable error,java.util.function.Consumer<Throwable> report,CompletableFuture<Void> done){
        if(error==null){done.complete(null);return;}var root=cause(error);
        if(root instanceof Error||root instanceof CancellationException){done.completeExceptionally(root);return;}
        try{report.accept(root);done.complete(null);}catch(Throwable reporting){done.completeExceptionally(reporting);}
    }
}
