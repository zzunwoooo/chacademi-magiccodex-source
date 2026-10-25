import java.nio.file.*;import java.util.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public class OpeningFinalAlign {
 static boolean lambda(MethodNode m){return m.name.startsWith("lambda$");}
 static String root(MethodNode m){return m.name.substring(0,m.name.lastIndexOf('$'));}
 static String semantic(MethodNode m){return root(m)+m.desc+OpeningDelta.norm(m).replaceAll("lambda\\$([A-Za-z0-9_$]+)\\$[0-9]+", "lambda\\$$1\\$N");}
 static void rename(ClassNode c,Map<String,String> names){
  for(var m:c.methods){String replacement=names.get(m.name+m.desc);if(replacement!=null)m.name=replacement;for(var n:m.instructions){if(n instanceof MethodInsnNode v&&v.owner.equals(c.name)){String k=names.get(v.name+v.desc);if(k!=null)v.name=k;}if(n instanceof InvokeDynamicInsnNode v)for(int i=0;i<v.bsmArgs.length;i++)if(v.bsmArgs[i] instanceof Handle h&&h.getOwner().equals(c.name)){String k=names.get(h.getName()+h.getDesc());if(k!=null)v.bsmArgs[i]=new Handle(h.getTag(),h.getOwner(),k,h.getDesc(),h.isInterface());}}}
  var seen=new HashSet<String>();for(var m:c.methods)if(!seen.add(OpeningDelta.key(m)))throw new IllegalStateException("duplicate after lambda alignment "+c.name+" "+OpeningDelta.key(m));
 }
 static void write(ClassNode c,Path p)throws Exception{var w=new ClassWriter(0);c.accept(w);Files.write(p,w.toByteArray());}
 public static void main(String[]a)throws Exception{
 var live=OpeningDelta.read(Files.readAllBytes(Path.of(a[0])),0);var newer=OpeningDelta.read(Files.readAllBytes(Path.of(a[1])),0);var old=OpeningDelta.read(Files.readAllBytes(Path.of(a[2])),0);
 if(live.name.equals("school/magiccodex/paper/SchoolBridge")){var p=new ProcessBuilder("C:/Program Files/Java/jdk-21.0.10/bin/java.exe","-cp",a[6],"OpeningDelta",a[0],a[1],a[2],a[5]).inheritIO().start();if(p.waitFor()!=0)System.exit(2);return;} var baseline=new HashMap<String,List<MethodNode>>();for(var m:old.methods)if(lambda(m))baseline.computeIfAbsent(semantic(m),k->new ArrayList<>()).add(m);
 var deployed=new HashMap<String,List<MethodNode>>();for(var m:live.methods)if(lambda(m))deployed.computeIfAbsent(semantic(m),k->new ArrayList<>()).add(m);
 var om=new HashMap<String,String>();var nm=new HashMap<String,String>();var used=new HashSet<String>();var oldToLive=new HashSet<String>();int serial=0,matched=0,fresh=0;
 for(var m:old.methods)if(lambda(m)){var options=deployed.getOrDefault(semantic(m),List.of());var eq=options.stream().filter(v->!used.contains(OpeningDelta.key(v))).findFirst();if(eq.isPresent()){var v=eq.get();used.add(OpeningDelta.key(v));om.put(OpeningDelta.key(m),v.name);oldToLive.add(OpeningDelta.key(m));matched++;}}
 for(var m:old.methods)if(lambda(m)&&!oldToLive.contains(OpeningDelta.key(m)))om.put(OpeningDelta.key(m),"baseline$unmatched$"+m.name);
 var claimed=new HashSet<String>();for(var m:newer.methods)if(lambda(m)){
  var options=baseline.getOrDefault(semantic(m),List.of());var eq=options.stream().filter(v->!claimed.contains(OpeningDelta.key(v))).findFirst();
  if(eq.isPresent()&&oldToLive.contains(OpeningDelta.key(eq.get()))){var v=eq.get();claimed.add(OpeningDelta.key(v));nm.put(OpeningDelta.key(m),om.get(OpeningDelta.key(v)));}
  else if(eq.isPresent()){var v=eq.get();claimed.add(OpeningDelta.key(v));nm.put(OpeningDelta.key(m),om.get(OpeningDelta.key(v)));}
  else {String name="opening$"+root(m)+"$"+(serial++);nm.put(OpeningDelta.key(m),name);fresh++;}
 }
 rename(old,om);rename(newer,nm);write(old,Path.of(a[3]));write(newer,Path.of(a[4]));
 System.out.println("PASS LAMBDA_ALIGNMENT "+live.name+" equalBodyOldToLive="+matched+" freshUniqueHelpers="+fresh+" oldMap="+om+" newMap="+nm);
 var p=new ProcessBuilder("C:/Program Files/Java/jdk-21.0.10/bin/java.exe","-Xmx128m","-cp",a[6],"OpeningDelta",a[0],a[4],a[3],a[5]).inheritIO().start();if(p.waitFor()!=0)System.exit(2);
 }
}
