import java.nio.file.*;import java.util.*;import java.util.zip.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public class BudgetDescriptorAudit {
 static final String OWNER="kr/chacademy/npc/budget/BudgetService";
 static byte[] read(String path)throws Exception{try(ZipFile z=new ZipFile(path)){return z.getInputStream(z.getEntry(OWNER+".class")).readAllBytes();}}
 public static void main(String[] a)throws Exception {
 byte[] was=read(a[0]),now=read(a[1]);if(!Arrays.equals(was,now))throw new AssertionError("BudgetService bytes changed");ClassNode provider=new ClassNode();new ClassReader(now).accept(provider,0);Set<String> methods=new HashSet<>();for(var m:provider.methods)methods.add(m.name+m.desc);
 for(String d:List.of("(Ljava/lang/String;)Z","(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;"))if(!methods.contains("tryUseCall"+d))throw new AssertionError("Required descriptor missing "+d);
 int checked=0;try(ZipFile z=new ZipFile(a[1])){for(var en=z.entries();en.hasMoreElements();){var e=en.nextElement();if(!e.getName().endsWith(".class"))continue;ClassNode n=new ClassNode();new ClassReader(z.getInputStream(e).readAllBytes()).accept(n,0);for(var m:n.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode call&&call.owner.equals(OWNER)){if(!methods.contains(call.name+call.desc))throw new AssertionError(e.getName()+" missing "+call.name+call.desc);checked++;}}}
 System.out.println("PASS BUDGET_BYTE_IDENTICAL; BUDGET_CALL_DESCRIPTORS_RESOLVED "+checked+"; BOTH_BOOLEAN_FUTURE_DESCRIPTORS_PRESERVED");
 }
}
