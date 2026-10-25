import java.nio.file.*;import java.util.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
final class OpeningSpecial implements Opcodes{
 static MethodNode copy(MethodNode m){var n=new MethodNode(m.access,m.name,m.desc,m.signature,m.exceptions.toArray(String[]::new));m.accept(n);return n;} static MethodNode get(ClassNode c,String n){return c.methods.stream().filter(m->m.name.equals(n)).findFirst().orElseThrow();}
 static AbstractInsnNode prev(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
 static void insert(ClassNode c,MethodNode m,AbstractInsnNode anchor,List<AbstractInsnNode> nodes,Set<String> changed,String proof){
  String original=OpeningDelta.norm(m);var seq=new InsnList();var labels=new HashMap<LabelNode,LabelNode>();var inserted=new ArrayList<AbstractInsnNode>();for(var n:nodes){var copy=n.clone(labels);seq.add(copy);inserted.add(copy);}m.instructions.insertBefore(anchor,seq);
  var shell=new ClassNode();shell.version=V21;shell.name=c.name;shell.superName=c.superName;shell.methods.add(m);var w=new ClassWriter(0);shell.accept(w);var desired=OpeningDelta.read(w.toByteArray(),0).methods.getFirst();
  for(var n:inserted)m.instructions.remove(n);if(!OpeningDelta.norm(m).equals(original))throw new IllegalStateException("strip proof "+proof);c.methods.set(c.methods.indexOf(m),desired);changed.add(OpeningDelta.key(desired));System.out.println("PROOF "+proof+" strip=original");
 }
 static Set<String> apply(ClassNode live,ClassNode fresh,ClassNode old,Path unused,Set<String> changed)throws Exception{
  var handled=new HashSet<String>();
  if(live.name.equals("school/magiccodex/paper/MagicCodexBridge")){
   var source=get(fresh,"onEnable");var instructions=source.instructions.toArray();int start=-1,end=-1;
   for(int i=0;i<instructions.length;i++){var n=instructions[i];if(n instanceof TypeInsnNode t&&t.desc.equals("school/magiccodex/paper/SpellSounds")){var p=prev(n);start=Arrays.asList(instructions).indexOf(p);}if(start>=0&&n instanceof MethodInsnNode call&&call.name.equals("reload")&&call.owner.endsWith("/ChacaApi")){end=i;break;}}
   if(start<0||end<start)throw new IllegalStateException("sound init slice missing");var seq=new ArrayList<AbstractInsnNode>();for(int i=start;i<=end;i++)if(instructions[i].getOpcode()>=0)seq.add(instructions[i]);
   var lm=get(live,"onEnable");AbstractInsnNode anchor=null;for(var n:lm.instructions)if(n instanceof TypeInsnNode t&&t.desc.equals("school/magiccodex/paper/TitleBridge")){anchor=prev(n);break;}
   if(anchor==null)throw new IllegalStateException("live title initialization anchor absent");
   insert(live,lm,anchor,seq,changed,"new SpellSounds init only; all live lifecycle retained");handled.add(OpeningDelta.key(source));
  }
  if(live.name.equals("school/magiccodex/paper/ManaBridge")){
   var source=fresh.methods.stream().filter(m->m.name.equals("cast")&&m.desc.contains("Ljava/util/function/BooleanSupplier;")).findFirst().orElseThrow();var real=new ArrayList<AbstractInsnNode>();for(var n:source.instructions)if(n.getOpcode()>=0)real.add(n);
   int sound=-1,artifact=-1;for(int i=0;i<real.size();i++)if(real.get(i) instanceof MethodInsnNode c){if(c.name.equals("artifactCast"))artifact=i;if(c.name.equals("spellSound")){sound=i;break;}}
   if(sound<0||artifact<0)throw new IllegalStateException("cast source sound slice");var seq=new ArrayList<AbstractInsnNode>(real.subList(artifact+1,sound+1));
   var lm=live.methods.stream().filter(m->OpeningDelta.key(m).equals(OpeningDelta.key(source))).findFirst().orElseThrow();AbstractInsnNode anchor=null;for(var n:lm.instructions)if(n instanceof MethodInsnNode c&&c.name.equals("artifactCast")){anchor=n.getNext();break;}
   if(anchor==null)throw new IllegalStateException("cast live artifact anchor missing");insert(live,lm,anchor,seq,changed,"new cast sound only; arena/live guards retained");handled.add(OpeningDelta.key(source));
  }
  if(live.name.equals("dev/portablevfx/client/claude/ClaudeBackend")){
   var before=get(old,"initialize");var after=get(fresh,"initialize");var a=new ArrayList<LdcInsnNode>();var b=new ArrayList<LdcInsnNode>();for(var n:before.instructions)if(n instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains("additiveMaterial&&localOpacity"))a.add(l);for(var n:after.instructions)if(n instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains("color=vec4(additiveMaterial"))b.add(l);
   if(a.size()!=1||b.size()!=1)throw new IllegalStateException("shader source anchors");var lm=get(live,"initialize");int count=0;for(var n:lm.instructions)if(n instanceof LdcInsnNode l&&l.cst.equals(a.getFirst().cst)){l.cst=b.getFirst().cst;count++;}
   if(count!=1)throw new IllegalStateException("live shader exact old string not unique "+count);
   changed.add(OpeningDelta.key(lm));handled.add(OpeningDelta.key(after));System.out.println("PROOF exact shader LDC replacement only; renderer/cache untouched");
  }
  if(live.name.equals("school/magiccodex/paper/SchoolBridge")){
   var restored=new ClassNode();var sw=new ClassWriter(0);fresh.accept(sw);restored=OpeningDelta.read(sw.toByteArray(),0);
   for(var m:restored.methods)for(var n:m.instructions){if(n instanceof LdcInsnNode l&&l.cst instanceof String t)l.cst=t.replace("ch1-17","ch1-15");if(n instanceof InvokeDynamicInsnNode dy)for(int j=0;j<dy.bsmArgs.length;j++)if(dy.bsmArgs[j] instanceof String t)dy.bsmArgs[j]=t.replace("ch1-17","ch1-15");}
   for(var m:old.methods){var r=restored.methods.stream().filter(v->OpeningDelta.key(v).equals(OpeningDelta.key(m))).findFirst().orElseThrow();if(!OpeningDelta.eq(m,r))throw new IllegalStateException("source SchoolBridge has non-dialogue delta "+OpeningDelta.key(m));}
   int count=0;for(var m:live.methods){boolean edited=false;for(var n:m.instructions){if(n instanceof LdcInsnNode l&&l.cst instanceof String t&&t.contains("ch1-15")){l.cst=t.replace("ch1-15","ch1-17");edited=true;count++;}if(n instanceof InvokeDynamicInsnNode dy)for(int j=0;j<dy.bsmArgs.length;j++)if(dy.bsmArgs[j] instanceof String t&&t.contains("ch1-15")){dy.bsmArgs[j]=t.replace("ch1-15","ch1-17");edited=true;count++;}}if(edited)changed.add(OpeningDelta.key(m));}
   if(count!=3&&!(count==0&&"arena".equals(System.getenv("OPENING_SERVER_AREA"))))throw new IllegalStateException("live dialogue ID anchors missing "+count);for(var m:fresh.methods)handled.add(OpeningDelta.key(m));System.out.println("PROOF SchoolBridge source delta exclusively dialogueID strings; live only strings replaced "+count);
  }
  return handled;
 }
}