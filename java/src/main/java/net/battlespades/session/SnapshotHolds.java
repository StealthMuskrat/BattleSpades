// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.transport.enet.DeliveryReceipt;

/** Connection._hold_snapshot_rows_for/reliable_unacked_through/held_snapshot_rows.
 * CreatePlayer/CreateEntity barriers use packet release, not guessed delivery ages.
 */
public final class SnapshotHolds {
    public record Row(boolean entity,int id) {}
    private record Pending(DeliveryReceipt receipt,long expires) {}
    private final Thread owner=Thread.currentThread();
    private final LinkedHashMap<Long,Pending> unacked=new LinkedHashMap<>();
    private final LinkedHashMap<Row,LinkedHashMap<Long,Pending>> holds=new LinkedHashMap<>();
    private long index;
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Snapshot holds accessed outside owner");}
    public long reliableSendIndex(){owned();return index;}
    public void queued(byte[] body,DeliveryReceipt receipt,int loop){
        owned();Objects.requireNonNull(receipt);long token=++index;
        unacked.put(token,new Pending(receipt,(long)loop+600));while(unacked.size()>8192)unacked.remove(unacked.keySet().iterator().next());
        Row row=null;
        if(body.length>=2&&(body[0]&255)==28)row=new Row(false,body[1]&255);
        else if(body.length>=3&&(body[0]&255)==21)row=new Row(true,(short)((body[1]&255)|((body[2]&255)<<8)));
        if(row!=null)holds.computeIfAbsent(row,key->new LinkedHashMap<>()).put(token,new Pending(receipt,(long)loop+120));
        Row key=row;Runnable released=()->{owned();unacked.remove(token);var pending=holds.get(key);if(pending!=null){pending.remove(token);if(pending.isEmpty())holds.remove(key);}};
        try{if(!receipt.onRelease(released))released.run();}catch(RuntimeException unsupported){released.run();}
    }
    public boolean reliableUnackedThrough(long through,int loop){
        owned();for(var iterator=unacked.entrySet().iterator();iterator.hasNext();){var entry=iterator.next();if(entry.getKey()>through)break;if(loop>entry.getValue().expires){iterator.remove();continue;}return true;}return false;
    }
    public Set<Row> heldRows(int loop){
        owned();for(var iterator=holds.entrySet().iterator();iterator.hasNext();){var entry=iterator.next();entry.getValue().entrySet().removeIf(token->loop>token.getValue().expires);if(entry.getValue().isEmpty())iterator.remove();}
        return Collections.unmodifiableSet(new LinkedHashSet<>(holds.keySet()));
    }
    public void clear(){owned();unacked.clear();holds.clear();index=0;}
}
