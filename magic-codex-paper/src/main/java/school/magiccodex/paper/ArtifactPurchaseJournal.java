package school.magiccodex.paper;
import java.nio.file.*;import java.nio.channels.FileChannel;import java.io.*;import java.util.*;
/** Private local receipt spool. Completed database operations are replay-safe across restarts. */
final class ArtifactPurchaseJournal {
 /** pity: 천장 설정을 영수증에 같이 남겨, DB 장애 뒤 재시도해도 같은 기준으로 처리한다 (#98). 옛 영수증은 천장 없음. */
 record Purchase(String operation,UUID player,List<ArtifactOwnershipStore.Choice> choices,String shardItem,ArtifactOwnershipStore.Pity pity){
  Purchase(String operation,UUID player,List<ArtifactOwnershipStore.Choice> choices,String shardItem){this(operation,player,choices,shardItem,ArtifactOwnershipStore.Pity.NONE);}
 }
 private final Path directory;
 ArtifactPurchaseJournal(Path directory)throws IOException{this.directory=directory;Files.createDirectories(directory);}
 void prepare(Purchase purchase)throws IOException{write(purchase,"prepared");}
 void paid(Purchase purchase)throws IOException{write(purchase,"paid");}
 void remove(String operation)throws IOException{Files.deleteIfExists(path(operation));}
 Map<String,Purchase> paid()throws IOException{var out=new LinkedHashMap<String,Purchase>();try(var files=Files.list(directory)){for(Path p:files.filter(x->x.toString().endsWith(".properties")).toList()){var v=new Properties();try(var in=Files.newInputStream(p)){v.load(in);}if(!"paid".equals(v.getProperty("state")))continue;var choices=new ArrayList<ArtifactOwnershipStore.Choice>();for(String row:v.getProperty("choices").split(";")){String[] pair=row.split("/",2);var main=choice(pair[0]);choices.add(pair.length<2?main:new ArtifactOwnershipStore.Choice(main.id(),main.grade(),main.shards(),main.temporaryAccess(),choice(pair[1])));}var pity=new ArtifactOwnershipStore.Pity(Integer.parseInt(v.getProperty("pityGrade","0")),Integer.parseInt(v.getProperty("pityCount","0")));var purchase=new Purchase(v.getProperty("operation"),UUID.fromString(v.getProperty("player")),List.copyOf(choices),v.getProperty("shardItem"),pity);out.put(purchase.operation(),purchase);}}return out;}
 long uncertain()throws IOException{try(var files=Files.list(directory)){long n=0;for(Path p:files.filter(x->x.toString().endsWith(".properties")).toList()){var v=new Properties();try(var in=Files.newInputStream(p)){v.load(in);}if(!"paid".equals(v.getProperty("state")))n++;}return n;}}
 private static ArtifactOwnershipStore.Choice choice(String row){var a=row.split(",");return new ArtifactOwnershipStore.Choice(a[0],Integer.parseInt(a[1]),Integer.parseInt(a[2]),a.length>3&&Boolean.parseBoolean(a[3]));}
 private static String text(ArtifactOwnershipStore.Choice c){return c.id()+","+c.grade()+","+c.shards()+","+c.temporaryAccess();}
 private Path path(String op){if(!op.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException();return directory.resolve(op+".properties");}
 private void write(Purchase p,String state)throws IOException{var v=new Properties();v.setProperty("state",state);v.setProperty("operation",p.operation());v.setProperty("player",p.player().toString());v.setProperty("shardItem",p.shardItem());v.setProperty("choices",String.join(";",p.choices().stream().map(c->text(c)+(c.pity()==null?"":"/"+text(c.pity()))).toList()));var pity=p.pity()==null?ArtifactOwnershipStore.Pity.NONE:p.pity();v.setProperty("pityGrade",Integer.toString(pity.grade()));v.setProperty("pityCount",Integer.toString(pity.count()));Path target=path(p.operation()),tmp=target.resolveSibling(target.getFileName()+".tmp");try(var out=Files.newOutputStream(tmp)){v.store(out,"Private artifact purchase receipt; never publish");}try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
}
