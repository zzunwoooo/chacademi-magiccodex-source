import java.nio.file.*;import java.net.*;import java.util.*;import java.util.jar.*;
public final class FinalJvmVerify {
 public static void main(String[]a)throws Exception{
  Path target=Path.of(a[0]).toAbsolutePath().normalize();var urls=new ArrayList<URL>();urls.add(target.toUri().toURL());if(a.length>2){String cp=Files.readString(Path.of(a[2]));for(String part:cp.trim().split(";")){if(part.isBlank())continue;Path p=Path.of(part);if(Files.exists(p)&&!p.toAbsolutePath().normalize().equals(target))urls.add(p.toUri().toURL());}}
  var names=Files.readAllLines(Path.of(a[1]));int pass=0,missing=0,failed=0;
  try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader())){
   for(String raw:names){if(raw.isBlank())continue;String name=raw.endsWith(".class")?raw.substring(0,raw.length()-6).replace('/','.'):raw;
    try{Class<?> type=Class.forName(name,false,loader);Path source=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();if(!source.equals(target))throw new IllegalStateException("Selected dependency instead of final artifact: "+source);type.getDeclaredMethods();type.getDeclaredConstructors();type.getDeclaredFields();pass++;System.out.println("PASS VERIFIED_NO_INITIALIZATION "+name);}
    catch(Throwable error){if(error instanceof NoClassDefFoundError||error instanceof ClassNotFoundException){String reason=String.valueOf(error.getMessage());if(!reason.contains("school/magiccodex")&&!reason.contains("school.magiccodex")){missing++;System.out.println("EXTERNAL_DEPENDENCY_MISSING "+name+" : "+error);continue;}}failed++;System.out.println("FAIL "+name+" : "+error);error.printStackTrace(System.out);}
   }
  }
  System.out.println("FINAL_JVM_VERIFY artifact="+target+" passed="+pass+" externalMissing="+missing+" failures="+failed+" noInitialization=true");if(failed>0)System.exit(2);if(missing>0)System.exit(3);
 }
}
