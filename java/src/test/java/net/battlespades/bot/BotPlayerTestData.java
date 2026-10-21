// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotFoundationsTest.*;
final class BotPlayerTestData {
    private static Vector nullableVector(Object value) {return value==null?null:vector(value);}
    private static List<Stock> stocks(Object value) {return list(value).stream().map(v->{var a=list(v);return new Stock(integer(a.get(0)),integer(a.get(1)));}).toList();}
    static Player player(Object value) {
        var p=map(value);
        return new Player(integer(p.get("player_id")),integer(p.get("generation")),integer(p.get("team")),integer(p.get("class_id")),bool(p.get("alive")),bool(p.get("spawned")),vector(p.get("position")),vector(p.get("eye")),vector(p.get("orientation")),vector(p.get("velocity")),integer(p.get("health")),integer(p.get("tool")),integer(p.get("blocks")),integer(p.get("ammo_clip")),integer(p.get("ammo_reserve")),bool(p.get("is_bot")),integer(p.get("weapon_tool")),list(p.get("loadout")).stream().map(BotFoundationsTest::integer).toList(),list(p.get("prefabs")).stream().map(BotFoundationsTest::string).toList(),stocks(p.get("oriented_stock")),integer(p.get("carried_entity_id")),integer(p.get("jetpack_id")),real(p.get("jetpack_fuel")),bool(p.get("grounded")),bool(p.get("wade")),bool(p.get("reloading")),string(p.get("last_action_kind")),bool(p.get("last_action_accepted")),nullableVector(p.get("last_action_position")),number(p.get("last_action_frame")),real(p.get("last_action_at")),real(p.get("last_damage_at")),integer(p.get("last_damage_source_id")),nullableVector(p.get("last_damage_source_position")),integer(p.get("life_id")),stocks(p.get("deployable_stock")),number(p.get("last_action_request_id")),string(p.get("last_action_reason")),bool(p.get("last_task_accepted")),real(p.get("last_task_at")),bool(p.get("can_shoot")),list(p.get("weapon_ammo")).stream().map(v->{var a=list(v);return new Ammo(integer(a.get(0)),integer(a.get(1)),integer(a.get(2)));}).toList(),bool(p.get("spawn_protected")),integer(p.get("last_damage_kind")),bool(p.get("can_dig")));
    }
}
