import java.nio.file.*;import java.util.*;import java.io.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;import org.objectweb.asm.util.*;
public class BoundedReleasePatch implements Opcodes {
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
   case "discovery-state" -> {
    String keyName="state.travel_waypoints";for(String methodName:List.of("state","progressKeys")){
     var candidates=c.methods.stream().filter(m->m.name.equals(methodName)&&(methodName.equals("progressKeys")||m.desc.equals("(Ljava/util/UUID;Ljava/lang/String;DZ)Z"))).toList();if(candidates.size()!=1)throw new IllegalStateException("Discovery method boundary");var m=candidates.getFirst();int count=0;
     for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode factory&&factory.getOpcode()==INVOKESTATIC&&factory.name.equals("of")&&factory.owner.equals(methodName.equals("state")?"java/util/Set":"java/util/List")){
      var prev=i.getPrevious();if(!(prev instanceof LdcInsnNode last)||!(last.cst instanceof String s)||!s.startsWith("state."))continue;
      int originalArgs=Type.getArgumentTypes(factory.desc).length;if(originalArgs<4||originalArgs>8)throw new IllegalStateException("Unexpected Discovery key count");
      m.instructions.insertBefore(i,new LdcInsnNode(keyName));Type[] args=new Type[originalArgs+1];Arrays.fill(args,Type.getType(Object.class));factory.desc=Type.getMethodDescriptor(Type.getReturnType(factory.desc),args);count++;
     }
     if(count!=1)throw new IllegalStateException("Discovery state-key anchor "+count);changed.add(key(m));
     var copy=readClass(c);var check=copy.methods.stream().filter(x->key(x).equals(key(m))).findFirst().orElseThrow();
     for(var i:check.instructions.toArray())if(i instanceof LdcInsnNode ldc&&Objects.equals(ldc.cst,keyName)){
      var next=i.getNext();if(!(next instanceof MethodInsnNode factory))throw new IllegalStateException("Discovery strip anchor");int args=Type.getArgumentTypes(factory.desc).length;Type[] before=new Type[args-1];Arrays.fill(before,Type.getType(Object.class));factory.desc=Type.getMethodDescriptor(Type.getReturnType(factory.desc),before);check.instructions.remove(i);
     }
     var was=read(original,0).methods.stream().filter(x->key(x).equals(key(m))).findFirst().orElseThrow();if(!normalized(check).equals(normalized(was)))throw new IllegalStateException("Unrelated Discovery state handling changed");
    }
   }
   case "bridge-init" -> {
    if(a.length<6)throw new IllegalStateException("Lifecycle helpers required");var helper=read(Files.readAllBytes(Path.of(a[5])),0);
    String start="task9$startSharedBagTravel",stop="task9$stopSharedBagTravel";
    for(String name:List.of(start,stop)){if(c.methods.stream().anyMatch(m->m.name.equals(name)))throw new IllegalStateException("Lifecycle helper collision");c.methods.add(method(helper,name));}
    for(var f:n.fields)if(baseline.fields.stream().noneMatch(x->x.name.equals(f.name))){if(c.fields.stream().anyMatch(x->x.name.equals(f.name)))throw new IllegalStateException("Lifecycle field collision");c.fields.add(f);}
    for(var m:n.methods){if(Set.of("onEnable","onDisable").contains(m.name))continue;var was=baseline.methods.stream().filter(x->key(x).equals(key(m))).findFirst().orElse(null);if(was==null){if(c.methods.stream().anyMatch(x->key(x).equals(key(m))))throw new IllegalStateException("New Bridge method collision");c.methods.add(m);}else if(!normalized(was).equals(normalized(m)))throw new IllegalStateException("Unexpected additional Bridge source method change "+key(m));}
    int hits=0;for(var i:method(c,"onEnable").instructions.toArray())if(i instanceof TypeInsnNode ty&&ty.getOpcode()==NEW&&ty.desc.equals("school/magiccodex/paper/AdminCommandBridge")){var ins=new InsnList();ins.add(new VarInsnNode(ALOAD,0));ins.add(new MethodInsnNode(INVOKESPECIAL,c.name,start,"()V",false));method(c,"onEnable").instructions.insertBefore(i,ins);hits++;}if(hits!=1)throw new IllegalStateException("Bridge start anchor not unique");
    var close=new InsnList();close.add(new VarInsnNode(ALOAD,0));close.add(new MethodInsnNode(INVOKESPECIAL,c.name,stop,"()V",false));method(c,"onDisable").instructions.insert(close);
    var stripped=readClass(c);for(String name:List.of("onEnable","onDisable")){int removed=0;for(var i:method(stripped,name).instructions.toArray())if(i instanceof MethodInsnNode mi&&mi.owner.equals(c.name)&&Set.of(start,stop).contains(mi.name)){var prev=i.getPrevious();if(prev.getOpcode()!=ALOAD)throw new IllegalStateException("Lifecycle strip anchor");method(stripped,name).instructions.remove(prev);method(stripped,name).instructions.remove(i);removed++;}if(removed!=1||!normalized(method(stripped,name)).equals(normalized(method(read(original,0),name))))throw new IllegalStateException("Unrelated Bridge lifecycle changed");changed.add(key(method(c,name)));}
   }
   case "client-init" -> {
    String init="onInitializeClient";var clean=readClass(n);int sourceCalls=0;
    for(var i:method(clean,init).instructions.toArray())if(i instanceof MethodInsnNode m&&m.getOpcode()==INVOKESTATIC&&m.name.equals("initialize")&&Set.of("school/magiccodex/client/TravelClient","school/magiccodex/client/BagPagesClient").contains(m.owner)){method(clean,init).instructions.remove(i);sourceCalls++;}
    if(sourceCalls!=2||!normalized(method(clean,init)).equals(normalized(method(readClass(baseline),init))))throw new IllegalStateException("Unexpected client source init delta");
    int travel=0,bag=0;for(var i:method(c,init).instructions.toArray())if(i instanceof MethodInsnNode m&&m.getOpcode()==INVOKESTATIC&&m.name.equals("initialize")){
     if(m.owner.equals("school/magiccodex/client/MileageClient")){method(c,init).instructions.insert(i,new MethodInsnNode(INVOKESTATIC,"school/magiccodex/client/TravelClient","initialize","()V",false));travel++;}
     if(m.owner.equals("school/magiccodex/client/FishTankClient")){method(c,init).instructions.insert(i,new MethodInsnNode(INVOKESTATIC,"school/magiccodex/client/BagPagesClient","initialize","()V",false));bag++;}
    }
    if(travel!=1||bag!=1)throw new IllegalStateException("Client init anchors missing/ambiguous");
    var verify=readClass(c);for(var i:method(verify,init).instructions.toArray())if(i instanceof MethodInsnNode m&&Set.of("school/magiccodex/client/TravelClient","school/magiccodex/client/BagPagesClient").contains(m.owner))method(verify,init).instructions.remove(i);
    if(!normalized(method(verify,init)).equals(normalized(method(read(original,0),init))))throw new IllegalStateException("Unrelated client init changed");changed.add(key(method(c,init)));
   }
   case "migration-version" -> {
    var field=c.fields.stream().filter(f->f.name.equals("VERSION")).findFirst().orElseThrow();
    Object old=field.value;Object desired=n.fields.stream().filter(f->f.name.equals("VERSION")).findFirst().orElseThrow().value;
    if(!Objects.equals(old,"spells-gimmick-239-250-20261020")||!Objects.equals(desired,"spells-spatial-leap-135-20261024"))throw new IllegalStateException("Unexpected migration marker boundary");
    int count=0;for(var m:c.methods)for(var ins:m.instructions.toArray())if(ins instanceof LdcInsnNode ldc&&Objects.equals(ldc.cst,old)){ldc.cst=desired;changed.add(key(m));count++;}
    if(count<1)throw new IllegalStateException("No marker constant anchor");field.value=desired;
    System.out.println("BOUNDED MARKER "+old+" -> "+desired+"; constants="+count);
   }
   case "delta" -> {
    if(baseline==null)throw new IllegalStateException("delta baseline missing");var oldMethods=new HashMap<String,MethodNode>();for(var m:baseline.methods)oldMethods.put(key(m),m);
    for(var m:n.methods){var was=oldMethods.get(key(m));boolean modified=was==null||!normalized(was).equals(normalized(m));
     boolean receiptApi=c.name.equals("kr/chacademy/npc/integration/MagicCodexLink") && Set.of("giftReceipts()Z","takeGift(Lorg/bukkit/entity/Player;ILorg/bukkit/inventory/ItemStack;Ljava/lang/String;)I").contains(key(m));
     if(!modified && receiptApi && c.methods.stream().noneMatch(x->key(x).equals(key(m)))){c.methods.add(m);changed.add(key(m));System.out.println("RESTORE_UNCHANGED_SOURCE_API "+key(m));continue;}
     if(!modified)continue;
     var existing=c.methods.stream().filter(x->key(x).equals(key(m))).findFirst().orElse(null);
     if(was!=null){if(existing==null||!normalized(existing).equals(normalized(was)))throw new IllegalStateException("Changed deployed method differs from source baseline "+c.name+"."+key(m));c.methods.remove(existing);changed.add(key(m));}
     else if(existing!=null)throw new IllegalStateException("new method collision "+key(m));c.methods.add(m);
    }
    for(var f:n.fields){
     var was=baseline.fields.stream().filter(x->x.name.equals(f.name)).findFirst().orElse(null);
     var current=c.fields.stream().filter(x->x.name.equals(f.name)).findFirst().orElse(null);
     if(was==null){if(current!=null)throw new IllegalStateException("new field collision "+f.name);c.fields.add(f);}
     else if(!was.desc.equals(f.desc))throw new IllegalStateException("field descriptor changed "+f.name);
     else if(was.access!=f.access || !Objects.equals(was.value,f.value)){
      if(current==null || !current.desc.equals(was.desc) || current.access!=was.access || !Objects.equals(current.value,was.value))throw new IllegalStateException("Changed deployed field differs from source baseline "+c.name+"."+f.name);
      current.access=f.access;current.value=f.value;System.out.println("BOUNDED FIELD "+c.name+"."+f.name);
     }
    }
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
