package school.chacademia.housing;
import java.util.*;
import java.nio.file.*;
import java.sql.*;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.HousingProtocol.Person;
/** Read-only view of Bridge's exact configured nickname domain. Owned by HousingStore's DB worker. */
final class HousingNames implements AutoCloseable {
 private final Path folder; private Connection db; private String table;
 HousingNames(Path folder){this.folder=folder;}
 HousingNames(Connection db,String table){this.folder=null;this.db=db;this.table=table;}
 static Path config(Path folder) throws Exception {
  var generic=folder.resolve("database.properties"); if(Files.exists(generic))return generic;
  var dedicated=folder.resolve("player-state-database.properties");var props=new Properties();
  if(Files.exists(dedicated)){try(var in=Files.newInputStream(dedicated)){props.load(in);}}
  return Boolean.parseBoolean(props.getProperty("use-for-all-domains","false").trim())?dedicated:generic;
 }
 private Connection connection() throws Exception {
  if(db!=null&&!db.isClosed()&&db.isValid(2))return db;
  close();var settings=DatabaseSettings.load(config(folder));table=settings.table("nicknames","nicknames");
  if(settings.mariaDb()){db=settings.connect(folder.resolve("friends.db"));db.setReadOnly(true);}
  else{var file=folder.resolve("friends.db").toAbsolutePath();if(!Files.isRegularFile(file))throw new SQLException("Nickname database unavailable");Class.forName("org.sqlite.JDBC");db=DriverManager.getConnection("jdbc:sqlite:"+file.toUri()+"?mode=ro");}
  return db;
 }
 Map<UUID,String> read(Collection<UUID> ids) throws SQLException {
  var keys=ids.stream().distinct().toList();var out=new HashMap<UUID,String>();if(keys.isEmpty())return out;
  try{var c=connection();for(int offset=0;offset<keys.size();offset+=128){var batch=keys.subList(offset,Math.min(offset+128,keys.size()));
   try(var s=c.prepareStatement("SELECT owner,nickname FROM "+table+" WHERE owner IN ("+String.join(",",Collections.nCopies(batch.size(),"?"))+")")){
    for(int i=0;i<batch.size();i++)s.setString(i+1,batch.get(i).toString());
    try(var r=s.executeQuery()){while(r.next()){String name=r.getString(2);if(name!=null&&!name.isBlank())out.put(UUID.fromString(r.getString(1)),name);}}
   }
  }return out;}catch(Exception e){try{close();}catch(Exception ignored){}throw new SQLException("Registered nickname lookup failed",e);}
 }
 static String shown(String registered){return registered==null||registered.isBlank()?"닉네임 미등록":registered;}
 static Person resolve(Person original,String registered){return new Person(original.id(),shown(registered),original.house(),original.skin(),original.slim());}
 public void close() throws SQLException {if(db!=null){try{db.close();}finally{db=null;}}}
}
