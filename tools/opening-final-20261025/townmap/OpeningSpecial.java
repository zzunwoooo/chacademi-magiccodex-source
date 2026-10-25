import java.nio.file.*;import java.util.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
final class OpeningSpecial implements Opcodes{
 static MethodNode copy(MethodNode m){var n=new MethodNode(m.access,m.name,m.desc,m.signature,m.exceptions.toArray(String[]::new));m.accept(n);return n;}
 static MethodNode cmd(ClassNode c){return c.methods.stream().filter(m->m.name.equals("commands")).findFirst().orElseThrow();}
 static AbstractInsnNode prev(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
 static LdcInsnNode description(MethodNode m){return java.util.Arrays.stream(m.instructions.toArray()).filter(n->n instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains("travel.yml")&&s.contains("서버상태")).map(n->(LdcInsnNode)n).findFirst().orElseThrow();}
 static Set<String> apply(ClassNode live,ClassNode newer,ClassNode old,Path unused,Set<String> changed)throws Exception{
  var handled=new HashSet<String>();if(!live.name.equals("school/magiccodex/paper/TravelBridge"))return handled;
  var n=cmd(newer);var was=cmd(old);var source=copy(n);AbstractInsnNode start=null;for(var i:source.instructions)if(i instanceof MethodInsnNode m&&m.name.equals("fitCommands")){start=prev(i);break;}if(start==null)throw new IllegalStateException("source fitCommands missing");
  var seq=new ArrayList<AbstractInsnNode>();var current=start;while(!(current instanceof VarInsnNode v&&v.getOpcode()==ASTORE)){if(current.getOpcode()>=0)seq.add(current);current=current.getNext();if(current==null)throw new IllegalStateException("source root store missing");}
  if(((VarInsnNode)current).var!=1)throw new IllegalStateException("unexpected source root local");
  for(var i:seq)source.instructions.remove(i);Object newDesc=description(n).cst,oldDesc=description(was).cst;description(source).cst=oldDesc;if(!OpeningDelta.eq(source,was))throw new IllegalStateException("source commands changed beyond fit branch/description");
  var target=cmd(live);String original=OpeningDelta.norm(target);AbstractInsnNode anchor=null;for(var i:target.instructions)if(i instanceof VarInsnNode store&&store.getOpcode()==ASTORE&&store.var==1){anchor=i;break;}if(anchor==null)throw new IllegalStateException("operating root local absent");
  var inserted=new ArrayList<AbstractInsnNode>();var list=new InsnList();for(var i:seq){var x=i.clone(new HashMap<LabelNode,LabelNode>());inserted.add(x);list.add(x);}target.instructions.insertBefore(anchor,list);var liveDesc=description(target);if(!liveDesc.cst.equals(oldDesc))throw new IllegalStateException("operating description diverged");liveDesc.cst=newDesc;
  var result=copy(target);for(var i:inserted)target.instructions.remove(i);liveDesc.cst=oldDesc;if(!OpeningDelta.norm(target).equals(original))throw new IllegalStateException("operating strip differs");live.methods.set(live.methods.indexOf(target),result);changed.add(OpeningDelta.key(result));handled.add(OpeningDelta.key(n));System.out.println("PROOF source delta=fitCommands branch+description only; operating root modified only at ASTORE1; strip exact original; travel flow retained");return handled;
 }
}