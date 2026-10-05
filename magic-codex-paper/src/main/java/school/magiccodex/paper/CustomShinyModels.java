package school.magiccodex.paper;

import com.ticxo.modelengine.api.ModelEngineAPI;
import org.bukkit.entity.LivingEntity;

/** Custom creatures use a dedicated textured model, never a single-color tint. */
final class CustomShinyModels {
    static void apply(LivingEntity entity,boolean shiny){
        if(!entity.getScoreboardTags().contains("chacademia_custom"))return;
        var modeled=ModelEngineAPI.getModeledEntity(entity.getUniqueId());if(modeled==null)return;
        for(var entry:java.util.List.copyOf(modeled.getModels().entrySet())){
            String old=entry.getKey();
            String wanted=targetModelId(old,shiny);if(wanted==null||wanted.equals(old))continue;
            var replacement=ModelEngineAPI.createActiveModel(wanted);
            if(replacement==null)throw new IllegalStateException("Missing shiny model: "+wanted);
            replacement.setScale(entry.getValue().getScale().x());
            modeled.removeModel(old);
            try{modeled.addModel(replacement,true);entry.getValue().destroy();}
            catch(RuntimeException error){replacement.destroy();modeled.addModel(entry.getValue(),true);throw error;}
        }
    }
    static String targetModelId(String old,boolean shiny){
        String claudeNormal=switch(old){
            case "doxy","doxy_shiny" -> "doxy";
            case "fenrir_mother","fenrir_mother_shiny" -> "fenrir_mother";
            case "fenrir_pup","fenrir_pup_shiny" -> "fenrir_pup";
            case "goblin","goblin_shiny" -> "goblin";
            default -> null;
        };
        if(claudeNormal!=null)return claudeNormal+(shiny?"_shiny":"");
        if(!old.startsWith("ca_"))return null;
        String normal=old.endsWith("_s")?old.substring(0,old.length()-2):old;
        return normal+(shiny?"_s":"");
    }
    private CustomShinyModels(){}
}
