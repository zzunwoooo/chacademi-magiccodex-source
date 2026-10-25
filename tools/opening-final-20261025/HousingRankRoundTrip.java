import java.nio.file.*;import java.net.*;import java.util.*;import java.lang.reflect.*;
public class HousingRankRoundTrip{
 static final String P="school.magiccodex.protocol.HousingRankProtocol";
 static Class<?> c(ClassLoader l,String n)throws Exception{return Class.forName(P+(n.isEmpty()?"":"$"+n),true,l);}
 static Object make(ClassLoader l,String n,Object...a)throws Exception{return c(l,n).getConstructors()[0].newInstance(a);}
 static void transfer(ClassLoader a,ClassLoader b,String direction,Object obj)throws Exception{
 var api=c(a,"");byte[] bytes=(byte[])api.getMethod("encode",c(a,direction)).invoke(null,obj);String method=direction.equals("ToServer")?"toServer":"toClient";Object decoded=c(b,"").getMethod(method,byte[].class).invoke(null,(Object)bytes);
 if(!obj.toString().equals(decoded.toString()))throw new AssertionError("cross decode "+obj);byte[] reverse=(byte[])c(b,"").getMethod("encode",c(b,direction)).invoke(null,decoded);if(!Arrays.equals(bytes,reverse))throw new AssertionError("byte roundtrip");System.out.println("PASS "+obj.getClass().getSimpleName()+" bytes="+bytes.length);
 }
 public static void main(String[]args)throws Exception{try(var a=new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader());var b=new URLClassLoader(new URL[]{Path.of(args[1]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
 for(int i=0;i<2;i++){ClassLoader from=i==0?a:b,to=i==0?b:a;var id=UUID.fromString("12345678-1234-5678-1234-567812345678");
 transfer(from,to,"ToServer",make(from,"Query",1L,2,0));transfer(from,to,"ToServer",make(from,"Visit",id));transfer(from,to,"ToClient",make(from,"Open",1));transfer(from,to,"ToClient",make(from,"Notice","test"));
 var row=make(from,"Row",1,id,"player",3,7,2,1);transfer(from,to,"ToClient",make(from,"Page",1L,0,0,1,List.of(row),""));
 for(String n:List.of("Query","Visit","Open","Page","Notice")){var k=c(from,n);if(!k.isRecord())throw new AssertionError("record");for(var iface:k.getInterfaces())if(iface.isSealed()&&!Arrays.asList(iface.getPermittedSubclasses()).contains(k))throw new AssertionError("sealed");}
 byte[] invalid={0,1,0};boolean rejected=false;try{Object v=c(to,"").getMethod("toServer",byte[].class).invoke(null,(Object)invalid);rejected=v==null;}catch(InvocationTargetException ex){if(ex.getCause() instanceof IllegalArgumentException)rejected=true;else throw ex;}if(!rejected)throw new AssertionError("invalid accepted");
 }System.out.println("PASS HousingRank actual JAR bidirectional sealed-record/version smoke");}}
}