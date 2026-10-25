package school.chacademia.magicmythic;
import org.mockito.Mockito;import static org.mockito.Mockito.*;import io.lumine.mythic.api.config.MythicLineConfig;import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
public class RegistrationProbe { public static void main(String[]a)throws Exception {
 var plugin=Mockito.mock(MagicMythicPlugin.class,Mockito.CALLS_REAL_METHODS);
 String[] names="chaally,chaarcana,chabolt,chabuff,chachannel,chafarm,chagimmick,chaguard,chahit,chamark,chameteor,chamobshot,chanote,chaselfpotion,chaselfvelocity,chastatus,chasummon,chatelegraph,chautil,chaworld,chazone".split(",");
 names=new String[]{"chameteor"}; for(String name:names){var ev=mock(MythicMechanicLoadEvent.class);var config=mock(MythicLineConfig.class);when(ev.getMechanicName()).thenReturn(name);when(ev.getConfig()).thenReturn(config);plugin.mechanic(ev);verify(ev,times(1)).register(any());System.out.println("REGISTER_PASS "+name); }
 System.out.println("PASS REAL_METEOR_REGISTERED");
 }}