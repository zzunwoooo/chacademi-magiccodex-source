package school.magiccodex.paper;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import school.magiccodex.protocol.SchoolProtocol.Donation;
import school.magiccodex.database.ConnectionHolder;
import school.magiccodex.database.DatabaseSettings;

/** Single IO worker owns this database; record claim and house points commit together. */
final class SchoolStore implements AutoCloseable {
    static final long MAX_SCORE=1_000_000_000L;
    record Snapshot(List<Long> scores,List<Donation> records,Map<UUID,Integer> houses){
        Snapshot{scores=List.copyOf(scores);records=List.copyOf(records);houses=Map.copyOf(houses);}
    }
    private final ConnectionHolder holder;
    private Connection db()throws SQLException{return holder.get();}
    private final boolean maria;
    private final String points,donations,houses;
    SchoolStore(Path file)throws Exception{this(file,new DatabaseSettings(false,"","",""));}
    SchoolStore(Path file,DatabaseSettings settings)throws Exception{
        maria=settings.mariaDb();points=settings.table("house_points","house_points");donations=settings.table("donations","donations");houses=settings.table("houses","houses");
        holder=settings.holder(file);
        try(var s=db().createStatement()){
            if(maria){
                s.execute("CREATE TABLE IF NOT EXISTS codex_house_points(house TINYINT PRIMARY KEY,points BIGINT NOT NULL DEFAULT 0) ENGINE=InnoDB");
                s.execute("CREATE TABLE IF NOT EXISTS codex_donations(spell VARCHAR(64) PRIMARY KEY,spell_name VARCHAR(100) NOT NULL,donor CHAR(36) NOT NULL,nickname VARCHAR(64) NOT NULL,house TINYINT NOT NULL,created BIGINT NOT NULL,KEY ix_donations_order(created,spell)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("CREATE TABLE IF NOT EXISTS codex_houses(player CHAR(36) PRIMARY KEY,house TINYINT NOT NULL) ENGINE=InnoDB");
            }else{
                s.execute("CREATE TABLE IF NOT EXISTS house_points(house INTEGER PRIMARY KEY,points INTEGER NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS donations(spell TEXT PRIMARY KEY,spell_name TEXT NOT NULL,donor TEXT NOT NULL,nickname TEXT NOT NULL,house INTEGER NOT NULL,created INTEGER NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS houses(player TEXT PRIMARY KEY,house INTEGER NOT NULL)");
            }
            s.execute("CREATE TABLE IF NOT EXISTS codex_quest_house_awards(token VARCHAR(36) PRIMARY KEY,house INT NOT NULL,points BIGINT NOT NULL)"+(maria?" ENGINE=InnoDB":""));
            for(int i=0;i<4;i++)s.execute((maria?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+points+" VALUES("+i+",0)");
        }
    }
    Snapshot snapshot()throws SQLException{return snapshot(null);}
    Snapshot snapshot(Collection<UUID> activePlayers)throws SQLException{
        var scores=new ArrayList<Long>();var records=new ArrayList<Donation>();var houses=new HashMap<UUID,Integer>();
        try(var s=db().createStatement();var r=s.executeQuery("SELECT points FROM "+points+" ORDER BY house")){while(r.next())scores.add(r.getLong(1));}
        try(var s=db().createStatement();var r=s.executeQuery("SELECT * FROM "+donations+" ORDER BY created DESC,"+(maria?"spell":"rowid")+" DESC")){while(r.next())records.add(new Donation(r.getString("spell"),r.getString("spell_name"),UUID.fromString(r.getString("donor")),r.getString("nickname"),r.getInt("house"),r.getLong("created")));}
        if(activePlayers==null){try(var s=db().createStatement();var r=s.executeQuery("SELECT * FROM "+this.houses)){while(r.next())houses.put(UUID.fromString(r.getString(1)),r.getInt(2));}}
        else if(!activePlayers.isEmpty()){
            var ids=List.copyOf(activePlayers);
            for(int from=0;from<ids.size();from+=128){
                var batch=ids.subList(from,Math.min(from+128,ids.size()));
                String placeholders=String.join(",",Collections.nCopies(batch.size(),"?"));
                try(var s=db().prepareStatement("SELECT player,house FROM "+this.houses+" WHERE player IN ("+placeholders+")")){
                    for(int i=0;i<batch.size();i++)s.setString(i+1,batch.get(i).toString());
                    try(var r=s.executeQuery()){while(r.next())houses.put(UUID.fromString(r.getString(1)),r.getInt(2));}
                }
            }
        }
        return new Snapshot(scores,records,houses);
    }
    boolean donate(Donation e,long points)throws SQLException{
        if(e.house()<0||e.house()>3||points<0||points>MAX_SCORE)throw new IllegalArgumentException("Donation");
        holder.begin();
        try{
            try(var s=db().prepareStatement((maria?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+donations+" VALUES(?,?,?,?,?,?)")){
                s.setString(1,e.spell());s.setString(2,e.spellName());s.setString(3,e.donor().toString());s.setString(4,e.nickname());s.setInt(5,e.house());s.setLong(6,e.time());
                if(s.executeUpdate()==0){holder.rollback();return false;}
            }
            change(e.house(),points,false);db().commit();return true;
        }catch(SQLException|RuntimeException ex){holder.rollback();throw ex;}finally{holder.end();}
    }
    boolean questReward(String token,int house,long value)throws SQLException{
        UUID.fromString(token);if(house<0||house>3||value<1||value>MAX_SCORE)throw new IllegalArgumentException("Quest house reward");holder.begin();
        try{try(var s=db().prepareStatement((maria?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+"codex_quest_house_awards(token,house,points) VALUES(?,?,?)")){s.setString(1,token);s.setInt(2,house);s.setLong(3,value);if(s.executeUpdate()==0){holder.rollback();return false;}}change(house,value,false);db().commit();return true;}catch(SQLException|RuntimeException ex){holder.rollback();throw ex;}finally{holder.end();}
    }
    void change(int house,long value,boolean set)throws SQLException{
        if(house<0||house>3||Math.abs(value)>MAX_SCORE)throw new IllegalArgumentException("Score range");
        if(maria){
            String sql=set?"UPDATE "+points+" SET points=? WHERE house=?":"UPDATE "+points+" SET points=points+? WHERE house=? AND points+? BETWEEN ? AND ?";
            try(var s=db().prepareStatement(sql)){s.setLong(1,value);s.setInt(2,house);if(!set){s.setLong(3,value);s.setLong(4,-MAX_SCORE);s.setLong(5,MAX_SCORE);}if(s.executeUpdate()==0&&!set)throw new IllegalArgumentException("Score range exceeded");}
            return;
        }
        long before;try(var s=db().prepareStatement("SELECT points FROM "+points+" WHERE house=?")){s.setInt(1,house);try(var r=s.executeQuery()){r.next();before=r.getLong(1);}}
        long next=set?value:Math.addExact(before,value);if(next < -MAX_SCORE||next>MAX_SCORE)throw new IllegalArgumentException("Score range exceeded");
        try(var s=db().prepareStatement("UPDATE "+points+" SET points=? WHERE house=?")){s.setLong(1,next);s.setInt(2,house);s.executeUpdate();}
    }
    void house(UUID id,int house)throws SQLException{if(house<0||house>3)throw new IllegalArgumentException("House");try(var s=db().prepareStatement(maria?"INSERT INTO "+houses+" VALUES(?,?) ON DUPLICATE KEY UPDATE house=VALUES(house)":"INSERT INTO "+houses+" VALUES(?,?) ON CONFLICT(player) DO UPDATE SET house=excluded.house")){s.setString(1,id.toString());s.setInt(2,house);s.executeUpdate();}}
    void reset(String spell)throws SQLException{
        if(spell.equals("all")){try(var s=db().createStatement()){s.executeUpdate("DELETE FROM "+donations);}}
        else try(var s=db().prepareStatement("DELETE FROM "+donations+" WHERE spell=?")){s.setString(1,spell);s.executeUpdate();}
    }
    void resetPoints()throws SQLException{try(var s=db().createStatement()){s.executeUpdate("UPDATE "+points+" SET points=0");}}
    public void close()throws SQLException{holder.close();}
}
