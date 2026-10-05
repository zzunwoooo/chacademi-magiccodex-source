package school.magiccodex.paper;
import java.util.UUID;
import org.bukkit.Bukkit;
/** Optional server-side integration; no plugin dependency or client-trusted cast claims. */
final class DiscoveryLink {
    static boolean signal(UUID player,String key,double amount){
        var plugin=Bukkit.getPluginManager().getPlugin("MagicDiscovery");if(plugin==null||!plugin.isEnabled())return false;
        try{Class<?> type=Class.forName("school.magiccodex.discovery.DiscoveryService",false,plugin.getClass().getClassLoader());Object service=Bukkit.getServicesManager().load(type);return service!=null&&Boolean.TRUE.equals(type.getMethod("signal",UUID.class,String.class,double.class).invoke(service,player,key,amount));}
        catch(ReflectiveOperationException|RuntimeException e){Bukkit.getLogger().warning("MagicDiscovery friend event failed: "+e.getClass().getSimpleName());return false;}
    }
    private static Class<?> serviceClass;private static java.lang.reflect.Method cast;
    static void cast(UUID player,String spell,double spent){
        var plugin=Bukkit.getPluginManager().getPlugin("MagicDiscovery");if(plugin==null||!plugin.isEnabled())return;
        try{
            Class<?> type=Class.forName("school.magiccodex.discovery.DiscoveryService",false,plugin.getClass().getClassLoader());
            if(type!=serviceClass){serviceClass=type;cast=type.getMethod("cast",UUID.class,String.class,double.class);}
            Object service=Bukkit.getServicesManager().load(type);if(service!=null)cast.invoke(service,player,spell,spent);
        }catch(ReflectiveOperationException|RuntimeException e){Bukkit.getLogger().warning("MagicDiscovery cast event failed: "+e.getClass().getSimpleName());}
    }
}
