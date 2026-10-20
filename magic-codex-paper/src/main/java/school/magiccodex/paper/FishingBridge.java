package school.magiccodex.paper;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.Vector;
import school.magiccodex.protocol.FishingProtocol;
import school.magiccodex.protocol.FishingProtocol.*;

/**
 * 낚시 (2026-10-20, owner-approved plan): the panel minigame on top of the vanilla fishing rod.
 *
 * Flow: the player casts a fishing rod as usual -> when the bobber bobs in water the server measures the water
 * (FishingWater) and opens the panel (SESSION, phase WATER) -> the client sinks the bobber and reports where it stopped
 * (STOP) -> the server picks what bites (biome group, season, time, weather, depth, water size, bait) and sends BITE ->
 * the client plays teases, the hook window and the tug of war, then reports RESULT -> the server checks the timing,
 * pays the catch (inventory, else mailbox), writes the codex and the gear wear into the rod's PDC, removes the bobber
 * and puts the panel back to "ready" (phase READY). Clicking there sends RECAST: the server throws a real bobber again
 * towards the first cast's landing spot (owner 2026-10-20).
 * Vanilla bites and loot never happen for a hook in a session. Gear changes are refused while a bobber is out.
 * Everything runs on the main thread except the codex database (FishingStore).
 */
final class FishingBridge implements PluginMessageListener, Listener, CommandExecutor, TabCompleter {
    private static final int LANDING = 0, READY = 1, SINKING = 2, BITING = 3;
    private static final String[] FILES = {"fishing.yml", "fishing-fish.yml", "fishing-gear.yml", "fishing-treasure.yml"};

    private final class Session {
        final long id = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        final UUID player;
        FishHook hook;
        Location target;          // where the first cast landed
        int state = LANDING;
        long since = System.currentTimeMillis();
        boolean ownCast, preview;
        int previewBackground;
        FishingWater water;
        float maxDepth;
        // the bite
        int token;
        FishingConfig.Fish fish;
        int chest = -1;
        float size, depth;
        long sentAt;
        int minimum;
        int rodSlot = -1;          // held hotbar slot when the fish was picked
        Session(UUID player) { this.player = player; }
    }

    private final MagicCodexBridge plugin;
    private FishingConfig config;
    private final FishingGear gear;
    private final FishingStore store;
    private final FishTank tank;
    private final ItemsAdderItems itemsAdder;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, long[]> limits = new HashMap<>();

    FishingBridge(MagicCodexBridge plugin) throws Exception {
        this.plugin = plugin;
        for (String f : FILES) if (!new File(plugin.getDataFolder(), f).exists()) plugin.saveResource(f, false);
        config = FishingConfig.load(plugin.getDataFolder());
        gear = new FishingGear(plugin, config);
        store = new FishingStore(plugin);
        tank = new FishTank(plugin);
        tank.capacity(config.tankCapacity);
        itemsAdder = new ItemsAdderItems(plugin.getLogger());
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, FishingProtocol.REQUEST, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, FishingProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (String name : List.of("낚시도감", "낚시관리")) {
            var command = Objects.requireNonNull(plugin.getCommand(name));
            command.setExecutor(this); command.setTabCompleter(this);
        }
        for (Player p : Bukkit.getOnlinePlayers()) { store.load(p.getUniqueId(), null); tank.refresh(p.getUniqueId(), null); }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2, 2);
        plugin.getLogger().info("낚시: 물고기 " + config.fish.size() + "종, 장비 " + config.gear.stream().mapToInt(Map::size).sum() + "종");
    }

    private FishTankBridge tankWindow;
    void tankWindow(FishTankBridge w) { tankWindow = w; }
    FishTank tank() { return tank; }
    /** A line is in the water (cast, sinking or a fish on it): the 통발 window waits until it is done. */
    boolean fishingNow(UUID player) {
        var s = sessions.get(player);
        return s != null && !s.preview && (s.state == SINKING || s.state == BITING || s.state == LANDING && s.hook != null);
    }
    FishingConfig config() { return config; }
    FishingGear gear() { return gear; }

    /** A fish item (taken out of the tank) for this tank row, or null when the species no longer exists. */
    ItemStack fishItem(FishTank.Fish f) {
        var def = config.fishById.get(f.fish());
        return def == null ? null : gear.fish(def, (float) f.size(), f.id(), f.caughtAt());
    }

    void close() {
        for (var s : List.copyOf(sessions.values())) { removeHook(s); Player p = Bukkit.getPlayer(s.player); if (p != null) send(p, new Close(s.id, "")); }
        sessions.clear();
        store.close();
        tank.close();
    }

    // ------------------------------------------------------------------ vanilla rod

    @EventHandler(priority = EventPriority.HIGH)
    public void fish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        switch (e.getState()) {
            case FISHING -> {
                // only refusals here; the session starts at MONITOR (after other plugins had their say)
                if (!config.enabled || e.isCancelled() || !modded(p)) return;
                ItemStack rod = rodOf(p);
                if (rod == null) return;
                var l = gear.read(rod);
                if (!l.rod().fallback() && l.rod().value() <= 0) {
                    e.setCancelled(true);
                    p.sendActionBar(FishingGear.plain("낚싯대가 고장 났어요. 수리한 뒤 다시 던져 주세요.", 0xFF9E9E));
                    return;
                }
                if (s != null && !s.preview && (s.state == SINKING || s.state == BITING)) { e.setCancelled(true); return; }
                if (tank.full(p.getUniqueId())) {
                    // owner 2026-10-20: a full tank blocks fishing
                    e.setCancelled(true);
                    p.sendActionBar(FishingGear.plain("통발 수조가 가득 찼어요 (" + tank.count(p.getUniqueId()) + "/" + tank.capacity() + "). 물고기를 팔거나 꺼낸 뒤 낚시해 주세요.", 0xFF9E9E));
                }
            }
            case BITE, CAUGHT_FISH, LURED -> {
                if (s != null && s.hook != null && s.hook.equals(e.getHook())) { e.setCancelled(true); e.setExpToDrop(0); }
            }
            default -> {
                // REEL_IN, IN_GROUND, FAILED_ATTEMPT, CAUGHT_ENTITY: the player pulled the bobber back by hand
                if (s != null && s.hook != null && s.hook.equals(e.getHook())) {
                    if (s.state == BITING) penalty(p, s);
                    if (s.state == SINKING || s.state == BITING) { end(p, s, ""); }
                    else if (s.state == LANDING) { if (s.ownCast) ready(p, s, ""); else sessions.remove(p.getUniqueId()); }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void cast(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.FISHING || !config.enabled) return;
        Player p = e.getPlayer();
        if (!modded(p) || rodOf(p) == null) return;     // no MagicCodex mod: plain vanilla fishing
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.preview) { sessions.remove(p.getUniqueId()); s = null; }
        if (s != null && (s.state == SINKING || s.state == BITING)) return;
        if (s == null) { s = new Session(p.getUniqueId()); sessions.put(p.getUniqueId(), s); }
        s.hook = e.getHook(); s.state = LANDING; s.since = System.currentTimeMillis();
        if (!s.ownCast) s.target = null;
        quiet(s.hook);
    }

    /** The MagicCodex client listens on the fishing channel (players without it fish the vanilla way). */
    private static boolean modded(Player p) { return p.getListeningPluginChannels().contains(FishingProtocol.RESPONSE); }

    /** Vanilla must never bite this hook (the minigame decides). */
    private static void quiet(FishHook hook) {
        try {
            hook.setApplyLure(false);
            hook.setMaxWaitTime(20 * 60 * 60); hook.setMinWaitTime(20 * 60 * 60 - 1); hook.setWaitTime(20 * 60 * 60 - 1);
        } catch (RuntimeException | LinkageError ignored) {}
    }

    /** The rod in the main hand (owner set: the gear window follows the held rod; an offhand rod fishes the vanilla way). */
    private ItemStack rodOf(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        return FishingGear.isRod(main) ? main : null;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (var s : List.copyOf(sessions.values())) {
            Player p = Bukkit.getPlayer(s.player);
            if (p == null) { removeHook(s); sessions.remove(s.player); continue; }
            if (s.preview) continue;
            switch (s.state) {
                case LANDING -> {
                    if (s.hook == null || !s.hook.isValid()) { if (s.ownCast) ready(p, s, "찌가 물에 닿지 않았어요."); else sessions.remove(s.player); continue; }
                    boolean bobbing;
                    try { bobbing = s.hook.getState() == FishHook.HookState.BOBBING; } catch (LinkageError err) { bobbing = s.hook.isInWater(); }
                    if (bobbing || (s.hook.isInWater() && s.hook.getVelocity().lengthSquared() < .01)) land(p, s);
                    else if (now - s.since > 8000) { removeHook(s); if (s.ownCast) ready(p, s, "찌가 물에 닿지 않았어요."); else sessions.remove(s.player); }
                }
                case SINKING, BITING -> {
                    if (s.hook == null || !s.hook.isValid()) { end(p, s, "찌가 사라져서 낚시를 멈췄어요."); continue; }
                    if (!s.hook.getWorld().equals(p.getWorld()) || s.hook.getLocation().distanceSquared(p.getLocation()) > 48 * 48) {
                        end(p, s, config.message("far", "찌가 너무 멀어져서 낚시를 멈췄어요.")); continue;
                    }
                    if (s.state == BITING && now - s.sentAt > 5 * 60_000) { penalty(p, s); end(p, s, ""); }
                    if (s.state == SINKING && now - s.since > 90_000) end(p, s, "");
                }
                default -> {}
            }
        }
    }

    private void land(Player p, Session s) {
        var water = FishingWater.measure(s.hook.getLocation(), config);
        if (water == null) return;   // still dropping into it
        quiet(s.hook);
        s.water = water;
        if (s.target == null) s.target = s.hook.getLocation().clone();
        var l = gear.read(rodOf(p));
        float cap = config.depthCap(water.depth());
        // the bobber may go to 85% of the line: the last 15% is the room a hooked fish can pull before the line runs out
        float reach = l.line().gear().a() * .85f;
        s.maxDepth = Math.min(FishingProtocol.MAX_DEPTH, cap <= 0 ? reach : Math.min(cap, reach));
        s.state = SINKING; s.since = System.currentTimeMillis();
        send(p, new FishingProtocol.Session(s.id, FishingProtocol.PHASE_WATER, water.background(), s.maxDepth, config.waterClassOf(water.area()),
                Math.min(water.area(), 1_000_000), gear.protocol(l), ""));
    }

    private void ready(Player p, Session s, String notice) {
        s.state = READY; s.ownCast = false; s.hook = null; s.since = System.currentTimeMillis();
        var l = gear.read(rodOf(p));
        int bg = s.preview ? s.previewBackground : s.water == null ? 0 : s.water.background();
        send(p, new FishingProtocol.Session(s.id, FishingProtocol.PHASE_READY, bg, s.maxDepth, s.water == null ? 0 : config.waterClassOf(s.water.area()),
                s.water == null ? 0 : Math.min(s.water.area(), 1_000_000), gear.protocol(l), cut(notice)));
    }

    private void end(Player p, Session s, String notice) {
        removeHook(s);
        sessions.remove(s.player);
        send(p, new Close(s.id, cut(notice)));
    }

    private static void removeHook(Session s) {
        if (s.hook != null && s.hook.isValid()) s.hook.remove();
        s.hook = null;
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        var s = sessions.remove(e.getPlayer().getUniqueId());
        if (s != null) removeHook(s);
        limits.remove(e.getPlayer().getUniqueId());
        store.forget(e.getPlayer().getUniqueId());
        tank.forget(e.getPlayer().getUniqueId());
    }
    @EventHandler public void join(PlayerJoinEvent e) { store.load(e.getPlayer().getUniqueId(), null); tank.refresh(e.getPlayer().getUniqueId(), null); }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent e) {
        var s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null && !s.preview) end(e.getPlayer(), s, "");
    }

    // ------------------------------------------------------------------ requests

    @Override public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!FishingProtocol.REQUEST.equals(channel)) return;
        long[] w = limits.computeIfAbsent(p.getUniqueId(), k -> new long[2]);
        long now = System.currentTimeMillis();
        if (now - w[0] > 1000) { w[0] = now; w[1] = 0; }
        if (++w[1] > 20) return;
        Request r;
        try { r = FishingProtocol.request(bytes); } catch (IllegalArgumentException e) { return; }
        Session s = sessions.get(p.getUniqueId());
        boolean ours = s != null && s.id == r.session();
        switch (r.action()) {
            case FishingProtocol.STOP -> { if (ours && s.state == SINKING) bite(p, s, Math.min(r.depth(), s.maxDepth)); }
            case FishingProtocol.RESULT -> { if (ours && s.state == BITING && r.token() == s.token) result(p, s, r.outcome()); }
            case FishingProtocol.RECAST -> { if (ours && s.state == READY) recast(p, s); }
            case FishingProtocol.CANCEL -> { if (ours) { if (s.state == BITING) penalty(p, s); removeHook(s); sessions.remove(p.getUniqueId()); } }
            case FishingProtocol.GEAR_OPEN -> sendGear(p, "");
            case FishingProtocol.GEAR_EQUIP -> equip(p, r.kind(), r.slot());
            case FishingProtocol.CODEX_OPEN -> openCodex(p);
            case FishingProtocol.CODEX_CLAIM -> claim(p, r.tier());
            default -> {}
        }
    }

    // ------------------------------------------------------------------ the bite

    private int season() {
        var c = plugin.climateService();
        return c == null ? -1 : c.season().ordinal();
    }

    private boolean timeMatches(String time, long t) {
        if (time.equals("전체")) return true;
        int[] r = config.times.get(time);
        if (r == null) return true;
        return r[0] <= r[1] ? t >= r[0] && t < r[1] : t >= r[0] || t < r[1];
    }

    private boolean eligible(FishingConfig.Fish f, Session s, World world, int season, float depth) {
        if (depth < f.minDepth() || depth > f.maxDepth()) return false;
        if (s.preview) return true;
        var w = s.water;
        int wc = config.waterClassOf(w.area());
        if (wc < f.waterMin() || wc > f.waterMax()) return false;
        if (!f.biomes().isEmpty() && Collections.disjoint(f.biomes(), w.groups())) return false;
        if (!f.seasons().isEmpty() && season >= 0 && !f.seasons().contains(ClimateService.Season.values()[season].label)) return false;
        if (!timeMatches(f.time(), world.getTime())) return false;
        switch (f.weather()) {
            case "맑음" -> { if (world.hasStorm()) return false; }
            case "비" -> { if (!world.hasStorm()) return false; }
            case "뇌우" -> { if (!world.isThundering()) return false; }
            default -> {}
        }
        if (f.underground() && !w.underground()) return false;
        if (f.yMin() != null && w.surfaceY() < f.yMin()) return false;
        if (f.yMax() != null && w.surfaceY() > f.yMax()) return false;
        if (!f.moon().isEmpty()) {
            long phase = (world.getFullTime() / 24000) % 8;
            boolean night = timeMatches("밤", world.getTime());
            if (!night || (f.moon().equals("full") && phase != 0) || (f.moon().equals("new") && phase != 4)) return false;
        }
        return true;
    }

    private void bite(Player p, Session s, float depth) {
        var rnd = ThreadLocalRandom.current();
        var l = gear.read(rodOf(p));
        var bait = l.bait().gear();
        World world = s.preview ? p.getWorld() : s.hook.getWorld();
        int zone = config.zone(depth);
        s.depth = depth; s.fish = null; s.chest = -1;
        double chestChance = config.treasureChance + bait.effect("treasure", 0);
        int shape, power, pattern;
        float size;
        if (rnd.nextDouble() < chestChance) {
            s.chest = pickGrade(config.treasureRarity[zone], new boolean[]{true, true, true, true}, 1);
            shape = FishingProtocol.SHAPE_CHEST; power = 3 + s.chest; pattern = FishingProtocol.CALM; size = .35f + .1f * s.chest; s.size = 0;
        } else {
            int season = season();
            var pool = new ArrayList<FishingConfig.Fish>();
            for (var f : config.fish) if (eligible(f, s, world, season, depth)) pool.add(f);
            if (pool.isEmpty()) for (var f : config.fish) if (f.biomes().isEmpty() && f.grade() == 0 && depth >= f.minDepth() && depth <= f.maxDepth()) pool.add(f);
            if (pool.isEmpty()) pool.add(config.fish.getFirst());
            boolean[] has = new boolean[4];
            for (var f : pool) has[f.grade()] = true;
            float bonus = 1 + bait.a() / 100f;
            int grade = pickGrade(config.rarity[zone], has, bonus);
            double total = 0;
            var weights = new double[pool.size()];
            for (int i = 0; i < pool.size(); i++) {
                var f = pool.get(i);
                if (f.grade() != grade) continue;
                double wgt = 1;
                if (f.time().equals("밤")) wgt *= bait.effect("night_fish", 1);
                if (f.magic()) wgt *= bait.effect("magic_fish", 1);
                if (f.minDepth() >= 50) wgt *= bait.effect("deep_fish", 1);
                weights[i] = wgt; total += wgt;
            }
            double pick = rnd.nextDouble() * total;
            for (int i = 0; i < pool.size(); i++) {
                if (weights[i] <= 0) continue;
                pick -= weights[i];
                if (pick <= 0) { s.fish = pool.get(i); break; }
            }
            if (s.fish == null) s.fish = pool.getLast();
            var f = s.fish;
            float u = (float) Math.pow(rnd.nextDouble(), 1.6);
            s.size = Math.round((f.minSize() + (f.maxSize() - f.minSize()) * u) * 10) / 10f;
            size = (float) Math.clamp((Math.log(s.size) - Math.log(3)) / (Math.log(1200) - Math.log(3)), 0, 1);
            shape = f.shape(); power = f.power(); pattern = f.pattern();
        }
        float wait = config.waitMin + (config.waitMax - config.waitMin) * rnd.nextFloat();
        boolean ocean = s.water != null && s.water.groups().stream().anyMatch(config.oceanGroups::contains);
        float reduction = Math.clamp(bait.b(), 0, 90);
        if ((bait.has("fresh_wait") && ocean) || (bait.has("sea_wait") && !ocean)) reduction = 0;   // wrong water: no help
        wait *= 1 - reduction / 100f;
        if (timeMatches("밤", world.getTime())) wait *= l.flt().gear().effect("night_wait", 1);
        int teases = config.teaseMin + rnd.nextInt(config.teaseMax - config.teaseMin + 1);
        int grade = l.flt().gear().has("grade_glow") ? (s.fish != null ? s.fish.grade() : s.chest) : -1;
        int window = Math.clamp(Math.round(l.rod().gear().b() * 1000), 100, 5000);
        s.token = rnd.nextInt(1, Integer.MAX_VALUE);
        var b = new Bite(s.id, s.token, shape, size, grade, Math.round(Math.min(wait, 59) * 1000), teases, window, power, pattern, rnd.nextLong(), depth,
                s.fish != null && s.fish.magic());
        s.minimum = FishingProtocol.minimumCatchMillis(b, l.rod().gear().a());
        s.sentAt = System.currentTimeMillis();
        s.rodSlot = p.getInventory().getHeldItemSlot();
        if (send(p, b)) s.state = BITING; else end(p, s, "낚시 정보를 보내지 못했어요.");
    }

    private static int pickGrade(double[] weights, boolean[] allowed, float bonus) {
        double total = 0; double[] w = new double[4];
        for (int g = 0; g < 4; g++) if (allowed[g]) { w[g] = weights[g] * (g > 0 ? bonus : 1); total += w[g]; }
        if (total <= 0) { for (int g = 0; g < 4; g++) if (allowed[g]) return g; return 0; }
        double pick = ThreadLocalRandom.current().nextDouble() * total;
        for (int g = 0; g < 4; g++) { pick -= w[g]; if (w[g] > 0 && pick <= 0) return g; }
        for (int g = 3; g >= 0; g--) if (allowed[g]) return g;
        return 0;
    }

    // ------------------------------------------------------------------ result

    private void result(Player p, Session s, int outcome) {
        long elapsed = System.currentTimeMillis() - s.sentAt;
        if (outcome == FishingProtocol.CAUGHT && elapsed < s.minimum) {
            plugin.getLogger().warning("FISHING too fast player=" + p.getName() + " elapsed=" + elapsed + "ms min=" + s.minimum + "ms");
            outcome = FishingProtocol.REFUSED;
        }
        if (s.preview) s.rodSlot = p.getInventory().getHeldItemSlot();
        if (outcome != FishingProtocol.REFUSED && !heldRod(p, s)) outcome = FishingProtocol.REFUSED;   // rod swapped after the bite
        var notes = new ArrayList<String>();
        var l = wearAndBait(p, s, outcome, notes);
        String fishId = "", name = ""; int grade = 0, shape = 0; float cm = 0; boolean record = false, fresh = false, mailed = false;
        var rewards = new ArrayList<Reward>();
        int shown = outcome;
        if (outcome == FishingProtocol.CAUGHT) {
            var items = new ArrayList<ItemStack>();
            if (s.chest >= 0) {
                shown = FishingProtocol.TREASURE; grade = s.chest; shape = FishingProtocol.SHAPE_CHEST; name = FishingProtocol.GRADE_LABELS[grade] + " 보물상자";
                var t = config.treasure[grade];
                var rnd = ThreadLocalRandom.current();
                int sum = t.rewards().stream().mapToInt(FishingConfig.Reward::weight).sum();
                for (int i = 0; i < t.rolls() && sum > 0; i++) {
                    int pick = rnd.nextInt(sum);
                    for (var rw : t.rewards()) {
                        pick -= rw.weight();
                        if (pick < 0) { var it = item(rw.item(), rnd.nextInt(rw.min(), rw.max() + 1)); if (it != null) items.add(it); break; }
                    }
                }
                if (!s.preview) { var r = store.record(p.getUniqueId(), "treasure_" + grade, 0); fresh = r[0]; }
            } else {
                var f = s.fish;
                fishId = f.id(); name = f.name(); grade = f.grade(); shape = f.shape(); cm = s.size;
                // owner 2026-10-20: fish go into the 통발 tank, not the inventory
                if (!s.preview) {
                    var r = store.record(p.getUniqueId(), f.id(), cm); fresh = r[0]; record = r[1];
                    tank.add(p.getUniqueId(), f.id(), cm);
                    notes.add("통발 수조에 넣었어요 (" + tank.count(p.getUniqueId()) + "/" + tank.capacity() + ").");
                } else fresh = true;
            }
            for (ItemStack it : merge(items)) rewards.add(new Reward(FishingGear.cut(AlchemyBridge.displayName(it)), it.getAmount(), preview(it)));
            if (rewards.size() > FishingProtocol.MAX_REWARDS) rewards.subList(FishingProtocol.MAX_REWARDS, rewards.size()).clear();
            if (!s.preview) mailed = give(p, items, "fishing:" + s.id + ":" + s.token);
            if (mailed) notes.add(config.message("mailed", "인벤토리가 가득 차서 우편으로 보냈어요."));
            if (grade == 3) p.getWorld().playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .8f, 1.2f);
        } else if (outcome == FishingProtocol.REFUSED) notes.add("결과를 확인하지 못했어요. 다시 시도해 주세요.");
        removeHook(s);
        s.state = READY; s.ownCast = false; s.since = System.currentTimeMillis();
        send(p, new FishingProtocol.Result(s.id, shown, fishId, FishingGear.cut(name), grade, shape, cm, record, fresh, rewards, mailed,
                gear.protocol(l), cut(String.join(" ", notes))));
    }

    private boolean heldRod(Player p, Session s) {
        return p.getInventory().getHeldItemSlot() == s.rodSlot && FishingGear.isRod(p.getInventory().getItemInMainHand());
    }

    /** Cancelling / reeling in by hand after the bite costs the same as losing the fish (no free re-rolls). */
    private void penalty(Player p, Session s) {
        if (s.preview || !heldRod(p, s)) return;
        var notes = new ArrayList<String>();
        wearAndBait(p, s, FishingProtocol.ESCAPED, notes);
        if (!notes.isEmpty()) p.sendActionBar(FishingGear.plain(String.join(" ", notes), 0xFFD9A0));
    }

    /** Wear and bait use for one try, written into the held rod. Returns the loadout after it. */
    private FishingGear.Loadout wearAndBait(Player p, Session s, int outcome, List<String> notes) {
        ItemStack rod = rodOf(p);
        var l = gear.read(rod);
        if (s.preview || rod == null || outcome == FishingProtocol.REFUSED) return l;
        boolean tug = outcome == FishingProtocol.CAUGHT || outcome == FishingProtocol.SNAPPED || outcome == FishingProtocol.ESCAPED;
        boolean baitUsed = tug || outcome == FishingProtocol.MISSED;
        var returned = new ArrayList<ItemStack>();
        var rodPart = wear(l.rod(), tug ? 1 : 0);
        var flt = wear(l.flt(), 1);
        var line = wear(l.line(), tug ? 1 + (outcome == FishingProtocol.SNAPPED ? config.snapDamage : 0) : 0);
        var bait = l.bait();
        if (baitUsed && !bait.fallback()) bait = bait.value() - 1 <= 0 ? new FishingGear.Part(config.fallback[3], -1, true) : new FishingGear.Part(bait.gear(), bait.value() - 1, false);
        if (!rodPart.fallback() && rodPart.value() <= 0) notes.add(rodPart.gear().name() + "이(가) 고장 났어요.");
        if (!flt.fallback() && flt.value() <= 0) { returned.add(gear.create(flt.gear(), 0, 1)); notes.add(flt.gear().name() + "이(가) 고장 나서 빠졌어요."); flt = new FishingGear.Part(config.fallback[1], -1, true); }
        if (!line.fallback() && line.value() <= 0) { returned.add(gear.create(line.gear(), 0, 1)); notes.add(line.gear().name() + "이(가) 고장 나서 빠졌어요."); line = new FishingGear.Part(config.fallback[2], -1, true); }
        if (!l.bait().fallback() && bait.fallback()) notes.add("미끼를 다 썼어요.");
        var next = new FishingGear.Loadout(rodPart, flt, line, bait);
        if (!(l.rod().fallback() && next.flt().fallback() && next.line().fallback() && next.bait().fallback())) gear.write(rod, next);
        if (!returned.isEmpty()) give(p, returned, "fishing-gear:" + s.id + ":" + s.token);
        return next;
    }

    private static FishingGear.Part wear(FishingGear.Part p, int amount) {
        if (p.fallback() || amount <= 0) return p;
        return new FishingGear.Part(p.gear(), Math.max(0, p.value() - amount), false);
    }

    private ItemStack item(String key, int amount) {
        if (key.startsWith("minecraft:") || !key.contains(":")) {
            Material m = Material.matchMaterial(key);
            return m == null || !m.isItem() ? null : new ItemStack(m, Math.clamp(amount, 1, m.getMaxStackSize()));
        }
        return itemsAdder.create(key, amount);
    }
    private static List<ItemStack> merge(List<ItemStack> items) {
        var out = new ArrayList<ItemStack>();
        outer:
        for (ItemStack it : items) {
            for (ItemStack o : out) if (o.isSimilar(it) && o.getAmount() + it.getAmount() <= o.getMaxStackSize()) { o.setAmount(o.getAmount() + it.getAmount()); continue outer; }
            out.add(it.clone());
        }
        return out;
    }
    private static byte[] preview(ItemStack item) {
        try {
            ItemStack one = item.clone(); one.setAmount(1);
            byte[] b = one.serializeAsBytes();
            if (b.length <= FishingProtocol.MAX_PREVIEW) return b;
            b = new ItemStack(item.getType()).serializeAsBytes();
            return b.length <= FishingProtocol.MAX_PREVIEW ? b : new byte[0];
        } catch (RuntimeException e) { return new byte[0]; }
    }

    /** Inventory first, the rest by mail (else at the feet). Returns true when something went to the mailbox. */
    boolean give(Player p, List<ItemStack> items, String source) {
        var leftover = new ArrayList<ItemStack>();
        for (ItemStack it : items) leftover.addAll(p.getInventory().addItem(it.clone()).values());
        if (leftover.isEmpty()) return false;
        var service = Bukkit.getServicesManager().load(MailService.class);
        if (service == null) { for (ItemStack it : leftover) p.getWorld().dropItemNaturally(p.getLocation(), it); return false; }
        for (int part = 0; part * 9 < leftover.size(); part++) {
            var chunk = List.copyOf(leftover.subList(part * 9, Math.min(leftover.size(), part * 9 + 9)));
            try {
                service.sendSystem(source + ":" + part, p.getUniqueId(), "낚시 보상", "인벤토리가 가득 차서 낚시 보상을 우편으로 보내 드립니다.", chunk)
                        .whenComplete((id, error) -> { if (error != null) Bukkit.getScheduler().runTask(plugin, () -> drop(p, chunk)); });
            } catch (RuntimeException e) { drop(p, chunk); }
        }
        return true;
    }
    private void drop(Player p, List<ItemStack> items) {
        plugin.getLogger().warning("FISHING MAIL FAILED player=" + p.getUniqueId() + " items=" + items);
        if (p.isOnline()) for (ItemStack it : items) p.getWorld().dropItemNaturally(p.getLocation(), it);
    }

    // ------------------------------------------------------------------ recast

    private void recast(Player p, Session s) {
        if (s.preview) {
            s.state = SINKING; s.since = System.currentTimeMillis();
            send(p, new FishingProtocol.Session(s.id, FishingProtocol.PHASE_WATER, s.previewBackground, s.maxDepth, 3, 4096, gear.protocol(gear.read(rodOf(p))), ""));
            return;
        }
        ItemStack main = p.getInventory().getItemInMainHand();
        if (!FishingGear.isRod(main)) { ready(p, s, "낚싯대를 손에 들어 주세요."); return; }
        var l = gear.read(main);
        if (!l.rod().fallback() && l.rod().value() <= 0) { ready(p, s, "낚싯대가 고장 났어요."); return; }
        Location target = s.target;
        if (target == null || !p.getWorld().equals(target.getWorld()) || target.distanceSquared(p.getLocation()) > 30 * 30) {
            end(p, s, "처음 던진 곳이 너무 멀어요. 다시 던져 주세요."); return;
        }
        FishHook hook;
        try { hook = p.launchProjectile(FishHook.class); } catch (RuntimeException | LinkageError e) {
            end(p, s, "찌를 던지지 못했어요. 다시 던져 주세요."); return;
        }
        hook.setVelocity(aim(hook.getLocation(), target.clone().add(0, .2, 0)));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, .6f, .5f);
        p.swingMainHand();
        s.hook = hook; s.ownCast = true; s.state = LANDING; s.since = System.currentTimeMillis();
        quiet(hook);
    }

    /** Initial velocity so a fishing bobber (drag 0.92, gravity 0.03 per tick) lands near `to`. */
    static Vector aim(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ(), dy = to.getY() - from.getY();
        double dist = Math.sqrt(dx * dx + dz * dz);
        int n = (int) Math.clamp(Math.round(dist * 1.4 + 8), 8, 40);
        double drag = .92, sum = (1 - Math.pow(drag, n)) / (1 - drag);
        double h = dist / sum;
        // y after n ticks = A*vy - B
        double y0 = 0, v0 = 0, y1 = 0, v1 = 1;
        for (int i = 0; i < n; i++) { y0 += v0; v0 = v0 * drag - .03; y1 += v1; v1 = v1 * drag - .03; }
        double a = y1 - y0, vy = (dy - y0) / a;
        var v = new Vector(dist < 1e-6 ? 0 : dx / dist * h, vy, dist < 1e-6 ? 0 : dz / dist * h);
        if (v.length() > 2.5) v.multiply(2.5 / v.length());
        return v;
    }

    // ------------------------------------------------------------------ gear window

    private boolean locked(Player p) {
        var s = sessions.get(p.getUniqueId());
        return s != null && !s.preview && s.state != READY;
    }

    private void sendGear(Player p, String notice) {
        ItemStack rod = rodOf(p);
        var l = gear.read(rod);
        var owned = new ArrayList<Owned>();
        var inv = p.getInventory();
        int held = inv.getHeldItemSlot();
        var contents = inv.getStorageContents();
        for (int slot = 0; slot < contents.length && owned.size() < FishingProtocol.MAX_OWNED; slot++) {
            ItemStack it = contents[slot];
            if (slot == held && FishingGear.isRod(inv.getItemInMainHand())) continue;
            int k = gear.kindOf(it);
            if (k < 0) continue;
            var g = gear.gearOf(it);
            if (g == null) continue;
            int d = k == 3 ? -1 : (gear.plainRod(it) ? -1 : gear.durabilityOf(it));
            int max = k == 3 || gear.plainRod(it) ? -1 : g.durability();
            owned.add(new Owned(k, slot, g.id(), FishingGear.cut(g.name()), g.grade(), d, max, it.getAmount(), cutText(FishingGear.stats(g)), cutText(g.specialText())));
        }
        var stats = new ArrayList<String>();
        for (int k = 0; k < 4; k++) stats.add(cutText(FishingGear.stats(l.part(k).gear())));
        send(p, new GearList(gear.protocol(l), locked(p) || rod == null, owned, stats, cut(rod == null ? "낚싯대를 손에 들어 주세요." : notice)));
    }

    private void equip(Player p, int kind, int slot) {
        if (locked(p)) { sendGear(p, config.message("no-gear-change", "낚시 중에는 장비를 바꿀 수 없어요.")); return; }
        var inv = p.getInventory();
        ItemStack rod = inv.getItemInMainHand();
        if (!FishingGear.isRod(rod)) { sendGear(p, "낚싯대를 손에 들어 주세요."); return; }
        var contents = inv.getStorageContents();
        if (slot >= contents.length || slot == inv.getHeldItemSlot()) { sendGear(p, ""); return; }
        ItemStack chosen = contents[slot];
        if (gear.kindOf(chosen) != kind) { sendGear(p, "그 장비를 찾지 못했어요."); return; }
        var g = gear.gearOf(chosen);
        if (g == null) { sendGear(p, ""); return; }
        if (kind != 3 && !gear.plainRod(chosen) && gear.durabilityOf(chosen) == 0) { sendGear(p, config.message("broken", "고장 난 장비는 장착할 수 없어요.")); return; }
        if (kind == FishingProtocol.ROD) {
            // swap the held rod with the chosen one
            inv.setItem(slot, rod);
            inv.setItemInMainHand(chosen);
        } else {
            var l = gear.read(rod);
            var old = l.part(kind);
            int value = kind == 3 ? chosen.getAmount() : gear.durabilityOf(chosen);
            var part = new FishingGear.Part(g, value < 0 ? g.durability() : value, false);
            var next = switch (kind) {
                case 1 -> new FishingGear.Loadout(l.rod(), part, l.line(), l.bait());
                case 2 -> new FishingGear.Loadout(l.rod(), l.flt(), part, l.bait());
                default -> new FishingGear.Loadout(l.rod(), l.flt(), l.line(), part);
            };
            if (l.rod().fallback()) next = new FishingGear.Loadout(new FishingGear.Part(config.fallback[0], config.fallback[0].durability(), false), next.flt(), next.line(), next.bait());
            inv.setItem(slot, null);
            gear.write(rod, next);
            inv.setItemInMainHand(rod);
            if (!old.fallback()) give(p, List.of(gear.create(old.gear(), old.value(), Math.max(1, old.value()))), "fishing-unequip:" + UUID.randomUUID());
        }
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, .7f, 1.2f);
        sendGear(p, "");
        var s = sessions.get(p.getUniqueId());
        if (s != null && s.state == READY) ready(p, s, "");
    }

    // ------------------------------------------------------------------ codex

    private void openCodex(Player p) {
        var book = store.book(p.getUniqueId());
        if (!book.loaded) { store.load(p.getUniqueId(), () -> { if (p.isOnline()) sendCodex(p, ""); }); return; }
        sendCodex(p, "");
    }

    private void sendCodex(Player p, String notice) {
        var book = store.book(p.getUniqueId());
        var entries = new ArrayList<Entry>();
        int caught = 0;
        int[] chests = new int[4];
        synchronized (book) {
            for (var f : config.fish) {
                var rec = book.rows.get(f.id());
                boolean got = rec != null;
                if (got) caught++;
                entries.add(new Entry(f.id(), got ? FishingGear.cut(f.name()) : "???", f.grade(), f.shape(), got, got ? (float) rec.best() : 0,
                        got ? rec.count() : 0, cutText(got ? f.description() : "아직 잡지 못한 물고기예요."), cutText(seasonText(f)),
                        cutText(got ? placeText(f) : "???"), cutText(depthText(f)), cutText(got ? timeText(f) : "???")));
            }
            for (int g = 0; g < 4; g++) { var r = book.rows.get("treasure_" + g); chests[g] = r == null ? 0 : r.count(); }
            int reached = FishingProtocol.tiersReached(caught, config.fish.size());
            int claimed = 0, available = 0;
            for (int t = 0; t < 4; t++) { if (book.claimed.contains(t)) claimed |= 1 << t; else if (t < reached) available |= 1 << t; }
            send(p, new Codex(entries, chests, claimed, available, cut(notice)));
        }
    }

    private static String seasonText(FishingConfig.Fish f) {
        return f.seasons().isEmpty() ? "사계절" : String.join("·", f.seasons().stream().sorted(Comparator.comparingInt(FishingBridge::seasonOrder)).toList());
    }
    private static int seasonOrder(String s) { return switch (s) { case "봄" -> 0; case "여름" -> 1; case "가을" -> 2; default -> 3; }; }
    private static String placeText(FishingConfig.Fish f) {
        String where = f.biomes().isEmpty() ? "어디서나" : String.join("·", f.biomes());
        String water = f.waterMin() == f.waterMax() ? FishingConfig.WATER[f.waterMin()] + "만" : f.waterMin() == 0 && f.waterMax() == 3 ? "" :
                f.waterMax() == 3 ? FishingConfig.WATER[f.waterMin()] + " 이상" : FishingConfig.WATER[f.waterMax()] + " 이하";
        String extra = f.underground() ? " · 지하" : "";
        if (f.yMin() != null) extra += " · 높은 곳";
        if (f.yMax() != null) extra += " · 깊은 땅속";
        return where + (water.isEmpty() ? "" : " · " + water) + extra;
    }
    private static String depthText(FishingConfig.Fish f) {
        return f.maxDepth() >= 999 ? FishingGear.num(f.minDepth()) + "m 이상" : FishingGear.num(f.minDepth()) + "~" + FishingGear.num(f.maxDepth()) + "m";
    }
    private static String timeText(FishingConfig.Fish f) {
        String t = f.time().equals("전체") ? "언제나" : f.time();
        if (!f.weather().equals("전체")) t += " · " + f.weather();
        if (f.moon().equals("full")) t += " · 보름달";
        if (f.moon().equals("new")) t += " · 그믐달";
        return t;
    }

    private void claim(Player p, int tier) {
        var book = store.book(p.getUniqueId());
        int caught;
        synchronized (book) { caught = (int) config.fish.stream().filter(f -> book.rows.containsKey(f.id())).count(); }
        if (tier >= FishingProtocol.tiersReached(caught, config.fish.size())) { sendCodex(p, "아직 받을 수 없는 보상이에요."); return; }
        store.claim(p.getUniqueId(), tier, ok -> {
            if (!p.isOnline()) return;
            if (!ok) { sendCodex(p, "이미 받은 보상이에요."); return; }
            var reward = config.codexRewards[tier];
            for (String cmd : reward.commands())
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()).replace("{uuid}", p.getUniqueId().toString()));
            var items = new ArrayList<ItemStack>();
            for (String spec : reward.items()) {
                int at = spec.lastIndexOf(':');
                String key = spec; int amount = 1;
                if (at > 0) { try { amount = Integer.parseInt(spec.substring(at + 1)); key = spec.substring(0, at); } catch (NumberFormatException ignored) {} }
                var it = item(key, amount);
                if (it != null) items.add(it);
            }
            if (!items.isEmpty()) give(p, items, "fishing-codex:" + p.getUniqueId() + ":" + tier);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .8f, 1.1f);
            sendCodex(p, "도감 " + new int[]{25, 50, 75, 100}[tier] + "% 보상을 받았어요!");
        });
    }

    // ------------------------------------------------------------------ commands

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("낚시도감")) {
            Player target = sender instanceof Player p ? p : null;
            if (args.length > 0 && sender.hasPermission("magiccodex.fishing.admin")) target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§c플레이어를 찾지 못했습니다."); return true; }
            openCodex(target);
            return true;
        }
        if (args.length == 0) { sender.sendMessage("§e/낚시관리 리로드 | 장비지급 <플레이어> <장비ID> [개수] | 물고기지급 <플레이어> <물고기ID> [cm] | 도감초기화 <플레이어> | 미리보기 [민물|바다|얼음|동굴] | 수조 [플레이어]"); return true; }
        switch (args[0]) {
            case "리로드" -> {
                try {
                    var next = FishingConfig.load(plugin.getDataFolder());
                    config = next; gear.config(next); tank.capacity(next.tankCapacity);
                    sender.sendMessage("§a낚시 설정을 다시 읽었습니다. (물고기 " + next.fish.size() + "종)");
                } catch (Exception e) { sender.sendMessage("§c낚시 설정 오류 (이전 설정 유지): " + e.getMessage()); }
            }
            case "장비지급" -> {
                if (args.length < 3) { sender.sendMessage("§e/낚시관리 장비지급 <플레이어> <장비ID> [개수]"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                FishingConfig.Gear g = null;
                for (int k = 0; k < 4 && g == null; k++) g = config.gear(k, args[2]);
                if (t == null || g == null) { sender.sendMessage("§c플레이어나 장비를 찾지 못했습니다."); return true; }
                int n = args.length > 3 ? parse(args[3], 1) : 1;
                var items = new ArrayList<ItemStack>();
                if (g.kind() == 3) items.add(gear.create(g, -1, Math.clamp(n, 1, 64)));
                else for (int i = 0; i < Math.clamp(n, 1, 36); i++) items.add(gear.create(g, -1, 1));
                give(t, items, "fishing-admin:" + UUID.randomUUID());
                sender.sendMessage("§a" + t.getName() + " 에게 " + g.name() + " 지급");
            }
            case "수조" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player self ? self : null;
                if (t == null || tankWindow == null) { sender.sendMessage("§c플레이어를 찾지 못했습니다."); return true; }
                if (!FishTankBridge.modded(t)) { sender.sendMessage("§c" + t.getName() + " 은(는) 모드가 없어서 수조 창을 열 수 없습니다."); return true; }
                tankWindow.open(t);
            }
            case "물고기지급" -> {
                if (args.length < 3) { sender.sendMessage("§e/낚시관리 물고기지급 <플레이어> <물고기ID> [cm]"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                var f = config.fishById.get(args[2]);
                if (t == null || f == null) { sender.sendMessage("§c플레이어나 물고기를 찾지 못했습니다."); return true; }
                float cm = args.length > 3 ? (float) parseDouble(args[3], f.maxSize()) : f.maxSize();
                give(t, List.of(gear.fish(f, cm)), "fishing-admin:" + UUID.randomUUID());
                sender.sendMessage("§a" + t.getName() + " 에게 " + f.name() + " (" + cm + "cm) 지급");
            }
            case "도감초기화" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) { sender.sendMessage("§c플레이어를 찾지 못했습니다."); return true; }
                store.reset(t.getUniqueId());
                sender.sendMessage("§a" + t.getName() + " 의 낚시 도감을 초기화했습니다.");
            }
            case "미리보기" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§c게임 안에서만 쓸 수 있습니다."); return true; }
                int bg = args.length > 1 ? Math.max(0, List.of("민물", "바다", "얼음", "동굴").indexOf(args[1])) : 0;
                var old = sessions.remove(p.getUniqueId());
                if (old != null) removeHook(old);
                var s = new Session(p.getUniqueId());
                s.preview = true; s.previewBackground = bg; s.state = SINKING;
                s.maxDepth = Math.min(FishingProtocol.MAX_DEPTH, gear.read(rodOf(p)).line().gear().a());
                sessions.put(p.getUniqueId(), s);
                send(p, new FishingProtocol.Session(s.id, FishingProtocol.PHASE_WATER, bg, s.maxDepth, 3, 4096, gear.protocol(gear.read(rodOf(p))),
                        "미리보기: 보상·내구도·도감 기록이 없어요."));
            }
            default -> sender.sendMessage("§c알 수 없는 명령입니다.");
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("낚시도감")) return args.length == 1 && sender.hasPermission("magiccodex.fishing.admin") ? null : List.of();
        if (args.length == 1) return filter(List.of("리로드", "장비지급", "물고기지급", "도감초기화", "미리보기", "수조"), args[0]);
        if (args.length == 2 && List.of("장비지급", "물고기지급", "도감초기화", "수조").contains(args[0])) return null;
        if (args.length == 2 && args[0].equals("미리보기")) return filter(List.of("민물", "바다", "얼음", "동굴"), args[1]);
        if (args.length == 3 && args[0].equals("장비지급")) { var ids = new ArrayList<String>(); for (var m : config.gear) ids.addAll(m.keySet()); return filter(ids, args[2]); }
        if (args.length == 3 && args[0].equals("물고기지급")) return filter(new ArrayList<>(config.fishById.keySet()), args[2]);
        return List.of();
    }
    private static List<String> filter(List<String> all, String prefix) { return all.stream().filter(s -> s.startsWith(prefix)).limit(80).toList(); }
    private static int parse(String s, int otherwise) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return otherwise; } }
    private static double parseDouble(String s, double otherwise) { try { return Double.parseDouble(s); } catch (NumberFormatException e) { return otherwise; } }

    // ------------------------------------------------------------------ io

    private boolean send(Player p, Frame frame) {
        try { p.sendPluginMessage(plugin, FishingProtocol.RESPONSE, FishingProtocol.encode(frame)); return true; }
        catch (IllegalArgumentException e) { plugin.getLogger().warning("낚시 패킷 만들기 실패: " + e.getMessage()); return false; }
    }
    private static String cut(String s) { return s == null ? "" : s.length() > FishingProtocol.MAX_NOTICE ? s.substring(0, FishingProtocol.MAX_NOTICE) : s; }
    private static String cutText(String s) { return s == null ? "" : s.length() > FishingProtocol.MAX_TEXT ? s.substring(0, FishingProtocol.MAX_TEXT) : s; }
}
