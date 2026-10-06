package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.ManaProtocol;
import school.magiccodex.protocol.ManaProtocol.*;

final class ManaBridge implements Listener,PluginMessageListener,CommandExecutor,TabCompleter,AutoCloseable {
    private final MagicCodexBridge plugin;
    final ManaService mana;
    private ManaSpells spells;
    java.util.function.Consumer<Player> windCast;
    Response castWindForFriend(Player p){var result=cast(p,0,spells.byId.get("wind_message"),()->true);send(p,result);return result;}
    List<String> discoveryPermissions(){var result=new ArrayList<>(spells.discoveryPermissions);if(!result.contains("magiccodex.taming"))result.add("magiccodex.taming");return List.copyOf(result);}
    private ManaExpansion expansion;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Set<UUID> casting=new HashSet<>();
    private int ticks;
    private static class Session {
        long expires,nextSubscribe,lastSequence,window;int requests;Snapshot last;
        boolean allow(long now){if(now-window>=1000){window=now;requests=0;}return ++requests<=8;}
    }
    private static long now(){return System.nanoTime()/1_000_000;}
    ManaBridge(MagicCodexBridge plugin)throws Exception{
        this.plugin=plugin;mana=new ManaService(plugin);
        if(!new File(plugin.getDataFolder(),"mana-spells.yml").exists())plugin.saveResource("mana-spells.yml",false);
        spells=ManaSpells.load(new File(plugin.getDataFolder(),"mana-spells.yml"));
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin,ManaProtocol.REQUEST,this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin,ManaProtocol.RESPONSE);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        plugin.getServer().getServicesManager().register(ManaService.class,mana,plugin,ServicePriority.Normal);
        Objects.requireNonNull(plugin.getCommand("마나관리")).setExecutor(this);
        plugin.getCommand("마나관리").setTabCompleter(this);
        for(Player p:Bukkit.getOnlinePlayers())mana.join(p);
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new ManaExpansion(mana);expansion.register();}
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    private void tick(){
        mana.tick();long time=now();
        sessions.entrySet().removeIf(e->e.getValue().expires<time);
        for(var e:sessions.entrySet()){
            var p=Bukkit.getPlayer(e.getKey());if(p==null)continue;
            var s=mana.snapshot(e.getKey()).orElse(null);
            if(s!=null&&!s.equals(e.getValue().last))send(p,new Response(0,ManaProtocol.SNAPSHOT,0,s));
        }
        if(++ticks%60==0)mana.saveDirty();
    }
    @EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent e){mana.join(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){mana.quit(e.getPlayer());sessions.remove(e.getPlayer().getUniqueId());casting.remove(e.getPlayer().getUniqueId());}
    @Override public void onPluginMessageReceived(String channel,Player p,byte[] data){
        if(!channel.equals(ManaProtocol.REQUEST))return;
        final Request r;try{r=ManaProtocol.decodeRequest(data);}catch(IllegalArgumentException e){return;}
        Session session=sessions.computeIfAbsent(p.getUniqueId(),id->new Session());long time=now();
        if(!session.allow(time))return;
        if(r.sequence()==0){
            if(time<session.nextSubscribe)return;session.nextSubscribe=time+1000;session.expires=time+65000;
            send(p,new Response(0,ManaProtocol.SNAPSHOT,0,mana.account(p.getUniqueId()).snapshot()));
        }else{
            if(r.sequence()<=session.lastSequence)return;session.lastSequence=r.sequence();session.expires=time+65000;
            if(r.spell().equals("taming")){
                boolean okay=plugin.castTaming(p);var a=mana.account(p.getUniqueId());
                send(p,new Response(r.sequence(),okay?ManaProtocol.OK:ManaProtocol.FAILED,(int)Math.min(86400000,a.remaining("taming",System.currentTimeMillis())),a.snapshot()));
            }else send(p,cast(p,r.sequence(),spells.byId.get(r.spell())));
        }
    }
    private void send(Player p,Response response){
        if(!p.getListeningPluginChannels().contains(ManaProtocol.RESPONSE))return;
        p.sendPluginMessage(plugin,ManaProtocol.RESPONSE,ManaProtocol.encode(response));
        var session=sessions.get(p.getUniqueId());if(session!=null)session.last=response.mana();
    }
    private Response cast(Player p,long sequence,ManaSpells.Spell spell){
        return cast(p,sequence,spell,null);
    }
    Map<String,Map<String,String>> registeredSpellCatalog(){return spells.registeredCatalog;}
    boolean isCatalogMode(){return spells.catalogMode;}
    boolean requiresCatalogVisual(String id){return spells.catalogMode&&spells.byId.containsKey(id);}
    boolean castRegistered(Player player,String id){
        var response=cast(player,0,spells.byId.get(id));send(player,response);
        if(response.status()!=ManaProtocol.OK)player.sendMessage(message(response.status()));
        return response.status()==ManaProtocol.OK;
    }
    private Response cast(Player p,long sequence,ManaSpells.Spell spell,java.util.function.BooleanSupplier internal){
        UUID id=p.getUniqueId();var a=mana.account(id);
        if(!plugin.playerStateReady(p))return new Response(sequence,ManaProtocol.FAILED,0,a.snapshot());
        if(casting.contains(id))return new Response(sequence,ManaProtocol.FAILED,0,a.snapshot());
        casting.add(id);
        ManaCasting.Result result;
        try {
            boolean permitted=spell!=null&&!p.isDead()&&p.getGameMode()!=org.bukkit.GameMode.SPECTATOR&&p.hasPermission(spell.permission());
            result=ManaCasting.attempt(a,spell,permitted,System.currentTimeMillis(),()->{
                mana.publish(id);
                if(internal!=null)return internal.getAsBoolean();
                if(spell.id().equals("wind_message")&&windCast!=null){
                    if(!p.hasPermission("magiccodex.friends")||!p.getListeningPluginChannels().contains(school.magiccodex.protocol.SocialProtocol.RESPONSE))return false;
                    Boolean visual=CatalogVfxLink.cast(plugin,p,spell.id());
                    if(Boolean.FALSE.equals(visual))return false;
                    windCast.accept(p);return true;
                }
                Boolean handled=plugin.runtimeCast(p,spell.id());
                if(handled!=null)return handled;
                String label=spell.command().split("\\s+",2)[0];
                return Bukkit.getCommandMap().getCommand(label)!=null&&p.performCommand(spell.command());
            });
        }catch(RuntimeException error){
            plugin.getLogger().warning("마법 명령 실패: "+(spell==null?"unknown":spell.id())+" ("+error.getClass().getSimpleName()+")");
            result=new ManaCasting.Result(ManaProtocol.FAILED,0);
        }finally{casting.remove(id);}
        mana.publish(id);
        if(result.status()==ManaProtocol.OK){mana.save(p);plugin.savePlayerState(p);DiscoveryLink.cast(p.getUniqueId(),spell.id(),spell.cost());}
        return new Response(sequence,result.status(),result.cooldownMillis(),a.snapshot());
    }
    /** The same exact configured command typed into chat also goes through the mana guard. */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent e){
        String normalized=ManaSpells.normalize(e.getMessage());
        var spell=spells.byCommand.get(normalized);
        if(spell==null){
            // Do not let ignored trailing arguments turn a known paid spell into a free cast.
            boolean paidPrefix=spells.byCommand.keySet().stream().anyMatch(c->normalized.startsWith(c+" "));
            if(paidPrefix){e.setCancelled(true);e.getPlayer().sendMessage("마법 명령에 등록되지 않은 인수가 있습니다.");}
            return;
        }
        e.setCancelled(true);
        if(!sessions.computeIfAbsent(e.getPlayer().getUniqueId(),id->new Session()).allow(now()))return;
        var r=cast(e.getPlayer(),0,spell);send(e.getPlayer(),r);
        if(r.status()!=ManaProtocol.OK)e.getPlayer().sendMessage(message(r.status()));
    }
    static String message(int status){return switch(status){
        case ManaProtocol.EMPTY->"마나가 부족합니다.";case ManaProtocol.COOLDOWN->"아직 재사용 대기 중입니다.";
        case ManaProtocol.LOCKED->"이 마법을 사용할 수 없습니다.";case ManaProtocol.UNKNOWN->"서버에 등록되지 않았거나 비활성화된 마법입니다.";
        default->"마법 명령을 실행하지 못했습니다. 마나는 차감하지 않았습니다.";
    };}
    void bindSpellCommand(){
        Objects.requireNonNull(plugin.getCommand("마법")).setExecutor(this);
        plugin.getCommand("마법").setTabCompleter(this);
        if(isCatalogMode()){plugin.getCommand("마법관리").setExecutor(this);plugin.getCommand("마법관리").setTabCompleter(this);}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equals("마법")){
            if(!(sender instanceof Player player)){sender.sendMessage("게임 안에서 사용해 주세요.");return true;}
            if(args.length==0){sender.sendMessage("사용법: /마법 <마법명>");return true;}
            String id=spells.resolve(String.join(" ",args));
            if(id==null){sender.sendMessage("서버에 등록되지 않았거나 비활성화된 마법입니다.");return true;}
            castRegistered(player,id);return true;
        }
        if(!sender.hasPermission("magiccodex.mana.admin")){sender.sendMessage("권한이 없습니다.");return true;}
        try{
            if(args.length==1&&(args[0].equals("새로고침")||args[0].equalsIgnoreCase("reload"))){
                var next=ManaSpells.load(new File(plugin.getDataFolder(),"mana-spells.yml"));plugin.reloadConfig();spells=next;
                sender.sendMessage("마나 설정과 마법 비용 "+spells.byId.size()+"종을 다시 불러왔습니다. 기존 플레이어 수치는 유지합니다.");return true;
            }
            sender.sendMessage("/마나관리 새로고침 · 유저 수치는 /유저관리 <유저> 스탯 <항목> 설정|추가|감소 <수치>로 관리합니다.");
        }catch(Exception error){sender.sendMessage("마나 설정 오류: "+error.getMessage());}return true;
    }
    private static String number(double v){return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();}
    public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){
        if(c.getName().equals("마법")){
            String prefix=String.join("",args).toLowerCase(Locale.ROOT);
            return spells.byId.values().stream().filter(x->s.hasPermission(x.permission())).map(ManaSpells.Spell::id).filter(x->x.startsWith(prefix)).sorted().toList();
        }
        if(!s.hasPermission("magiccodex.mana.admin"))return List.of();
        var options=args.length==1?List.of("새로고침"):List.<String>of();
        return options.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
    }
    public void close(){if(expansion!=null)expansion.unregister();mana.close();Bukkit.getServicesManager().unregister(ManaService.class,mana);sessions.clear();}
}
