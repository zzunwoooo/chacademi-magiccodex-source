package school.magiccodex.protocol;

import java.io.*;
import java.util.*;

/** Bounded public profile snapshots, separate from the viewer's personal mana channel. */
public final class StatsProtocol {
    public static final String REQUEST="magiccodex:stats_request", RESPONSE="magiccodex:stats_response";
    public static final int MAX_BYTES=30000, OPEN=1, SNAPSHOT=2, NOTICE=3, GONE=4;
    public static final int REFRESH=1, CLOSE=2, FRIEND=3, POPULARITY=4;
    private static final int VERSION=0x53544102;
    public record Request(int action,long session,UUID target){}
    public record Profile(UUID id,String name,int circle,String dormitory,Double power,Double popularity,
                          double health,double armor,Double mana,Double maximum,Double regeneration,
                          int learned,int total,String texture,String signature,List<byte[]> equipment,Double haste){
        public Profile(UUID id,String name,int circle,String dormitory,Double power,Double popularity,double health,double armor,Double mana,Double maximum,Double regeneration,int learned,int total,String texture,String signature,List<byte[]> equipment){this(id,name,circle,dormitory,power,popularity,health,armor,mana,maximum,regeneration,learned,total,texture,signature,equipment,null);}
    }
    public record Response(int kind,long session,UUID target,String message,Profile profile){}
    private StatsProtocol(){}
    private interface Writer {void write(DataOutputStream d)throws IOException;}
    private static byte[] encode(Writer writer){try{var bytes=new ByteArrayOutputStream();var d=new DataOutputStream(bytes);d.writeInt(VERSION);writer.write(d);d.flush();if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("Stats payload too large");return bytes.toByteArray();}catch(IOException e){throw new IllegalArgumentException(e);}}
    private static DataInputStream input(byte[] bytes)throws IOException{if(bytes.length<4||bytes.length>MAX_BYTES)throw new IOException("Invalid size");var d=new DataInputStream(new ByteArrayInputStream(bytes));if(d.readInt()!=VERSION)throw new IOException("Invalid version");return d;}
    private static void uuid(DataOutputStream d,UUID id)throws IOException{d.writeLong(id.getMostSignificantBits());d.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream d)throws IOException{return new UUID(d.readLong(),d.readLong());}
    private static void string(DataOutputStream d,String s,int max)throws IOException{if(s==null||s.length()>max)throw new IOException("Invalid string");d.writeUTF(s);}
    private static String string(DataInputStream d,int max)throws IOException{String s=d.readUTF();if(s.length()>max)throw new IOException("Invalid string");return s;}
    private static double finite(double v)throws IOException{if(!Double.isFinite(v)||Math.abs(v)>1e9)throw new IOException("Invalid number");return v;}
    private static void number(DataOutputStream d,Double v)throws IOException{d.writeBoolean(v!=null);if(v!=null)d.writeDouble(finite(v));}
    private static Double number(DataInputStream d)throws IOException{return d.readBoolean()?finite(d.readDouble()):null;}
    private static void requestValid(Request r)throws IOException{if(r.action()<1||r.action()>4||r.session()<=0)throw new IOException("Invalid request");}
    public static byte[] encodeRequest(Request r){return encode(d->{requestValid(r);d.writeByte(r.action());d.writeLong(r.session());uuid(d,r.target());});}
    public static Request decodeRequest(byte[] bytes){try{var d=input(bytes);var r=new Request(d.readUnsignedByte(),d.readLong(),uuid(d));requestValid(r);if(d.available()!=0)throw new IOException("Trailing bytes");return r;}catch(IOException e){throw new IllegalArgumentException(e);}}
    public static byte[] encodeResponse(Response r){return encode(d->{
        if(r.kind()<1||r.kind()>4||r.session()<=0)throw new IOException("Invalid response");
        d.writeByte(r.kind());d.writeLong(r.session());uuid(d,r.target());string(d,r.message(),160);
        if(r.kind()==OPEN||r.kind()==SNAPSHOT){var p=r.profile();validate(p);if(!p.id().equals(r.target()))throw new IOException("Wrong target");
            string(d,p.name(),16);d.writeByte(p.circle());string(d,p.dormitory(),64);number(d,p.power());number(d,p.popularity());
            d.writeDouble(finite(p.health()));d.writeDouble(finite(p.armor()));number(d,p.mana());number(d,p.maximum());number(d,p.regeneration());number(d,p.haste());
            d.writeInt(p.learned());d.writeInt(p.total());string(d,p.texture(),4096);string(d,p.signature(),2048);
            for(byte[] item:p.equipment()){if(item.length>3072)throw new IOException("Large item");d.writeShort(item.length);d.write(item);}
        }
    });}
    private static void validate(Profile p)throws IOException{if(p.haste()!=null&&(p.haste()<0||!Double.isFinite(p.haste())||p.haste()>MagicHaste.MAX))throw new IOException("Invalid haste");if(p.circle()<0||p.circle()>9||p.total()<0||p.total()>4096||p.learned()<0||p.learned()>p.total()||p.equipment().size()!=6)throw new IOException("Invalid profile");}
    public static Response decodeResponse(byte[] bytes){try{var d=input(bytes);int kind=d.readUnsignedByte();long session=d.readLong();UUID id=uuid(d);String message=string(d,160);Profile p=null;
        if(kind<1||kind>4||session<=0)throw new IOException("Invalid response");
        if(kind==OPEN||kind==SNAPSHOT){String name=string(d,16);int circle=d.readUnsignedByte();String dorm=string(d,64);Double power=number(d),popularity=number(d);double health=finite(d.readDouble()),armor=finite(d.readDouble());Double mana=number(d),max=number(d),regen=number(d),haste=number(d);int learned=d.readInt(),total=d.readInt();String texture=string(d,4096),signature=string(d,2048);var items=new ArrayList<byte[]>();
            for(int i=0;i<6;i++){int size=d.readUnsignedShort();if(size>3072||size>d.available())throw new IOException("Invalid item size");items.add(d.readNBytes(size));}
            p=new Profile(id,name,circle,dorm,power,popularity,health,armor,mana,max,regen,learned,total,texture,signature,List.copyOf(items),haste);validate(p);
        }
        if(d.available()!=0)throw new IOException("Trailing bytes");return new Response(kind,session,id,message,p);
    }catch(IOException e){throw new IllegalArgumentException(e);}}
}
