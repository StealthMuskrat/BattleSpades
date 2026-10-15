// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.function.IntUnaryOperator;
import net.battlespades.protocol.SimplePackets;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.ModeCatalog;
import org.tomlj.TomlTable;

/** Pre-life checks from Connection._resolve_join_team / _enforce_join_class.
 * Trusted identity and mode hooks are supplied by their owners, never by packet forced_team.
 */
public final class AdmissionPolicy {
    public record Occupant(int id,String name,boolean admin,int team){}
    public record Settings(boolean autoBalance,int balanceThreshold,List<String> reservedNames,String botLabel){
        public Settings {
            reservedNames=List.copyOf(reservedNames);Objects.requireNonNull(botLabel);
            if(botLabel.length()>6||botLabel.codePoints().anyMatch(c->c<32||c>126))throw new IllegalArgumentException("bots.name_prefix must be printable ASCII of at most 6 characters");
        }
        public static Settings defaults(){return new Settings(true,2,List.of(),"[BOT]");}
        public static Settings from(TomlTable raw){
            var extra=raw.getArray("conduct.reserved_names");var names=new ArrayList<String>();
            if(extra!=null)for(Object value:extra.toList())names.add((String)value);
            Long threshold=raw.getLong("teams.balance_threshold");
            return new Settings(raw.getBoolean("teams.auto_balance",()->true),threshold==null?2:Math.toIntExact(threshold),names,raw.getString("bots.name_prefix",()->"[BOT]"));
        }
    }
    public interface ModePolicy {
        /** Null means no source prepare_join_team hook; otherwise its actual override. */
        default IntUnaryOperator prepareTeam(){return null;}
        default boolean customClassPolicy(){return false;}
        default boolean ugcRuntime(){return false;}
        default ClassCatalog.Selection prepareSelection(int team,ClassCatalog.Selection selection){return selection;}
    }
    public record Context(List<Occupant> occupants,Integer assignedTeam,ModePolicy mode){
        public Context {occupants=List.copyOf(occupants);Objects.requireNonNull(mode);}
        public static Context ordinary(List<Occupant> occupants){return new Context(occupants,null,new ModePolicy(){});}
    }
    public record Team(int internal,int wire,boolean rebalanced){}
    public record Decision(String name,Team team,ClassCatalog.Selection selection){}
    private AdmissionPolicy(){}
    public static Team team(int wireTeam,boolean spectators,Settings settings,List<Occupant> occupants,int joiningId,Integer assignedTeam,IntUnaryOperator prepareTeam){
        boolean assigned=assignedTeam!=null&&(assignedTeam==2||assignedTeam==3);
        int requested=assigned?assignedTeam:wireTeam;
        return resolveTeam(requested,spectators,settings,occupants,joiningId,!assigned,prepareTeam);
    }
    public static Team resolveTeam(int requested,boolean spectators,Settings settings,List<Occupant> occupants,int joiningId,boolean balance,IntUnaryOperator prepareTeam){
        if(requested==0&&spectators)return new Team(0,0,false);
        boolean recognized=requested==2||requested==3;
        int internal=recognized?requested:2;
        if(prepareTeam!=null)internal=prepareTeam.applyAsInt(internal);
        else if(balance&&settings.autoBalance){
            int blue=0,green=0;
            for(var player:occupants)if(player.id!=joiningId){if(player.team==2)blue++;else if(player.team==3)green++;}
            int threshold=Math.max(1,settings.balanceThreshold);
            if(internal==2&&blue-green>=threshold)internal=3;
            else if(internal==3&&green-blue>=threshold)internal=2;
        }
        int wire=internal==0||internal==2||internal==3?internal:2;
        // Source emits the notice only for recognized playable requests, not invalid-team fallback.
        return new Team(internal,wire,recognized&&prepareTeam==null&&internal!=requested);
    }
    public static boolean classSelectable(int id,GameRules rules,ModeCatalog.Definition mode,boolean customPolicy,boolean ugc){
        return rules.classEnabled(id)&&(customPolicy||ugc||mode.classes().isEmpty()||mode.classes().contains(id));
    }
    public static Integer fallbackClass(GameRules rules,ModeCatalog.Definition mode,boolean customPolicy,boolean ugc){
        var candidates=new LinkedHashSet<>(mode.classes());candidates.add(5);candidates.addAll(ClassCatalog.definitions().keySet());
        for(int id:candidates)if(ClassCatalog.definitions().containsKey(id)&&classSelectable(id,rules,mode,customPolicy,ugc))return id;return null;
    }
    public static ClassCatalog.Selection enforceClass(ClassCatalog.Selection selection,int team,GameRules rules,ModeCatalog.Definition mode,boolean customPolicy,boolean ugc){
        if(team==0||classSelectable(selection.classId(),rules,mode,customPolicy,ugc))return selection;
        Integer fallback=fallbackClass(rules,mode,customPolicy,ugc);
        if(fallback==null)return selection;
        var replacement=ClassCatalog.normalize(fallback,List.of(),List.of(),List.of(),fallback,rules.disabledTools());
        // normalize_server_selection: the isolated UGC Builder uses manipulator 42, never prefab tool 23.
        return ugc&&fallback==13?new ClassCatalog.Selection(13,replacement.loadout().stream().filter(t->t!=23).toList(),List.of(),replacement.ugcTools()):replacement;
    }
    public static Decision resolve(int id,SimplePackets.NewPlayerConnection request,ClassCatalog.Selection selection,Settings settings,GameRules rules,ModeCatalog.Definition mode,Context context){
        Team team=team(request.team(),rules.enabled("RULE_ENABLE_SPECTATORS"),settings,context.occupants,id,context.assignedTeam,context.mode.prepareTeam());
        var existing=context.occupants.stream().map(p->new PlayerNames.Existing(p.name,p.admin)).toList();
        String name=PlayerNames.allocate(request.name(),existing,settings.reservedNames,settings.botLabel);
        selection=enforceClass(selection,team.internal,rules,mode,context.mode.customClassPolicy(),context.mode.ugcRuntime());
        if(team.internal!=0)selection=context.mode.prepareSelection(team.internal,selection);
        return new Decision(name,team,Objects.requireNonNull(selection));
    }
}
