package school.magiccodex.paper;
public final class MagicCodexBridge extends org.bukkit.plugin.java.JavaPlugin {
 private TravelBridge travel;
 TitleBridge titleService(){return null;}
 private void opening$startReturnTicket(){if(travel!=null)try{new ReturnTicketBridge(this,travel);}catch(Exception error){getLogger().severe("귀환권 초기화 실패 (return-ticket.yml 확인): "+error.getMessage());}}
}
