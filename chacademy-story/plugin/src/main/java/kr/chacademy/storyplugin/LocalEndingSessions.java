package kr.chacademy.storyplugin;

import java.util.*;

/** Server-issued, player-bound sessions. No commands or target identities arrive from the client. */
final class LocalEndingSessions {
    static final long TTL=3_600_000;
    record Session(String id,String token,long expires,Map<String,List<String>> endings,List<String> end,boolean ready) {}
    private final Map<UUID,Session> sessions=new HashMap<>();
    Session current(UUID player,long now){var s=sessions.get(player);if(s!=null&&now>=s.expires()){sessions.remove(player);return null;}return s;}
    Session begin(UUID player,StoryDef def,long now){
        if(def==null||!StorySafety.validId(def.id)||def.endings.isEmpty())return null;
        var old=current(player,now);if(old!=null)return old.id().equals(def.id)?old:null;
        var s=new Session(def.id,UUID.randomUUID().toString(),now+TTL,Map.copyOf(def.endings),List.copyOf(def.end),false);
        sessions.put(player,s);return s;
    }
    private Session match(UUID player,String id,String token,long now){var s=current(player,now);return s!=null&&s.id().equals(id)&&s.token().equals(token)?s:null;}
    boolean ready(UUID player,String id,String token,long now){var s=match(player,id,token,now);if(s==null)return false;sessions.put(player,new Session(s.id(),s.token(),s.expires(),s.endings(),s.end(),true));return true;}
    List<String> finish(UUID player,String id,String token,String ending,long now){
        var s=match(player,id,token,now);
        if(s==null||!s.ready()||!StorySafety.validId(ending)||!s.endings().containsKey(ending))return List.of();
        sessions.remove(player); // Consume before dispatch, including reentrant commands and duplicate packets.
        var commands=new ArrayList<>(s.endings().get(ending));commands.addAll(s.end());return List.copyOf(commands);
    }
    boolean abort(UUID player,String id,String token,long now){if(match(player,id,token,now)==null)return false;sessions.remove(player);return true;}
    void cancel(UUID player){sessions.remove(player);}
    void clear(){sessions.clear();}
}
