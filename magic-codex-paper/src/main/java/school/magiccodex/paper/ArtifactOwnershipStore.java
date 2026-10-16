package school.magiccodex.paper;

import java.sql.*;
import java.util.*;

/** Artifact-only shared authority. No LuckPerms writes and no player-state/equipment storage. */
final class ArtifactOwnershipStore {
    interface Connections { Connection open() throws Exception; }
    record Choice(String id,int grade,int shards,boolean temporaryAccess) { Choice(String id,int grade,int shards){this(id,grade,shards,false);} }
    record Pull(String id,int grade,boolean fresh,int shards) {}
    record Delivery(String operation,int index,UUID player,String item,int amount) {}
    private final Connections connections;
    ArtifactOwnershipStore(Connections connections) { this.connections=connections; }
    void initialize() throws Exception {
        try(var c=connections.open();var s=c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS codex_artifact_accounts(player VARCHAR(36) PRIMARY KEY)");
            s.execute("CREATE TABLE IF NOT EXISTS codex_artifact_ownership(player VARCHAR(36) NOT NULL,artifact VARCHAR(64) NOT NULL,owned BOOLEAN NOT NULL,PRIMARY KEY(player,artifact))");
            s.execute("CREATE TABLE IF NOT EXISTS codex_artifact_operations(operation_id VARCHAR(64) PRIMARY KEY,player VARCHAR(36) NOT NULL,result TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS codex_artifact_delivery(operation_id VARCHAR(64) NOT NULL,pull_index INT NOT NULL,player VARCHAR(36) NOT NULL,item VARCHAR(128) NOT NULL,amount INT NOT NULL,delivered BOOLEAN NOT NULL DEFAULT FALSE,PRIMARY KEY(operation_id,pull_index))");
            s.execute("CREATE TABLE IF NOT EXISTS codex_artifact_migration(source VARCHAR(32) PRIMARY KEY,checksum VARCHAR(64) NOT NULL,imported_count INT NOT NULL)");
        }
    }
    boolean migrationComplete() throws Exception {
        try(var c=connections.open();var p=c.prepareStatement("SELECT COUNT(*) FROM codex_artifact_migration WHERE source IN ('school-v1','wild-v1')");var r=p.executeQuery()){r.next();return r.getInt(1)==2;}
    }
    /** Each source is imported exactly once; a later revoke can never be undone by rerunning import. */
    boolean migrate(String source,String checksum,Map<UUID,Set<String>> ownership) throws Exception {
        if(!Set.of("school-v1","wild-v1").contains(source)||!checksum.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
        try(var c=connections.open()) {c.setAutoCommit(false);try {
            // The fixed migration mutex also serializes two installers importing the same source.
            lock(c,new UUID(0,0));
            try(var p=c.prepareStatement("SELECT checksum FROM codex_artifact_migration WHERE source=?")){p.setString(1,source);try(var r=p.executeQuery()){if(r.next()){if(!checksum.equals(r.getString(1)))throw new IllegalStateException("Migration source changed after completion");c.commit();return false;}}}
            int count=0;
            for(var e:ownership.entrySet())for(String id:e.getValue()){valid(id);try(var p=c.prepareStatement("INSERT IGNORE INTO codex_artifact_ownership(player,artifact,owned) VALUES(?,?,TRUE)")){p.setString(1,e.getKey().toString());p.setString(2,id);count+=p.executeUpdate();}}
            try(var p=c.prepareStatement("INSERT INTO codex_artifact_migration(source,checksum,imported_count) VALUES(?,?,?)")){p.setString(1,source);p.setString(2,checksum);p.setInt(3,count);p.executeUpdate();}c.commit();return true;
        }catch(Exception e){c.rollback();throw e;}}
    }
    Set<String> owned(UUID player) throws Exception {
        try(var c=connections.open();var p=c.prepareStatement("SELECT artifact FROM codex_artifact_ownership WHERE player=? AND owned=TRUE")){p.setString(1,player.toString());try(var r=p.executeQuery()){var result=new HashSet<String>();while(r.next())result.add(r.getString(1));return Set.copyOf(result);}}
    }
    void set(UUID player,String id,boolean owned) throws Exception {
        valid(id);try(var c=connections.open()){c.setAutoCommit(false);try{lock(c,player);write(c,player,id,owned);c.commit();}catch(Exception e){c.rollback();throw e;}}
    }
    void reset(UUID player) throws Exception {
        try(var c=connections.open()){c.setAutoCommit(false);try{lock(c,player);try(var p=c.prepareStatement("UPDATE codex_artifact_ownership SET owned=FALSE WHERE player=?")){p.setString(1,player.toString());p.executeUpdate();}c.commit();}catch(Exception e){c.rollback();throw e;}}
    }
    /** Unique operation ID + per-player transaction lock makes retries and simultaneous servers safe. */
    List<Pull> purchase(String operation,UUID player,List<Choice> choices,String shardItem) throws Exception {
        if(!operation.matches("[a-f0-9-]{36}")||choices.isEmpty()||choices.size()>5)throw new IllegalArgumentException();
        for(var ch:choices){valid(ch.id());if(ch.grade()<0||ch.grade()>3||ch.shards()<0||ch.shards()>64)throw new IllegalArgumentException();}
        try(var c=connections.open()){c.setAutoCommit(false);try{
            lock(c,player);
            try(var p=c.prepareStatement("SELECT player,result FROM codex_artifact_operations WHERE operation_id=?")){p.setString(1,operation);try(var r=p.executeQuery()){if(r.next()){if(!player.toString().equals(r.getString(1)))throw new IllegalStateException("Operation owner mismatch");var result=decode(r.getString(2));c.commit();return result;}}}
            var result=new ArrayList<Pull>();int index=0;
            for(var ch:choices){boolean duplicate=false;try(var p=c.prepareStatement("SELECT owned FROM codex_artifact_ownership WHERE player=? AND artifact=?")){p.setString(1,player.toString());p.setString(2,ch.id());try(var r=p.executeQuery()){duplicate=(r.next()&&r.getBoolean(1))||ch.temporaryAccess();}}
                int shards=duplicate?ch.shards():0;if(!duplicate)write(c,player,ch.id(),true);
                result.add(new Pull(ch.id(),ch.grade(),!duplicate,shards));
                if(shards>0)try(var p=c.prepareStatement("INSERT INTO codex_artifact_delivery(operation_id,pull_index,player,item,amount) VALUES(?,?,?,?,?)")){p.setString(1,operation);p.setInt(2,index);p.setString(3,player.toString());p.setString(4,shardItem);p.setInt(5,shards);p.executeUpdate();}index++;
            }
            try(var p=c.prepareStatement("INSERT INTO codex_artifact_operations(operation_id,player,result) VALUES(?,?,?)")){p.setString(1,operation);p.setString(2,player.toString());p.setString(3,encode(result));p.executeUpdate();}c.commit();return List.copyOf(result);
        }catch(Exception e){c.rollback();throw e;}}
    }
    List<Delivery> pendingDeliveries() throws Exception {
        try(var c=connections.open();var p=c.prepareStatement("SELECT operation_id,pull_index,player,item,amount FROM codex_artifact_delivery WHERE delivered=FALSE LIMIT 100");var r=p.executeQuery()){var rows=new ArrayList<Delivery>();while(r.next())rows.add(new Delivery(r.getString(1),r.getInt(2),UUID.fromString(r.getString(3)),r.getString(4),r.getInt(5)));return rows;}
    }
    void delivered(Delivery row) throws Exception {try(var c=connections.open();var p=c.prepareStatement("UPDATE codex_artifact_delivery SET delivered=TRUE WHERE operation_id=? AND pull_index=?")){p.setString(1,row.operation());p.setInt(2,row.index());p.executeUpdate();}}
    private static void lock(Connection c,UUID player)throws SQLException {
        try(var p=c.prepareStatement("INSERT IGNORE INTO codex_artifact_accounts(player) VALUES(?)")){p.setString(1,player.toString());p.executeUpdate();}
        try(var p=c.prepareStatement("SELECT player FROM codex_artifact_accounts WHERE player=? FOR UPDATE")){p.setString(1,player.toString());try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Account lock unavailable");}}
    }
    private static void write(Connection c,UUID player,String id,boolean owned)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO codex_artifact_ownership(player,artifact,owned) VALUES(?,?,?) ON DUPLICATE KEY UPDATE owned=VALUES(owned)")){p.setString(1,player.toString());p.setString(2,id);p.setBoolean(3,owned);p.executeUpdate();}
    }
    static void valid(String id){if(id==null||!id.matches("[a-z0-9_]{1,64}"))throw new IllegalArgumentException("Invalid artifact ID");}
    private static String encode(List<Pull> rows){return String.join(";",rows.stream().map(p->p.id()+","+p.grade()+","+p.fresh()+","+p.shards()).toList());}
    private static List<Pull> decode(String text){var out=new ArrayList<Pull>();for(String row:text.split(";")){var p=row.split(",");out.add(new Pull(p[0],Integer.parseInt(p[1]),Boolean.parseBoolean(p[2]),Integer.parseInt(p[3])));}return List.copyOf(out);}
}
