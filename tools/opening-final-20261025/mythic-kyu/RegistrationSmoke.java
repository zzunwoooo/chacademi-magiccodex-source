import java.nio.file.*;import java.util.*;import java.util.zip.*;import org.objectweb.asm.tree.*;
public class RegistrationSmoke{
 static ClassNode read(ZipFile z,String name)throws Exception{return OpeningDelta.read(z.getInputStream(z.getEntry(name+".class")).readAllBytes(),0);}
 static MethodNode m(ClassNode c,String name){return c.methods.stream().filter(x->x.name.equals(name)).findFirst().orElseThrow();}
 static long calls(MethodNode m,String owner,String name){return Arrays.stream(m.instructions.toArray()).filter(x->x instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name)).count();}
 public static void main(String[]a)throws Exception{try(var z=new ZipFile(a[0]);var live=new ZipFile(a[1])){
 if(a[2].equals("mythic")){String p="school/chacademia/magicmythic/";var c=read(z,p+"MagicMythicPlugin");var before=read(live,p+"MagicMythicPlugin");if(!OpeningDelta.eq(m(c,"mechanic"),m(before,"mechanic")))throw new IllegalStateException("Existing mechanic/Meteor registration changed");
 for(String name:List.of("onEnable","reloadAll"))if(calls(m(c,name),p+"QuidditchCombat","load")!=1)throw new IllegalStateException("Quidditch config load missing "+name);
 for(String entry:List.of("MeteorFall.class","MeteorFall$State.class","MeteorMech.class","MeteorMech$1.class"))if(!Arrays.equals(z.getInputStream(z.getEntry(p+entry)).readAllBytes(),live.getInputStream(live.getEntry(p+entry)).readAllBytes()))throw new IllegalStateException("Existing Meteor bytecode changed "+entry);
 System.out.println("PASS Quidditch config load onEnable/reloadAll; operating mechanic registration exact; all4 Meteor classes byte-identical");
 }else{String p="school/chacademia/kyudichi/";var c=read(z,p+"KyudichiPlugin");if(calls(m(c,"lambda$onEnable$0"),p+"GameListener","hookMatch")!=1)throw new IllegalStateException("Missing hookMatch startup registration");var listener=read(z,p+"GameListener");if(calls(m(listener,"hookMatch"),p+"Hooks","listenMatch")!=1)throw new IllegalStateException("Missing match event subscription");System.out.println("PASS startup hookMatch and match-event subscription registration");}
 }}
}
