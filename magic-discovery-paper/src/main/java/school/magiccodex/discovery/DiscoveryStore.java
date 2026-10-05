package school.magiccodex.discovery;
import java.sql.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import school.magiccodex.database.DatabaseSettings;
/** All runtime disk I/O is serialized off the server thread; first discovery and acquisition share a transaction. */
final class DiscoveryStore implements AutoCloseable {
    record Acquisition(long token,String spell,boolean first,int reward,boolean notified){}
    record Loaded(Map<String,Double> progress,List<Acquisition> acquired){}
    record Award(boolean created,Acquisition acquisition){}
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"MagicDiscovery-storage");t.setDaemon(true);return t;});
    private final Connection db;
    private final boolean maria;
    private final String progress,acquisitions,firsts,progressKey;
    DiscoveryStore(Path file)throws Exception{this(file,new DatabaseSettings(false,"","",""));}
    DiscoveryStore(Path file,DatabaseSettings settings)throws Exception{
        maria=settings.mariaDb();progress=settings.table("discovery_progress","progress");acquisitions=settings.table("discovery_acquisitions","acquisitions");firsts=settings.table("discovery_firsts","firsts");progressKey=maria?"progress_key":"key";
        db=settings.connect(file);settings.configure(db);
        try(var s=db.createStatement()){
            if(maria){
                s.execute("CREATE TABLE IF NOT EXISTS codex_discovery_progress(player CHAR(36) NOT NULL,progress_key VARCHAR(128) NOT NULL,value DOUBLE NOT NULL,PRIMARY KEY(player,progress_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("CREATE TABLE IF NOT EXISTS codex_discovery_acquisitions(token BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,player CHAR(36) NOT NULL,spell VARCHAR(64) NOT NULL,first_discovery TINYINT NOT NULL,reward TINYINT NOT NULL,notified TINYINT NOT NULL DEFAULT 0,UNIQUE KEY uq_acquisition(player,spell),KEY ix_acquisition_player(player,token)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("CREATE TABLE IF NOT EXISTS codex_discovery_firsts(spell VARCHAR(64) PRIMARY KEY,player CHAR(36) NOT NULL,created BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            }else{
                s.execute("CREATE TABLE IF NOT EXISTS progress(player TEXT,key TEXT,value REAL NOT NULL,PRIMARY KEY(player,key))");
                s.execute("CREATE TABLE IF NOT EXISTS acquisitions(token INTEGER PRIMARY KEY AUTOINCREMENT,player TEXT,spell TEXT,first INTEGER NOT NULL,reward INTEGER NOT NULL,notified INTEGER NOT NULL DEFAULT 0,UNIQUE(player,spell))");
                s.execute("CREATE TABLE IF NOT EXISTS firsts(spell TEXT PRIMARY KEY,player TEXT NOT NULL,created INTEGER NOT NULL)");
            }
        }
    }
    interface Work<T>{T run()throws Exception;}
    private <T> CompletableFuture<T> task(Work<T> work){return CompletableFuture.supplyAsync(()->{try{return work.run();}catch(Exception e){throw new CompletionException(e);}},io);}
    CompletableFuture<Loaded> load(UUID id){return task(()->{
        Map<String,Double> values=new HashMap<>();try(var p=db.prepareStatement("SELECT "+progressKey+",value FROM "+progress+" WHERE player=?")){p.setString(1,id.toString());try(var r=p.executeQuery()){while(r.next())values.put(r.getString(1),r.getDouble(2));}}
        var acquired=new ArrayList<Acquisition>();try(var p=db.prepareStatement("SELECT token,spell,"+(maria?"first_discovery":"first")+",reward,notified FROM "+acquisitions+" WHERE player=? ORDER BY token")){p.setString(1,id.toString());try(var r=p.executeQuery()){while(r.next())acquired.add(row(r));}}
        return new Loaded(values,acquired);
    });}
    private Acquisition row(ResultSet r)throws SQLException{return new Acquisition(r.getLong(1),r.getString(2),r.getBoolean(3),r.getInt(4),r.getBoolean(5));}
    private void putProgress(UUID id,Map<String,Double> values)throws SQLException{
        String sql=maria?"INSERT INTO "+progress+"(player,progress_key,value) VALUES(?,?,?) ON DUPLICATE KEY UPDATE value=VALUES(value)":"INSERT INTO "+progress+" VALUES(?,?,?) ON CONFLICT(player,key) DO UPDATE SET value=excluded.value";
        try(var p=db.prepareStatement(sql)){for(var e:values.entrySet()){p.setString(1,id.toString());p.setString(2,e.getKey());p.setDouble(3,e.getValue());p.addBatch();}p.executeBatch();}
    }
    CompletableFuture<Void> save(UUID id,Map<String,Double> values){return task(()->{db.setAutoCommit(false);try{putProgress(id,values);db.commit();return null;}catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}});}
    CompletableFuture<Award> acquire(UUID id,String spell,Map<String,Double> values,boolean everyPlayer){return task(()->{
        db.setAutoCommit(false);try{
            try(var q=db.prepareStatement("SELECT token,spell,"+(maria?"first_discovery":"first")+",reward,notified FROM "+acquisitions+" WHERE player=? AND spell=?")){q.setString(1,id.toString());q.setString(2,spell);try(var r=q.executeQuery()){if(r.next()){var old=row(r);db.commit();return new Award(false,old);}}}
            boolean first;try(var q=db.prepareStatement((maria?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+firsts+" VALUES(?,?,?)")){q.setString(1,spell);q.setString(2,id.toString());q.setLong(3,System.currentTimeMillis());first=q.executeUpdate()==1;}
            long token;try(var q=db.prepareStatement("INSERT INTO "+acquisitions+"(player,spell,"+(maria?"first_discovery":"first")+",reward) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){q.setString(1,id.toString());q.setString(2,spell);q.setBoolean(3,first);q.setInt(4,first||everyPlayer?1:0);q.executeUpdate();try(var keys=q.getGeneratedKeys()){keys.next();token=keys.getLong(1);}}
            putProgress(id,values);db.commit();return new Award(true,new Acquisition(token,spell,first,first||everyPlayer?1:0,false));
        }catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    });}
    CompletableFuture<Void> acknowledge(UUID id,long token){return task(()->{try(var p=db.prepareStatement("UPDATE "+acquisitions+" SET notified=1 WHERE player=? AND token=?")){p.setString(1,id.toString());p.setLong(2,token);p.executeUpdate();}return null;});}
    CompletableFuture<Boolean> reserve(UUID id,long token){return task(()->{try(var p=db.prepareStatement("UPDATE "+acquisitions+" SET reward=2 WHERE player=? AND token=? AND reward=1")){p.setString(1,id.toString());p.setLong(2,token);return p.executeUpdate()==1;}});}
    CompletableFuture<Void> delivered(UUID id,long token){return task(()->{try(var p=db.prepareStatement("UPDATE "+acquisitions+" SET reward=3 WHERE player=? AND token=? AND reward=2")){p.setString(1,id.toString());p.setLong(2,token);p.executeUpdate();}return null;});}
    CompletableFuture<Void> unreserve(UUID id,long token){return task(()->{try(var p=db.prepareStatement("UPDATE "+acquisitions+" SET reward=1 WHERE player=? AND token=? AND reward=2")){p.setString(1,id.toString());p.setLong(2,token);p.executeUpdate();}return null;});}
    @Override public void close()throws Exception{io.shutdown();if(!io.awaitTermination(20,TimeUnit.SECONDS))throw new IllegalStateException("Storage still flushing; do not close connection");db.close();}
}
