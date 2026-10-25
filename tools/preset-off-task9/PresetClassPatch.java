import java.nio.file.*;import java.util.*;import java.io.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;import org.objectweb.asm.util.*;
public class PresetClassPatch implements Opcodes {
 static ClassNode read(byte[] b,int flags){var c=new ClassNode();new ClassReader(b).accept(c,flags);return c;}
 static MethodNode method(ClassNode c,String n){var m=c.methods.stream().filter(x->x.name.equals(n)).toList();if(m.size()!=1)throw new IllegalStateException("method not unique "+c.name+"."+n);return m.getFirst();}
 static String key(MethodNode m){return m.name+m.desc;}
 static String normalized(MethodNode original){var shell=new ClassNode();shell.version=Opcodes.V21;shell.access=Opcodes.ACC_PUBLIC;shell.name="Fingerprint";shell.superName="java/lang/Object";shell.methods.add(original);var bytes=new ClassWriter(0);shell.accept(bytes);var copy=read(bytes.toByteArray(),ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES).methods.getFirst();copy.maxStack=0;copy.maxLocals=0;var p=new Textifier();copy.accept(new TraceMethodVisitor(p));var out=new StringWriter();p.print(new PrintWriter(out));return out.toString();}

 static void field(ClassNode c,ClassNode n,String name){var f=n.fields.stream().filter(x->x.name.equals(name)).findFirst().orElseThrow();if(c.fields.stream().anyMatch(x->x.name.equals(name)))throw new IllegalStateException("field collision "+name);c.fields.add(f);}
 static void add(ClassNode c,ClassNode n,String name){var m=method(n,name);if(c.methods.stream().anyMatch(x->key(x).equals(key(m))))throw new IllegalStateException("method collision "+name);c.methods.add(m);}
 static void replace(ClassNode c,ClassNode n,ClassNode baseline,String name){if(baseline==null)throw new IllegalStateException("raw baseline required");if(!normalized(method(readClass(c),name)).equals(normalized(method(readClass(baseline),name))))throw new IllegalStateException("deployed/raw baseline method differs: "+c.name+"."+name);c.methods.removeIf(x->x.name.equals(name));c.methods.add(method(n,name));}
 static ClassNode readClass(ClassNode c){var w=new ClassWriter(0);c.accept(w);return read(w.toByteArray(),ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}
 public static void main(String[] a)throws Exception {
  String mode=a[0];byte[] original=Files.readAllBytes(Path.of(a[1]));var c=read(original,0);var n=read(Files.readAllBytes(Path.of(a[2])),0);ClassNode baseline=a.length>4?read(Files.readAllBytes(Path.of(a[4])),0):null;Set<String> changed=new HashSet<>();
  switch(mode){
   case "delta" -> {
    if(baseline==null)throw new IllegalStateException("delta baseline missing");var oldMethods=new HashMap<String,MethodNode>();for(var m:baseline.methods)oldMethods.put(key(m),m);
    for(var m:n.methods){var was=oldMethods.get(key(m));boolean modified=was==null||!normalized(was).equals(normalized(m));if(!modified)continue;
     var existing=c.methods.stream().filter(x->key(x).equals(key(m))).findFirst().orElse(null);
     if(was!=null){if(existing==null||!normalized(existing).equals(normalized(was)))throw new IllegalStateException("Changed deployed method differs from source baseline "+c.name+"."+key(m));c.methods.remove(existing);changed.add(key(m));}
     else if(existing!=null)throw new IllegalStateException("new method collision "+key(m));c.methods.add(m);
    }
    for(var f:n.fields)if(baseline.fields.stream().noneMatch(x->x.name.equals(f.name))){if(c.fields.stream().anyMatch(x->x.name.equals(f.name)))throw new IllegalStateException("new field collision");c.fields.add(f);}
   }
   case "core" -> {field(c,n,"coreVisuals");add(c,n,"coreAppearance");add(c,n,"coreSimilar");String owner=c.name,target="school/magiccodex/paper/CoreVisualBridge";int hits=0;for(var i:method(c,"onEnable").instructions.toArray())if(i instanceof FieldInsnNode f&&f.getOpcode()==PUTFIELD&&f.name.equals("equipment")&&f.owner.equals(owner)){var v=new InsnList();v.add(new VarInsnNode(ALOAD,0));v.add(new TypeInsnNode(NEW,target));v.add(new InsnNode(DUP));v.add(new VarInsnNode(ALOAD,0));v.add(new MethodInsnNode(INVOKESPECIAL,target,"<init>","(L"+owner+";)V",false));v.add(new FieldInsnNode(PUTFIELD,owner,"coreVisuals","L"+target+";"));method(c,"onEnable").instructions.insert(i,v);hits++;}if(hits!=1)throw new IllegalStateException("core init anchor "+hits);}
   case "util" -> {replace(c,n,baseline,"star");changed.add("star");}
   case "engine" -> {replace(c,n,baseline,"<init>");replace(c,n,baseline,"view");changed.addAll(List.of("<init>","view"));add(c,n,"guide");for(var f:n.fields)if(f.name.startsWith("GUIDE_")&&c.fields.stream().noneMatch(x->x.name.equals(f.name)))c.fields.add(f);}
   case "plugin" -> add(c,n,"questGuide");
   case "codec" -> {field(c,n,"GUIDE");add(c,n,"guide");}
   case "packets" -> {if(n.nestMembers!=null){if(c.nestMembers==null)c.nestMembers=new ArrayList<>();for(String member:n.nestMembers)if(!c.nestMembers.contains(member))c.nestMembers.add(member);}for(var ic:n.innerClasses)if(c.innerClasses.stream().noneMatch(x->x.name.equals(ic.name)))c.innerClasses.add(ic);}
   case "client" -> {replace(c,n,baseline,"install");changed.add("install");for(var m:c.methods)if(m.name.startsWith("lambda$install$"))changed.add(m.name);c.methods.removeIf(x->x.name.startsWith("lambda$install$"));for(var m:n.methods)if(m.name.startsWith("lambda$install$"))c.methods.add(m);for(String f:List.of("guideSession","guideAt","guideEndAt"))field(c,n,f);add(c,n,"onGuide");}
   case "world" -> {replace(c,n,baseline,"render");changed.add("render");for(String f:List.of("ARROW","GUIDE_NEAR","guideIrisInUse","guideIrisApi","guideIrisChecked"))field(c,n,f);for(String m:List.of("drawGuide","plane","guideShaderPack","guideVertex"))add(c,n,m);}
   default -> throw new IllegalArgumentException(mode);
  }
  if(n.nestMembers!=null){if(c.nestMembers==null)c.nestMembers=new ArrayList<>();for(String member:n.nestMembers)if(!c.nestMembers.contains(member))c.nestMembers.add(member);}
  for(var ic:n.innerClasses)if(c.innerClasses.stream().noneMatch(x->x.name.equals(ic.name)))c.innerClasses.add(ic);
  var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);byte[] output=w.toByteArray();var v=read(output,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);var old=read(original,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
  for(var m:old.methods){if(changed.contains(m.name)||changed.contains(key(m)))continue;var now=v.methods.stream().filter(x->key(x).equals(key(m))).findFirst().orElseThrow();if(mode.equals("core")&&m.name.equals("onEnable")){int removed=0;for(var i:now.instructions.toArray())if(i instanceof FieldInsnNode f&&f.getOpcode()==PUTFIELD&&f.name.equals("coreVisuals")){var x=i;for(int j=0;j<6;j++){var p=x.getPrevious();now.instructions.remove(x);x=p;}removed++;}if(removed!=1)throw new IllegalStateException("core strip failed");}if(!normalized(m).equals(normalized(now)))throw new IllegalStateException("Unrelated original method changed: "+c.name+"."+key(m));}
  for(var m:read(output,0).methods)if((m.access&(ACC_ABSTRACT|ACC_NATIVE))==0)new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,m);
  Files.write(Path.of(a[3]),output);System.out.println("PASS "+mode+" "+c.name+": preserved "+old.methods.size()+" original methods; bounded changed methods="+changed);
 }
}
