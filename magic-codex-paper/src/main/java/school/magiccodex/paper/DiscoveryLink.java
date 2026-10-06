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
    @SuppressWarnings("unchecked") static java.util.Map<String,String> registeredSpells(){
        var plugin=Bukkit.getPluginManager().getPlugin("MagicDiscovery");if(plugin==null||!plugin.isEnabled())return java.util.Map.of();
        try{Class<?> type=Class.forName("school.magiccodex.discovery.DiscoveryService",false,plugin.getClass().getClassLoader());Object service=Bukkit.getServicesManager().load(type);return service==null?java.util.Map.of():java.util.Map.copyOf((java.util.Map<String,String>)type.getMethod("registeredSpells").invoke(service));}
        catch(ReflectiveOperationException|RuntimeException e){return java.util.Map.of();}
    }
    static void adminSetLearned(UUID player,String spell,boolean learned,java.util.function.Consumer<String> done){
        var plugin=Bukkit.getPluginManager().getPlugin("MagicDiscovery");if(plugin==null||!plugin.isEnabled()){done.accept("마법 발견 플러그인이 준비되지 않았습니다.");return;}
        try{Class<?> type=Class.forName("school.magiccodex.discovery.DiscoveryService",false,plugin.getClass().getClassLoader());Object service=Bukkit.getServicesManager().load(type);if(service==null){done.accept("마법 발견 서비스가 준비되지 않았습니다.");return;}type.getMethod("adminSetLearned",UUID.class,String.class,boolean.class,java.util.function.Consumer.class).invoke(service,player,spell,learned,done);}
        catch(ReflectiveOperationException|RuntimeException e){Bukkit.getLogger().warning("MagicDiscovery admin update failed: "+e.getClass().getSimpleName());done.accept("마법 습득 상태를 저장하지 못했습니다. 발견 플러그인 버전을 확인해 주세요.");}
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
