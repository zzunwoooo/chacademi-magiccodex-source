package school.magiccodex.paper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.ArtifactProtocol;
import school.magiccodex.protocol.ArtifactProtocol.Entry;

/**
 * Artifact codex (아티팩트 도감). Definitions live in plugins/MagicCodexBridge/artifacts.yml so the owner can edit them at
 * any time (/아티팩트관리 새로고침); clients receive the catalogue from here, so the mod never needs a rebuild for content.
 *
 * Ownership: permission magiccodex.artifact.own.<id> (LuckPerms, shared across the school/wild servers like brooms).
 * Equipped list: player PDC key "artifact_equipped". NOTE: PDC is per server; cross-server sync belongs in
 * PlayerStateStore (left for the owner/GPT because the DB schema is being reworked).
 * Use key (2026-10-15): artifacts with `use: true` can be used with the client's artifact key (default X). The server
 * checks ownership, equipment and the per-player cooldown, then fires {@link ArtifactUseEvent}; the cooldown starts
 * only when a listener reports the effect happened. Abilities themselves are NOT implemented yet.
 * Cooldowns live in memory per server (kept across relogs on the same server, not across servers).
 *
 * Summon (유물함 소환, 2026-10-15): /아티팩트소환 opens the client window. [소환하기] spends one ItemsAdder key item
 * (artifacts.yml summon.item, default item:random_artifact), rolls a grade by summon.weights and then a uniform
 * artifact of that grade. New ones are granted as the ownership permission (LuckPerms); duplicates mail
 * summon.duplicate.item (item:artifact_shard). The client only plays the reveal; the result is decided here.
 */
final class ArtifactBridge implements PluginMessageListener, Listener, CommandExecutor, TabCompleter {
    private final MagicCodexBridge plugin;
    private final NamespacedKey equippedKey;
    private volatile List<Entry> catalog = List.of();
    private volatile long revision;
    private int maxEquipped = 1;
    private boolean ownsAll;
    /** Players whose client has asked at least once (only they get pushes after a reload). */
    private final Set<UUID> listening = new HashSet<>();
    private final Map<UUID, Long> limits = new HashMap<>();
    /** player -> artifact id -> System.currentTimeMillis() when it is ready again. Expired entries are dropped lazily. */
    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();
    // ---- summon settings (artifacts.yml summon:)
    private String summonItem = "item:random_artifact", shardItem = "item:artifact_shard", summonItemName = "유물함 열쇠";
    private int summonCost = 1;
    private final double[] weights = {5, 3, 1, 0.1};
    private final int[] shardAmount = {1, 1, 1, 1};
    private String mailTitle = "아티팩트 조각", mailBody = "이미 가진 아티팩트가 나와 조각으로 바꿔 드렸습니다.";
    private final ItemsAdderItems itemsAdder;
    /** Grants sent to LuckPerms but maybe not visible as a permission yet (fast repeated summons). */
    private final Map<UUID, Set<String>> pendingGrants = new HashMap<>();
    private final java.util.Random random = new java.security.SecureRandom();

    ArtifactBridge(MagicCodexBridge plugin) throws Exception {
        this.plugin = plugin;
        equippedKey = new NamespacedKey(plugin, "artifact_equipped");
        itemsAdder = new ItemsAdderItems(plugin.getLogger());
        if (!new File(plugin.getDataFolder(), "artifacts.yml").exists()) plugin.saveResource("artifacts.yml", false);
        reload();
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ArtifactProtocol.REQUEST, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, ArtifactProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var command = Objects.requireNonNull(plugin.getCommand("아티팩트관리"));
        command.setExecutor(this); command.setTabCompleter(this);
        var summon = Objects.requireNonNull(plugin.getCommand("아티팩트소환"));
        summon.setExecutor(this); summon.setTabCompleter(this);
    }

    // ------------------------------------------------------------------ catalogue

    void reload() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(new File(plugin.getDataFolder(), "artifacts.yml"));
        int max = Math.clamp(yaml.getInt("settings.max-equipped", 1), 1, ArtifactProtocol.MAX_EQUIPPED);
        boolean all = yaml.getBoolean("settings.test-owns-all", false);
        ConfigurationSection root = yaml.getConfigurationSection("artifacts");
        record Row(Entry entry, int order) {}
        var rows = new ArrayList<Row>();
        if (root != null) for (String id : root.getKeys(false)) {
            var s = root.getConfigurationSection(id);
            if (s == null) throw new IllegalArgumentException("artifacts." + id + " 형식 오류");
            if (!s.getBoolean("enabled", true)) continue;
            if (!ArtifactProtocol.validId(id)) throw new IllegalArgumentException("아이디는 영문 소문자·숫자·_ 만: " + id);
            int grade = grade(s.getString("grade", ""));
            if (grade < 0) throw new IllegalArgumentException(id + ".grade 는 common/rare/unique/legendary (또는 일반/고급/유일/전설)");
            String name = s.getString("name", "").trim();
            if (name.isEmpty() || name.length() > ArtifactProtocol.MAX_NAME) throw new IllegalArgumentException(id + ".name 은 1~40자");
            String description = clip(s.getString("description", ""), ArtifactProtocol.MAX_DESCRIPTION, id + ".description");
            String effect = clip(s.getString("effect", ""), ArtifactProtocol.MAX_EFFECT, id + ".effect");
            String icon = clip(s.getString("icon", ""), ArtifactProtocol.MAX_ICON, id + ".icon");
            if (!icon.isBlank() && !icon.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png")) throw new IllegalArgumentException(id + ".icon 은 네임스페이스:경로.png 형식");
            int cooldown = ArtifactProtocol.PASSIVE;
            if (s.getBoolean("use", false)) {
                cooldown = s.getInt("cooldown", 0);
                if (cooldown < 0 || cooldown > ArtifactProtocol.MAX_COOLDOWN) throw new IllegalArgumentException(id + ".cooldown 은 0~" + ArtifactProtocol.MAX_COOLDOWN + "초");
            }
            rows.add(new Row(new Entry(id, name, grade, description, effect, icon, cooldown), s.getInt("order", 0)));
        }
        if (rows.size() > ArtifactProtocol.MAX_ARTIFACTS) throw new IllegalArgumentException("아티팩트는 최대 " + ArtifactProtocol.MAX_ARTIFACTS + "개");
        rows.sort(Comparator.comparingInt((Row r) -> r.entry().grade()).thenComparingInt(Row::order).thenComparing(r -> r.entry().id()));
        var next = rows.stream().map(Row::entry).toList();
        ArtifactProtocol.catalog(0, next); // fail now (not on send) if a part would be too large
        var crc = new CRC32();
        crc.update((max + "|" + next).getBytes(StandardCharsets.UTF_8));
        // summon settings
        var sm = yaml.getConfigurationSection("summon");
        String item = sm == null ? "item:random_artifact" : sm.getString("item", "item:random_artifact").trim();
        String shard = sm == null ? "item:artifact_shard" : sm.getString("duplicate.item", "item:artifact_shard").trim();
        if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !shard.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("summon.item / summon.duplicate.item 은 아이템애더 네임스페이스:아이디");
        int cost = sm == null ? 1 : sm.getInt("cost", 1);
        if (cost < 1 || cost > 64) throw new IllegalArgumentException("summon.cost 는 1~64");
        double[] w = new double[4]; int[] shards = new int[4]; double total = 0;
        for (int g = 0; g < 4; g++) {
            String key = ArtifactProtocol.GRADE_KEYS[g];
            w[g] = sm == null ? new double[]{5, 3, 1, 0.1}[g] : sm.getDouble("weights." + key, new double[]{5, 3, 1, 0.1}[g]);
            shards[g] = sm == null ? 1 : sm.getInt("duplicate.amount." + key, 1);
            if (w[g] < 0 || !Double.isFinite(w[g])) throw new IllegalArgumentException("summon.weights." + key + " 는 0 이상");
            if (shards[g] < 0 || shards[g] > 64) throw new IllegalArgumentException("summon.duplicate.amount." + key + " 는 0~64");
            total += w[g];
        }
        if (total <= 0) throw new IllegalArgumentException("summon.weights 합이 0");
        catalog = next; revision = crc.getValue() == 0 ? 1 : crc.getValue(); maxEquipped = max; ownsAll = all;
        summonItem = item; shardItem = shard; summonCost = cost;
        System.arraycopy(w, 0, weights, 0, 4); System.arraycopy(shards, 0, shardAmount, 0, 4);
        if (sm != null) {
            mailTitle = sm.getString("duplicate.mail-title", mailTitle); mailBody = sm.getString("duplicate.mail-body", mailBody);
            summonItemName = clip(sm.getString("item-name", summonItemName), 30, "summon.item-name");
        }
    }

    private static int grade(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < 4; i++) if (v.equals(ArtifactProtocol.GRADE_KEYS[i]) || v.equals(ArtifactProtocol.GRADE_LABELS[i])) return i;
        return -1;
    }

    private static String clip(String value, int max, String where) {
        String v = value == null ? "" : value.strip();
        if (v.length() > max) throw new IllegalArgumentException(where + " 는 최대 " + max + "자");
        return v;
    }

    // ------------------------------------------------------------------ ownership / equipped

    boolean owns(Player p, String id) {
        return ownsAll || p.hasPermission("magiccodex.artifact.own." + id) || p.hasPermission("magiccodex.artifact.own.*")
                || pendingGrants.getOrDefault(p.getUniqueId(), Set.of()).contains(id);
    }

    /** Equipped ids that still exist and are still owned (stale ones are dropped silently). */
    List<String> equipped(Player p) {
        String raw = p.getPersistentDataContainer().getOrDefault(equippedKey, PersistentDataType.STRING, "");
        var known = new HashSet<String>(); for (Entry e : catalog) known.add(e.id());
        var out = new ArrayList<String>();
        for (String id : raw.split(",")) {
            if (id.isBlank() || !known.contains(id) || !owns(p, id) || out.contains(id)) continue;
            out.add(id);
            if (out.size() >= maxEquipped) break;
        }
        return out;
    }

    private void store(Player p, List<String> ids) {
        if (ids.isEmpty()) p.getPersistentDataContainer().remove(equippedKey);
        else p.getPersistentDataContainer().set(equippedKey, PersistentDataType.STRING, String.join(",", ids));
    }

    // ------------------------------------------------------------------ network

    @Override public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!ArtifactProtocol.REQUEST.equals(channel) || !p.isOnline()) return;
        ArtifactProtocol.Request r;
        try { r = ArtifactProtocol.request(bytes); } catch (IllegalArgumentException e) { return; }
        long now = System.currentTimeMillis();
        if (now < limits.getOrDefault(p.getUniqueId(), 0L)) return;
        limits.put(p.getUniqueId(), now + 150);
        listening.add(p.getUniqueId());
        String notice = "";
        if (r.action() == ArtifactProtocol.EQUIP || r.action() == ArtifactProtocol.UNEQUIP) {
            if (!plugin.playerStateReady(p)) notice = "캐릭터 정보를 불러오는 중입니다.";
            else if (catalog.stream().noneMatch(e -> e.id().equals(r.id()))) notice = "도감에 없는 아티팩트입니다.";
            else if (!owns(p, r.id())) notice = "아직 발견하지 못한 아티팩트입니다.";
            else {
                var list = new ArrayList<>(equipped(p));
                if (r.action() == ArtifactProtocol.EQUIP) {
                    if (list.contains(r.id())) notice = "이미 장착 중입니다.";
                    else {
                        if (list.size() >= maxEquipped) {
                            if (maxEquipped == 1) list.clear();
                            else { notice = "장착 칸이 가득 찼습니다. 먼저 하나를 해제해 주세요."; }
                        }
                        if (notice.isEmpty()) { list.add(r.id()); store(p, list); notice = "장착했습니다."; }
                    }
                } else {
                    if (list.remove(r.id())) { store(p, list); notice = "해제했습니다."; } else notice = "장착 중이 아닙니다.";
                }
            }
        } else if (r.action() == ArtifactProtocol.USE) {
            notice = use(p, r.id(), now);
        } else if (r.action() == ArtifactProtocol.SUMMON_INFO || r.action() == ArtifactProtocol.SUMMON) {
            if (r.knownCatalog() != revision) send(p, true, "");
            if (r.action() == ArtifactProtocol.SUMMON) {
                var result = summon(p);
                send(p, false, ""); // owned bitset now includes the new artifact (codex + NEW badge stay in sync)
                sendSummon(p, result);
            } else sendSummon(p, ArtifactProtocol.Summon.simple(ArtifactProtocol.SUMMON_STATE, keys(p), summonInfoText()));
            return;
        }
        send(p, r.knownCatalog() != revision, notice);
    }

    private Entry entry(String id) { for (Entry e : catalog) if (e.id().equals(id)) return e; return null; }

    private long remaining(UUID player, String id, long now) {
        var map = readyAt.get(player);
        if (map == null) return 0;
        map.values().removeIf(t -> t <= now);
        if (map.isEmpty()) { readyAt.remove(player); return 0; }
        return Math.max(0, map.getOrDefault(id, 0L) - now);
    }

    /** Artifact use key. Returns the action-bar notice. */
    private String use(Player p, String id, long now) {
        if (!plugin.playerStateReady(p)) return "캐릭터 정보를 불러오는 중입니다.";
        Entry e = entry(id);
        if (e == null || !owns(p, id) || !equipped(p).contains(id)) return "장착한 아티팩트가 없습니다.";
        if (!e.usable()) return e.name() + "은(는) 사용하는 아티팩트가 아닙니다.";
        long left = remaining(p.getUniqueId(), id, now);
        if (left > 0) return e.name() + " 재사용 대기 " + ((left + 999) / 1000) + "초";
        var event = new ArtifactUseEvent(p, id, e.cooldown());
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isUsed()) return event.getResponse() != null ? event.getResponse() : e.name() + "의 효과는 아직 준비 중입니다.";
        if (e.cooldown() > 0) readyAt.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(id, now + e.cooldown() * 1000L);
        return event.getResponse() != null ? event.getResponse() : "";
    }

    // ------------------------------------------------------------------ summon

    private int keys(Player p) {
        if (!itemsAdder.available()) return 0;
        int n = 0;
        for (var item : p.getInventory().getStorageContents()) if (summonItem.equals(itemsAdder.idOf(item))) n += item.getAmount();
        return n;
    }

    private boolean takeKeys(Player p, int amount) {
        var inv = p.getInventory(); var contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            var item = contents[i];
            if (!summonItem.equals(itemsAdder.idOf(item))) continue;
            int take = Math.min(left, item.getAmount());
            if (take == item.getAmount()) inv.setItem(i, null); else item.setAmount(item.getAmount() - take);
            left -= take;
        }
        return left == 0;
    }

    private int rollGrade() {
        double total = 0; for (int g = 0; g < 4; g++) if (hasGrade(g)) total += weights[g];
        double x = random.nextDouble() * total;
        for (int g = 0; g < 4; g++) { if (!hasGrade(g)) continue; x -= weights[g]; if (x < 0) return g; }
        for (int g = 3; g >= 0; g--) if (hasGrade(g) && weights[g] > 0) return g;
        return 0;
    }
    private boolean hasGrade(int g) { if (weights[g] <= 0) return false; for (Entry e : catalog) if (e.grade() == g) return true; return false; }

    /** Spends one key and rolls. Returns RESULT or REFUSED (keys untouched on refusal). */
    private ArtifactProtocol.Summon summon(Player p) {
        int have = keys(p);
        java.util.function.Function<String, ArtifactProtocol.Summon> refuse = msg -> ArtifactProtocol.Summon.simple(ArtifactProtocol.SUMMON_REFUSED, have, msg);
        if (!plugin.playerStateReady(p)) return refuse.apply("캐릭터 정보를 불러오는 중입니다.");
        if (!itemsAdder.available()) return refuse.apply("소환 아이템을 확인할 수 없습니다. 관리자에게 문의해 주세요.");
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return refuse.apply("아티팩트를 지급할 수 없습니다. 관리자에게 문의해 주세요.");
        boolean any = false; for (int g = 0; g < 4; g++) any |= hasGrade(g);
        if (!any) return refuse.apply("소환할 수 있는 아티팩트가 없습니다.");
        if (have < summonCost) return refuse.apply("유물함 열쇠가 부족합니다.");
        if (!takeKeys(p, summonCost)) return refuse.apply("유물함 열쇠를 사용하지 못했습니다.");
        int grade = rollGrade();
        var pool = catalog.stream().filter(e -> e.grade() == grade).toList();
        Entry won = pool.get(random.nextInt(pool.size()));
        boolean duplicate = owns(p, won.id());
        int shards = 0;
        String summonId = UUID.randomUUID().toString();
        if (!duplicate) {
            pendingGrants.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(won.id());
            UUID uid = p.getUniqueId(); String id = won.id();
            ArtifactGrants.grant(uid, "magiccodex.artifact.own." + id).whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                var set = pendingGrants.get(uid); if (set != null) { set.remove(id); if (set.isEmpty()) pendingGrants.remove(uid); }
                if (error != null) {
                    plugin.getLogger().warning("ARTIFACT SUMMON GRANT FAILED player=" + uid + " artifact=" + id + " summon=" + summonId + ": " + error);
                    refundKeys(uid, summonId);
                }
            }));
        } else {
            shards = shardAmount[grade];
            if (shards > 0) mailShards(p.getUniqueId(), shards, won, summonId);
        }
        plugin.getLogger().info("ARTIFACT SUMMON player=" + p.getName() + " (" + p.getUniqueId() + ") artifact=" + won.id()
                + " grade=" + ArtifactProtocol.GRADE_KEYS[grade] + (duplicate ? " duplicate shards=" + shards : " new") + " summon=" + summonId);
        int[] decoys = new int[ArtifactProtocol.DECOYS];
        for (int i = 0; i < decoys.length; i++) decoys[i] = rollGrade();
        String notice = duplicate ? (shards > 0 ? "이미 가진 아티팩트입니다. 아티팩트 조각 " + shards + "개를 우편으로 보냈습니다." : "이미 가진 아티팩트입니다.") : "";
        return new ArtifactProtocol.Summon(ArtifactProtocol.SUMMON_RESULT, keys(p), won.id(), grade, !duplicate, shards, decoys, notice);
    }

    private void mailShards(UUID player, int amount, Entry won, String summonId) {
        var mail = Bukkit.getServicesManager().load(MailService.class);
        var item = itemsAdder.create(shardItem, amount);
        if (mail == null || item == null) {
            plugin.getLogger().warning("ARTIFACT SHARD NOT SENT player=" + player + " amount=" + amount + " summon=" + summonId
                    + (mail == null ? " (우편 기능 없음)" : " (아이템 " + shardItem + " 없음)"));
            return;
        }
        mail.sendSystem("artifact-summon:" + summonId, player, mailTitle, mailBody + " (" + won.name() + ")", List.of(item))
                .whenComplete((id, error) -> { if (error != null) plugin.getLogger().warning("ARTIFACT SHARD MAIL FAILED summon=" + summonId + ": " + error); });
    }

    /** Ownership could not be saved: give the key back by mail so nothing is lost. */
    private void refundKeys(UUID player, String summonId) {
        var mail = Bukkit.getServicesManager().load(MailService.class);
        var item = itemsAdder.create(summonItem, summonCost);
        if (mail == null || item == null) { plugin.getLogger().warning("ARTIFACT SUMMON REFUND NEEDED player=" + player + " keys=" + summonCost + " summon=" + summonId); return; }
        mail.sendSystem("artifact-summon-refund:" + summonId, player, "유물함 열쇠 반환", "아티팩트 지급에 실패해 유물함 열쇠를 돌려 드립니다.", List.of(item));
    }

    private void sendSummon(Player p, ArtifactProtocol.Summon frame) {
        if (!p.getListeningPluginChannels().contains(ArtifactProtocol.RESPONSE)) return;
        p.sendPluginMessage(plugin, ArtifactProtocol.RESPONSE, ArtifactProtocol.encode(frame));
    }

    /** /아티팩트소환 [플레이어]: opens the client window. OPEN/STATE notices carry the grade chances for the window. */
    private boolean openSummon(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("magiccodex.artifact.admin")) { sender.sendMessage("§c다른 플레이어의 소환 창은 관리자만 열 수 있습니다."); return true; }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§c접속 중인 플레이어가 아닙니다: " + args[0]); return true; }
        } else if (sender instanceof Player p) target = p;
        else { sender.sendMessage("/아티팩트소환 <플레이어>"); return true; }
        if (!target.getListeningPluginChannels().contains(ArtifactProtocol.RESPONSE)) { sender.sendMessage("§c차카데미 모드가 있어야 소환 창을 열 수 있습니다."); return true; }
        listening.add(target.getUniqueId());
        send(target, true, "");
        sendSummon(target, ArtifactProtocol.Summon.simple(ArtifactProtocol.SUMMON_OPEN, keys(target), summonInfoText()));
        return true;
    }

    private void send(Player p, boolean withCatalog, String notice) {
        if (!p.getListeningPluginChannels().contains(ArtifactProtocol.RESPONSE)) return;
        var snapshot = catalog; long rev = revision;
        if (withCatalog) for (byte[] part : ArtifactProtocol.catalog(rev, snapshot)) p.sendPluginMessage(plugin, ArtifactProtocol.RESPONSE, part);
        boolean[] owned = new boolean[snapshot.size()];
        for (int i = 0; i < owned.length; i++) owned[i] = owns(p, snapshot.get(i).id());
        String n = notice.length() > ArtifactProtocol.MAX_NOTICE ? notice.substring(0, ArtifactProtocol.MAX_NOTICE) : notice;
        var equipped = equipped(p);
        long now = System.currentTimeMillis();
        int[] cooldown = new int[equipped.size()];
        for (int i = 0; i < cooldown.length; i++)
            cooldown[i] = (int) Math.min(remaining(p.getUniqueId(), equipped.get(i), now), ArtifactProtocol.MAX_COOLDOWN * 1000L);
        p.sendPluginMessage(plugin, ArtifactProtocol.RESPONSE,
                ArtifactProtocol.encode(new ArtifactProtocol.State(rev, maxEquipped, owned, equipped, n, cooldown)));
    }

    /** OPEN/STATE notice: "<key item name>\u0001<cost>\u0001<grade chances>" for the window's guide text. */
    private String summonInfoText() { return summonItemName + "\u0001" + summonCost + "\u0001" + rates(); }

    /** Grade chances as shown to players, e.g. 일반 55.0% / 고급 33.0% / 유일 11.0% / 전설 1.1%. */
    private String rates() {
        double total = 0; for (int g = 0; g < 4; g++) if (hasGrade(g)) total += weights[g];
        var b = new StringBuilder();
        for (int g = 0; g < 4; g++) {
            if (b.length() > 0) b.append(" / ");
            b.append(ArtifactProtocol.GRADE_LABELS[g]).append(' ').append(total <= 0 || !hasGrade(g) ? "0" : String.format(java.util.Locale.ROOT, "%.1f", weights[g] * 100 / total)).append('%');
        }
        return b.toString();
    }

    @EventHandler public void quit(PlayerQuitEvent e) { listening.remove(e.getPlayer().getUniqueId()); limits.remove(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------ admin

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("아티팩트소환")) return openSummon(sender, args);
        String sub = args.length == 0 ? "" : args[0];
        switch (sub) {
            case "새로고침", "reload" -> {
                try { reload(); } catch (Exception e) { sender.sendMessage("§cartifacts.yml 오류: " + e.getMessage()); return true; }
                for (Player p : Bukkit.getOnlinePlayers()) if (listening.contains(p.getUniqueId())) send(p, true, "");
                sender.sendMessage("§a아티팩트 " + catalog.size() + "개를 다시 불러왔습니다. (장착 최대 " + maxEquipped + "개" + (ownsAll ? ", 전원 보유 테스트 모드" : "") + ")");
                sender.sendMessage("§7소환: " + summonItem + " " + summonCost + "개, 확률 " + rates() + ", 중복 → " + shardItem);
            }
            case "목록", "list" -> {
                for (Entry e : catalog) sender.sendMessage("§7[" + ArtifactProtocol.GRADE_LABELS[e.grade()] + "] §f" + e.name() + " §8" + e.id()
                        + (e.usable() ? " §b(사용형" + (e.cooldown() > 0 ? " " + e.cooldown() + "초" : "") + ")" : ""));
                sender.sendMessage("§7총 " + catalog.size() + "개");
            }
            case "확인", "check" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) { sender.sendMessage("/아티팩트관리 확인 <플레이어>"); return true; }
                long owned = catalog.stream().filter(e -> owns(target, e.id())).count();
                sender.sendMessage("§f" + target.getName() + "§7: 보유 " + owned + " / " + catalog.size() + ", 장착 " + equipped(target));
            }
            default -> sender.sendMessage("/아티팩트관리 <새로고침|목록|확인 <플레이어>>  · 지급은 권한 magiccodex.artifact.own.<아이디>");
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("아티팩트소환"))
            return args.length == 1 && sender.hasPermission("magiccodex.artifact.admin") ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
        if (args.length == 1) return List.of("새로고침", "목록", "확인");
        if (args.length == 2 && args[0].equals("확인")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
