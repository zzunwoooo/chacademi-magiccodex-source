import java.nio.file.*; import jdk.internal.org.objectweb.asm.*;
public class RemoveAccidentalInit {
 public static void main(String[] a)throws Exception{
 ClassReader cr=new ClassReader(Files.readAllBytes(Path.of(a[0]))); ClassWriter cw=new ClassWriter(0); int[] removed={0};
 cr.accept(new ClassVisitor(Opcodes.ASM8,cw){
 public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
 MethodVisitor mv=super.visitMethod(access,name,desc,sig,ex);
 if(!name.equals("onInitializeClient")||!desc.equals("()V")) return mv;
 return new MethodVisitor(Opcodes.ASM8,mv){
 public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
 if(op==Opcodes.INVOKESTATIC&&owner.equals("school/magiccodex/client/CastAimClient")&&name.equals("initialize")&&desc.equals("()V")) {removed[0]++;return;}
 super.visitMethodInsn(op,owner,name,desc,itf);
 }}; }},0);
 if(removed[0]!=1)throw new IllegalStateException("Expected one call, got "+removed[0]);
 Files.write(Path.of(a[1]),cw.toByteArray()); System.out.println("removed_exactly_one_call");
 }
}