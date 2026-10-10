package school.magiccodex.paper;

import java.sql.*;
import java.util.*;
import school.magiccodex.database.ConnectionHolder;
import school.magiccodex.database.DatabaseSettings;

/** Only persistent, original player-owned values; derived equipment/season stats stay in memory. */
final class PlayerStateStore implements AutoCloseable {
    record State(int circle,double manaCurrent,double manaMaximum,double manaRegeneration,double magicHaste,
                 String cooldowns,byte[][] accessories,int[] reconfigurationCredits) {
        State {
            if(circle<1||circle>9||!valid(manaCurrent)||!valid(manaMaximum)||!valid(manaRegeneration)||!valid(magicHaste)
                    ||cooldowns==null||cooldowns.length()>32768||accessories==null||accessories.length!=4
                    ||reconfigurationCredits==null||reconfigurationCredits.length!=3)throw new IllegalArgumentException("Invalid player state");
            accessories=Arrays.stream(accessories).map(b->b==null?null:b.clone()).toArray(byte[][]::new);
            for(var slot:accessories)if(slot!=null&&slot.length>32768)throw new IllegalArgumentException("Accessory too large");
            reconfigurationCredits=reconfigurationCredits.clone();
            for(int credit:reconfigurationCredits)if(credit<0||credit>1_000_000)throw new IllegalArgumentException("Invalid credit");
        }
        private static boolean valid(double n){return Double.isFinite(n)&&n>=0&&n<=1_000_000;}
    }
    /** UPDATE가 0행: 다른 서버가 임대를 가져갔다는 확정 신호. 일시적인 SQL/연결 오류와 구분한다. */
    static final class LeaseLostException extends SQLException {
        LeaseLostException(UUID id){super("Player state lease lost: "+id);}
    }
    private static final String COLUMNS="player,circle,mana_current,mana_maximum,mana_regeneration,magic_haste,mana_cooldowns,accessory_0,accessory_1,accessory_2,accessory_3,reconfig_credit_0,reconfig_credit_1,reconfig_credit_2";
    private final ConnectionHolder holder;
    private Connection db()throws SQLException{return holder.get();}
    PlayerStateStore(DatabaseSettings settings)throws Exception {
        if(!settings.mariaDb())throw new IllegalArgumentException("MariaDB required");
        holder=settings.holder(null);
        try(var s=db().createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS codex_player_state(player CHAR(36) PRIMARY KEY,circle TINYINT NOT NULL DEFAULT 1,mana_current DOUBLE NOT NULL DEFAULT 100,mana_maximum DOUBLE NOT NULL DEFAULT 100,mana_regeneration DOUBLE NOT NULL DEFAULT 5,magic_haste DOUBLE NOT NULL DEFAULT 0,mana_cooldowns TEXT NOT NULL,accessory_0 BLOB NULL,accessory_1 BLOB NULL,accessory_2 BLOB NULL,accessory_3 BLOB NULL,reconfig_credit_0 INT NOT NULL DEFAULT 0,reconfig_credit_1 INT NOT NULL DEFAULT 0,reconfig_credit_2 INT NOT NULL DEFAULT 0,lease_token CHAR(36) NULL,lease_until BIGINT NOT NULL DEFAULT 0,revision BIGINT NOT NULL DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }
    Optional<State> load(UUID id)throws SQLException {
        try(var s=db().prepareStatement("SELECT "+COLUMNS+" FROM codex_player_state WHERE player=?")){
            s.setString(1,id.toString());try(var r=s.executeQuery()){
                if(!r.next())return Optional.empty();
                var slots=new byte[4][];for(int i=0;i<4;i++)slots[i]=r.getBytes(8+i);
                var credits=new int[3];for(int i=0;i<3;i++)credits[i]=r.getInt(12+i);
                return Optional.of(new State(r.getInt(2),r.getDouble(3),r.getDouble(4),r.getDouble(5),r.getDouble(6),r.getString(7),slots,credits));
            }
        }
    }
    State firstOrCurrent(UUID id,State fallback)throws SQLException {
        try(var s=db().prepareStatement("INSERT IGNORE INTO codex_player_state("+COLUMNS+") VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
            bind(s,id,fallback);s.executeUpdate();
        }
        return load(id).orElseThrow(()->new SQLException("Player state insert failed"));
    }
    boolean claim(UUID id,String token)throws SQLException {
        try(var s=db().prepareStatement("UPDATE codex_player_state SET lease_token=?,lease_until=? WHERE player=? AND (lease_token IS NULL OR lease_until<? OR lease_token=?)")){
            long now=System.currentTimeMillis();s.setString(1,token);s.setLong(2,now+60_000);s.setString(3,id.toString());s.setLong(4,now);s.setString(5,token);
            return s.executeUpdate()==1;
        }
    }
    void release(UUID id,String token)throws SQLException {
        try(var s=db().prepareStatement("UPDATE codex_player_state SET lease_token=NULL,lease_until=0 WHERE player=? AND lease_token=?")){
            s.setString(1,id.toString());s.setString(2,token);s.executeUpdate();
        }
    }
    /** @throws LeaseLostException 이 토큰이 더 이상 행을 소유하지 않을 때만. 그 밖의 SQLException은 일시 오류로 다시 시도할 수 있다. */
    void save(UUID id,State state,String token,boolean release)throws SQLException {
        String updates="circle=?,mana_current=?,mana_maximum=?,mana_regeneration=?,magic_haste=?,mana_cooldowns=?,"
                +"accessory_0=?,accessory_1=?,accessory_2=?,accessory_3=?,reconfig_credit_0=?,reconfig_credit_1=?,reconfig_credit_2=?,";
        String sql="UPDATE codex_player_state SET "+updates+(release?"lease_token=NULL,lease_until=0,":"lease_until=?,")
                +"revision=revision+1 WHERE player=? AND lease_token=?";
        try(var s=db().prepareStatement(sql)){
            s.setInt(1,state.circle());s.setDouble(2,state.manaCurrent());s.setDouble(3,state.manaMaximum());s.setDouble(4,state.manaRegeneration());s.setDouble(5,state.magicHaste());s.setString(6,state.cooldowns());
            for(int i=0;i<4;i++)s.setBytes(7+i,state.accessories()[i]);
            for(int i=0;i<3;i++)s.setInt(11+i,state.reconfigurationCredits()[i]);
            int index=14;if(!release)s.setLong(index++,System.currentTimeMillis()+60_000);
            s.setString(index++,id.toString());s.setString(index,token);
            if(s.executeUpdate()!=1)throw new LeaseLostException(id);
        }
    }
    private static void bind(PreparedStatement s,UUID id,State state)throws SQLException {
        s.setString(1,id.toString());s.setInt(2,state.circle());s.setDouble(3,state.manaCurrent());s.setDouble(4,state.manaMaximum());
        s.setDouble(5,state.manaRegeneration());s.setDouble(6,state.magicHaste());s.setString(7,state.cooldowns());
        for(int i=0;i<4;i++)s.setBytes(8+i,state.accessories()[i]);
        for(int i=0;i<3;i++)s.setInt(12+i,state.reconfigurationCredits()[i]);
    }
    @Override public void close()throws SQLException{holder.close();}
}
