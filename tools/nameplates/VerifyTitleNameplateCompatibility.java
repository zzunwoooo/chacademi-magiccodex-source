import java.net.*;import java.nio.file.*;import java.util.*;
public class VerifyTitleNameplateCompatibility {
 public static void main(String[] args)throws Exception{
  var loaders=new ArrayList<URLClassLoader>();var types=new ArrayList<Class<?>>();for(String a:args){var l=new URLClassLoader(new URL[]{Path.of(a).toUri().toURL()},ClassLoader.getPlatformClassLoader());loaders.add(l);types.add(l.loadClass("school.magiccodex.protocol.NameplateProtocol"));}
  UUID id=UUID.randomUUID();byte[] bytes=null;
  for(var type:types){var run=type.getClassLoader().loadClass(type.getName()+"$TitleRun");var entry=type.getClassLoader().loadClass(type.getName()+"$Entry");var runs=List.of(run.getConstructor(String.class,int.class).newInstance("소환술",0x96e4ec),run.getConstructor(String.class,int.class).newInstance("교수",0xdec58e));Object row=entry.getConstructor(int.class,UUID.class,int.class,String.class,List.class).newInstance(47,id,4,"황준우",runs);byte[] encoded=(byte[])type.getMethod("encode",List.class).invoke(null,List.of(row));if(bytes==null)bytes=encoded;else if(!Arrays.equals(bytes,encoded))throw new AssertionError("encoding mismatch");}
  for(var type:types){var row=((List<?>)type.getMethod("decode",byte[].class).invoke(null,(Object)bytes)).getFirst();var runs=(List<?>)row.getClass().getMethod("titles").invoke(row);if(runs.size()!=2)throw new AssertionError("slot count");if(!runs.getFirst().getClass().getMethod("text").invoke(runs.getFirst()).equals("소환술"))throw new AssertionError("prefix");if(!runs.getLast().getClass().getMethod("color").invoke(runs.getLast()).equals(0xdec58e))throw new AssertionError("suffix color");}
  for(var l:loaders)l.close();System.out.println("PASS: actual title-v2 Bridge and remapped Fabric jars byte-identically encode and cross-decode both original colored title runs");
 }
}
