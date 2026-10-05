package school.magiccodex.paper;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import school.magiccodex.database.ConnectionHolder;
import school.magiccodex.protocol.QuestAdminProtocol;
import school.magiccodex.database.DatabaseSettings;

/** One worker owns this connection. Transactions lock a UUID row across all backend servers. */
final class QuestStore implements AutoCloseable {
    record Entry(QuestDefinition quest,String cycle,String status,int[] progress,String token,int completions) {
        boolean current(long now){return cycle.equals(quest.cycle(now));}
        boolean complete(){for(int i=0;i<progress.length;i++)if(quest.objectives().get(i).type()!=QuestDefinition.Type.SUBMIT&&progress[i]<quest.objectives().get(i).amount())return false;return true;}
    }
    private final ConnectionHolder holder;private final boolean maria;
    private Connection db()throws SQLException{return holder.get();}
    QuestStore(DatabaseSettings settings,Path path)throws Exception{
        maria=settings.mariaDb();holder=settings.holder(path);
        try(var s=db().createStatement()){
            String tail=maria?" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4":"";
            s.execute("CREATE TABLE IF NOT EXISTS codex_quest_lock(player VARCHAR(36) PRIMARY KEY,version BIGINT NOT NULL)"+tail);
            s.execute("CREATE TABLE IF NOT EXISTS codex_quest_catalog(id VARCHAR(48) PRIMARY KEY,definition TEXT NOT NULL)"+tail);
            s.execute("CREATE TABLE IF NOT EXISTS codex_quest_progress(player VARCHAR(36) NOT NULL,quest VARCHAR(48) NOT NULL,cycle VARCHAR(16) NOT NULL,definition TEXT NOT NULL,status VARCHAR(16) NOT NULL,progress VARCHAR(128) NOT NULL,token VARCHAR(36) NOT NULL,updated BIGINT NOT NULL,PRIMARY KEY(player,quest,cycle))"+tail);
        }
        boolean column=false;try(var r=db().getMetaData().getColumns(null,null,"codex_quest_progress","completions")){column=r.next();}
        if(!column)try(var s=db().createStatement()){s.execute("ALTER TABLE codex_quest_progress ADD COLUMN completions INT NOT NULL DEFAULT 0");}catch(SQLException ex){try(var r=db().getMetaData().getColumns(null,null,"codex_quest_progress","completions")){if(!r.next())throw ex;}}
        try(var s=db().createStatement()){s.executeUpdate("UPDATE codex_quest_progress SET completions=1 WHERE status='claimed' AND completions=0");}
    }
    Map<String,QuestDefinition> catalog()throws SQLException{var map=new TreeMap<String,QuestDefinition>();try(var s=db().createStatement();var r=s.executeQuery("SELECT definition FROM codex_quest_catalog")){while(r.next()){var q=QuestDefinition.decode(r.getString(1));map.put(q.id(),q);}}return Map.copyOf(map);}
    void publish(Map<String,QuestDefinition> definitions)throws Exception{
        if(definitions.size()>QuestAdminProtocol.MAX_ENTRIES)throw new IllegalArgumentException("최대 "+QuestAdminProtocol.MAX_ENTRIES+"개 의뢰");
        transaction("catalog",()->{try(var s=db().createStatement()){s.executeUpdate("DELETE FROM codex_quest_catalog");}try(var s=db().prepareStatement("INSERT INTO codex_quest_catalog(id,definition) VALUES(?,?)")){for(var q:definitions.values()){s.setString(1,q.id());s.setString(2,q.encode());s.addBatch();}s.executeBatch();}return null;});
    }
    void seed(Map<String,QuestDefinition> definitions)throws Exception{transaction("catalog",()->{if(catalog().isEmpty()){try(var s=db().prepareStatement("INSERT INTO codex_quest_catalog(id,definition) VALUES(?,?)")){for(var q:definitions.values()){s.setString(1,q.id());s.setString(2,q.encode());s.addBatch();}s.executeBatch();}}return null;});}
    List<Entry> entries(UUID player)throws SQLException{return entries(player,System.currentTimeMillis());}
    private List<Entry> entries(UUID player,long now)throws SQLException{var out=new ArrayList<Entry>();try(var s=db().prepareStatement("SELECT definition,cycle,status,progress,token,completions FROM codex_quest_progress WHERE player=? AND (cycle='once' OR cycle=? OR status='paying')")){s.setString(1,player.toString());s.setString(2,java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate().toString());try(var r=s.executeQuery()){while(r.next()){var q=QuestDefinition.decode(r.getString(1));int[] p=Arrays.stream(r.getString(4).split(",")).mapToInt(Integer::parseInt).toArray();if(p.length!=q.objectives().size())throw new SQLException("Quest progress mismatch");out.add(new Entry(q,r.getString(2),r.getString(3),p,r.getString(5),r.getInt(6)));}}}return out;}
    boolean accept(UUID player,QuestDefinition q,long now)throws Exception{return transaction(player.toString(),()->{
        if(!q.available(now))return false;
        var all=entries(player,now);var previous=all.stream().filter(e->e.quest.id().equals(q.id())&&e.cycle.equals(q.cycle(now))).findFirst().orElse(null);
        if(all.stream().anyMatch(e->e.quest.id().equals(q.id())&&(e.status.equals("paying")||(e.current(now)&&e.status.equals("active"))))||all.stream().filter(e->e.status.equals("paying")||(e.current(now)&&e.status.equals("active"))).count()>=3)return false;
        int completed=previous==null?0:previous.completions;if(completed>=q.completionLimit())return false;
        if(previous!=null)try(var s=db().prepareStatement("DELETE FROM codex_quest_progress WHERE player=? AND quest=? AND cycle=?")){s.setString(1,player.toString());s.setString(2,q.id());s.setString(3,q.cycle(now));s.executeUpdate();}
        try(var s=db().prepareStatement("INSERT INTO codex_quest_progress(player,quest,cycle,definition,status,progress,token,updated,completions) VALUES(?,?,?,?,?,?,?,?,?)")){s.setString(1,player.toString());s.setString(2,q.id());s.setString(3,q.cycle(now));s.setString(4,q.encode());s.setString(5,"active");s.setString(6,counts(new int[q.objectives().size()]));s.setString(7,"");s.setLong(8,now);s.setInt(9,completed);s.executeUpdate();}return true;
    });}
    void advance(UUID player,QuestDefinition.Type type,String target,String server,String world,int amount,long now)throws Exception{transaction(player.toString(),()->{for(var e:entries(player)){if(!e.status.equals("active")||!e.current(now))continue;boolean dirty=false;for(int i=0;i<e.progress.length;i++){var g=e.quest.objectives().get(i);if(g.matches(type,target,server,world)&&type!=QuestDefinition.Type.SUBMIT){int v=(int)Math.min(g.amount(),(long)e.progress[i]+amount);if(v!=e.progress[i]){e.progress[i]=v;dirty=true;}}}if(dirty)try(var s=db().prepareStatement("UPDATE codex_quest_progress SET progress=?,updated=? WHERE player=? AND quest=? AND cycle=? AND status='active'")){s.setString(1,counts(e.progress));s.setLong(2,now);s.setString(3,player.toString());s.setString(4,e.quest.id());s.setString(5,e.cycle);s.executeUpdate();}}return null;});}
    Entry reserve(UUID player,String id,String expectedCycle,long now)throws Exception{return transaction(player.toString(),()->{for(var e:entries(player))if(e.quest.id().equals(id)&&e.cycle.equals(expectedCycle)&&e.current(now)&&e.status.equals("active")&&e.complete()){String token=UUID.randomUUID().toString();try(var s=db().prepareStatement("UPDATE codex_quest_progress SET status='paying',token=?,updated=? WHERE player=? AND quest=? AND cycle=? AND status='active'")){s.setString(1,token);s.setLong(2,now);s.setString(3,player.toString());s.setString(4,id);s.setString(5,e.cycle);if(s.executeUpdate()==1)return new Entry(e.quest,e.cycle,"paying",e.progress,token,e.completions);}}return null;});}
    void finish(UUID player,Entry e,boolean delivered)throws Exception{
        if(!holder.inTransaction())transaction(player.toString(),()->{finishLocked(player,e,delivered);return null;});else finishLocked(player,e,delivered);
    }
    private void finishLocked(UUID player,Entry e,boolean delivered)throws SQLException{
        try(var s=db().prepareStatement("UPDATE codex_quest_progress SET status=?,token='',updated=?,completions=completions+? WHERE player=? AND quest=? AND cycle=? AND status='paying' AND token=?")){s.setString(1,delivered?"claimed":"active");s.setLong(2,System.currentTimeMillis());s.setInt(3,delivered?1:0);s.setString(4,player.toString());s.setString(5,e.quest.id());s.setString(6,e.cycle);s.setString(7,e.token);if(s.executeUpdate()!=1)throw new SQLException("Claim reservation changed");}
    }
    boolean abandon(UUID p,String id,long now)throws Exception{return transaction(p.toString(),()->{for(var e:entries(p))if(e.quest.id().equals(id)&&e.current(now)&&e.status.equals("active")){try(var s=db().prepareStatement("UPDATE codex_quest_progress SET status='ready',progress=?,token='' WHERE player=? AND quest=? AND cycle=? AND status='active'")){s.setString(1,counts(new int[e.progress.length]));s.setString(2,p.toString());s.setString(3,id);s.setString(4,e.cycle);return s.executeUpdate()==1;}}return false;});}
    /** Compare-and-swap one definition so two administrators cannot overwrite each other. */
    boolean edit(String id,String expected,QuestDefinition replacement)throws Exception{return transaction("catalog",()->{var all=catalog();var old=all.get(id);String actual=old==null?"":fingerprint(old);if(!actual.equals(expected))return false;if(old==null&&replacement!=null&&all.size()>=QuestAdminProtocol.MAX_ENTRIES)throw new IllegalArgumentException("최대 "+QuestAdminProtocol.MAX_ENTRIES+"개 의뢰");try(var s=db().prepareStatement("DELETE FROM codex_quest_catalog WHERE id=?")){s.setString(1,id);s.executeUpdate();}if(replacement!=null)try(var s=db().prepareStatement("INSERT INTO codex_quest_catalog(id,definition) VALUES(?,?)")){s.setString(1,id);s.setString(2,replacement.encode());s.executeUpdate();}return true;});}
    Map<String,QuestDefinition> release(List<String> ids,long start,long spacing,boolean enabled)throws Exception{return transaction("catalog",()->{var all=new TreeMap<>(catalog());if(ids.size()>QuestAdminProtocol.MAX_ENTRIES||new HashSet<>(ids).size()!=ids.size()||ids.stream().anyMatch(id->!all.containsKey(id)))throw new IllegalArgumentException("의뢰 ID를 확인하세요");int n=0;for(String id:ids){var q=all.get(id).release(start+spacing*n++,enabled);try(var s=db().prepareStatement("UPDATE codex_quest_catalog SET definition=? WHERE id=?")){s.setString(1,q.encode());s.setString(2,id);s.executeUpdate();}all.put(id,q);}return Map.copyOf(all);});}
    static String fingerprint(QuestDefinition q){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(q.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    void resolve(UUID p,String id,String cycle,boolean delivered)throws Exception{transaction(p.toString(),()->{for(var e:entries(p))if(e.quest.id().equals(id)&&e.cycle.equals(cycle)&&e.status.equals("paying")){finish(p,e,delivered);return null;}throw new IllegalArgumentException("검토 대기 기록 없음");});}
    private static String counts(int[] p){return String.join(",",Arrays.stream(p).mapToObj(Integer::toString).toList());}
    private interface Tx<T>{T run()throws Exception;}
    private <T>T transaction(String player,Tx<T> action)throws Exception{
        for(int attempt=0;;attempt++){holder.begin();try{
            // A single exclusive upsert avoids INSERT IGNORE shared-lock -> UPDATE lock-upgrade deadlocks.
            String lock="INSERT INTO codex_quest_lock(player,version) VALUES(?,1) "+(maria?"ON DUPLICATE KEY UPDATE version=version+1":"ON CONFLICT(player) DO UPDATE SET version=version+1");
            try(var s=db().prepareStatement(lock)){s.setString(1,player);s.executeUpdate();}
            T value=action.run();db().commit();return value;
        }catch(Exception e){holder.rollback();if(attempt<3&&e instanceof SQLException sql&&(sql.getErrorCode()==1213||sql.getErrorCode()==1205||"40001".equals(sql.getSQLState()))){Thread.sleep(10L*(attempt+1));continue;}throw e;}finally{holder.end();}}
    }
    @Override public void close()throws SQLException{holder.close();}
}
