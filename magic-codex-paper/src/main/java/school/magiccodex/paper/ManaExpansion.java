package school.magiccodex.paper;

import java.math.BigDecimal;
import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

final class ManaExpansion extends PlaceholderExpansion {
    private final ManaService mana;
    ManaExpansion(ManaService mana){this.mana=mana;}
    public String getIdentifier(){return "magiccodex";}
    public String getAuthor(){return "Chacademia";}
    public String getVersion(){return "0.6.0";}
    public boolean persist(){return true;}
    public String onRequest(OfflinePlayer player,String params){
        if(player==null)return "";
        var s=mana.snapshot(player.getUniqueId()).orElse(null);if(s==null)return "";
        return switch(params){
            case "mana_current"->number(s.current());case "mana_max"->number(s.maximum());
            case "mana_regen"->number(s.regeneration());
            case "magic_haste"->number(s.haste());
            case "cooldown_reduction"->number(school.magiccodex.protocol.MagicHaste.reduction(s.haste()));
            case "mana_percent"->number(s.maximum()==0?0:s.current()*100/s.maximum());default->null;
        };
    }
    static String number(double value){return BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString();}
}
