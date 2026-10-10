package school.magiccodex.paper;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

/** Cosmetic names only. Commands and relationships continue to use UUIDs. Main-thread access. */
final class DisplayNames {
    private final MagicCodexBridge plugin;
    private final NicknameBridge nicknames;
    private record Cached(String name,long until){}
    private final Map<UUID,Cached> cache=new HashMap<>();
    DisplayNames(MagicCodexBridge plugin){this.plugin=plugin;try{nicknames=new NicknameBridge(plugin,this);}catch(Exception e){throw new IllegalStateException("닉네임 저장소 초기화 실패",e);}}
    void invalidate(UUID id){cache.remove(id);}
    /** "magiccodex" 식별자를 가진 닉네임 확장이 마나 등 다른 키를 넘겨줄 대상. 닉네임 확장이 그 식별자를 갖지 못했으면 false. */
    boolean delegatePlaceholders(java.util.function.BiFunction<OfflinePlayer,String,String> delegate){return nicknames.delegatePlaceholders(delegate);}
    /** %..._nickname% / %..._account% 값 (다른 키는 null). */
    String placeholder(OfflinePlayer player,String params){return nicknames.answer(player,params);}
    String name(OfflinePlayer player,String fallback){
        String saved=nicknames.stored(player.getUniqueId());if(!saved.isEmpty())return saved;
        long now=System.currentTimeMillis();var old=cache.get(player.getUniqueId());if(old!=null&&old.until()>now)return old.name();
        String value="";
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))try{value=me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player,plugin.getConfig().getString("display-name-placeholder","%user_nickname%"));}catch(RuntimeException ignored){}
        value=clean(value);
        if(value.isBlank()||value.contains("%")||value.equalsIgnoreCase("<none>")||value.equalsIgnoreCase("null"))value=clean(fallback);
        if(value.isBlank())value="기록 없음";
        if(cache.size()>4096)cache.entrySet().removeIf(e->e.getValue().until()<now);
        cache.put(player.getUniqueId(),new Cached(value,now+2000));return value;
    }
    String name(Player player){return name(player,player.getName());}
    Player resolve(String input){
        try{Player p=Bukkit.getPlayer(UUID.fromString(input));if(p!=null)return p;}catch(IllegalArgumentException ignored){}
        Player p=Bukkit.getPlayerExact(input);if(p!=null)return p;
        Player match=null;for(Player candidate:Bukkit.getOnlinePlayers())if(name(candidate).equals(input)){if(match!=null)return null;match=candidate;}return match;
    }
    private static String clean(String s){
        if(s==null)return "";
        s=ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',s)).replaceAll("<[^>]*>","").replaceAll("[\\p{Cntrl}\\p{Cf}]","").strip();
        if(s.length()>16)s=s.substring(0,Character.isHighSurrogate(s.charAt(15))?15:16);
        return s;
    }
}
