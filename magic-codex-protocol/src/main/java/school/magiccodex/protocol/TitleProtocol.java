package school.magiccodex.protocol;

import java.io.*;
import java.util.*;

/** Client sends IDs only; ownership, side and revision are verified by the server. */
public final class TitleProtocol {
    public static final String REQUEST="magiccodex:title_request",RESPONSE="magiccodex:title_response";
    public static final int MAX_BYTES=24000,MAX_TITLES=96,OPEN=0,APPLY=1,CLOSE=2;
    private static final int VERSION=0x54495401;
    public record Request(int action,long sequence,String token,long revision,String prefix,String suffix){}
    public record Entry(String id,int side,String name,int color){}
    public record Response(boolean open,long sequence,String token,long revision,String nickname,String prefix,String suffix,String message,List<Entry> entries){public Response{entries=List.copyOf(entries);}}
    public static boolean id(String id){return id!=null&&id.matches("[a-z0-9_-]{1,48}");}
    private interface Writer{void run(DataOutputStream d)throws IOException;}
    private static byte[] out(Writer w){try{var b=new ByteArrayOutputStream();var d=new DataOutputStream(b);d.writeInt(VERSION);w.run(d);if(b.size()>MAX_BYTES)throw new IOException("Size");return b.toByteArray();}catch(IOException e){throw new IllegalArgumentException(e);}}
    private static DataInputStream in(byte[] b)throws IOException{if(b==null||b.length<4||b.length>MAX_BYTES)throw new IOException("Size");var d=new DataInputStream(new ByteArrayInputStream(b));if(d.readInt()!=VERSION)throw new IOException("Version");return d;}
    private static void str(DataOutputStream d,String s,int max)throws IOException{if(s==null||s.length()>max)throw new IOException("String");d.writeUTF(s);}
    private static String str(DataInputStream d,int max)throws IOException{String s=d.readUTF();if(s.length()>max)throw new IOException("String");return s;}
    private static boolean optional(String s){return s!=null&&(s.isEmpty()||id(s));}
    private static void valid(Request r)throws IOException{if(r.action()<0||r.action()>2||r.sequence()<1||r.revision()<0||!optional(r.prefix())||!optional(r.suffix())||r.token()==null||!(r.token().isEmpty()||r.token().matches("[a-f0-9-]{36}"))||(r.action()==APPLY&&r.token().isEmpty()))throw new IOException("Request");}
    public static byte[] encode(Request r){return out(d->{valid(r);d.writeByte(r.action());d.writeLong(r.sequence());str(d,r.token(),36);d.writeLong(r.revision());str(d,r.prefix(),48);str(d,r.suffix(),48);});}
    public static Request request(byte[] b){try{var d=in(b);var r=new Request(d.readUnsignedByte(),d.readLong(),str(d,36),d.readLong(),str(d,48),str(d,48));valid(r);if(d.available()!=0)throw new IOException("Trailing");return r;}catch(IOException e){throw new IllegalArgumentException(e);}}
    public static byte[] encode(Response r){return out(d->{
        if(r.sequence()<0||r.revision()<0||r.entries().size()>MAX_TITLES||!optional(r.prefix())||!optional(r.suffix())||!r.token().matches("[a-f0-9-]{36}"))throw new IOException("Response");
        d.writeBoolean(r.open());d.writeLong(r.sequence());str(d,r.token(),36);d.writeLong(r.revision());str(d,r.nickname(),64);str(d,r.prefix(),48);str(d,r.suffix(),48);str(d,r.message(),160);d.writeByte(r.entries().size());
        var ids=new HashSet<String>();for(var e:r.entries()){if(!id(e.id())||!ids.add(e.id())||e.side()<0||e.side()>1||e.name().isBlank()||e.color()<0||e.color()>0xffffff)throw new IOException("Entry");str(d,e.id(),48);d.writeByte(e.side());str(d,e.name(),48);d.writeInt(e.color());}
    });}
    public static Response response(byte[] b){try{var d=in(b);boolean open=d.readBoolean();long sequence=d.readLong();String token=str(d,36);long revision=d.readLong();String nickname=str(d,64),prefix=str(d,48),suffix=str(d,48),message=str(d,160);int count=d.readUnsignedByte();if(count>MAX_TITLES)throw new IOException("Count");var entries=new ArrayList<Entry>();for(int i=0;i<count;i++)entries.add(new Entry(str(d,48),d.readUnsignedByte(),str(d,48),d.readInt()));var r=new Response(open,sequence,token,revision,nickname,prefix,suffix,message,entries);encode(r);if(d.available()!=0)throw new IOException("Trailing");return r;}catch(IOException e){throw new IllegalArgumentException(e);}}
    private TitleProtocol(){}
}
