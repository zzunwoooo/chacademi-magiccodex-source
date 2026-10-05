package school.magiccodex.paper;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import school.magiccodex.protocol.SocialProtocol;
import school.magiccodex.database.ConnectionHolder;
import school.magiccodex.database.DatabaseSettings;

/** Owned by one IO worker. UUID relations survive renames/restarts. */
final class FriendStore implements AutoCloseable {
    private final ConnectionHolder holder;
    private Connection db()throws SQLException{return holder.get();}
    private final boolean maria;
    private final String friends,signals;
    FriendStore(Path file)throws Exception{this(file,new DatabaseSettings(false,"","",""));}
    FriendStore(Path file,DatabaseSettings settings)throws Exception{
        maria=settings.mariaDb();friends=settings.table("friends","friends");signals=settings.table("first_friend_signal","first_friend_signal");
        holder=settings.holder(file);
        try(var s=db().createStatement()){
            if(maria){
                s.execute("CREATE TABLE IF NOT EXISTS codex_friend_owners(owner CHAR(36) PRIMARY KEY) ENGINE=InnoDB");
                s.execute("CREATE TABLE IF NOT EXISTS codex_friends(id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,owner CHAR(36) NOT NULL,target CHAR(36) NOT NULL,name VARCHAR(64) NOT NULL,dorm VARCHAR(64) NOT NULL,UNIQUE KEY uq_friend(owner,target),KEY ix_friend_owner(owner,id),KEY ix_friend_target(target)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("CREATE TABLE IF NOT EXISTS codex_first_friend_signal(owner CHAR(36) PRIMARY KEY,delivered TINYINT NOT NULL DEFAULT 0) ENGINE=InnoDB");
            }else{
                s.execute("CREATE TABLE IF NOT EXISTS friends(owner TEXT NOT NULL,target TEXT NOT NULL,name TEXT NOT NULL,dorm TEXT NOT NULL,PRIMARY KEY(owner,target))");
                s.execute("CREATE TABLE IF NOT EXISTS first_friend_signal(owner TEXT PRIMARY KEY,delivered INTEGER NOT NULL DEFAULT 0)");
            }
        }
    }
    List<SocialProtocol.Entry> load(UUID owner)throws SQLException{
        var list=new ArrayList<SocialProtocol.Entry>();
        try(var s=db().prepareStatement("SELECT target,name,dorm FROM "+friends+" WHERE owner=? ORDER BY "+(maria?"id":"rowid"))){
            s.setString(1,owner.toString());try(var r=s.executeQuery()){while(r.next())list.add(new SocialProtocol.Entry(UUID.fromString(r.getString(1)),r.getString(2),r.getString(3),false));}
        }
        return List.copyOf(list);
    }
    boolean add(UUID owner,SocialProtocol.Entry friend)throws SQLException{
        if(owner.equals(friend.id()))return false;
        holder.begin();
        try{
            if(maria){
                try(var s=db().prepareStatement("INSERT IGNORE INTO codex_friend_owners(owner) VALUES(?)")){s.setString(1,owner.toString());s.executeUpdate();}
                try(var s=db().prepareStatement("SELECT owner FROM codex_friend_owners WHERE owner=? FOR UPDATE")){s.setString(1,owner.toString());try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Friend owner lock missing");}}
            }
            var all=load(owner);if(all.size()>=SocialProtocol.LIMIT||all.stream().anyMatch(e->e.id().equals(friend.id()))){holder.rollback();return false;}
            try(var s=db().prepareStatement("INSERT INTO "+friends+"(owner,target,name,dorm) VALUES(?,?,?,?)")){s.setString(1,owner.toString());s.setString(2,friend.id().toString());s.setString(3,friend.name());s.setString(4,friend.dormitory());s.executeUpdate();}
            try(var s=db().prepareStatement((maria?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+signals+"(owner) VALUES(?)")){s.setString(1,owner.toString());s.executeUpdate();}
            db().commit();return true;
        }catch(SQLException e){holder.rollback();throw e;}finally{holder.end();}
    }
    boolean remove(UUID owner,UUID friend)throws SQLException{
        try(var s=db().prepareStatement("DELETE FROM "+friends+" WHERE owner=? AND target=?")){s.setString(1,owner.toString());s.setString(2,friend.toString());return s.executeUpdate()>0;}
    }
    void profile(UUID id,String name,String dorm)throws SQLException{
        try(var s=db().prepareStatement("UPDATE "+friends+" SET name=?,dorm=? WHERE target=? AND (name<>? OR dorm<>?)")){s.setString(1,name);s.setString(2,dorm);s.setString(3,id.toString());s.setString(4,name);s.setString(5,dorm);s.executeUpdate();}
    }
    boolean pendingSignal(UUID owner)throws SQLException{try(var s=db().prepareStatement("SELECT delivered FROM "+signals+" WHERE owner=?")){s.setString(1,owner.toString());try(var r=s.executeQuery()){return r.next()&&r.getInt(1)==0;}}}
    void signalDelivered(UUID owner)throws SQLException{try(var s=db().prepareStatement("UPDATE "+signals+" SET delivered=1 WHERE owner=?")){s.setString(1,owner.toString());s.executeUpdate();}}
    @Override public void close()throws SQLException{holder.close();}
}
