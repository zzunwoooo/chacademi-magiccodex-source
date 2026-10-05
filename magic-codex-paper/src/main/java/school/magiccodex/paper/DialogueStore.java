package school.magiccodex.paper;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import school.magiccodex.database.ConnectionHolder;
import school.magiccodex.database.DatabaseSettings;

/** Single-worker JDBC ownership; optimistic revision fences serialize cross-server choices. */
final class DialogueStore implements AutoCloseable {
    record State(long revision,Map<String,String> values){}
    record Transition(State state,String receipt,List<String> external){}
    private final ConnectionHolder holder;
    private Connection db()throws SQLException{return holder.get();}
    DialogueStore(DatabaseSettings settings,Path path)throws Exception{
        holder=settings.holder(path);String tail=settings.mariaDb()?" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4":"";
        try(var s=db().createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS codex_dialogue_catalog(id VARCHAR(48) PRIMARY KEY,revision VARCHAR(64) NOT NULL,definition TEXT NOT NULL)"+tail);
            s.execute("CREATE TABLE IF NOT EXISTS codex_story_state(player VARCHAR(36) PRIMARY KEY,revision BIGINT NOT NULL,content TEXT NOT NULL)"+tail);
            s.execute("CREATE TABLE IF NOT EXISTS codex_dialogue_effect(player VARCHAR(36) NOT NULL,effect_key VARCHAR(160) NOT NULL,token VARCHAR(36) NOT NULL,status VARCHAR(12) NOT NULL,actions TEXT NOT NULL,PRIMARY KEY(player,effect_key))"+tail);
        }
    }
    Map<String,DialogueDefinition> catalog()throws Exception{var out=new TreeMap<String,DialogueDefinition>();try(var s=db().createStatement();var r=s.executeQuery("SELECT id,definition FROM codex_dialogue_catalog")){while(r.next()){var d=DialogueDefinition.decode(r.getString(1),r.getString(2));out.put(d.id(),d);}}return Map.copyOf(out);}
    boolean edit(String id,String expected,DialogueDefinition next)throws Exception{
        String encoded=next==null?"":next.encode();if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>26000)throw new IllegalArgumentException("대화 문서가 큽니다. 여러 대화로 나누세요 (26KB)");
        if(expected.isEmpty()){if(next==null)return false;try(var s=db().prepareStatement("INSERT INTO codex_dialogue_catalog(id,revision,definition) VALUES(?,?,?)")){s.setString(1,id);s.setString(2,DialogueDefinition.revision(next));s.setString(3,encoded);try{return s.executeUpdate()==1;}catch(SQLException e){if(constraint(e))return false;throw e;}}}
        String sql=next==null?"DELETE FROM codex_dialogue_catalog WHERE id=? AND revision=?":"UPDATE codex_dialogue_catalog SET revision=?,definition=? WHERE id=? AND revision=?";
        try(var s=db().prepareStatement(sql)){int i=1;if(next!=null){s.setString(i++,DialogueDefinition.revision(next));s.setString(i++,encoded);}s.setString(i++,id);s.setString(i,expected);return s.executeUpdate()==1;}
    }
    void seed(DialogueDefinition d)throws Exception{if(catalog().isEmpty())edit(d.id(),"",d);}
    State state(UUID player)throws SQLException{try(var s=db().prepareStatement("SELECT revision,content FROM codex_story_state WHERE player=?")){s.setString(1,player.toString());try(var r=s.executeQuery()){if(r.next())return new State(r.getLong(1),decode(r.getString(2)));}}
        try(var s=db().prepareStatement("INSERT INTO codex_story_state(player,revision,content) VALUES(?,0,'')")){s.setString(1,player.toString());try{s.executeUpdate();}catch(SQLException e){if(!constraint(e))throw e;}}
        try(var s=db().prepareStatement("SELECT revision,content FROM codex_story_state WHERE player=?")){s.setString(1,player.toString());try(var r=s.executeQuery()){if(!r.next())throw new SQLException("missing state");return new State(r.getLong(1),decode(r.getString(2)));}}
    }
    Transition transition(UUID player,State before,String effectKey,List<String> actions)throws Exception{
        var values=new TreeMap<>(before.values);var external=new ArrayList<String>();
        for(String action:actions){String[] a=action.split(" ",3);if(a[0].equals("flag")||a[0].equals("story"))values.put(a[0]+"."+a[1],a[2]);else external.add(action);}
        if(values.size()>512)throw new IllegalArgumentException("스토리 상태 한도 초과");
        String content=encode(values),token="";holder.begin();
        try{
            try(var s=db().prepareStatement("UPDATE codex_story_state SET revision=revision+1,content=? WHERE player=? AND revision=?")){s.setString(1,content);s.setString(2,player.toString());s.setLong(3,before.revision);if(s.executeUpdate()!=1)throw new IllegalStateException("다른 서버에서 진행이 바뀌었습니다. 대화를 다시 열어 주세요.");}
            // Fence all branches while an ambiguous external effect awaits review.
            if(!audit(player).isEmpty())throw new IllegalStateException("이전 대화 실행 기록은 관리자 확인이 필요합니다.");
            if(!external.isEmpty()){
                boolean done=false;
                try(var s=db().prepareStatement("SELECT status FROM codex_dialogue_effect WHERE player=? AND effect_key=?")){s.setString(1,player.toString());s.setString(2,effectKey);try(var r=s.executeQuery()){if(r.next()){if(!r.getString(1).equals("done"))throw new IllegalStateException("이 선택지의 실행 기록은 관리자 확인이 필요합니다.");done=true;}}}
                if(done)external.clear();else{token=UUID.randomUUID().toString();try(var s=db().prepareStatement("INSERT INTO codex_dialogue_effect(player,effect_key,token,status,actions) VALUES(?,?,?,'pending',?)")){s.setString(1,player.toString());s.setString(2,effectKey);s.setString(3,token);s.setString(4,String.join("\n",external));s.executeUpdate();}}
            }
            db().commit();return new Transition(new State(before.revision+1,Map.copyOf(values)),token,List.copyOf(external));
        }catch(Exception e){holder.rollback();throw e;}finally{holder.end();}
    }
    void finish(UUID player,String token)throws SQLException{try(var s=db().prepareStatement("UPDATE codex_dialogue_effect SET status='done' WHERE player=? AND token=? AND status='pending'")){s.setString(1,player.toString());s.setString(2,token);if(s.executeUpdate()!=1)throw new SQLException("receipt changed");}}
    List<String> audit(UUID player)throws SQLException{var out=new ArrayList<String>();try(var s=db().prepareStatement("SELECT effect_key,token,actions FROM codex_dialogue_effect WHERE player=? AND status='pending'")){s.setString(1,player.toString());try(var r=s.executeQuery()){while(r.next())out.add(r.getString(1)+" | "+r.getString(2)+" | "+r.getString(3));}}return out;}
    private static boolean constraint(SQLException e){return e.getErrorCode()==19||e.getErrorCode()==1062||"23505".equals(e.getSQLState());}
    private static String encode(Map<String,String> map){return String.join("\n",new TreeMap<>(map).entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList());}
    private static Map<String,String> decode(String data){var out=new TreeMap<String,String>();data.lines().forEach(l->{int i=l.indexOf('=');if(i>0)out.put(l.substring(0,i),l.substring(i+1));});return Map.copyOf(out);}
    public void close()throws SQLException{holder.close();}
}
