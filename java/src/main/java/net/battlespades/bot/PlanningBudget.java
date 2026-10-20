// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import static net.battlespades.bot.BotState.Observer;
/** Worker-owned fair credits. Deferral means retry later and never marks terrain impassable. */
public final class PlanningBudget {
    public static final int MAX_PENDING=128,MAX_SEARCHES=2,MAX_EXPANSIONS=512;
    public static final class Job {
        private int searches,expansions;private Observer observer;
        public Job(){}private Job(Observer observer){this.observer=observer;}
        public int beginSearch(int maximum){if(searches>=MAX_SEARCHES||expansions>=MAX_EXPANSIONS)return 0;searches++;return Math.min(Math.max(0,maximum),MAX_EXPANSIONS-expansions);}
        public int searches(){return searches;}public int expansions(){return expansions;}public void expanded(){expansions++;}public void expansions(int value){expansions=value;}
    }
    private static final class Waiter{final double since;double last;Waiter(double now){since=last=now;}}
    private static final class Turn{final double at;double spent;Job open;Turn(double at,Job job){this.at=at;open=job;}}
    private final Thread owner=Thread.currentThread();private final double configuredRate,frequency,staleSeconds;private double rate,credits;private int burst;private Double lastTime;
    private final LinkedHashMap<Observer,Waiter> pending=new LinkedHashMap<>();private final LinkedHashMap<Observer,Turn> turns=new LinkedHashMap<>();private final ArrayDeque<Double> durations=new ArrayDeque<>(),waits=new ArrayDeque<>();
    private long requested,granted,deferred,expired,queueFull,searches,expansions;private double longestWait;private String lastDenial="";
    public PlanningBudget(){this(24,8);}public PlanningBudget(double rate,double frequency){if(!Double.isFinite(rate)||rate<=0||!Double.isFinite(frequency)||frequency<=0)throw new IllegalArgumentException("Planning rate and frequency must be finite and positive");configuredRate=this.rate=rate;this.frequency=frequency;burst=burst(rate);staleSeconds=Math.max(1,2/frequency);credits=burst;}
    private int burst(double value){return (int)Math.max(1,Math.min(8,Math.ceil(value/frequency)));}private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Planning budget outside worker");}private static double timestamp(double now){if(!Double.isFinite(now))throw new IllegalArgumentException("Planning timestamp must be finite");return now;}
    public void reset(){owned();pending.clear();turns.clear();lastTime=null;credits=burst;}
    public void scaleFor(int bots){owned();double next=Math.max(configuredRate,6.*Math.max(0,bots));if(next!=rate){rate=next;burst=burst(rate);}}
    public void refreshWaiters(Collection<Observer> observers,double now){owned();timestamp(now);for(var observer:observers){var waiter=pending.get(observer);if(waiter!=null)waiter.last=Math.max(now,waiter.last);}}
    public void cancel(Observer observer){owned();pending.remove(observer);}
    private double advance(double now){if(lastTime!=null){now=Math.max(now,lastTime);credits=Math.min(burst,credits+(now-lastTime)*rate);}lastTime=now;double cutoff=now-staleSeconds;for(var iterator=pending.values().iterator();iterator.hasNext();)if(iterator.next().last<cutoff){iterator.remove();expired++;}turns.values().removeIf(turn->turn.at<cutoff);return now;}
    private Boolean used(Observer observer,double now){var turn=turns.get(observer);return turn==null||turn.at!=now?null:turn.open!=null||turn.spent>2+1e-9;}
    private static int claims(Observer observer,Collection<Observer> waiters,boolean first){int ahead=0;for(var key:waiters){if(first&&key.equals(observer))break;if(!key.equals(observer))ahead++;}return ahead;}
    public Job acquire(Observer observer,double now){owned();Objects.requireNonNull(observer);timestamp(now);requested++;now=advance(now);Boolean used=used(observer,now);if(Boolean.TRUE.equals(used)){deferred++;lastDenial="decision";return null;}boolean first=used==null;
        if(credits+1e-9<1+claims(observer,pending.keySet(),first)){deferred++;lastDenial=credits+1e-9<1?"credit":"reserved";var waiter=pending.get(observer);if(waiter!=null)waiter.last=now;else if(first){if(pending.size()>=MAX_PENDING){queueFull++;lastDenial="queue_full";}else pending.put(observer,new Waiter(now));}return null;}
        var waiter=pending.remove(observer);double waited=waiter==null?0:now-waiter.since;sample(waits,waited);longestWait=Math.max(longestWait,waited);credits=Math.max(0,credits-1);var job=new Job(observer);if(first){turns.put(observer,new Turn(now,job));if(turns.size()>MAX_PENDING)turns.remove(turns.keySet().iterator().next());}else turns.get(observer).open=job;granted++;lastDenial="";return job;
    }
    private record Preview(double now,double credits,List<Observer> waiters){}
    private Preview preview(double now){timestamp(now);double elapsed=0;if(lastTime!=null){now=Math.max(now,lastTime);elapsed=now-lastTime;}double cutoff=now-staleSeconds;var waiters=pending.entrySet().stream().filter(e->e.getValue().last>=cutoff).map(Map.Entry::getKey).toList();return new Preview(now,Math.min(burst,credits+elapsed*rate),waiters);}
    public boolean wouldGrant(Observer observer,double now){owned();var p=preview(now);var used=used(observer,p.now());return !Boolean.TRUE.equals(used)&&p.credits()+1e-9>=1+claims(observer,p.waiters(),used==null);}
    public boolean spare(Observer observer,double now){owned();var p=preview(now);return !Boolean.TRUE.equals(used(observer,p.now()))&&p.credits()+1e-9>=1+(burst-1)/2.+claims(observer,p.waiters(),false);}
    public void finish(Job job,double seconds){owned();searches+=job.searches;expansions+=job.expansions;if(Double.isFinite(seconds))sample(durations,Math.max(0,seconds)*1000);var observer=job.observer;job.observer=null;var turn=observer==null?null:turns.get(observer);if(turn==null||turn.open!=job)return;double charge=Math.min(1,Math.max(.125,job.expansions/512.));turn.spent+=charge;turn.open=null;credits=Math.min(burst,credits+1-charge);}
    private static void sample(ArrayDeque<Double> samples,double value){if(samples.size()==256)samples.removeFirst();samples.addLast(value);}private static double percentile(Collection<Double> samples){var values=samples.stream().sorted().toList();return values.isEmpty()?0:values.get(Math.max(0,(int)Math.ceil(values.size()*.95)-1));}
    public String lastDenial(){owned();return lastDenial;}
    public Map<String,Number> snapshot(){owned();var result=new LinkedHashMap<String,Number>();result.put("requested",requested);result.put("granted",granted);result.put("deferred",deferred);result.put("expired",expired);result.put("queue_full",queueFull);result.put("pending",pending.size());result.put("searches",searches);result.put("expansions",expansions);result.put("samples",durations.size());result.put("work_p95_ms",percentile(durations));result.put("work_max_ms",durations.stream().mapToDouble(Double::doubleValue).max().orElse(0));result.put("rate",rate);result.put("burst",burst);result.put("wait_p95_s",percentile(waits));result.put("wait_max_s",longestWait);return Collections.unmodifiableMap(result);}
}
