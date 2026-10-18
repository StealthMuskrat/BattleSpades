// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.battlespades.protocol.*;
import net.battlespades.session.JoinScene;
import net.battlespades.session.PlayerRoster;
import net.battlespades.world.MutableWorld;
import net.battlespades.world.VxlMap;

/** Test-only protocol scene: no production gameplay is simulated by this fixture. */
public final class FixtureScene implements JoinScene,net.battlespades.scene.ConfiguredScene.Gameplay,AutoCloseable {
    @Override public void roundTripMillis(int id,Double measurement){}
    @Override public void actionLink(int id,net.battlespades.player.ActionClock.Link link){}
    private final MutableWorld world;
    private final InitialInfo info;
    private final StateData state;
    public final Map<Integer,Admission> players=new LinkedHashMap<>();
    public final List<LiveClientInput> inputs=new ArrayList<>();
    public final List<Integer> receivedLoops=new ArrayList<>();
    public final Map<Integer,Integer> capabilities=new LinkedHashMap<>();
    public final List<GamePacket> received=new ArrayList<>();
    public final List<Integer> departed=new ArrayList<>();
    private final PlayerRoster roster=new PlayerRoster();
    public int reveals,requiredCapability;
    public RuntimeException revealFailure;
    public boolean failAdmission;
    public Runnable onAdmission=()->{};
    public java.util.function.BiFunction<Integer,SimplePackets.NewPlayerConnection,Vector3f> placement=(id,request)->new Vector3f(256,256,237);
    public FixtureScene(long epoch,int journalLimit) {
        this(VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"auto"),epoch,journalLimit);
    }
    public FixtureScene(VxlMap base,long epoch,int journalLimit) {
        world=new MutableWorld(base,epoch,journalLimit);
        InitialInfo source=(InitialInfo)golden("InitialInfo");state=(StateData)golden("StateDataEmpty");
        info=copy(source,Map.of("map",new InitialInfo.MapInfo("Fixture "+epoch,"fixture",world.sourceCrc(),0)));
    }
    private static GamePacket golden(String name) {
        try(var in=FixtureScene.class.getResourceAsStream("/golden/loading.tsv")) {
            if(in==null) throw new IllegalStateException("Missing source fixture");
            String row=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().filter(s->s.startsWith(name+"\t")).findFirst().orElseThrow();
            return PacketCodec.decode(HexFormat.of().parseHex(row.split("\t")[1]));
        } catch(IOException e) {throw new IllegalStateException(e);}
    }
    /** Keep all source fixture fields unless this test explicitly replaces a record component. */
    @SuppressWarnings("unchecked")
    public static <T extends Record> T copy(T source,Map<String,Object> changed) {
        try {
            var components=source.getClass().getRecordComponents();var types=new Class<?>[components.length];var values=new Object[components.length];
            for(int i=0;i<components.length;i++) {types[i]=components[i].getType();values[i]=changed.containsKey(components[i].getName())?changed.get(components[i].getName()):components[i].getAccessor().invoke(source);}
            return (T)source.getClass().getDeclaredConstructor(types).newInstance(values);
        } catch(ReflectiveOperationException e) {throw new IllegalStateException(e instanceof InvocationTargetException invoke?invoke.getCause():e);}
    }
    @Override public MutableWorld world() {return world;}
    @Override public int requiredCapability() {return requiredCapability;}
    @Override public InitialInfo initialInfo(int capability) {return info;}
    @Override public StateData stateData(int id,int capability) {return copy(state,Map.of("playerId",id));}
    @Override public String skybox() {return "skybox/default";}
    @Override public PlayerRoster roster() {return roster;}
    @Override public boolean spectator(int id){var p=players.get(id);return p!=null&&p.player().team()==0;}
    @Override public net.battlespades.player.AdmissionPolicy.Context admissionContext(int id){
        return net.battlespades.player.AdmissionPolicy.Context.ordinary(players.values().stream().map(a->new net.battlespades.player.AdmissionPolicy.Occupant(a.player().playerId(),a.player().name(),false,a.player().team())).toList());
    }
    @Override public Admission admit(int id,SimplePackets.NewPlayerConnection request,PlayerPackets.SetClassLoadout selection,int capability) {
        var player=new PlayerPackets.CreatePlayer(id,0,request.class_id(),request.team(),0,request.local_language(),placement.apply(id,request),new Vector3f(1,0,0),request.name(),selection==null?List.of(7):selection.loadout(),selection==null?List.of():selection.prefabs());
        var admission=new Admission(player,0x123456,List.of());players.put(id,admission);capabilities.put(id,capability);roster.create(player,admission.color());onAdmission.run();if(failAdmission) throw new IllegalStateException("Fixture admission failure");return admission;
    }
    @Override public Admission admit(int id,SimplePackets.NewPlayerConnection request,net.battlespades.player.ClassCatalog.Selection selection,int capability){
        var normalized=new SimplePackets.NewPlayerConnection(request.team(),selection.classId(),request.forced_team(),request.local_language(),request.name());
        return admit(id,normalized,new PlayerPackets.SetClassLoadout(id,selection.classId(),0,selection.loadout(),selection.prefabs(),selection.ugcTools()),capability);
    }
    @Override public List<GamePacket> reveal(int id) {reveals++;if(revealFailure!=null)throw revealFailure;return List.of(new SimplePackets.ChatMessage(id,0,"fixture-reveal"));}
    @Override public void input(int id,LiveClientInput input,int receivedServerLoop) {inputs.add(input);receivedLoops.add(receivedServerLoop);}
    public java.util.function.BiFunction<Integer,GamePacket,java.util.concurrent.CompletionStage<Void>> asyncPacket;
    @Override public java.util.concurrent.CompletionStage<Void> packetAsync(int id,GamePacket packet){if(asyncPacket!=null)return asyncPacket.apply(id,packet);packet(id,packet);return java.util.concurrent.CompletableFuture.completedFuture(null);}
    @Override public void packet(int id,GamePacket packet) {received.add(packet);}
    @Override public void transitionDetach(int id){players.remove(id);capabilities.remove(id);roster.remove(id);}
    @Override public void disconnected(int id) {departed.add(id);players.remove(id);capabilities.remove(id);roster.remove(id);}
    @Override public void close() {world.close();}
}
