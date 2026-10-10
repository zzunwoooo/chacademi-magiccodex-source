package dev.portablevfx.paper;

import dev.portablevfx.paper.api.EffectRequest;
import dev.portablevfx.paper.api.PlayResult;
import dev.portablevfx.paper.api.PortableVfxService;
import dev.portablevfx.paper.api.VfxStatus;
import dev.portablevfx.paper.internal.RelayEngine;
import dev.portablevfx.paper.internal.RelayLimits;
import dev.portablevfx.protocol.EffectBasis;
import dev.portablevfx.protocol.PlayEffect;
import dev.portablevfx.protocol.VfxProtocol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

/** Paper relay only: no asset loading, entity effects, combat, or gameplay integration. */
public final class PortableVfxPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private RelayEngine engine;
    private BukkitTask ticker;
    private PortableVfxService service;
    private dev.portablevfx.paper.internal.spell.SpellCatalog spellCatalog;
    private dev.portablevfx.paper.internal.spell.CatalogVisualEngine catalogVisuals;
    private long lastTransportWarningTick = Long.MIN_VALUE;
    private boolean bindingDriftChecked;
    /** effect 채널을 등록한 플레이어. 등록/해제 이벤트로 유지하고 주기적으로 대조한다(패킷마다 채널 목록을 복사하지 않는다). */
    private final java.util.Set<UUID> effectChannel = new java.util.HashSet<>();
    /** 월드 UID -> namespaced key 문자열(스냅샷마다 문자열을 새로 만들지 않는다). */
    private final java.util.Map<UUID, String> worldKeys = new java.util.HashMap<>();
    private int housekeepingTick;
    /** 시전 거부 집계: 사유별 횟수와 표본 1건을 모아 REFUSAL_LOG_INTERVAL_MS 마다 한 줄로 남긴다. */
    private static final long REFUSAL_LOG_INTERVAL_MS = 10_000L;
    private static final int CHANNEL_RESYNC_TICKS = 100, MAX_REFUSAL_REASONS = 32;
    private final java.util.Map<String, int[]> refusals = new java.util.LinkedHashMap<>();
    private int refusalTotal;
    private UUID refusalPlayer;
    private String refusalPlayerName = "", refusalSpell = "", refusalReason = "";
    private RuntimeException refusalStack;
    private long lastRefusalLog, reportedTickFailures, reportedPhaseFailures, tickerFailures, reportedTickerFailures;
    private String tickerFailureSample = "";
    private Object reportedVisuals;

    /** 카탈로그 (재)로딩 결과. applied=false 면 이전 카탈로그가 그대로다. */
    private record CatalogReload(boolean applied, int spells, int bindings, java.util.Map<String, String> skipped, String error) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        engine = new RelayEngine(new BukkitTransport(), readLimits());
        getServer().getMessenger().registerIncomingPluginChannel(this, VfxProtocol.HELLO_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, VfxProtocol.ORIENTATION_HELLO_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, VfxProtocol.EXTENDED_PLAY_HELLO_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, VfxProtocol.AUTHORITATIVE_HELLO_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, VfxProtocol.WIDTH_PLAY_HELLO_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, VfxProtocol.STOP_INTENT_HELLO_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, dev.portablevfx.protocol.CatalogReadiness.CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, VfxProtocol.EFFECT_CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        // 플러그인 재활성화: 이미 접속한 플레이어의 채널 등록 상태를 한 번 읽어 둔다.
        effectChannel.clear();
        for (Player player : getServer().getOnlinePlayers()) syncEffectChannel(player);
        service = new ServerService();
        reloadSpellCatalog(true, engine.limits().maxDurationTicks());
        getServer().getServicesManager().register(PortableVfxService.class, service, this, ServicePriority.Normal);
        PluginCommand command = Objects.requireNonNull(getCommand("pvfxserverdebug"), "pvfxserverdebug missing from plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        ticker = getServer().getScheduler().runTaskTimer(this, this::tickRuntime, 1L, 1L);
        // After all channels are registered: ask already-connected clients to hello again.
        getServer().getScheduler().runTask(this, this::requestClientHellos);
        getLogger().info("PortableVFX relay enabled; protocol " + VfxProtocol.VERSION + ", no gameplay hooks.");
    }

    /** tick 작업 하나가 실패해도 다음 작업과 다음 tick 은 계속 돈다. 실패는 집계 로그로만 남긴다. */
    private void tickRuntime() {
        try { engine.tick(); } catch (RuntimeException failure) { tickerFailed("relay", failure); }
        try { if (catalogVisuals != null) catalogVisuals.tick(); } catch (RuntimeException failure) { tickerFailed("catalog", failure); }
        if (++housekeepingTick >= CHANNEL_RESYNC_TICKS) { housekeepingTick = 0; resyncEffectChannels(); }
        flushRefusalLog();
    }

    private void tickerFailed(String where, RuntimeException failure) {
        tickerFailures++;
        tickerFailureSample = where + ": " + failure;
        if (refusalStack == null) refusalStack = failure;
    }

    /**
     * Called only by the existing MagicCodex mana transaction; this method never charges mana.
     * null: 이 카탈로그의 마법이 아님. false: 연출을 시작하지 못함(호출자가 마나를 환불하고 시전을 실패 처리한다).
     * 거부는 건별 로그 대신 사유별로 집계해 약 10초에 한 줄만 남긴다.
     */
    public Boolean castAuthorizedSpell(Player player,String id) {
        if(spellCatalog==null)return null;
        var spell=spellCatalog.resolve(id).orElse(null);
        if(spell==null)return null;
        if(catalogVisuals==null)return refuse(player,id,"visual engine unavailable",null);
        if(!spell.enabled())return refuse(player,id,"spell disabled in catalogue",null);
        if(!engine.supportsCatalogCast(player.getUniqueId())){
            player.sendActionBar(net.kyori.adventure.text.Component.text("마법 이펙트가 아직 준비되지 않았습니다. 로딩 완료 후 다시 시도하세요."));
            return refuse(player,id,"client not ready",null);
        }
        Boolean result;
        try{result=catalogVisuals.cast(player,id);}
        catch(RuntimeException failure){return refuse(player,id,"dispatch exception "+failure.getClass().getSimpleName()+": "+failure.getMessage(),failure);}
        if(Boolean.TRUE.equals(result))return Boolean.TRUE;
        return refuse(player,id,result==null?"no visual plan":catalogVisuals.lastFailure(player.getUniqueId()),null);
    }

    private Boolean refuse(Player player,String spellId,String reason,RuntimeException failure) {
        // 사유 키: 괄호 안 숫자와 예외 메시지를 떼어 종류 수를 작게 유지한다.
        String key=reason==null||reason.isBlank()?"unknown":reason;
        int cut=key.indexOf(':');if(cut>0)key=key.substring(0,cut);
        cut=key.indexOf(" (");if(cut>0)key=key.substring(0,cut);
        if(!refusals.containsKey(key)&&refusals.size()>=MAX_REFUSAL_REASONS)key="other";
        refusals.computeIfAbsent(key,ignored->new int[1])[0]++;
        refusalTotal++;
        refusalPlayer=player.getUniqueId();refusalPlayerName=player.getName();refusalSpell=spellId;refusalReason=String.valueOf(reason);
        if(failure!=null&&refusalStack==null)refusalStack=failure;
        flushRefusalLog();
        return Boolean.FALSE;
    }

    /** 약 10초에 최대 한 번: 사유별 횟수 + 표본 1건(진단 문자열은 이때만 계산) + tick 경로 실패 수. */
    private void flushRefusalLog() {
        var visuals=catalogVisuals;
        if(visuals!=reportedVisuals){reportedVisuals=visuals;reportedTickFailures=0;reportedPhaseFailures=0;}
        long tickFailed=visuals==null?0:visuals.tickFailures()-reportedTickFailures;
        long phaseFailed=visuals==null?0:visuals.phaseFailures()-reportedPhaseFailures;
        long tickerFailed=tickerFailures-reportedTickerFailures;
        if(refusalTotal==0&&tickFailed<=0&&phaseFailed<=0&&tickerFailed<=0)return;
        long now=System.currentTimeMillis();
        if(now-lastRefusalLog<REFUSAL_LOG_INTERVAL_MS&&now>=lastRefusalLog)return;
        long seconds=lastRefusalLog==0?0:Math.min(3600,(now-lastRefusalLog)/1000);
        lastRefusalLog=now;
        StringBuilder line=new StringBuilder("Catalog VFX");
        if(seconds>0)line.append(" (since last report ").append(seconds).append("s ago)");
        line.append(':');
        if(refusalTotal>0){
            line.append(" refused casts=").append(refusalTotal).append(" {");
            boolean first=true;
            for(var entry:refusals.entrySet()){if(!first)line.append(", ");first=false;line.append(entry.getKey()).append('=').append(entry.getValue()[0]);}
            line.append("}; sample: player=").append(refusalPlayerName).append(", spell=").append(refusalSpell).append(", reason=").append(refusalReason);
            if(refusalPlayer!=null)line.append(", client=[").append(engine.catalogDiagnostic(refusalPlayer)).append(']');
            line.append(';');
        }
        if(tickFailed>0||phaseFailed>0)line.append(" running casts cancelled by errors=").append(Math.max(0,tickFailed)).append(", skipped phases=").append(Math.max(0,phaseFailed))
                .append(", sample: ").append(visuals.lastTickFailure()).append(';');
        if(tickerFailed>0)line.append(" tick task errors=").append(tickerFailed).append(", sample: ").append(tickerFailureSample).append(';');
        if(refusalStack!=null)getLogger().log(java.util.logging.Level.WARNING,line.toString(),refusalStack);
        else getLogger().warning(line.toString());
        refusals.clear();refusalTotal=0;refusalStack=null;refusalPlayer=null;
        if(visuals!=null){reportedTickFailures=visuals.tickFailures();reportedPhaseFailures=visuals.phaseFailures();}
        reportedTickerFailures=tickerFailures;
    }

    /** Optional server gameplay adapters feed confirmed events; no client event channel exists. */
    public int emitSpellVisualEvent(Player player,String id,String event,Location point,UUID target) {
        return catalogVisuals==null?0:catalogVisuals.trigger(player.getUniqueId(),id,event,point,target);
    }

    /**
     * Re-handshake after a plugin re-enable (PlugMan, /reload). The relay's hello/capability/readiness
     * state lives in this plugin instance and is gone, but connected clients already sent their hellos.
     * CraftBukkit/Paper only sends minecraft:register (CraftPlayer#sendSupportedChannels) once while the
     * player joins; registering incoming channels later does not notify online players. Re-advertising
     * the channel list makes Fabric fire C2SPlayChannelEvents.REGISTER(portablevfx:hello), on which the
     * PortableVFX client re-sends every hello plus catalog_ready. Duplicate hellos are idempotent here.
     * If the non-API method is unavailable, clients still re-announce periodically on their own.
     */
    private void requestClientHellos() {
        java.lang.reflect.Method advertise = null;
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                if (advertise == null) advertise = player.getClass().getMethod("sendSupportedChannels");
                advertise.invoke(player);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                getLogger().warning("Could not re-advertise PortableVFX channels (" + failure
                        + "); connected clients will re-announce on their periodic hello instead.");
                return;
            }
        }
    }

    /** Startup-only warning: never overwrites an operator-edited spell-bindings.yml. */
    private void warnBindingDrift(org.bukkit.configuration.file.YamlConfiguration deployed) {
        try (var in = getResource("spell-bindings.yml")) {
            if (in == null) return;
            var bundled = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            var drift = dev.portablevfx.paper.internal.spell.BindingDrift.differing(
                    plainSection(deployed.getConfigurationSection("bindings")), plainSection(bundled.getConfigurationSection("bindings")));
            if (!drift.isEmpty()) getLogger().warning("plugins/" + getDataFolder().getName() + "/spell-bindings.yml differs from the bundled default for "
                    + drift.size() + " spell(s): " + drift + ". The file was NOT overwritten; back it up and merge the bundled values (README.ko.md).");
        } catch (Exception error) {
            getLogger().warning("Could not compare spell-bindings.yml with the bundled default: " + error.getMessage());
        }
    }

    private static java.util.Map<String, Object> plainSection(org.bukkit.configuration.ConfigurationSection section) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (section != null) for (String key : section.getKeys(false)) out.put(key, plainValue(section.get(key)));
        return out;
    }

    private static Object plainValue(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) return plainSection(section);
        if (value instanceof List<?> list) return list.stream().map(PortableVfxPlugin::plainValue).toList();
        if (value instanceof java.util.Map<?, ?> map) {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            map.forEach((key, item) -> out.put(String.valueOf(key), plainValue(item)));
            return out;
        }
        return value;
    }

    /**
     * 마법별로 검증해 잘못된 마법만 건너뛴다(ID 와 사유를 로그에 남긴다). 파일을 읽을 수 없거나 구조가 잘못되면
     * 이전 카탈로그와 실행 중인 연출을 그대로 두고 applied=false 를 돌려준다.
     */
    private CatalogReload reloadSpellCatalog(boolean startup, int maxDuration) {
        java.util.Map<String,String> skipped=new java.util.LinkedHashMap<>();
        try {
            for(String name:List.of("spell-catalog.yml","spell-bindings.yml"))if(!new java.io.File(getDataFolder(),name).exists())saveResource(name,false);
            var catalogYaml=new org.bukkit.configuration.file.YamlConfiguration();catalogYaml.load(new java.io.File(getDataFolder(),"spell-catalog.yml"));
            var bindingYaml=new org.bukkit.configuration.file.YamlConfiguration();bindingYaml.load(new java.io.File(getDataFolder(),"spell-bindings.yml"));
            if(!bindingDriftChecked){bindingDriftChecked=true;warnBindingDrift(bindingYaml);}
            java.util.Map<String,String> catalogProblems=new java.util.LinkedHashMap<>(),bindingProblems=new java.util.LinkedHashMap<>();
            var next=dev.portablevfx.paper.internal.spell.SpellCatalogYaml.readLenient(catalogYaml,catalogProblems);
            if(next.entries().isEmpty()&&!catalogProblems.isEmpty())throw new IllegalArgumentException("every spell in spell-catalog.yml is invalid, e.g. "+catalogProblems.entrySet().iterator().next());
            var section=bindingYaml.getConfigurationSection("bindings");
            java.util.Map<String,dev.portablevfx.paper.internal.spell.CatalogVisualEngine.Plan> bindings=new java.util.LinkedHashMap<>();
            if(section!=null)bindings.putAll(dev.portablevfx.paper.internal.spell.CatalogVisualEngine.readLenient(section,bindingProblems));
            for(var ids=bindings.keySet().iterator();ids.hasNext();){
                String id=ids.next();
                if(next.resolve(id).isEmpty()){ids.remove();bindingProblems.put(id,"binding has no (valid) catalogue ID");}
            }
            catalogProblems.forEach((id,reason)->skipped.put("spell-catalog.yml/"+id,reason));
            bindingProblems.forEach((id,reason)->skipped.put("spell-bindings.yml/"+id,reason));
            int logged=0;
            for(var problem:skipped.entrySet()){
                if(logged++>=50){getLogger().warning("... and "+(skipped.size()-50)+" more skipped spell(s)");break;}
                getLogger().warning("Skipped broken spell "+problem.getKey()+": "+problem.getValue());
            }
            for(var plan:bindings.values()){
                int longest=plan.phases().stream().mapToInt(phase->phase.ttl()).max().orElse(0);
                if(longest>maxDuration)getLogger().warning("Spell "+plan.id()+" has a phase of "+longest+" ticks, longer than limits.max-duration-ticks="+maxDuration
                        +"; that phase will be refused when cast. Raise the limit or shorten duration-ticks.");
            }
            var visuals=new dev.portablevfx.paper.internal.spell.CatalogVisualEngine(service,bindings,readCastLimits());
            if(catalogVisuals!=null)catalogVisuals.clear();spellCatalog=next;catalogVisuals=visuals;
            engine.catalogRequirements(bindings.values().stream().flatMap(p->p.phases().stream()).map(p->p.effect()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
            getLogger().info("Spell catalogue loaded: "+next.entries().size()+" spell(s), "+bindings.size()+" visual binding(s), "+skipped.size()+" skipped.");
            return new CatalogReload(true,next.entries().size(),bindings.size(),skipped,"");
        } catch(Exception error) {
            String reason=error.getClass().getSimpleName()+": "+error.getMessage();
            if(startup||spellCatalog==null)getLogger().severe("Spell catalogue could NOT be loaded ("+reason+"). No catalogue is active: every catalog cast is refused until spell-catalog.yml / spell-bindings.yml are fixed and /pvfxserverdebug reload succeeds.");
            else getLogger().warning("Spell catalog reload rejected; retaining previous catalog and running effects: "+reason);
            return new CatalogReload(false,spellCatalog==null?0:spellCatalog.entries().size(),0,skipped,reason);
        }
    }

    /**
     * Bukkit 은 onDisable 전에 isEnabled()=false 로 만든다. 그래서 공개 서비스 경로(requireMainThread)를 쓰지 않고,
     * 단계마다 예외를 격리한다: 가상 엔티티 제거 -> 릴레이 상태 정리 -> 등록 해제. 비활성 상태에서는 플러그인
     * 메시지를 보낼 수 없으므로 남은 클라이언트 효과는 각자의 TTL/월드 변경으로 끝난다.
     */
    @Override
    public void onDisable() {
        try { if (ticker != null) ticker.cancel(); } catch (RuntimeException failure) { disableFailed("ticker", failure); }
        try {
            if (catalogVisuals != null) {
                int failed = catalogVisuals.shutdown();
                if (failed > 0) getLogger().warning(failed + " virtual anchor entity(ies) could not be removed; they are non-persistent and vanish on chunk unload/restart.");
            }
        } catch (RuntimeException failure) { disableFailed("catalog visuals", failure); }
        try { if (engine != null) engine.shutdown(); } catch (RuntimeException failure) { disableFailed("relay engine", failure); }
        try { getServer().getServicesManager().unregisterAll(this); } catch (RuntimeException failure) { disableFailed("services", failure); }
        try {
            getServer().getMessenger().unregisterIncomingPluginChannel(this);
            getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        } catch (RuntimeException failure) { disableFailed("channels", failure); }
        try { HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this); } catch (RuntimeException failure) { disableFailed("listeners", failure); }
        effectChannel.clear();
        worldKeys.clear();
        catalogVisuals = null;
        service = null;
    }

    private void disableFailed(String step, RuntimeException failure) {
        getLogger().log(java.util.logging.Level.WARNING, "PortableVFX disable step failed: " + step, failure);
    }

    private void syncEffectChannel(Player player) {
        UUID id = player.getUniqueId();
        boolean registered = player.getListeningPluginChannels().contains(VfxProtocol.EFFECT_CHANNEL);
        if (registered ? effectChannel.add(id) : effectChannel.remove(id)) engine.viewerChanged(id);
    }

    /** 이벤트 순서/누락에 대비한 저빈도 대조(플레이어당 CHANNEL_RESYNC_TICKS 마다 한 번). */
    private void resyncEffectChannels() {
        java.util.Set<UUID> online = new java.util.HashSet<>();
        for (Player player : getServer().getOnlinePlayers()) { online.add(player.getUniqueId()); syncEffectChannel(player); }
        effectChannel.retainAll(online);
    }

    private String worldKey(World world) {
        return worldKeys.computeIfAbsent(world.getUID(), ignored -> world.getKey().toString());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!Bukkit.isPrimaryThread()) return;
        if (dev.portablevfx.protocol.CatalogReadiness.CHANNEL.equals(channel)) {engine.catalogReady(player.getUniqueId(),message);return;}
        if (VfxProtocol.STOP_INTENT_HELLO_CHANNEL.equals(channel)) { engine.stopIntentHello(player.getUniqueId(), message); return; }
        if (VfxProtocol.WIDTH_PLAY_HELLO_CHANNEL.equals(channel)) { engine.widthPlayHello(player.getUniqueId(), message); return; }
        if (VfxProtocol.AUTHORITATIVE_HELLO_CHANNEL.equals(channel)) { engine.authoritativeHello(player.getUniqueId(), message); return; }
        if (VfxProtocol.EXTENDED_PLAY_HELLO_CHANNEL.equals(channel)) { engine.extendedPlayHello(player.getUniqueId(), message); return; }
        if (VfxProtocol.ORIENTATION_HELLO_CHANNEL.equals(channel)) { engine.orientationHello(player.getUniqueId(), message); return; }
        if (!VfxProtocol.HELLO_CHANNEL.equals(channel)) return;
        // Only a bounded 4-byte protocol announcement is accepted from the client.
        // Clients never submit play commands or effect payloads to the server.
        engine.hello(player.getUniqueId(), message);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // 채널 등록이 접속 이벤트보다 먼저 끝난 경우를 대비해 한 번 읽는다. 이후는 등록/해제 이벤트가 유지한다.
        syncEffectChannel(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegister(PlayerRegisterChannelEvent event) {
        if (VfxProtocol.EFFECT_CHANNEL.equals(event.getChannel()) && effectChannel.add(event.getPlayer().getUniqueId()))
            engine.viewerChanged(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if(catalogVisuals!=null)catalogVisuals.removePlayer(event.getPlayer().getUniqueId());
        effectChannel.remove(event.getPlayer().getUniqueId());
        engine.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if(catalogVisuals!=null)catalogVisuals.removePlayer(event.getPlayer().getUniqueId());
        engine.worldChanged(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnregister(PlayerUnregisterChannelEvent event) {
        if (VfxProtocol.EFFECT_CHANNEL.equals(event.getChannel())) {
            effectChannel.remove(event.getPlayer().getUniqueId());
            if(catalogVisuals!=null)catalogVisuals.removePlayer(event.getPlayer().getUniqueId());
            engine.forget(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        if(catalogVisuals!=null)catalogVisuals.removeWorld(event.getWorld().getUID());
        engine.worldUnloaded(worldKey(event.getWorld()));
        for (Player player : event.getWorld().getPlayers()) engine.worldChanged(player.getUniqueId());
        worldKeys.remove(event.getWorld().getUID());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("portablevfx.admin")) {
            sender.sendMessage("[PortableVFX] portablevfx.admin 권한이 필요합니다.");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "play" -> playCommand(sender, args);
                case "follow" -> followCommand(sender, args);
                case "catalog" -> {
                    if(!(sender instanceof Player player))throw new IllegalArgumentException("catalog requires a player");
                    expectArgs(args,2,"catalog <spellId>");sender.sendMessage("[PortableVFX debug] catalog="+castAuthorizedSpell(player,args[1]));
                }
                case "event" -> {
                    if(!(sender instanceof Player player))throw new IllegalArgumentException("event requires a player");
                    expectArgs(args,3,"event <spellId> <eventName>");sender.sendMessage("[PortableVFX debug] preview="+(catalogVisuals==null?false:catalogVisuals.previewEvent(player,args[1],args[2])));
                }
                case "finish" -> {
                    expectArgs(args,2,"finish <handle>");sender.sendMessage("[PortableVFX debug] finish="+service.finishCast(UUID.fromString(args[1])));
                }
                case "stop" -> {
                    expectArgs(args, 2, "stop <uuid>");
                    UUID handle = UUID.fromString(args[1]);
                    boolean stopped = service.stop(handle);
                    sender.sendMessage(stopped
                            ? "[PortableVFX] 중지 요청: " + handle
                            : "[PortableVFX] 활성 핸들이 없습니다: " + handle);
                }
                case "clear" -> {
                    expectArgs(args, 1, "clear");
                    if(catalogVisuals!=null)catalogVisuals.clear();
                    sender.sendMessage("[PortableVFX] " + service.clear() + "개 핸들을 정리했습니다.");
                }
                case "status" -> {
                    expectArgs(args, 1, "status");
                    VfxStatus status = service.status();
                    sender.sendMessage("[PortableVFX] protocol=" + status.protocolVersion()
                            + ", 호환=" + status.compatibleClients() + ", 수신 가능=" + status.receivingClients()
                            + ", 핸들=" + status.activeHandles() + ", 제어 대기=" + status.pendingControlRecipients());
                    sender.sendMessage("[PortableVFX] 이번 tick 전송=" + status.packetsThisTick()
                            + ", play=" + status.playsThisTick() + ", 누적 전송=" + status.packetsSent()
                            + ", 잘못된 hello=" + status.rejectedHellos());
                    sender.sendMessage("[PortableVFX] authority 호환=" + engine.authoritativeClients()
                            + ", width 호환=" + engine.widthPlayClients() + ", 카탈로그=" + (spellCatalog==null?0:spellCatalog.entries().size()) + " (리소스 존재/렌더링 확인 아님)");
                    sender.sendMessage("[PortableVFX] 종료 추적 핸들=" + engine.drainingHandles() + ", PLAY 재시도 대기=" + engine.deferredPlayViewers()
                            + "명(기한 초과 누적 " + engine.deferredPlayDrops() + "), hello 제한=" + engine.throttledHellos() + ", 중복 readiness=" + engine.duplicateReadyPackets());
                    if(catalogVisuals!=null)sender.sendMessage("[PortableVFX] 진행 중 시전=" + catalogVisuals.activeCasts() + "/" + catalogVisuals.limits().maxCasts()
                            + " (플레이어당 " + catalogVisuals.limits().maxPerPlayer() + "), 오류로 취소=" + catalogVisuals.tickFailures()
                            + ", 건너뛴 단계=" + catalogVisuals.phaseFailures() + ", 시청자 없던 단계=" + catalogVisuals.noViewerPhases());
                    if(sender instanceof Player player)sender.sendMessage("[VFX 진단] "+engine.catalogDiagnostic(player.getUniqueId()));
                    if(catalogVisuals!=null){
                        if(sender instanceof Player player&&!catalogVisuals.lastFailure(player.getUniqueId()).isEmpty())sender.sendMessage("[VFX 진단] 내 최근 시전 거부="+catalogVisuals.lastFailure(player.getUniqueId()));
                        if(!catalogVisuals.lastFailure().isEmpty())sender.sendMessage("[VFX 진단] 서버 전체 최근 시전 거부(다른 플레이어일 수 있음)="+catalogVisuals.lastFailure());
                        if(!catalogVisuals.lastTickFailure().isEmpty())sender.sendMessage("[VFX 진단] 최근 실행 중 오류="+catalogVisuals.lastTickFailure());
                    }
                }
                case "reload" -> {
                    expectArgs(args, 1, "reload");
                    requireMainThread();
                    reloadConfig();
                    RelayLimits limits = readLimits();
                    CatalogReload outcome = reloadSpellCatalog(false, limits.maxDurationTicks());
                    if (outcome.applied()) {
                        // 새 카탈로그가 적용됐다: 이전 연출은 reloadSpellCatalog 가 정리했고, 릴레이 핸들도 비운다.
                        engine.reload(limits);
                        sender.sendMessage("[PortableVFX] 설정과 마법 카탈로그를 다시 읽었습니다: 마법 " + outcome.spells() + "개, 연출 " + outcome.bindings()
                                + "개, 건너뜀 " + outcome.skipped().size() + "개. 기존 효과 정리를 요청했습니다.");
                    } else {
                        // 카탈로그가 거부됐다: 이전 카탈로그와 실행 중인 효과는 그대로 두고 config.yml 한도만 적용한다.
                        engine.applyLimits(limits);
                        if (catalogVisuals != null) catalogVisuals.limits(readCastLimits());
                        sender.sendMessage("[PortableVFX] 마법 카탈로그 reload 실패: " + outcome.error());
                        sender.sendMessage("[PortableVFX] 이전 카탈로그(마법 " + outcome.spells() + "개)를 유지하고 실행 중인 효과는 건드리지 않았습니다. config.yml 한도만 적용했습니다.");
                    }
                    int shown = 0;
                    for (var problem : outcome.skipped().entrySet()) {
                        if (shown++ >= 5) { sender.sendMessage("[PortableVFX] ... 외 " + (outcome.skipped().size() - 5) + "개 (서버 로그 참고)"); break; }
                        sender.sendMessage("[PortableVFX] 건너뛴 마법 " + problem.getKey() + ": " + problem.getValue());
                    }
                }
                default -> usage(sender);
            }
        } catch (IllegalArgumentException | RejectedExecutionException e) {
            sender.sendMessage("[PortableVFX] 거부: " + e.getMessage());
        }
        return true;
    }

    private void followCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 9) throw new IllegalArgumentException("follow <effect> <entityUUID|self> [scale] [durationTicks] [anchor] [offsetX] [offsetY] [offsetZ]");
        org.bukkit.entity.Entity entity;
        if (args[2].equalsIgnoreCase("self")) {
            if (!(sender instanceof Player player)) throw new IllegalArgumentException("follow self requires an in-game player");
            entity = player;
        } else entity = Bukkit.getEntity(UUID.fromString(args[2]));
        if (entity == null || !entity.isValid() || entity.isDead()) throw new IllegalArgumentException("Entity is not alive and loaded");
        Location at = entity.getLocation();
        var effect = new PlayEffect(UUID.randomUUID(), args[1], at.getWorld().getKey().toString(),
                at.getX(), at.getY(), at.getZ(), 0, 0, 0, number(args, 3, 1), 0xffffff, 1,
                integer(args, 4, 100), entity.getUniqueId());
        if (args.length > 5) effect = new PlayEffect(effect.instanceId(), effect.effectId(), effect.dimensionId(),
                effect.x(), effect.y(), effect.z(), effect.yaw(), effect.pitch(), effect.roll(), effect.scale(),
                effect.rgb(), effect.opacity(), effect.durationTicks(), effect.followEntity(), effect.seed(), -1,
                dev.portablevfx.protocol.EffectAnchor.parse(args[5]), number(args, 6, 0), number(args, 7, 0), number(args, 8, 0));
        PlayResult result = engine.play(effect, engine.limits().defaultRadius());
        sender.sendMessage("[PortableVFX] follow handle=" + result.handle() + ", recipients=" + result.recipients());
    }

    private void playCommand(CommandSender sender, String[] args) {
        if (args.length < 6 || args.length > 14) {
            throw new IllegalArgumentException("play <effect> <world> <x> <y> <z> [scale] [durationTicks] [yaw] [pitch] [roll] [rgbHex] [opacity] [radius]");
        }
        EffectRequest request = new EffectRequest(args[1], args[2],
                Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]),
                number(args, 6, 1), integer(args, 7, Math.min(40, engine.limits().maxDurationTicks())),
                number(args, 8, 0), number(args, 9, 0), number(args, 10, 0),
                args.length > 11 ? color(args[11]) : 0xFFFFFF,
                number(args, 12, 1), args.length > 13 ? Double.parseDouble(args[13]) : engine.limits().defaultRadius());
        PlayResult result = service.play(request);
        sender.sendMessage("[PortableVFX] handle=" + result.handle() + ", 전송=" + result.recipients()
                + ", 다음 tick 재시도=" + result.deferred() + ", 예산 제외=" + result.skippedRateLimited() + ", 반경=" + result.effectiveRadius());
        if (result.recipients() == 0 && result.deferred() == 0) {
            sender.sendMessage("[PortableVFX] 활성 핸들이 생성되지 않았습니다. 같은 월드·반경 내 호환 클라이언트와 채널 등록을 확인하세요.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("portablevfx.admin")) return List.of();
        List<String> candidates;
        if (args.length == 1) candidates = List.of("play","follow","catalog","event","finish","stop","clear","status","reload");
        else if(args.length==2&&(args[0].equalsIgnoreCase("catalog")||args[0].equalsIgnoreCase("event")))candidates=spellCatalog==null?List.of():spellCatalog.complete(args[1]);
        else if (args.length == 3 && args[0].equalsIgnoreCase("follow") && sender instanceof Player) candidates = List.of("self");
        else if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
            candidates = getServer().getWorlds().stream().map(World::getName).toList();
        } else return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static float number(String[] args, int index, float fallback) {
        return args.length > index ? Float.parseFloat(args[index]) : fallback;
    }

    private static int integer(String[] args, int index, int fallback) {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static int color(String input) {
        String hex = input.startsWith("#") ? input.substring(1) : input;
        if (!hex.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("rgbHex must be six hexadecimal digits (RRGGBB)");
        return Integer.parseInt(hex, 16);
    }

    private static void expectArgs(String[] args, int expected, String syntax) {
        if (args.length != expected) throw new IllegalArgumentException(syntax);
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage("/pvfxserverdebug play <effect> <world> <x> <y> <z> [scale] [ticks]");
        sender.sendMessage("/pvfxserverdebug follow <effect> <entityUUID|self> [scale] [ticks]");
        sender.sendMessage("/pvfxserverdebug catalog <spellId> | event <spellId> <eventName>");
        sender.sendMessage("/pvfxserverdebug finish <handle> | stop <handle> | clear | status | reload");
    }

    private RelayLimits readLimits() {
        FileConfiguration config = getConfig();
        RelayLimits defaults = RelayLimits.DEFAULT;
        RelayLimits result = new RelayLimits(
                config.getDouble("view.max-radius", defaults.maxRadius()),
                config.getDouble("view.default-radius", defaults.defaultRadius()),
                raisedDefault(config, "limits.max-active-handles", 512, defaults.maxActiveHandles()),
                raisedDefault(config, "limits.packets-per-tick", 1024, defaults.maxPacketsPerTick()),
                config.getInt("limits.packets-per-player-per-tick", defaults.maxPacketsPerPlayerPerTick()),
                raisedDefault(config, "limits.play-requests-per-tick", 128, defaults.maxPlaysPerTick()),
                config.getInt("limits.max-duration-ticks", defaults.maxDurationTicks()),
                config.getInt("limits.finish-drain-ticks", defaults.finishDrainTicks()),
                config.getInt("limits.play-retry-ticks", defaults.playRetryTicks()));
        getLogger().info("Relay limits: " + result + ", cast limits: " + readCastLimits());
        return result;
    }

    /**
     * 서버에 이미 있는 config.yml 은 덮어쓰지 않는다. config-version 이 없는(2 미만) 예전 파일에 예전 기본값이
     * 그대로 적혀 있으면 새 기본값을 쓴다(예전 기본값은 100명 규모에서 시전 실패를 일으켰다).
     * 예전 값을 일부러 유지하려면 config.yml 에 config-version: 2 를 적는다.
     */
    private int raisedDefault(FileConfiguration config, String path, int oldDefault, int newDefault) {
        int value = config.getInt(path, newDefault);
        if (value == oldDefault && config.getInt("config-version", 1) < 2) {
            getLogger().warning("config.yml " + path + "=" + oldDefault + " is the old default; using the new default " + newDefault
                    + ". Update config.yml (see the bundled default) or add 'config-version: 2' to keep " + oldDefault + ".");
            return newDefault;
        }
        return value;
    }

    private dev.portablevfx.paper.internal.spell.CatalogVisualEngine.Limits readCastLimits() {
        FileConfiguration config = getConfig();
        var defaults = dev.portablevfx.paper.internal.spell.CatalogVisualEngine.Limits.DEFAULT;
        return new dev.portablevfx.paper.internal.spell.CatalogVisualEngine.Limits(
                config.getInt("limits.max-active-casts", defaults.maxCasts()),
                config.getInt("limits.max-casts-per-player", defaults.maxPerPlayer()),
                config.getInt("limits.max-pending-phases", defaults.maxPending()),
                config.getInt("limits.finish-drain-ticks", defaults.finishDrainTicks()));
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("PortableVFX API requires the main server thread");
        if (!isEnabled()) throw new IllegalStateException("PortableVFX is not enabled");
    }

    private final class ServerService implements PortableVfxService {
        @Override public PlayResult play(EffectRequest request) {
            requireMainThread();
            Objects.requireNonNull(request, "request");
            World world = dev.portablevfx.paper.internal.WorldLookup.resolve(request.worldName(),getServer()::getWorld,getServer()::getWorld);
            if (world == null) throw new IllegalArgumentException("Unknown world: " + request.worldName());
            PlayEffect effect = new PlayEffect(UUID.randomUUID(), request.effectId(), world.getKey().toString(),
                    request.x(), request.y(), request.z(), request.yaw(), request.pitch(), request.roll(),
                    request.scale(), request.rgb(), request.opacity(), request.durationTicks());
            return engine.play(effect, request.radius());
        }
        @Override public PlayResult play(EffectRequest request, dev.portablevfx.paper.api.PlaybackOptions options) {
            requireMainThread(); Objects.requireNonNull(request, "request"); Objects.requireNonNull(options, "options");
            World world = dev.portablevfx.paper.internal.WorldLookup.resolve(request.worldName(),getServer()::getWorld,getServer()::getWorld);
            if (world == null) throw new IllegalArgumentException("Unknown world: " + request.worldName());
            double x = request.x(), y = request.y(), z = request.z();
            if (options.followEntity() != null) {
                var target = Bukkit.getEntity(options.followEntity());
                if (target == null || !target.isValid() || target.isDead() || !world.equals(target.getWorld())) {
                    throw new IllegalArgumentException("Target is not alive and loaded in the requested world");
                }
                Location at = target.getLocation(); x = at.getX(); y = at.getY(); z = at.getZ();
            }
            return engine.play(new PlayEffect(UUID.randomUUID(), request.effectId(), world.getKey().toString(),
                    x, y, z, request.yaw(), request.pitch(), request.roll(), request.scale(), request.rgb(), request.opacity(),
                    request.durationTicks(), options.followEntity(), options.seed(), options.startTick(), options.anchor(),
                    options.offsetX(), options.offsetY(), options.offsetZ()), request.radius());
        }
        @Override public PlayResult follow(EffectRequest request, UUID target) {
            requireMainThread(); Objects.requireNonNull(request,"request"); Objects.requireNonNull(target,"target");
            var entity=Bukkit.getEntity(target);
            if(entity==null || !entity.isValid() || entity.isDead())throw new IllegalArgumentException("Target is not alive and loaded");
            World world=dev.portablevfx.paper.internal.WorldLookup.resolve(request.worldName(),getServer()::getWorld,getServer()::getWorld);
            if(world==null || !world.equals(entity.getWorld()))throw new IllegalArgumentException("Follow target world mismatch");
            Location at=entity.getLocation();
            return engine.play(new PlayEffect(UUID.randomUUID(),request.effectId(),world.getKey().toString(),
                    at.getX(),at.getY(),at.getZ(),request.yaw(),request.pitch(),request.roll(),request.scale(),
                    request.rgb(),request.opacity(),request.durationTicks(),target),request.radius());
        }
        @Override public boolean stop(UUID handle) {
            requireMainThread();
            return engine.stop(Objects.requireNonNull(handle, "handle"));
        }
        @Override public int orient(UUID handle, float yaw, float pitch, float roll) {
            requireMainThread(); return engine.orient(handle,yaw,pitch,roll);
        }
        @Override public int clear() { requireMainThread(); if(catalogVisuals!=null)catalogVisuals.clear(); return engine.clear(); }
        @Override public PlayResult startCast(EffectRequest request, EffectBasis basis, long seed) {
            requireMainThread();
            return engine.startCast(castEffect(request, seed), request.radius(), Objects.requireNonNull(basis, "basis"));
        }
        @Override public PlayResult startCast(EffectRequest request, EffectBasis basis, long seed, double effectWidth) {
            requireMainThread();
            return engine.startCast(castEffect(request, seed).withEffectWidth(effectWidth), request.radius(), Objects.requireNonNull(basis, "basis"));
        }
        @Override public int updateCast(UUID handle, double x, double y, double z, EffectBasis basis) {
            requireMainThread();
            return engine.updateCast(Objects.requireNonNull(handle, "handle"), x, y, z, Objects.requireNonNull(basis, "basis"));
        }
        @Override public PlayResult impactCast(UUID handle, EffectRequest request, EffectBasis basis, long seed) {
            requireMainThread();
            return engine.impactCast(Objects.requireNonNull(handle, "handle"), castEffect(request, seed), Objects.requireNonNull(basis, "basis"));
        }
        @Override public PlayResult impactCast(UUID handle, EffectRequest request, EffectBasis basis, long seed, double effectWidth) {
            requireMainThread();
            return engine.impactCast(Objects.requireNonNull(handle, "handle"), castEffect(request, seed).withEffectWidth(effectWidth), Objects.requireNonNull(basis, "basis"));
        }
        @Override public boolean supportsCatalogCast(UUID player) { requireMainThread();return engine.supportsCatalogCast(player); }
        @Override public PlayResult startCast(EffectRequest request,EffectBasis basis,long seed,double width,double scaleInput,double linkLength) {
            requireMainThread();return engine.startCatalogCast(castEffect(request,seed).withEffectWidth(width).withParameters(scaleInput,linkLength),request.radius(),basis);
        }
        @Override public PlayResult startFollowCast(EffectRequest request,UUID target,long seed,double width,double scaleInput) {
            requireMainThread();var entity=Bukkit.getEntity(target);
            if(entity==null||!entity.isValid()||entity.isDead())throw new IllegalArgumentException("Follow target unavailable");
            World world=dev.portablevfx.paper.internal.WorldLookup.resolve(request.worldName(),getServer()::getWorld,getServer()::getWorld);
            if(world==null||!world.equals(entity.getWorld()))throw new IllegalArgumentException("Follow target world mismatch");
            Location at=entity.getLocation();
            var effect=new PlayEffect(UUID.randomUUID(),request.effectId(),world.getKey().toString(),at.getX(),at.getY(),at.getZ(),
                request.yaw(),request.pitch(),request.roll(),request.scale(),request.rgb(),request.opacity(),request.durationTicks(),target,
                seed,world.getGameTime(),dev.portablevfx.protocol.EffectAnchor.ENTITY,0,0,0,width).withParameters(scaleInput,-1);
            return engine.startCatalogCast(effect,request.radius(),EffectBasis.identity());
        }
        @Override public int updateCast(UUID handle,double x,double y,double z,EffectBasis basis,double linkLength) {
            requireMainThread();return engine.updateCast(handle,x,y,z,basis,linkLength);
        }
        @Override public boolean finishCast(UUID handle) {
            requireMainThread(); return engine.finishCast(Objects.requireNonNull(handle, "handle"));
        }
        @Override public boolean finishCast(UUID handle,boolean clearLocal) { requireMainThread();return engine.finishCast(handle,clearLocal); }
        private PlayEffect castEffect(EffectRequest request, long seed) {
            Objects.requireNonNull(request, "request");
            World world = dev.portablevfx.paper.internal.WorldLookup.resolve(request.worldName(),getServer()::getWorld,getServer()::getWorld);
            if (world == null) throw new IllegalArgumentException("Unknown world: " + request.worldName());
            return new PlayEffect(UUID.randomUUID(), request.effectId(), world.getKey().toString(),
                    request.x(), request.y(), request.z(), request.yaw(), request.pitch(), request.roll(),
                    request.scale(), request.rgb(), request.opacity(), request.durationTicks(), null, seed,
                    world.getGameTime(), dev.portablevfx.protocol.EffectAnchor.WORLD, 0, 0, 0);
        }
        @Override public VfxStatus status() { requireMainThread(); return engine.status(); }
    }

    private final class BukkitTransport implements RelayEngine.Transport {
        @Override public long currentTick() { return Integer.toUnsignedLong(Bukkit.getCurrentTick()); }

        @Override public long worldTick(String dimensionId) {
            for (World world : getServer().getWorlds()) if (worldKey(world).equals(dimensionId)) return world.getGameTime();
            return -1;
        }

        @Override public Collection<RelayEngine.Viewer> viewers(String dimensionId) {
            List<RelayEngine.Viewer> viewers = new ArrayList<>();
            for (World world : getServer().getWorlds()) {
                if (!worldKey(world).equals(dimensionId)) continue;
                for (Player player : world.getPlayers()) viewers.add(snapshot(player));
                break;
            }
            return viewers;
        }

        @Override public RelayEngine.Viewer viewer(UUID id) {
            Player player = getServer().getPlayer(id);
            return player == null || !player.isOnline() ? null : snapshot(player);
        }

        /** RelayEngine 이 tick 당 플레이어별 한 번만 부른다. 채널 여부는 캐시한 플래그를 쓴다. */
        private RelayEngine.Viewer snapshot(Player player) {
            Location location = player.getLocation();
            UUID id = player.getUniqueId();
            return new RelayEngine.Viewer(id, worldKey(player.getWorld()),
                    location.getX(), location.getY(), location.getZ(), effectChannel.contains(id));
        }

        @Override public boolean send(UUID playerId, byte[] payload) {
            // 비활성화 중에는 플러그인 메시지를 보낼 수 없다(Messenger 가 거부한다): 시도하지 않는다.
            if (!isEnabled() || !effectChannel.contains(playerId)) return false;
            Player player = getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) return false;
            try {
                player.sendPluginMessage(PortableVfxPlugin.this, VfxProtocol.EFFECT_CHANNEL, payload);
                return true;
            } catch (IllegalArgumentException | IllegalStateException e) {
                long tick = currentTick();
                if (lastTransportWarningTick == Long.MIN_VALUE || tick - lastTransportWarningTick >= 200) {
                    lastTransportWarningTick = tick;
                    getLogger().warning("Effect send failed; client TTL remains the fallback: " + e.getMessage());
                }
                return false;
            }
        }
    }
}
