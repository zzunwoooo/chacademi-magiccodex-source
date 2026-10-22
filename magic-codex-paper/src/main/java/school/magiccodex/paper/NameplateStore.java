package school.magiccodex.paper;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import school.magiccodex.protocol.NameplateProtocol;
final class NameplateStore {
 record Label(String name,String title){Label{NameplateProtocol.text(name,48,false);NameplateProtocol.text(title,24,true);}}
 static Map<String,Label> load(Path path){
  var result=new HashMap<String,Label>();var config=new YamlConfiguration();if(Files.exists(path))try{config.load(path.toFile());}catch(Exception ex){throw new IllegalArgumentException("Could not load nameplate storage",ex);}var section=config.getConfigurationSection("labels");
  if(section!=null)for(String key:section.getKeys(false))result.put(key,new Label(section.getString(key+".name",""),section.getString(key+".title","")));
  return Map.copyOf(result);
 }
 static void save(Path path,Map<String,Label> data)throws Exception{
  var y=new YamlConfiguration();for(var e:data.entrySet()){if(!e.getKey().matches("[A-Za-z0-9-]+"))throw new IllegalArgumentException("unsafe identity");y.set("labels."+e.getKey()+".name",e.getValue().name);y.set("labels."+e.getKey()+".title",e.getValue().title);}
  Path tmp=path.resolveSibling(path.getFileName()+".tmp");Files.createDirectories(path.getParent());Files.writeString(tmp,y.saveToString());Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
 }
}
