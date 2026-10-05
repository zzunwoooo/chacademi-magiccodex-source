package school.magiccodex.paper;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import school.magiccodex.database.DatabaseSettings;
import school.magiccodex.protocol.NicknameProtocol;

/** One IO worker owns this connection; SQLite reuses the existing friends.db. */
final class NicknameStore implements AutoCloseable {
    record State(String account,String nickname,long revision){}
    private final Connection db;
    private final String table;
    private final boolean maria;
    NicknameStore(Path file,DatabaseSettings settings)throws Exception{
        maria=settings.mariaDb();table=settings.table("nicknames","nicknames");db=settings.connect(file);settings.configure(db);
        try(var s=db.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS "+table+" (owner "+(maria?"CHAR(36)":"TEXT")+" PRIMARY KEY,account VARCHAR(16) NOT NULL,nickname VARCHAR(16) NOT NULL,revision BIGINT NOT NULL DEFAULT 1)"+(maria?" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4":""));}
    }
    Map<UUID,State> all()throws SQLException{
        var result=new HashMap<UUID,State>();try(var s=db.createStatement();var r=s.executeQuery("SELECT owner,account,nickname,revision FROM "+table)){while(r.next())result.put(UUID.fromString(r.getString(1)),new State(r.getString(2),r.getString(3),r.getLong(4)));}return result;
    }
    State load(UUID owner)throws SQLException{try(var s=db.prepareStatement("SELECT account,nickname,revision FROM "+table+" WHERE owner=?")){s.setString(1,owner.toString());try(var r=s.executeQuery()){return r.next()?new State(r.getString(1),r.getString(2),r.getLong(3)):null;}}}
    /** Revision compare occurs in SQL, including simultaneous first saves across servers. */
    State save(UUID owner,String account,String nickname,long revision)throws SQLException{
        NicknameProtocol.validate(nickname);
        if(revision==0){try(var s=db.prepareStatement((maria?"INSERT IGNORE INTO ":"INSERT OR IGNORE INTO ")+table+"(owner,account,nickname,revision) VALUES(?,?,?,1)")){s.setString(1,owner.toString());s.setString(2,account);s.setString(3,nickname);s.executeUpdate();}}
        else try(var s=db.prepareStatement("UPDATE "+table+" SET account=?,nickname=?,revision=revision+1 WHERE owner=? AND revision=?")){s.setString(1,account);s.setString(2,nickname);s.setString(3,owner.toString());s.setLong(4,revision);s.executeUpdate();}
        return load(owner);
    }
    public void close()throws SQLException{db.close();}
}
