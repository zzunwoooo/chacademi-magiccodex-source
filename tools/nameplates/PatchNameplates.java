import java.nio.file.*;import java.util.*;import java.util.zip.*;import java.io.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public class PatchNameplates {
 static ClassNode node(byte[] data){var n=new ClassNode();new ClassReader(data).accept(n,0);return n;}
 static MethodNode method(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow(()->new AssertionError(n.name+" "+name));}
 static byte[] bytes(ClassNode n){var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);return w.toByteArray();}
 static byte[] patch(String path,byte[] old,ZipFile built,boolean client)throws Exception{
  var n=node(old);String prefix="school/magiccodex/";
  if(path.equals(prefix+"paper/MagicCodexBridge.class")){
   var m=method(n,"onEnable");int hooks=0;
   for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode x&&x.owner.equals(prefix+"paper/AdminCommandBridge")&&x.name.equals("<init>")){
    var add=new InsnList();add.add(new VarInsnNode(Opcodes.ALOAD,0));add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,prefix+"paper/NameplateBootstrap","initialize","(Lschool/magiccodex/paper/MagicCodexBridge;)V",false));m.instructions.insert(i,add);hooks++;
   }if(hooks!=1)throw new AssertionError("Bridge hook count "+hooks);
  }else if(path.equals(prefix+"client/MagicCodexClient.class")){
   int hooks=0;for(var m:n.methods)for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode x&&x.owner.equals(prefix+"client/PermissionClient")&&x.name.equals("initialize")){m.instructions.insert(i,new MethodInsnNode(Opcodes.INVOKESTATIC,prefix+"client/NameplateClient","initialize","()V",false));hooks++;}if(hooks!=1)throw new AssertionError("Client hook count "+hooks);
  }else{
   var full=node(built.getInputStream(built.getEntry(path)).readAllBytes());
   if(path.endsWith("/TamingNameMixin.class")){
    var before=method(n,"magiccodex$tamingName");var after=method(full,"magiccodex$tamingName");if(!before.desc.equals(after.desc))throw new AssertionError("handler ABI");n.methods.set(n.methods.indexOf(before),after);
   }else{
    var names=path.endsWith("/HudTextureCache.class")?List.of("worldReady","drawWorld"):List.of("drawWorld");
    for(var name:names){if(n.methods.stream().anyMatch(m->m.name.equals(name)))throw new AssertionError("already patched "+path);n.methods.add(method(full,name));}
   }
  }return bytes(n);
 }
 public static void main(String[] args)throws Exception{
  boolean client=args[3].equals("client");String prefix="school/magiccodex/";
  var changed=client?Set.of(prefix+"client/MagicCodexClient.class",prefix+"client/CodexTypography.class",prefix+"client/HudTextureCache.class",prefix+"mixin/TamingNameMixin.class"):Set.of(prefix+"paper/MagicCodexBridge.class");
  int replaced=0,added=0;
  try(var base=new ZipFile(args[0]);var built=new ZipFile(args[1]);var out=new ZipOutputStream(Files.newOutputStream(Path.of(args[2])))){
   var seen=new HashSet<String>();var en=base.entries();while(en.hasMoreElements()){var entry=en.nextElement();String path=entry.getName();seen.add(path);byte[] content=base.getInputStream(entry).readAllBytes();
    if(changed.contains(path)){content=patch(path,content,built,client);replaced++;}
    if(path.equals(client?"fabric.mod.json":"plugin.yml")){
     String s=new String(content,java.nio.charset.StandardCharsets.UTF_8);
     if(client)s=s.replaceFirst("\"version\"\\s*:\\s*\"[^\"]+\"","\"version\": \"0.40.3-integrated-20261020.1-gimmick.1-commands.1-followups.1-tooltip.1-nameplates.1+mc1.21.4\"");
     else{s=s.replaceFirst("(?m)^version:.*$","version: '0.31.3-integrated-20261020.1-gimmick.1-houses.1-commands.1-followups.1-nameplates.1'");s=s.replace("permissions:","permissions:\n  magiccodex.nameplates.admin:\n    default: op");}
     content=s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    var copy=new ZipEntry(entry);copy.setCompressedSize(-1);copy.setSize(content.length);if(copy.getMethod()==ZipEntry.STORED){var crc=new java.util.zip.CRC32();crc.update(content);copy.setCrc(crc.getValue());}out.putNextEntry(copy);out.write(content);out.closeEntry();
   }
   if(replaced!=changed.size())throw new AssertionError("Missing existing classes");
   en=built.entries();while(en.hasMoreElements()){var e=en.nextElement();String p=e.getName();boolean use=p.startsWith(prefix+(client?"client/Nameplate":"paper/Nameplate"))||p.startsWith(prefix+"protocol/NameplateProtocol")||(client&&p.startsWith("assets/magiccodex/textures/nameplates/"));if(use&&!seen.contains(p)){out.putNextEntry(new ZipEntry(e));out.write(built.getInputStream(e).readAllBytes());out.closeEntry();added++;}}
  }
  System.out.println("PATCHED "+args[3]+" existing classes="+replaced+" added entries="+added);
 }
}
