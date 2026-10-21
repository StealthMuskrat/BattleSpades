// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
/** Length-framed, bounded, explicitly allowlisted data records for a local worker pipe. No object deserialization. */
final class BotWire {
    private static final int MAX_BYTES=256*1024*1024,MAX_ITEMS=4*1024*1024;
    private static final List<Class<?>> RECORDS=List.of(Vector.class,BotProfile.class,Stock.class,Ammo.class,Player.class,Entity.class,Objective.class,Perception.class,Movement.class,Look.class,Action.class,Intent.class,Diagnostic.class,Stimulus.class,VoxelChange.class,WorldDelta.class,Cell.class,BotPrefabPolicy.Geometry.class,BotPrefabPolicy.Bounds.class,BotProcessSession.Request.class,BotProcessSession.Response.class);
    private static final List<Class<?>> ENUMS=List.of(ActionKind.class,Priority.class,Affordance.class,StimulusKind.class);
    private BotWire(){}
    static void send(OutputStream stream,Object value)throws IOException{var buffer=new ByteArrayOutputStream();try(var out=new DataOutputStream(buffer)){write(out,value,0);}if(buffer.size()>MAX_BYTES)throw new IOException("Bot pipe record exceeds bound");var out=new DataOutputStream(stream);out.writeInt(buffer.size());buffer.writeTo(out);out.flush();}
    static Object receive(InputStream stream)throws IOException{var in=new DataInputStream(stream);int length=in.readInt();if(length<1||length>MAX_BYTES)throw new IOException("Bot pipe length outside bound");byte[] raw=in.readNBytes(length);if(raw.length!=length)throw new EOFException("Incomplete bot pipe record");try(var record=new DataInputStream(new ByteArrayInputStream(raw))){Object value=read(record,0);if(record.available()!=0)throw new IOException("Trailing bot pipe bytes");return value;}}
    private static void text(DataOutputStream out,String value)throws IOException{byte[] raw=value.getBytes(StandardCharsets.UTF_8);if(raw.length>8*1024*1024)throw new IOException("Bot pipe string bound");out.writeInt(raw.length);out.write(raw);}
    private static String text(DataInputStream in)throws IOException{int length=count(in,8*1024*1024);byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException();return new String(bytes,StandardCharsets.UTF_8);}
    private static int count(DataInputStream in,int maximum)throws IOException{int count=in.readInt();if(count<0||count>maximum||count>in.available())throw new IOException("Bot pipe collection bound");return count;}
    private static void write(DataOutputStream out,Object value,int depth)throws IOException{
        if(depth>48)throw new IOException("Bot pipe nesting bound");
        if(value==null){out.writeByte(0);return;}if(value instanceof String s){out.writeByte(1);text(out,s);return;}if(value instanceof Integer i){out.writeByte(2);out.writeInt(i);return;}if(value instanceof Long l){out.writeByte(3);out.writeLong(l);return;}if(value instanceof Double d){out.writeByte(4);out.writeDouble(d);return;}if(value instanceof Boolean b){out.writeByte(5);out.writeBoolean(b);return;}
        if(value instanceof List<?> list){if(list.size()>MAX_ITEMS)throw new IOException("Bot pipe list bound");out.writeByte(6);out.writeInt(list.size());for(var item:list)write(out,item,depth+1);return;}
        if(value instanceof Map<?,?> map){if(map.size()>16384)throw new IOException("Bot pipe map bound");out.writeByte(7);out.writeInt(map.size());for(var e:map.entrySet()){if(!(e.getKey() instanceof String key))throw new IOException("Bot pipe string keys required");text(out,key);write(out,e.getValue(),depth+1);}return;}
        if(value instanceof Record record){int id=RECORDS.indexOf(value.getClass());if(id<0)throw new IOException("Bot pipe record type rejected");out.writeByte(8);out.writeByte(id);try{for(var component:record.getClass().getRecordComponents())write(out,component.getAccessor().invoke(record),depth+1);}catch(ReflectiveOperationException error){throw new IOException("Bot record access",error);}return;}
        if(value instanceof Enum<?> enumeration){int id=ENUMS.indexOf(value.getClass());if(id<0)throw new IOException("Bot pipe enum rejected");out.writeByte(9);out.writeByte(id);out.writeByte(enumeration.ordinal());return;}
        if(value instanceof MapSnapshot map){out.writeByte(10);out.writeLong(map.mapEpoch());out.writeLong(map.topologyVersion());write(out,map.rawVxl(),depth+1);text(out,map.mode());text(out,map.mapName());text(out,map.sourceFormat());text(out,map.mapDirectory());write(out,map.cells(),depth+1);write(out,map.prefabGeometry(),depth+1);return;}
        if(value instanceof byte[] bytes){if(bytes.length>MAX_BYTES-1024)throw new IOException("Bot raw map bound");out.writeByte(11);out.writeInt(bytes.length);out.write(bytes);return;}
        throw new IOException("Unsupported bot pipe value "+value.getClass().getName());
    }
    @SuppressWarnings("unchecked") private static Object read(DataInputStream in,int depth)throws IOException{
        if(depth>48)throw new IOException("Bot pipe nesting bound");return switch(in.readUnsignedByte()){
            case 0->null;case 1->text(in);case 2->in.readInt();case 3->in.readLong();case 4->in.readDouble();case 5->in.readBoolean();
            case 6->{int count=count(in,MAX_ITEMS);var rows=new ArrayList<Object>(count);for(int i=0;i<count;i++)rows.add(read(in,depth+1));yield Collections.unmodifiableList(rows);}
            case 7->{int count=count(in,16384);var map=new LinkedHashMap<String,Object>();for(int i=0;i<count;i++){String key=text(in);if(map.containsKey(key))throw new IOException("Repeated bot pipe key");map.put(key,read(in,depth+1));}yield Collections.unmodifiableMap(map);}
            case 8->{int id=in.readUnsignedByte();if(id>=RECORDS.size())throw new IOException("Bot pipe record id");var type=RECORDS.get(id);var components=type.getRecordComponents();var arguments=new Object[components.length];for(int i=0;i<arguments.length;i++)arguments[i]=read(in,depth+1);try{yield type.getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new)).newInstance(arguments);}catch(ReflectiveOperationException|IllegalArgumentException error){throw new IOException("Invalid bot pipe record",error);}}
            case 9->{int id=in.readUnsignedByte(),ordinal=in.readUnsignedByte();if(id>=ENUMS.size())throw new IOException("Bot enum id");var values=ENUMS.get(id).getEnumConstants();if(ordinal>=values.length)throw new IOException("Bot enum value");yield values[ordinal];}
            case 10->{long epoch=in.readLong(),version=in.readLong();var raw=(byte[])read(in,depth+1);String mode=text(in),name=text(in),format=text(in),directory=text(in);var cells=(List<VoxelChange>)read(in,depth+1);var geometry=(List<BotPrefabPolicy.Geometry>)read(in,depth+1);yield new MapSnapshot(epoch,version,raw,mode,name,cells,format,directory).withPrefabs(geometry);}
            case 11->{int count=count(in,MAX_BYTES);byte[] raw=in.readNBytes(count);if(raw.length!=count)throw new EOFException();yield raw;}
            default->throw new IOException("Bot pipe value tag");
        };}
}
