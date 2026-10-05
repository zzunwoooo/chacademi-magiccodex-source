package school.magiccodex.protocol;

import java.io.*;
import java.util.*;

public final class ReconfigurationProtocol {
    public static final String REQUEST="magiccodex:reconfig_request", RESPONSE="magiccodex:reconfig_response";
    public static final int MAX_BYTES=4096, APPLY=0, SELECT=1, CLOSE=2;
    public record Request(long token,int action,int mode) {
        public Request { if(token<=0||action<0||action>2||mode<0||mode>2)throw new IllegalArgumentException(); }
    }
    // state: offer / committed / refused. Values always originate on the server.
    public record Response(long token,int mode,int state,int slot,String name,String message,int credits,
                           int nodes,int affinity,int nextAffinity,int attempts,int successes,int limit,
                           int nextAttempts,List<Double> before,List<Double> after,boolean available) {
        public Response {
            before=List.copyOf(before);after=List.copyOf(after);
            if(token<=0||mode<0||mode>2||state<0||state>2||slot< -1||slot>35||name.length()>80||message.length()>180||credits<0||credits>1000000||nodes<0||nodes>10||affinity<0||affinity>2||nextAffinity< -1||nextAffinity>2||attempts<0||attempts>100||successes<0||successes>attempts||limit<1||limit>100||nextAttempts<0||nextAttempts>100||before.size()!=3||after.size()!=3)throw new IllegalArgumentException();
            for(var list:List.of(before,after))for(double v:list)if(!Double.isFinite(v)||v<0||v>1000000)throw new IllegalArgumentException();
        }
    }
    public static byte[] request(Request r){return write(d->{d.writeLong(r.token);d.writeByte(r.action);d.writeByte(r.mode);});}
    public static Request request(byte[] b){return read(b,d->new Request(d.readLong(),d.readUnsignedByte(),d.readUnsignedByte()));}
    public static byte[] response(Response r){return write(d->{d.writeByte(1);d.writeLong(r.token);for(int v:new int[]{r.mode,r.state,r.slot,r.credits,r.nodes,r.affinity,r.nextAffinity,r.attempts,r.successes,r.limit,r.nextAttempts})d.writeInt(v);d.writeUTF(r.name);d.writeUTF(r.message);for(double v:r.before)d.writeDouble(v);for(double v:r.after)d.writeDouble(v);d.writeBoolean(r.available);});}
    public static Response response(byte[] b){return read(b,d->{if(d.readUnsignedByte()!=1)throw new IOException();long token=d.readLong();int[] v=new int[11];for(int i=0;i<v.length;i++)v[i]=d.readInt();String name=d.readUTF(),msg=d.readUTF();List<Double> a=new ArrayList<>(),z=new ArrayList<>();for(int i=0;i<3;i++)a.add(d.readDouble());for(int i=0;i<3;i++)z.add(d.readDouble());return new Response(token,v[0],v[1],v[2],name,msg,v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],a,z,d.readBoolean());});}
    private interface Writer{void go(DataOutputStream d)throws IOException;}
    private interface Reader<T>{T go(DataInputStream d)throws IOException;}
    private static byte[] write(Writer w){try{var b=new ByteArrayOutputStream();w.go(new DataOutputStream(b));if(b.size()>MAX_BYTES)throw new IOException();return b.toByteArray();}catch(IOException e){throw new IllegalArgumentException(e);}}
    private static <T>T read(byte[] b,Reader<T> r){try{if(b.length>MAX_BYTES)throw new IOException();var d=new DataInputStream(new ByteArrayInputStream(b));T v=r.go(d);if(d.available()!=0)throw new IOException();return v;}catch(IOException e){throw new IllegalArgumentException(e);}}
}
