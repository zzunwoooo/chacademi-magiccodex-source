from pathlib import Path
o=Path('C:/Chacademi/artifacts/opening-final-20261025-task3/candidate-tools')
p=o/'OpeningSpecial.java';s=p.read_text();s=s.replace('var source=get(fresh,"cast");','var source=fresh.methods.stream().filter(m->m.name.equals("cast")&&m.desc.contains("Ljava/util/function/BooleanSupplier;")).findFirst().orElseThrow();')
s=s.replace('var lm=get(live,"cast");','var lm=live.methods.stream().filter(m->OpeningDelta.key(m).equals(OpeningDelta.key(source))).findFirst().orElseThrow();')
block="""  if(live.name.equals("school/magiccodex/paper/SchoolBridge")){
   var restored=new ClassNode();var sw=new ClassWriter(0);fresh.accept(sw);restored=OpeningDelta.read(sw.toByteArray(),0);
   for(var m:restored.methods)for(var n:m.instructions){if(n instanceof LdcInsnNode l&&l.cst instanceof String t)l.cst=t.replace("ch1-17","ch1-15");if(n instanceof InvokeDynamicInsnNode dy)for(int j=0;j<dy.bsmArgs.length;j++)if(dy.bsmArgs[j] instanceof String t)dy.bsmArgs[j]=t.replace("ch1-17","ch1-15");}
   for(var m:old.methods){var r=restored.methods.stream().filter(v->OpeningDelta.key(v).equals(OpeningDelta.key(m))).findFirst().orElseThrow();if(!OpeningDelta.eq(m,r))throw new IllegalStateException("source SchoolBridge has non-dialogue delta "+OpeningDelta.key(m));}
   int count=0;for(var m:live.methods){boolean edited=false;for(var n:m.instructions){if(n instanceof LdcInsnNode l&&l.cst instanceof String t&&t.contains("ch1-15")){l.cst=t.replace("ch1-15","ch1-17");edited=true;count++;}if(n instanceof InvokeDynamicInsnNode dy)for(int j=0;j<dy.bsmArgs.length;j++)if(dy.bsmArgs[j] instanceof String t&&t.contains("ch1-15")){dy.bsmArgs[j]=t.replace("ch1-15","ch1-17");edited=true;count++;}}if(edited)changed.add(OpeningDelta.key(m));}
   if(count<4)throw new IllegalStateException("live dialogue ID anchors missing "+count);for(var m:fresh.methods)handled.add(OpeningDelta.key(m));System.out.println("PROOF SchoolBridge source delta exclusively dialogueID strings; live only strings replaced "+count);
  }
"""
s=s.replace('  return handled;',block+'  return handled;');p.write_text(s)
p=o/'OpeningFinalAlign.java';s=p.read_text();s=s.replace(' var baseline=new HashMap', ' if(live.name.equals("school/magiccodex/paper/SchoolBridge")){var p=new ProcessBuilder("C:/Program Files/Java/jdk-21.0.10/bin/java.exe","-cp",a[6],"OpeningDelta",a[0],a[1],a[2],a[5]).inheritIO().start();if(p.waitFor()!=0)System.exit(2);return;} var baseline=new HashMap')
p.write_text(s)
print('cast overload and exact dialogue string patch prepared')
