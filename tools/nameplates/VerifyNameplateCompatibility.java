import java.net.*;import java.nio.file.*;import java.util.*;
public class VerifyNameplateCompatibility {
 public static void main(String[] args)throws Exception{
  var paths=new ArrayList<Path>();for(String a:args)paths.add(Path.of(a));var loaders=new ArrayList<URLClassLoader>();
  for(var p:paths)loaders.add(new URLClassLoader(new URL[]{p.toUri().toURL()},ClassLoader.getPlatformClassLoader()));
  var types=new ArrayList<Class<?>>();for(var l:loaders)types.add(l.loadClass("school.magiccodex.protocol.NameplateProtocol"));
  UUID id=UUID.randomUUID();byte[] bytes=null;
  for(var type:types){Class<?> entry=type.getClassLoader().loadClass(type.getName()+"$Entry");Object row=entry.getConstructor(int.class,UUID.class,int.class,String.class,String.class).newInstance(47,id,4,"엘레나😀","교수");byte[] encoded=(byte[])type.getMethod("encode",List.class).invoke(null,List.of(row));if(bytes==null)bytes=encoded;else if(!Arrays.equals(bytes,encoded))throw new AssertionError("encoding mismatch");}
  for(var type:types){var list=(List<?>)type.getMethod("decode",byte[].class).invoke(null,(Object)bytes);var row=list.getFirst();if(!row.getClass().getMethod("uuid").invoke(row).equals(id))throw new AssertionError("identity mismatch");if(!row.getClass().getMethod("name").invoke(row).equals("엘레나😀"))throw new AssertionError("text mismatch");}
  for(var l:loaders)l.close();System.out.println("PASS: actual Bridge and remapped Fabric jars cross-decode and byte-identically encode authoritative Unicode snapshot");
 }
}
