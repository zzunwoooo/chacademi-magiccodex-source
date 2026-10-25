import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public class KyudichiPatch implements Opcodes {
 static final String OWNER="school/chacademia/kyudichi/Match", MP="school/chacademia/kyudichi/MatchPlayer";
 static final String KEY="rejoin(Lorg/bukkit/entity/Player;)Z";
 static MethodNode method(ClassNode c){return c.methods.stream().filter(m->OpeningDelta.key(m).equals(KEY)).findFirst().orElseThrow();}
 static MethodNode copy(MethodNode m){var c=new ClassNode();c.version=V21;c.access=ACC_PUBLIC;c.name="Fingerprint";c.superName="java/lang/Object";c.methods.add(m);var w=new ClassWriter(0);c.accept(w);return OpeningDelta.read(w.toByteArray(),0).methods.getFirst();}
 static AbstractInsnNode prev(AbstractInsnNode i){do{i=i.getPrevious();}while(i!=null&&i.getOpcode()<0);return i;}
 static List<AbstractInsnNode> insert(MethodNode m){
  var fields=Arrays.stream(m.instructions.toArray()).filter(i->i instanceof FieldInsnNode f&&f.getOpcode()==PUTFIELD&&f.owner.equals(MP)&&f.name.equals("house")&&f.desc.equals("I")).toList();
  if(fields.size()!=1)throw new IllegalStateException("house anchor not unique");
  var house=fields.getFirst();var call=prev(house);var player=prev(call);var hooks=prev(player);var plugin=prev(hooks);var self=prev(plugin);var anchor=prev(self);
  if(!(anchor instanceof VarInsnNode a&&a.getOpcode()==ALOAD&&a.var==2)||!(self instanceof VarInsnNode b&&b.getOpcode()==ALOAD&&b.var==0)||!(plugin instanceof FieldInsnNode f&&f.owner.equals(OWNER)&&f.name.equals("plugin"))||!(hooks instanceof MethodInsnNode h1&&h1.name.equals("hooks"))||!(player instanceof VarInsnNode p&&p.getOpcode()==ALOAD&&p.var==1)||!(call instanceof MethodInsnNode h2&&h2.owner.equals("school/chacademia/kyudichi/Hooks")&&h2.name.equals("house")))throw new IllegalStateException("house anchor instruction shape diverged");
  var list=new InsnList();var end=new LabelNode();
  list.add(new VarInsnNode(ALOAD,2));list.add(new LdcInsnNode(-1L));list.add(new FieldInsnNode(PUTFIELD,MP,"deadUntil","J"));
  list.add(new VarInsnNode(ALOAD,2));list.add(new FieldInsnNode(GETFIELD,MP,"returnTo","Lorg/bukkit/Location;"));list.add(new JumpInsnNode(IFNONNULL,end));
  list.add(new VarInsnNode(ALOAD,2));list.add(new VarInsnNode(ALOAD,1));list.add(new MethodInsnNode(INVOKEINTERFACE,"org/bukkit/entity/Player","getLocation","()Lorg/bukkit/Location;",true));list.add(new FieldInsnNode(PUTFIELD,MP,"returnTo","Lorg/bukkit/Location;"));
  list.add(new VarInsnNode(ALOAD,2));list.add(new VarInsnNode(ALOAD,1));list.add(new MethodInsnNode(INVOKEINTERFACE,"org/bukkit/entity/Player","getGameMode","()Lorg/bukkit/GameMode;",true));list.add(new FieldInsnNode(PUTFIELD,MP,"returnMode","Lorg/bukkit/GameMode;"));
  list.add(end);list.add(new FrameNode(F_FULL,3,new Object[]{OWNER,"org/bukkit/entity/Player",MP},0,null));
  var inserted=Arrays.asList(list.toArray());m.instructions.insertBefore(anchor,list);return inserted;
 }
 static boolean member(Map<String,ClassNode> classes,String owner,String name,String desc,boolean method,Set<String> seen){
  if(!seen.add(owner))return false;var c=classes.get(owner);if(c==null)return !internal(owner);
  if(method&&c.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc)))return true;
  if(!method&&c.fields.stream().anyMatch(f->f.name.equals(name)&&f.desc.equals(desc)))return true;
  if(name.equals("<init>"))return false;
  if(c.superName!=null&&member(classes,c.superName,name,desc,method,seen))return true;
  for(var it:c.interfaces)if(member(classes,it,name,desc,method,seen))return true;return false;
 }
 static boolean internal(String s){return s.startsWith("school/chacademia/kyudichi/")||s.startsWith("school/magiccodex/");}
 static void linkage(ClassNode patched,Path liveJar)throws Exception{
  var classes=new HashMap<String,ClassNode>();try(var z=new ZipFile(liveJar.toFile())){for(var en=z.entries();en.hasMoreElements();){var e=en.nextElement();if(e.getName().endsWith(".class")){var c=OpeningDelta.read(z.getInputStream(e).readAllBytes(),0);classes.put(c.name,c);}}}classes.put(patched.name,patched);
  for(var c:classes.values())for(var m:c.methods)for(var i:m.instructions){
   if(i instanceof MethodInsnNode call&&(c==patched&&internal(call.owner)||call.owner.equals(OWNER)))if(!member(classes,call.owner,call.name,call.desc,true,new HashSet<>()))throw new IllegalStateException("INTERNAL_METHOD_LINKAGE "+c.name+" -> "+call.owner+"."+call.name+call.desc);
   if(i instanceof FieldInsnNode f&&(c==patched&&internal(f.owner)||f.owner.equals(OWNER)))if(!member(classes,f.owner,f.name,f.desc,false,new HashSet<>()))throw new IllegalStateException("INTERNAL_FIELD_LINKAGE "+c.name+" -> "+f.owner+"."+f.name+f.desc);
  }
 }
 public static void main(String[] a)throws Exception{
  var live=OpeningDelta.read(Files.readAllBytes(Path.of(a[0])),0);var old=OpeningDelta.read(Files.readAllBytes(Path.of(a[1])),0);var newer=OpeningDelta.read(Files.readAllBytes(Path.of(a[2])),0);
  if(!live.name.equals(OWNER)||!old.name.equals(OWNER)||!newer.name.equals(OWNER))throw new IllegalStateException("Unexpected class");
  var source=copy(method(old));insert(source);
  if(!OpeningDelta.eq(source,method(newer)))throw new IllegalStateException("Source old + bounded insert does not equal source final");
  System.out.println("PASS SOURCE_DELTA old+deadUntil reset+null-only returnpoint initialization == final exact");
  var m=method(live);var prior=OpeningDelta.norm(m);var inserted=insert(m);var patched=copy(m);for(var i:inserted)m.instructions.remove(i);
  if(!OpeningDelta.norm(m).equals(prior))throw new IllegalStateException("Insertion strip did not restore exact operating method");live.methods.set(live.methods.indexOf(m),patched);
  System.out.println("PASS STRIP_PROOF inserted removal == operating rejoin exact; neverArrived operating behavior retained");
  var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);live.accept(w);var bytes=w.toByteArray();var actual=OpeningDelta.read(bytes,0);var original=OpeningDelta.read(Files.readAllBytes(Path.of(a[0])),0);
  for(var before:original.methods){var after=actual.methods.stream().filter(x->OpeningDelta.key(x).equals(OpeningDelta.key(before))).findFirst().orElseThrow();if(!OpeningDelta.key(before).equals(KEY)&&!OpeningDelta.eq(before,after))throw new IllegalStateException("Unrelated method changed "+OpeningDelta.key(before));}
  for(var mm:actual.methods)if((mm.access&(ACC_ABSTRACT|ACC_NATIVE))==0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(actual.name,mm);
  linkage(actual,Path.of(a[4]));Files.write(Path.of(a[3]),bytes);
  System.out.println("PASS BasicVerifier all methods; internal method/field linkage including unchanged callers; unrelated methods preserved="+(original.methods.size()-1));
 }
}
