from pathlib import Path
O=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/candidate-tools')
(O/'OpeningSpecial.java').write_text("""import java.nio.file.*;import java.util.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
final class OpeningSpecial implements Opcodes{
 static MethodNode get(ClassNode c,String n){return c.methods.stream().filter(m->m.name.equals(n)).findFirst().orElseThrow();}
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
   var source=get(fresh,"cast");var real=new ArrayList<AbstractInsnNode>();for(var n:source.instructions)if(n.getOpcode()>=0)real.add(n);
   int sound=-1,artifact=-1;for(int i=0;i<real.size();i++)if(real.get(i) instanceof MethodInsnNode c){if(c.name.equals("artifactCast"))artifact=i;if(c.name.equals("spellSound")){sound=i;break;}}
   if(sound<0||artifact<0)throw new IllegalStateException("cast source sound slice");var seq=new ArrayList<AbstractInsnNode>(real.subList(artifact+1,sound+1));
   var lm=get(live,"cast");AbstractInsnNode anchor=null;for(var n:lm.instructions)if(n instanceof MethodInsnNode c&&c.name.equals("artifactCast")){anchor=n.getNext();break;}
   if(anchor==null)throw new IllegalStateException("cast live artifact anchor missing");insert(live,lm,anchor,seq,changed,"new cast sound only; arena/live guards retained");handled.add(OpeningDelta.key(source));
  }
  if(live.name.equals("dev/portablevfx/client/claude/ClaudeBackend")){
   var before=get(old,"initialize");var after=get(fresh,"initialize");var a=new ArrayList<LdcInsnNode>();var b=new ArrayList<LdcInsnNode>();for(var n:before.instructions)if(n instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains("additiveMaterial&&localOpacity"))a.add(l);for(var n:after.instructions)if(n instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains("color=vec4(additiveMaterial"))b.add(l);
   if(a.size()!=1||b.size()!=1)throw new IllegalStateException("shader source anchors");var lm=get(live,"initialize");int count=0;for(var n:lm.instructions)if(n instanceof LdcInsnNode l&&l.cst.equals(a.getFirst().cst)){l.cst=b.getFirst().cst;count++;}
   if(count!=1)throw new IllegalStateException("live shader exact old string not unique "+count);
   changed.add(OpeningDelta.key(lm));handled.add(OpeningDelta.key(after));System.out.println("PROOF exact shader LDC replacement only; renderer/cache untouched");
  }
  return handled;
 }
}""",encoding='utf-8')
# Alignment is staging-only. Keep raw/live intact, emit derived old/new beside each cache input.
src=Path('C:/Chacademi/staging/opening-final-20261025-task3/tools/live-handoff-20261025/other-preservation/OtherLambdaAlign.java').read_text()
src=src.replace('public class OtherLambdaAlign','public class OpeningFinalAlign')
src=src.replace('else {String name=', 'else if(eq.isPresent()){var v=eq.get();claimed.add(OpeningDelta.key(v));nm.put(OpeningDelta.key(m),om.get(OpeningDelta.key(v)));}\n  else {String name=')
# Keep original 7-argument alignment entry API; wrapper uses this explicit API.
(O/'OpeningFinalAlign.java').write_text(src,encoding='utf-8')
print('new bounded specials written')
