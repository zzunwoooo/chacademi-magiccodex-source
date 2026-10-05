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
import org.bukkit.event.player.PlayerQuitEvent;
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
        service = new ServerService();
        reloadSpellCatalog();
        getServer().getServicesManager().register(PortableVfxService.class, service, this, ServicePriority.Normal);
        PluginCommand command = Objects.requireNonNull(getCommand("pvfxserverdebug"), "pvfxserverdebug missing from plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        ticker = getServer().getScheduler().runTaskTimer(this, () -> { engine.tick(); if(catalogVisuals!=null)catalogVisuals.tick(); }, 1L, 1L);
        // After all channels are registered: ask already-connected clients to hello again.
        getServer().getScheduler().runTask(this, this::requestClientHellos);
        getLogger().info("PortableVFX relay enabled; protocol " + VfxProtocol.VERSION + ", no gameplay hooks.");
    }

    /** Called only by the existing MagicCodex mana transaction; this method never charges mana. */
    public Boolean castAuthorizedSpell(Player player,String id) {
        if(spellCatalog==null||spellCatalog.resolve(id).isEmpty())return null;
        if(catalogVisuals==null||!spellCatalog.resolve(id).orElseThrow().enabled()){
            getLogger().warning("Catalog cast refused "+id+": visuals="+(catalogVisuals!=null)+", enabled="+spellCatalog.resolve(id).orElseThrow().enabled());return false;
        }
        if(!engine.supportsCatalogCast(player.getUniqueId())){
            getLogger().warning("Catalog cast refused "+id+": "+engine.catalogDiagnostic(player.getUniqueId()));
            player.sendActionBar(net.kyori.adventure.text.Component.text("마법 이펙트가 아직 준비되지 않았습니다. 로딩 완료 후 다시 시도하세요."));return false;
        }
        Boolean result;
        try{result=catalogVisuals.cast(player,id);}
        catch(RuntimeException failure){getLogger().log(java.util.logging.Level.WARNING,"Catalog visual dispatch exception "+id,failure);throw failure;}
        if(!Boolean.TRUE.equals(result))getLogger().warning("Catalog visual dispatch refused "+id+": "+catalogVisuals.lastFailure()+" / "+engine.catalogDiagnostic(player.getUniqueId()));
        return result==null?Boolean.FALSE:result;
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

    private void reloadSpellCatalog() {
        try {
            for(String name:List.of("spell-catalog.yml","spell-bindings.yml"))if(!new java.io.File(getDataFolder(),name).exists())saveResource(name,false);
            var catalogYaml=new org.bukkit.configuration.file.YamlConfiguration();catalogYaml.load(new java.io.File(getDataFolder(),"spell-catalog.yml"));
            var bindingYaml=new org.bukkit.configuration.file.YamlConfiguration();bindingYaml.load(new java.io.File(getDataFolder(),"spell-bindings.yml"));
            if(!bindingDriftChecked){bindingDriftChecked=true;warnBindingDrift(bindingYaml);}
            var next=dev.portablevfx.paper.internal.spell.SpellCatalogYaml.read(catalogYaml);
            var section=bindingYaml.getConfigurationSection("bindings");
            var bindings=section==null?java.util.Map.<String,dev.portablevfx.paper.internal.spell.CatalogVisualEngine.Plan>of():dev.portablevfx.paper.internal.spell.CatalogVisualEngine.read(section);
            for(String id:bindings.keySet())if(next.resolve(id).isEmpty())throw new IllegalArgumentException("Binding has no catalogue ID: "+id);
            var visuals=new dev.portablevfx.paper.internal.spell.CatalogVisualEngine(service,bindings);
            if(catalogVisuals!=null)catalogVisuals.clear();spellCatalog=next;catalogVisuals=visuals;
            engine.catalogRequirements(bindings.values().stream().flatMap(p->p.phases().stream()).map(p->p.effect()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        } catch(Exception error) {
            getLogger().warning("Spell catalog reload rejected; retaining previous catalog: "+error.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (catalogVisuals != null) catalogVisuals.clear();
        if (engine != null) engine.shutdown();
        getServer().getServicesManager().unregisterAll(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this);
        service = null;
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
    public void onQuit(PlayerQuitEvent event) {
        if(catalogVisuals!=null)catalogVisuals.removePlayer(event.getPlayer().getUniqueId());
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
            if(catalogVisuals!=null)catalogVisuals.removePlayer(event.getPlayer().getUniqueId());
            engine.forget(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        if(catalogVisuals!=null)catalogVisuals.removeWorld(event.getWorld().getUID());
        engine.worldUnloaded(event.getWorld().getKey().toString());
        for (Player player : event.getWorld().getPlayers()) engine.worldChanged(player.getUniqueId());
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
                    if(sender instanceof Player player)sender.sendMessage("[VFX 진단] "+engine.catalogDiagnostic(player.getUniqueId()));
                    if(catalogVisuals!=null&&!catalogVisuals.lastFailure().isEmpty())sender.sendMessage("[VFX 진단] 최근 시전 거부="+catalogVisuals.lastFailure());
                }
                case "reload" -> {
                    expectArgs(args, 1, "reload");
                    requireMainThread();
                    reloadConfig();
                    reloadSpellCatalog();
                    if(catalogVisuals!=null)catalogVisuals.clear();
                    engine.reload(readLimits());
                    sender.sendMessage("[PortableVFX] 설정을 다시 읽고 기존 효과 정리를 요청했습니다.");
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
                + ", 예산 제외=" + result.skippedRateLimited() + ", 반경=" + result.effectiveRadius());
        if (result.recipients() == 0) {
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
                config.getInt("limits.max-active-handles", defaults.maxActiveHandles()),
                config.getInt("limits.packets-per-tick", defaults.maxPacketsPerTick()),
                config.getInt("limits.packets-per-player-per-tick", defaults.maxPacketsPerPlayerPerTick()),
                config.getInt("limits.play-requests-per-tick", defaults.maxPlaysPerTick()),
                config.getInt("limits.max-duration-ticks", defaults.maxDurationTicks()));
        getLogger().info("Relay limits: " + result);
        return result;
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
            for (World world : getServer().getWorlds()) if (world.getKey().toString().equals(dimensionId)) return world.getGameTime();
            return -1;
        }

        @Override public Collection<RelayEngine.Viewer> viewers(String dimensionId) {
            List<RelayEngine.Viewer> viewers = new ArrayList<>();
            for (World world : getServer().getWorlds()) {
                if (!world.getKey().toString().equals(dimensionId)) continue;
                for (Player player : world.getPlayers()) viewers.add(snapshot(player));
                break;
            }
            return viewers;
        }

        @Override public RelayEngine.Viewer viewer(UUID id) {
            Player player = getServer().getPlayer(id);
            return player == null || !player.isOnline() ? null : snapshot(player);
        }

        private RelayEngine.Viewer snapshot(Player player) {
            Location location = player.getLocation();
            return new RelayEngine.Viewer(player.getUniqueId(), player.getWorld().getKey().toString(),
                    location.getX(), location.getY(), location.getZ(),
                    player.getListeningPluginChannels().contains(VfxProtocol.EFFECT_CHANNEL));
        }

        @Override public boolean send(UUID playerId, byte[] payload) {
            Player player = getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()
                    || !player.getListeningPluginChannels().contains(VfxProtocol.EFFECT_CHANNEL)) return false;
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
