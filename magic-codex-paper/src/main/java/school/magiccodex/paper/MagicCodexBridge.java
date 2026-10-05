package school.magiccodex.paper;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.PermissionProtocol;
import school.magiccodex.database.DatabaseSettings;

/** Permission/wallet synchronization and authoritative mana/casting service. */
public final class MagicCodexBridge extends JavaPlugin implements PluginMessageListener, Listener {
    private PermissionSubscriptions subscriptions;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private AutoCloseable luckPermsHook;
    private WalletBridge wallet;
    private MailboxBridge mailbox;
    private ShopBridge shops;
    private ManaBridge mana;
    private SpellRuntime spellRuntime;
    Boolean runtimeCast(Player player,String id){
        Boolean visual=CatalogVfxLink.cast(this,player,id);
        return visual!=null?visual:(spellRuntime==null?null:spellRuntime.cast(player,id));
    }
    boolean requiresCatalogVisual(String id){return mana!=null&&mana.requiresCatalogVisual(id);}
    public boolean castMagic(Player player,String id){return mana!=null&&mana.castRegistered(player,id);}
    public double magicPower(Player player){
        refreshEquipment(player);
        Double base=stats==null?null:stats.service().get(player.getUniqueId()).power();
        Double value=equipmentPower(player.getUniqueId(),base);
        return value==null?0:Math.clamp(value,0,1_000_000);
    }
    public double magicHaste(Player player){return mana==null?0:mana.mana.snapshot(player.getUniqueId()).map(s->s.haste()).orElse(0d);}
    private StatsBridge stats;
    private SocialBridge social;
    private AscensionBridge ascension;
    private AppraisalBridge appraisal;
    private EnhancementBridge enhancement;
    /** Server-thread API for future scroll items/NPC scripts. mode: 0 reset, 1 restore, 2 affinity. */
    public void grantReconfiguration(org.bukkit.entity.Player player,int mode,int count){reconfiguration.grant(player,mode,count);}
    public void openReconfiguration(org.bukkit.entity.Player player,int mode){reconfiguration.open(player,mode);}
    private ReconfigurationBridge reconfiguration;
    void refreshEquipment(org.bukkit.entity.Player p){if(equipment!=null)equipment.refresh(p);}
    private TemperatureBridge temperature;
    private PetBridge pets;
    private TamingBridge taming;
    private QuestBridge quests;
    private DialogueBridge dialogues;
    private NpcSocialService npcSocial;
    DialogueBridge dialogueBridge(){return dialogues;}
    QuestBridge questBridge(){return quests;}
    private TitleBridge titles;
    /** Trusted server-thread API; the callback runs after database commit. */
    public void grantTitle(UUID player,String id,java.util.function.Consumer<Boolean> result){if(titles==null)result.accept(false);else titles.grant(player,id,false,result);}
    public void revokeTitle(UUID player,String id,java.util.function.Consumer<Boolean> result){if(titles==null)result.accept(false);else titles.grant(player,id,true,result);}
    public void registerDialogueAction(String id,java.util.function.BiConsumer<Player,String> action){if(dialogues==null)throw new IllegalStateException("Dialogue not ready");dialogues.register(id,action);}
    public void registerDialogueCondition(String id,java.util.function.BiPredicate<Player,String> condition){if(dialogues==null)throw new IllegalStateException("Dialogue not ready");dialogues.registerCondition(id,condition);}
    boolean dialogueQuestCondition(Player p,String id,String status){return quests!=null&&quests.dialogueCondition(p,id,status);}
    void acceptDialogueQuest(Player p,String id,java.util.function.Consumer<Boolean> result){if(quests==null)result.accept(false);else quests.acceptFromDialogue(p,id,result);}
    void dialogueQuestEvent(Player p,String id,java.util.function.Consumer<Boolean> result){if(quests==null)result.accept(false);else quests.eventFromDialogue(p,id,result);}
    /** Trusted server integrations only; not a client-authoritative progress endpoint. */
    public void questEvent(Player player,String eventId,int amount){if(quests!=null)quests.event(player,QuestDefinition.Type.EVENT,eventId,amount);}
    /** Server-thread API for a spell/NPC integration. Server validates aim, mana, cooldown and permission. */
    public boolean castTaming(Player player){return taming!=null&&taming.cast(player);}
    private ClimateService climate;
    private DisplayNames names;
    private SchoolBridge school;
    private EquipmentBridge equipment;
    private PlayerStateBridge playerState;
    boolean playerStateReady(Player p){return playerState==null||playerState.ready(p);}
    void savePlayerState(Player p){if(playerState!=null)playerState.save(p);}
    Double equipmentPower(UUID id,Double base){return equipment==null?base:equipment.power(id,base);}
    DisplayNames names(){return names;}
    int questHouse(Player p){return school==null?-1:school.house(p);}
    void questHouseReward(String token,int house,long points,Runnable done,java.util.function.Consumer<String> fail){if(school==null){fail.accept("학교 기록 없음");return;}school.questReward(token,house,points,done,fail);}
    String dorm(Player p){return school==null?"":school.dorm(p);}
    int circle(Player player){return ascension==null?1:ascension.current(player);}
    private boolean immediateScheduled;
    private static long now() { return System.nanoTime() / 1_000_000L; }

    @Override public void onEnable() {
        saveDefaultConfig();
        names=new DisplayNames(this);
        wallet=new WalletBridge(this);
        long refresh = Math.clamp(getConfig().getLong("refresh-interval-ticks", 200), 20, 1200) * 50;
        long timeout = Math.clamp(getConfig().getLong("session-timeout-seconds", 45), 30, 120) * 1000;
        int budget = Math.clamp(getConfig().getInt("max-permission-checks-per-pass", 2048), PermissionProtocol.MAX_PERMISSIONS, 16384);
        subscriptions = new PermissionSubscriptions(refresh, timeout, budget);
        getServer().getMessenger().registerIncomingPluginChannel(this, PermissionProtocol.REQUEST, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PermissionProtocol.RESPONSE);
        getServer().getPluginManager().registerEvents(this, this);
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            try { luckPermsHook = LuckPermsHook.connect(this, this::markDirty); }
            catch (RuntimeException | LinkageError error) { getLogger().warning("LuckPerms 알림 연결 실패: 보조 권한 확인을 사용합니다."); }
        }
        getServer().getScheduler().runTaskTimer(this, this::process, 1L, 20L);
        try { mana=new ManaBridge(this);stats=new StatsBridge(this,mana);school=new SchoolBridge(this,stats.service());social=new SocialBridge(this,mana,stats.service());ascension=new AscensionBridge(this,stats.service());temperature=new TemperatureBridge(this);pets=new PetBridge(this);climate=new ClimateService(this,mana.mana,temperature.service());equipment=new EquipmentBridge(this,mana.mana);appraisal=new AppraisalBridge(this);enhancement=new EnhancementBridge(this);reconfiguration=new ReconfigurationBridge(this,enhancement);playerState=new PlayerStateBridge(this,mana.mana,DatabaseSettings.load(getDataFolder().toPath().resolve("database.properties"))); }
        catch(Exception error) {
            getLogger().severe("마나 초기화 실패: "+error.getMessage());
            getServer().getPluginManager().disablePlugin(this);return;
        }
        try{taming=new TamingBridge(this,mana.mana,pets);}catch(Exception error){getLogger().severe("교화 초기화 실패: "+error.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        try{quests=new QuestBridge(this);}catch(Exception error){getLogger().severe("의뢰 초기화 실패: "+error.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        try{dialogues=new DialogueBridge(this);}catch(Exception error){getLogger().severe("대화 초기화 실패: "+error.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        try{npcSocial=new NpcSocialService(this);}catch(Exception error){npcSocial=null;getLogger().warning("NPC 호감도 초기화 실패 (AI NPC 연결 없이 계속): "+error.getMessage());}
        try{titles=new TitleBridge(this);}catch(Exception error){getLogger().severe("칭호 초기화 실패: "+error.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        if(!mana.isCatalogMode()&&getServer().getPluginManager().isPluginEnabled("MythicMobs"))try{spellRuntime=new SpellRuntime(this);}catch(Exception error){getLogger().severe("마법 실행 초기화 실패: "+error.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        mana.bindSpellCommand();
        try { mailbox=new MailboxBridge(this); shops=new ShopBridge(this); } catch(Exception e) { getLogger().severe("Mailbox initialization failed: "+e.getClass().getSimpleName()); getServer().getPluginManager().disablePlugin(this); return; }
        getLogger().info("권한 연동 준비 완료. 활성 도감만 조회, 작업당 최대 " + budget + "회, 보조 확인 " + refresh / 1000 + "초.");
    }
    private void markDirty(UUID player) {
        // LuckPerms may invoke this off-thread. No Bukkit API is accessed here.
        if (viewers.contains(player)) dirty.add(player);
    }
    @Override public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!PermissionProtocol.REQUEST.equals(channel) || data.length < 13 || data.length > PermissionProtocol.MAX_BYTES) return;
        int action = data[4];
        if (action == PermissionProtocol.OPEN) {
            if (!subscriptions.allowOpen(player.getUniqueId(), now())) return;
        } else if ((action != PermissionProtocol.CLOSE && action != PermissionProtocol.KEEPALIVE) || data.length != 13) return;
        try {
            var request = PermissionProtocol.decodeRequest(data);
            subscriptions.accept(player.getUniqueId(), request, now());
            if (subscriptions.subscribed(player.getUniqueId())) viewers.add(player.getUniqueId());
            else { viewers.remove(player.getUniqueId()); dirty.remove(player.getUniqueId()); }
            if (request.action() == PermissionProtocol.OPEN) scheduleImmediate();
        } catch (IOException | IllegalArgumentException ignored) { /* Invalid input cannot trigger permission work. */ }
    }
    private void scheduleImmediate() {
        if (immediateScheduled) return;
        immediateScheduled = true;
        getServer().getScheduler().runTask(this, () -> {
            immediateScheduled = false;
            process();
        });
    }
    private void process() {
        for (UUID player : dirty) if (dirty.remove(player)) subscriptions.dirty(player);
        if (subscriptions.size() == 0) { viewers.clear(); return; }
        subscriptions.process(now(), new PermissionSubscriptions.Access() {
            private UUID cachedId;
            private Player cachedPlayer;
            private Player player(UUID id) {
                if (!id.equals(cachedId)) { cachedId = id; cachedPlayer = getServer().getPlayer(id); }
                return cachedPlayer;
            }
            public boolean online(UUID id) { var p = player(id); return p != null && p.isOnline(); }
            public boolean hasPermission(UUID id, String permission) {
                var p = player(id);
                return p != null && p.hasPermission(permission);
            }
            public void send(UUID id, PermissionProtocol.Response response) {
                var p = player(id);
                if (p != null) p.sendPluginMessage(MagicCodexBridge.this, PermissionProtocol.RESPONSE, PermissionProtocol.encodeResponse(response));
            }
        });
        viewers.removeIf(id -> !subscriptions.subscribed(id));
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        subscriptions.remove(id); viewers.remove(id); dirty.remove(id);
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { markDirty(event.getPlayer().getUniqueId()); }
    @Override public void onDisable() {
        if(shops!=null)shops.close(); if(mailbox!=null)mailbox.close();
        if(spellRuntime!=null){spellRuntime.close();spellRuntime=null;}
        if(titles!=null){titles.close();titles=null;}
        if(npcSocial!=null){npcSocial.close();npcSocial=null;}
        if(dialogues!=null){dialogues.close();dialogues=null;}
        if(quests!=null){quests.close();quests=null;}
        if(taming!=null){taming.close();taming=null;}
        if(playerState!=null){playerState.close();playerState=null;}
        if(reconfiguration!=null)reconfiguration.close();
        if(enhancement!=null)enhancement.close();
        if(appraisal!=null)appraisal.close();
        if(equipment!=null){equipment.close();equipment=null;}
        if(climate!=null){climate.close();climate=null;}
        if(pets!=null){pets.close();pets=null;}
        if(temperature!=null){temperature.close();temperature=null;}
        if(ascension!=null){ascension.close();ascension=null;}
        if(social!=null){social.close();social=null;}
        if(school!=null){school.close();school=null;}
        if(stats!=null){stats.close();stats=null;}
        if(mana!=null){mana.close();mana=null;}
        if(wallet!=null){wallet.close();wallet=null;}
        if (luckPermsHook != null) try { luckPermsHook.close(); } catch (Exception ignored) {}
        getServer().getScheduler().cancelTasks(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (subscriptions != null) subscriptions.clear();
        immediateScheduled = false;
        viewers.clear(); dirty.clear();
    }
}
