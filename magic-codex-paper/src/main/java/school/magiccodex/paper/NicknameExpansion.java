package school.magiccodex.paper;

import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/** Returns plain display data; never resolves permissions or changes the account name. */
final class NicknameExpansion extends PlaceholderExpansion {
    private final NicknameBridge bridge;private final String identifier,version;
    NicknameExpansion(NicknameBridge bridge,String identifier,String version){this.bridge=bridge;this.identifier=identifier;this.version=version;}
    public String getIdentifier(){return identifier;}
    public String getAuthor(){return "Chacademia";}
    public String getVersion(){return version;}
    public boolean persist(){return true;}
    public String onRequest(OfflinePlayer player,String params){return player==null?"":params.equals("nickname")?bridge.placeholder(player):params.equals("account")?(player.getName()==null?"":player.getName()):null;}
}
