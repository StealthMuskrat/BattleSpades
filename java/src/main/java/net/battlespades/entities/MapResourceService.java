// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.*;
import java.util.function.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.world.*;

/** Original map-owned pickup/light assembly over the canonical world and registry. */
public final class MapResourceService {
    /** Required external domains; no default score, achievement or audio implementation. */
    public interface Effects {
        boolean wireReady();
        boolean pickupsEnabled();
        double respawnDelay();
        Object crateBaseline(PlayerLife player);
        void recordCrate(PlayerLife player,int award);
        void crateCollected(PlayerLife player,int award,Object baseline);
        void create(MapEntity entity);
        void destroy(int id);
        void personalSound(PlayerLife player,int soundId);
        void flyby(AudioPackets.PlaySound packet);
        void rebuilt(Result result);
    }
    public record Result(int pickups,int flares,int invalidPickups,int invalidFlares,int truncatedLights,Map<Integer,Integer> missingFamilies) {
        public Result { missingFamilies=Map.copyOf(missingFamilies); }
    }
    private record Placement(double x,double y,double z,int type,Integer rgb){}
    private record Pickup(String kind,PickupCrateBehavior behavior){}
    private static final Set<String> MANAGED=Set.of("map_ammo","map_health","map_block","map_jetpack","map_flare");
    private final Thread owner=Thread.currentThread();
    private final LifeRuntime lives;
    private final MutableWorld world;
    private final PickupWorld terrain;
    private final EntityRegistry registry;
    private final MapMetadata metadata;
    private final IntFunction<Vector> baseAnchor;
    private final Effects effects;
    public MapResourceService(LifeRuntime lives,EntityRegistry registry,MapMetadata metadata,IntFunction<Vector> baseAnchor,Effects effects) {
        this.lives=Objects.requireNonNull(lives);world=lives.world();terrain=new PickupWorld(world);
        this.registry=Objects.requireNonNull(registry);this.metadata=Objects.requireNonNull(metadata);
        this.baseAnchor=Objects.requireNonNull(baseAnchor);this.effects=Objects.requireNonNull(effects);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Map resources outside owner");world.revision();}
    private Consumer<PlayerLife> counted(int award,Consumer<PlayerLife> action) {
        return player->{Object before=effects.crateBaseline(player);action.accept(player);effects.recordCrate(player,award);effects.crateCollected(player,award,before);};
    }
    private Map<Integer,Pickup> behaviors() {
        double delay=effects.respawnDelay();var result=new HashMap<Integer,Pickup>();
        result.put(3,pickup("map_ammo",counted(3,p->lives.restock(p.id(),3,false)),delay,13));
        result.put(4,pickup("map_health",counted(2,p->lives.heal(p.id(),p.maxHealth())),delay,14));
        result.put(5,pickup("map_block",counted(4,p->lives.restockBlocks(p.id())),delay,15));
        result.put(6,pickup("map_jetpack",p->lives.restockJetpack(p.id()),delay,13));
        return result;
    }
    private Pickup pickup(String kind,Consumer<PlayerLife> refill,double delay,int sound) {
        return new Pickup(kind,new PickupCrateBehavior(refill,delay,sound,effects::personalSound,true,1,this::dropCue));
    }
    public int crateFlybySound() {
        owned();String sky=metadata.environment().skybox();
        if("LunarBase.txt".equals(sky)||"User_Lunar.txt".equals(sky))return 26;
        return "WW1.txt".equals(sky)?24:25;
    }
    private void dropCue(MapEntity entity) {
        if(effects.wireReady())effects.flyby(new AudioPackets.PlaySound(crateFlybySound(),false,true,1,0,0,new Vector3f((float)entity.x,(float)entity.y,(float)entity.z),.25f));
    }
    private Placement authored(MapMetadata.Entity spec) {
        var p=spec.position();return new Placement(p.x(),p.y(),p.z()+world.sourceZShift(),spec.type(),spec.color());
    }
    private List<Placement> fallback() {
        var spots=new ArrayList<Vector>();
        for(int team:new int[]{2,3}){var p=baseAnchor.apply(team);spots.add(new Vector(p.x()+8,p.y(),0));spots.add(new Vector(p.x()-8,p.y(),0));spots.add(new Vector(p.x(),p.y()+8,0));}
        spots.add(new Vector(248,256,0));spots.add(new Vector(256,256,0));spots.add(new Vector(264,256,0));
        var result=new ArrayList<Placement>();int index=0;for(var p:spots){var a=terrain.drySurfaceAnchor(p.x(),p.y(),24);result.add(new Placement(a.x(),a.y(),a.z(),3+index++%3,null));}return result;
    }
    public Result rebuild() {
        owned();
        for(var entity:registry.all())if(MANAGED.contains(entity.kind)){if(entity.wireVisible&&entity.alive)effects.destroy(entity.id());registry.remove(entity.id());}
        int pickups=0,invalidPickups=0;
        if(effects.pickupsEnabled()) {
            var behaviors=behaviors();var authored=metadata.entities().stream().filter(e->e.type()>=3&&e.type()<=6).map(this::authored).toList();
            for(var p:authored.isEmpty()?fallback():authored) {
                if(!(p.x>=0&&p.x<512&&p.y>=0&&p.y<512&&p.z>=0&&p.z<240)){invalidPickups++;continue;}
                var behavior=behaviors.get(p.type);var settings=new EntityRegistry.Placement();settings.state=1;settings.kind=behavior.kind;settings.behavior=behavior.behavior;
                var entity=registry.place(p.type,p.x,p.y,p.z,settings);if(effects.wireReady())effects.create(entity);pickups++;
            }
        }
        var placements=new ArrayList<Placement>();var missing=new TreeMap<Integer,Integer>();
        for(var spec:metadata.entities())if(spec.type()==13&&spec.color()!=null)placements.add(authored(spec));
        for(var marker:world.markers()) {
            Integer rgb=metadata.staticLightColors().get(marker.family());
            if(rgb==null){missing.merge(marker.family(),1,Integer::sum);continue;}
            placements.add(new Placement(marker.x(),marker.y(),marker.z(),13,rgb));
        }
        int truncated=Math.max(0,placements.size()-2048),flares=0,invalidFlares=0;
        for(var p:placements.subList(0,Math.min(2048,placements.size()))) {
            if(!world.restoreStaticLight(p.x,p.y,p.z,p.rgb)){invalidFlares++;continue;}
            var settings=new EntityRegistry.Placement();settings.state=1;settings.kind="map_flare";settings.playerId=0;settings.color=new Vector((p.rgb>>16)&255,(p.rgb>>8)&255,p.rgb&255);
            var entity=registry.place(13,p.x,p.y,p.z,settings);if(effects.wireReady())effects.create(entity);flares++;
        }
        var result=new Result(pickups,flares,invalidPickups,invalidFlares,truncated,missing);effects.rebuilt(result);return result;
    }
}
