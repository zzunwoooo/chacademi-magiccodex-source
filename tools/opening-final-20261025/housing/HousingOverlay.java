import java.nio.file.*;import java.util.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
public class HousingOverlay {
static MethodNode copy(MethodNode m){var n=new MethodNode(m.access,m.name,m.desc,m.signature,m.exceptions.toArray(String[]::new));m.accept(n);return n;}
static String norm(MethodNode m){return OpeningDelta.norm(m).replaceAll("lambda\\$([A-Za-z0-9_$]+)\\$[0-9]+","lambda\\$$1\\$N");}
static MethodNode get(ClassNode c,String n){return c.methods.stream().filter(m->m.name.equals(n)).findFirst().orElseThrow();}
static List<AbstractInsnNode> real(MethodNode m){var x=new ArrayList<AbstractInsnNode>();for(var n:m.instructions)if(n.getOpcode()>=0)x.add(n);return x;}
static void eq(MethodNode a,MethodNode b,String why){if(!norm(a).equals(norm(b)))throw new IllegalStateException(why);}
static boolean call(AbstractInsnNode n,String name){return n instanceof MethodInsnNode v&&v.name.equals(name)&&v.owner.endsWith("/ChacaLink");}
static void rename(MethodNode m,String owner,Map<String,String> map){if(map.containsKey(m.name))m.name=map.get(m.name);for(var n:m.instructions){if(n instanceof MethodInsnNode v&&v.owner.equals(owner)&&map.containsKey(v.name))v.name=map.get(v.name);if(n instanceof InvokeDynamicInsnNode v)for(int i=0;i<v.bsmArgs.length;i++)if(v.bsmArgs[i] instanceof Handle h&&h.getOwner().equals(owner)&&map.containsKey(h.getName()))v.bsmArgs[i]=new Handle(h.getTag(),h.getOwner(),map.get(h.getName()),h.getDesc(),h.isInterface());}}
static InsnList cloned(List<AbstractInsnNode> list){var r=new InsnList();var labels=new HashMap<LabelNode,LabelNode>();for(var n:list)r.add(n.clone(labels));return r;}
static void addHelpers(ClassNode live,ClassNode fresh,MethodNode entry,String family){var map=new LinkedHashMap<String,String>();var todo=new ArrayList<MethodNode>();todo.add(entry);for(int z=0;z<todo.size();z++)for(var n:todo.get(z).instructions)if(n instanceof InvokeDynamicInsnNode v)for(Object arg:v.bsmArgs)if(arg instanceof Handle hh&&hh.getOwner().equals(live.name)&&hh.getName().startsWith("lambda$"+family+"$")&&!map.containsKey(hh.getName())){map.put(hh.getName(),"final$housing$"+family+"$"+map.size());todo.add(get(fresh,hh.getName()));}rename(entry,live.name,map);for(var e:map.entrySet()){if(live.methods.stream().anyMatch(m->m.name.equals(e.getValue())))throw new IllegalStateException("helper collision");var m=copy(get(fresh,e.getKey()));rename(m,live.name,map);live.methods.add(m);}}
public static void main(String[]a)throws Exception{
var live=OpeningDelta.read(Files.readAllBytes(Path.of(a[0])),0);var fresh=OpeningDelta.read(Files.readAllBytes(Path.of(a[1])),0);var old=OpeningDelta.read(Files.readAllBytes(Path.of(a[2])),0);
var before=new HashMap<String,String>();for(var m:live.methods)before.put(OpeningDelta.key(m),OpeningDelta.norm(m));var modified=new HashSet<String>();
if(live.name.endsWith("/HousingCommands")){
for(var m:old.methods)if(!m.name.startsWith("lambda$")&&!m.name.equals("register"))eq(m,fresh.methods.stream().filter(x->OpeningDelta.key(x).equals(OpeningDelta.key(m))).findFirst().orElseThrow(),"unexpected command source delta "+m.name);
var source=copy(get(fresh,"register"));var ns=real(source);int mark=-1;
for(int i=0;i<ns.size();i++)if(ns.get(i)instanceof LdcInsnNode l&&l.cst.equals("\uD558\uC6B0\uC9D5\uB7AD\uD0B9")){mark=i;break;}if(mark<2)throw new IllegalStateException("rank literal");
int start=mark-2,stop=-1;if(!(ns.get(start) instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==0)||!(ns.get(start+1)instanceof FieldInsnNode f&&f.name.equals("plugin")))throw new IllegalStateException("command start");
for(int i=mark;i<ns.size();i++)if(call(ns.get(i),"playerCommand")){stop=i+1;break;}
var chunk=new ArrayList<>(ns.subList(start,stop));for(var n:chunk)source.instructions.remove(n);eq(source,get(old,"register"),"source addition not only ranking");
var wrap=new MethodNode();wrap.instructions=cloned(chunk);addHelpers(live,fresh,wrap,"register");
var target=get(live,"register");var ret=real(target).getLast();if(ret.getOpcode()!=Opcodes.RETURN)throw new IllegalStateException("register return");var inserted=new ArrayList<AbstractInsnNode>();for(var n:wrap.instructions)inserted.add(n);target.instructions.insertBefore(ret,wrap.instructions);var patched=copy(target);for(var n:inserted)target.instructions.remove(n);if(!OpeningDelta.norm(target).equals(before.get(OpeningDelta.key(target))))throw new IllegalStateException("command strip");live.methods.remove(target);live.methods.add(patched);modified.add(OpeningDelta.key(patched));
}else if(live.name.endsWith("/HousingStore")){
for(var m:old.methods)if(!m.name.startsWith("lambda$"))eq(m,fresh.methods.stream().filter(x->OpeningDelta.key(x).equals(OpeningDelta.key(m))).findFirst().orElseThrow(),"unexpected store source delta "+m.name);
if(live.methods.stream().anyMatch(m->m.name.equals("ranking")))throw new IllegalStateException("ranking exists");
var rank=copy(get(fresh,"ranking"));addHelpers(live,fresh,rank,"ranking");live.methods.add(rank);
}else if(live.name.endsWith("/HousingPlugin")){
var source=copy(get(fresh,"onEnable"));var ns=real(source);int end=-1;
for(int i=0;i<ns.size();i++)if(ns.get(i)instanceof FieldInsnNode f&&f.name.equals("rank")&&f.getOpcode()==Opcodes.PUTFIELD){end=i;break;}
if(end<5)throw new IllegalStateException("rank init");var chunk=new ArrayList<>(ns.subList(end-5,end+1));for(var n:chunk)source.instructions.remove(n);eq(source,get(old,"onEnable"),"enable unexpected delta");
var target=get(live,"onEnable");AbstractInsnNode anchor=null;for(var n:target.instructions)if(n instanceof FieldInsnNode f&&f.name.equals("search")&&f.getOpcode()==Opcodes.PUTFIELD){anchor=n;break;}if(anchor==null)throw new IllegalStateException("search anchor");
var add=cloned(chunk);var inserted=new ArrayList<AbstractInsnNode>();for(var n:add)inserted.add(n);target.instructions.insert(anchor,add);var patched=copy(target);for(var n:inserted)target.instructions.remove(n);if(!OpeningDelta.norm(target).equals(before.get(OpeningDelta.key(target))))throw new IllegalStateException("enable strip");live.methods.remove(target);live.methods.add(patched);modified.add(OpeningDelta.key(patched));
var disable=get(live,"onDisable");eq(disable,get(old,"onDisable"),"disable operating divergence");live.methods.remove(disable);live.methods.add(copy(get(fresh,"onDisable")));modified.add(OpeningDelta.key(disable));
live.fields.add(fresh.fields.stream().filter(f->f.name.equals("rank")).findFirst().orElseThrow());live.methods.add(copy(get(fresh,"rank")));
}else throw new IllegalStateException("unsupported");
if(fresh.nestMembers!=null){if(live.nestMembers==null)live.nestMembers=new ArrayList<>();for(var n:fresh.nestMembers)if(!live.nestMembers.contains(n))live.nestMembers.add(n);}for(var n:fresh.innerClasses)if(live.innerClasses.stream().noneMatch(x->x.name.equals(n.name)))live.innerClasses.add(n);
for(var m:live.methods)if(before.containsKey(OpeningDelta.key(m))&&!modified.contains(OpeningDelta.key(m))&&!OpeningDelta.norm(m).equals(before.get(OpeningDelta.key(m))))throw new IllegalStateException("unrelated changed "+m.name);
var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);live.accept(w);byte[] out=w.toByteArray();var check=OpeningDelta.read(out,0);for(var m:check.methods)if((m.access&1280)==0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name,m);Files.write(Path.of(a[3]),out);
System.out.println("PASS HOUSING_OVERLAY "+live.name+" modified="+modified+" preservedMethods="+(before.size()-modified.size())+" allOtherMethodInstructionsIdentical=true operatingDBMethodsPreserved=true");
}}
