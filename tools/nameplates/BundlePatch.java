import java.nio.file.*;import java.util.*;import java.util.zip.*;import java.io.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
/** Reconciles only approved nameplate/title hooks and the chapter sorting ID on current operating jars. */
public class BundlePatch {
 static final String P="school/magiccodex/";
 static ClassNode node(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
 static MethodNode method(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
 static boolean has(ClassNode n,String name){return n.methods.stream().anyMatch(m->m.name.equals(name));}
 static byte[] patch(String path,byte[] old,ZipFile built)throws Exception{
  var n=node(old);var full=node(built.getInputStream(built.getEntry(path)).readAllBytes());
  if(path.endsWith("/MagicCodexBridge.class")){
   if(!has(n,"titleService"))n.methods.add(method(full,"titleService"));int hooks=0;var m=method(n,"onEnable");
   for(var i:m.instructions)if(i instanceof MethodInsnNode x&&x.owner.equals(P+"paper/NameplateBootstrap")&&x.name.equals("initialize"))hooks++;
   if(hooks==0)for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode x&&x.owner.equals(P+"paper/AdminCommandBridge")&&x.name.equals("<init>")){var add=new InsnList();add.add(new VarInsnNode(Opcodes.ALOAD,0));add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,P+"paper/NameplateBootstrap","initialize","(Lschool/magiccodex/paper/MagicCodexBridge;)V",false));m.instructions.insert(i,add);hooks++;}if(hooks!=1)throw new AssertionError("bridge hook");
  }else if(path.endsWith("/TitleBridge.class")){for(String name:List.of("nameplateTitles","nameplateDefinition")){if(has(n,name))n.methods.remove(method(n,name));n.methods.add(method(full,name));}}
  else if(path.endsWith("/SchoolBridge.class")){int changed=0;for(var m:n.methods)for(var i:m.instructions){if(i instanceof LdcInsnNode x&&x.cst instanceof String s&&s.startsWith("ch1-14")){x.cst=s.replace("ch1-14","ch1-15");changed++;}if(i instanceof InvokeDynamicInsnNode x)for(int k=0;k<x.bsmArgs.length;k++)if(x.bsmArgs[k] instanceof String s&&s.contains("ch1-14")){x.bsmArgs[k]=s.replace("ch1-14","ch1-15");changed++;}}if(changed<2)throw new AssertionError("sorting IDs not found");}
  else if(path.endsWith("/MagicCodexClient.class")){boolean present=false;for(var m:n.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode x&&x.owner.equals(P+"client/NameplateClient")&&x.name.equals("initialize"))present=true;if(!present){int count=0;for(var m:n.methods)for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode x&&x.owner.equals(P+"client/PermissionClient")&&x.name.equals("initialize")){m.instructions.insert(i,new MethodInsnNode(Opcodes.INVOKESTATIC,P+"client/NameplateClient","initialize","()V",false));count++;}if(count!=1)throw new AssertionError("client hook");}}
  else if(path.endsWith("/HudTextureCache.class")){for(String name:List.of("worldReady","drawWorld"))if(!has(n,name))n.methods.add(method(full,name));}
  else if(path.endsWith("/TamingNameMixin.class")){var m=method(n,"magiccodex$tamingName");n.methods.set(n.methods.indexOf(m),method(full,m.name));}
  else throw new AssertionError(path);
  var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);return w.toByteArray();
 }
 public static void main(String[] a)throws Exception{
  boolean client=a[3].equals("client");String version=a[4];
  var patched=client?Set.of(P+"client/MagicCodexClient.class",P+"client/HudTextureCache.class",P+"mixin/TamingNameMixin.class"):Set.of(P+"paper/MagicCodexBridge.class",P+"paper/TitleBridge.class",P+"paper/SchoolBridge.class");
  var full=client?Set.of(P+"client/CodexTypography.class",P+"client/CodexTextRasterizer.class"):Set.<String>of();
  try(var base=new ZipFile(a[0]);var built=new ZipFile(a[1]);var out=new ZipOutputStream(Files.newOutputStream(Path.of(a[2])))){
   var seen=new HashSet<String>();var en=base.entries();while(en.hasMoreElements()){var e=en.nextElement();String path=e.getName();seen.add(path);byte[] data=base.getInputStream(e).readAllBytes();boolean feature=path.startsWith(P+(client?"client/Nameplate":"paper/Nameplate"))||path.startsWith(P+"protocol/NameplateProtocol");
    if(patched.contains(path))data=patch(path,data,built);else if(full.contains(path)||feature){var b=built.getEntry(path);if(b!=null)data=built.getInputStream(b).readAllBytes();}
    if(path.equals(client?"fabric.mod.json":"plugin.yml")){String s=new String(data,java.nio.charset.StandardCharsets.UTF_8);if(client)s=s.replaceFirst("\"version\"\\s*:\\s*\"[^\"]+\"","\"version\": \""+version+"\"");else{s=s.replaceFirst("(?m)^version:.*$","version: '"+version+"'");if(!s.contains("magiccodex.nameplates.admin:"))s=s.replace("permissions:","permissions:\n  magiccodex.nameplates.admin:\n    default: op");}data=s.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    var copy=new ZipEntry(e);copy.setCompressedSize(-1);copy.setSize(data.length);if(copy.getMethod()==ZipEntry.STORED){var crc=new CRC32();crc.update(data);copy.setCrc(crc.getValue());}out.putNextEntry(copy);out.write(data);out.closeEntry();
   }
   en=built.entries();while(en.hasMoreElements()){var e=en.nextElement();String p=e.getName();boolean use=p.startsWith(P+(client?"client/Nameplate":"paper/Nameplate"))||p.startsWith(P+"protocol/NameplateProtocol")||(client&&(p.equals(P+"client/CodexTypography$TitleKey.class")||p.startsWith("assets/magiccodex/textures/nameplates/")));if(use&&!seen.contains(p)){out.putNextEntry(new ZipEntry(e));out.write(built.getInputStream(e).readAllBytes());out.closeEntry();}}
  }System.out.println("Prepared bounded "+a[3]+" bundle "+a[2]);
 }
}
