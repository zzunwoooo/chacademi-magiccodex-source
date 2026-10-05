package school.magiccodex.protocol;

import java.io.*;
import java.util.*;

/** Small, bounded messages. The authenticated connection is always the acting player. */
public final class SocialProtocol {
    public static final String REQUEST="magiccodex:social_request", RESPONSE="magiccodex:social_response";
    public static final int MAX_BYTES=16000, LIMIT=50, TEXT_LIMIT=240;
    public static final int LIST=1, ADD=2, REMOVE=3, WHISPER=4, SEND=5, CLOSE=6, CANCEL=7;
    public static final int SNAPSHOT=1, NOTICE=2, COMPOSE=3, SENT=4, RECEIVED=5, OPEN=6;
    public static final UUID NONE=new UUID(0,0);
    private static final int VERSION=0x534F4301;
    public record Entry(UUID id,String name,String dormitory,boolean online){}
    public record Request(int action,long sequence,UUID target,long ticket,String text){}
    public record Response(int kind,long sequence,UUID target,long ticket,String name,String dormitory,String text,int cooldown,List<Entry> entries){
        public Response{entries=List.copyOf(entries);}
    }
    public static String cleanMessage(String s){
        if(s==null||s.isBlank()||s.length()>TEXT_LIMIT||s.codePoints().anyMatch(c->Character.isISOControl(c)||c==0xA7||Character.getType(c)==Character.FORMAT))throw new IllegalArgumentException("Invalid message");
        return s.strip();
    }
    private interface Writer{void write(DataOutputStream d)throws IOException;}
    private static byte[] out(Writer w){try{var b=new ByteArrayOutputStream();var d=new DataOutputStream(b);d.writeInt(VERSION);w.write(d);d.flush();if(b.size()>MAX_BYTES)throw new IOException("Size");return b.toByteArray();}catch(IOException e){throw new IllegalArgumentException(e);}}
    private static DataInputStream in(byte[] b)throws IOException{if(b.length<4||b.length>MAX_BYTES)throw new IOException("Size");var d=new DataInputStream(new ByteArrayInputStream(b));if(d.readInt()!=VERSION)throw new IOException("Version");return d;}
    private static void uuid(DataOutputStream d,UUID id)throws IOException{d.writeLong(id.getMostSignificantBits());d.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream d)throws IOException{return new UUID(d.readLong(),d.readLong());}
    private static void str(DataOutputStream d,String s,int max)throws IOException{if(s==null||s.length()>max)throw new IOException("String");d.writeUTF(s);}
    private static String str(DataInputStream d,int max)throws IOException{var s=d.readUTF();if(s.length()>max)throw new IOException("String");return s;}
    public static byte[] encode(Request r){if(r.action()<1||r.action()>7||r.sequence()<=0||r.ticket()<0)throw new IllegalArgumentException("Request");return out(d->{d.writeByte(r.action());d.writeLong(r.sequence());uuid(d,r.target());d.writeLong(r.ticket());str(d,r.text(),TEXT_LIMIT);});}
    public static Request request(byte[] bytes){try{var d=in(bytes);var r=new Request(d.readUnsignedByte(),d.readLong(),uuid(d),d.readLong(),str(d,TEXT_LIMIT));if(d.available()!=0||r.action()<1||r.action()>7||r.sequence()<=0||r.ticket()<0)throw new IOException("Request");return r;}catch(IOException e){throw new IllegalArgumentException(e);}}
    public static byte[] encode(Response r){return out(d->{if(r.kind()<1||r.kind()>6||r.sequence()<0||r.ticket()<0||r.cooldown()<0||r.cooldown()>86400000||r.entries().size()>LIMIT)throw new IOException("Response");d.writeByte(r.kind());d.writeLong(r.sequence());uuid(d,r.target());d.writeLong(r.ticket());str(d,r.name(),16);str(d,r.dormitory(),64);str(d,r.text(),TEXT_LIMIT);d.writeInt(r.cooldown());d.writeByte(r.entries().size());for(var e:r.entries()){uuid(d,e.id());str(d,e.name(),16);str(d,e.dormitory(),64);d.writeBoolean(e.online());}});}
    public static Response response(byte[] bytes){try{var d=in(bytes);int kind=d.readUnsignedByte();long seq=d.readLong();UUID target=uuid(d);long ticket=d.readLong();String name=str(d,16),dorm=str(d,64),text=str(d,TEXT_LIMIT);int cd=d.readInt(),size=d.readUnsignedByte();if(kind<1||kind>6||seq<0||ticket<0||size>LIMIT||cd<0||cd>86400000)throw new IOException("Response");var entries=new ArrayList<Entry>();var ids=new HashSet<UUID>();for(int i=0;i<size;i++){var e=new Entry(uuid(d),str(d,16),str(d,64),d.readBoolean());if(!ids.add(e.id()))throw new IOException("Duplicate");entries.add(e);}if(d.available()!=0)throw new IOException("Trailing bytes");return new Response(kind,seq,target,ticket,name,dorm,text,cd,entries);}catch(IOException e){throw new IllegalArgumentException(e);}}
}
