import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.matcher.NodeMatcher;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.TimeUnit;
public class ArtifactOwnershipAudit implements java.util.function.Function<String,String>{
 public String apply(String destination){
  try{
   var lp=LuckPermsProvider.get();var rows=new TreeSet<String>();int users=0,groups=0,negative=0,temporary=0,contextual=0;
   var matchers=List.of(NodeMatcher.keyStartsWith("magiccodex.artifact.own."),NodeMatcher.key("magiccodex.artifact.*"),NodeMatcher.key("magiccodex.*"),NodeMatcher.key("*"));
   for(var matcher:matchers){
    for(var e:lp.getUserManager().searchAll(matcher).get(10,TimeUnit.SECONDS).entrySet())for(var n:e.getValue())rows.add(row("user",e.getKey().toString(),n));
    for(var e:lp.getGroupManager().searchAll(matcher).get(10,TimeUnit.SECONDS).entrySet())for(var n:e.getValue())rows.add(row("group",e.getKey(),n));
   }
   Path path=Path.of(destination);if(Files.exists(path))throw new IllegalStateException("Destination exists");Files.createDirectories(path.getParent());Files.write(path,rows,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
   for(String row:rows){String[] p=row.split("\t",-1);if(p[0].equals("user"))users++;else groups++;if(p[3].equals("false"))negative++;if(!p[4].equals("0"))temporary++;if(!p[5].isEmpty())contextual++;}
   return "Artifact audit only: userNodes="+users+" groupNodes="+groups+" negative="+negative+" temporary="+temporary+" contextual="+contextual;
  }catch(Exception e){return "Artifact audit failed: "+e.getClass().getSimpleName();}
 }
 private static String row(String kind,String owner,Node node){
  var contexts=new TreeSet<String>();for(var c:node.getContexts().toSet())contexts.add(enc(c.getKey())+":"+enc(c.getValue()));
  return kind+"\t"+owner+"\t"+node.getKey()+"\t"+node.getValue()+"\t"+(node.hasExpiry()?node.getExpiry().getEpochSecond():0)+"\t"+String.join(",",contexts);
 }
 private static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
}
