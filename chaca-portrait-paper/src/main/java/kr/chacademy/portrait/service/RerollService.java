package kr.chacademy.portrait.service;

import io.papermc.paper.event.player.AsyncChatEvent;
import kr.chacademy.portrait.ChacaPortraitPlugin;
import kr.chacademy.portrait.core.PortraitSettings;
import kr.chacademy.portrait.core.PromptBuilder;
import kr.chacademy.portrait.data.PortraitStorage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import school.magiccodex.portrait.PortraitProtocol;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 다시 그리기 아이템 (ItemsAdder item:reroll 우클릭).
 * 흐름: 우클릭 → 입력창(HUD) 또는 채팅 입력 → 문장 검사 → 같은 칸에 아이템이 그대로 있으면 1개 차감 → 기록(PENDING) → 생성.
 * 성공하면 DONE(아이템 소모), 실패하면 REFUND_DUE → 접속 중이면 바로, 아니면 다음 접속 때 돌려준다.
 * 서버가 생성 중에 꺼지면 다음 시작 때 이 서버의 PENDING을 REFUND_DUE로 바꾼다.
 * 아이템 차감 후 기록 전에 서버가 꺼지는 아주 짧은 구간은 복제 방지를 위해 돌려주지 않는다 (관리자 지급으로 처리).
 */
public final class RerollService implements Listener {

    private record Pending(String token, int slot, ItemStack snapshot, long expiresAt, boolean chat) {
    }

    private final ChacaPortraitPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    /** 문장 검사 중 (중복 제출 방지). */
    private final Map<UUID, String> checking = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickGate = new ConcurrentHashMap<>();

    public RerollService(ChacaPortraitPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        PortraitSettings s = plugin.settings();
        String id = plugin.itemsAdder().idOf(hand);
        if (id == null || !id.equalsIgnoreCase(s.rerollItemId)) {
            return;
        }
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = clickGate.get(p.getUniqueId());
        if (last != null && now - last < 700) {
            return;
        }
        clickGate.put(p.getUniqueId(), now);
        begin(p);
    }

    private void begin(Player p) {
        PortraitSettings s = plugin.settings();
        UUID uuid = p.getUniqueId();
        if (!s.enabled || !s.hasKey() || plugin.service().referenceError() != null) {
            p.sendMessage(s.message("disabled"));
            return;
        }
        if (!p.hasPermission("chacaportrait.reroll")) {
            return;
        }
        if (plugin.service().busy(uuid) || checking.containsKey(uuid)) {
            p.sendMessage(s.message("reroll-busy"));
            return;
        }
        if (plugin.service().waitingCount() >= s.maxWaiting) {
            p.sendMessage(s.message("reroll-busy"));
            return;
        }
        if (kr.chacademy.portrait.skin.SkinFetcher.refOf(p).url() == null) {
            p.sendMessage(s.message("reroll-no-skin"));
            return;
        }
        int slot = p.getInventory().getHeldItemSlot();
        ItemStack snapshot = p.getInventory().getItem(slot);
        if (snapshot == null) {
            return;
        }
        ItemStack one = snapshot.clone();
        one.setAmount(1);
        String day = LocalDate.now(PortraitService.ZONE).toString();
        plugin.service().then(plugin.db().call(() -> plugin.storage().rerollUsage(uuid, day)), usage -> {
            if (!p.isOnline()) {
                return;
            }
            long wait = usage[0] + s.rerollCooldownSeconds * 1000L - System.currentTimeMillis();
            if (wait > 0) {
                p.sendMessage(s.message("reroll-cooldown", "seconds", (wait + 999) / 1000));
                return;
            }
            if (s.rerollDailyLimit > 0 && usage[1] >= s.rerollDailyLimit) {
                p.sendMessage(s.message("reroll-daily"));
                return;
            }
            String token = PortraitService.newId();
            boolean hud = plugin.channel().capable(p);
            pending.put(uuid, new Pending(token, slot, one, System.currentTimeMillis() + s.promptTimeoutSeconds * 1000L, !hud));
            if (hud) {
                plugin.channel().openPrompt(p, token, "원하는 느낌을 적어 주세요 (비워 두면 기본 그림). 예: 밤하늘 배경, 웃는 얼굴");
            } else {
                p.sendMessage(s.message("reroll-prompt-chat"));
            }
        }, ex -> p.sendMessage(s.message("disabled")));
    }

    /** HUD 입력창 응답 (메인 스레드). */
    public void answer(Player p, String token, String text, boolean proceed) {
        Pending pd = pending.get(p.getUniqueId());
        if (pd == null || !pd.token().equals(token)) {
            return;
        }
        handle(p, pd, text, proceed);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Pending pd = pending.get(e.getPlayer().getUniqueId());
        if (pd == null || !pd.chat()) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).strip();
        Player p = e.getPlayer();
        plugin.main(() -> {
            Pending now = pending.get(p.getUniqueId());
            if (now == null || !now.token().equals(pd.token())) {
                return;
            }
            if (text.equals("취소")) {
                handle(p, now, "", false);
            } else {
                handle(p, now, text.equals("기본") ? "" : text, true);
            }
        });
    }

    private void handle(Player p, Pending pd, String text, boolean proceed) {
        PortraitSettings s = plugin.settings();
        UUID uuid = p.getUniqueId();
        if (System.currentTimeMillis() > pd.expiresAt()) {
            pending.remove(uuid, pd);
            p.sendMessage(s.message("reroll-cancelled"));
            return;
        }
        if (!proceed) {
            pending.remove(uuid, pd);
            p.sendMessage(s.message("reroll-cancelled"));
            return;
        }
        String request = text == null ? "" : text.strip();
        if (!PortraitProtocol.validPrompt(request)) {
            p.sendMessage(s.message("reroll-rejected"));
            return; // 입력은 유지 (다시 보낼 수 있음)
        }
        if (checking.putIfAbsent(uuid, pd.token()) != null) {
            return;
        }
        pending.remove(uuid, pd);
        if (request.isEmpty() || !s.moderationEnabled) {
            take(p, pd, request);
            return;
        }
        String clean = PromptBuilder.clean(request, PortraitProtocol.MAX_PROMPT_CHARS);
        java.util.concurrent.CompletableFuture<Boolean> check = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return !plugin.ai().flagged(s, clean);
            } catch (Exception ex) {
                plugin.getLogger().info("[ChacaPortrait] 문장 검사 실패 → 거부: " + ex.getMessage());
                return false;
            }
        });
        plugin.service().then(check.orTimeout(30, TimeUnit.SECONDS), ok -> {
            if (!Boolean.TRUE.equals(ok)) {
                rejected(p, pd);
                return;
            }
            take(p, pd, clean);
        }, ex -> rejected(p, pd));
    }

    /** 문장이 거부됨: 같은 토큰으로 다시 입력받는다 (아이템은 그대로). */
    private void rejected(Player p, Pending pd) {
        UUID uuid = p.getUniqueId();
        checking.remove(uuid);
        if (!p.isOnline()) {
            return;
        }
        PortraitSettings s = plugin.settings();
        p.sendMessage(s.message("reroll-rejected"));
        if (System.currentTimeMillis() <= pd.expiresAt()) {
            pending.put(uuid, pd);
            if (!pd.chat()) {
                plugin.channel().openPrompt(p, pd.token(), "그 요청은 쓸 수 없어요. 다른 문장으로 적어 주세요.");
            }
        }
    }

    /** 메인 스레드: 같은 칸에 같은 아이템이 있으면 1개 차감 → 기록 → 작업. */
    private void take(Player p, Pending pd, String request) {
        PortraitSettings s = plugin.settings();
        UUID uuid = p.getUniqueId();
        if (!p.isOnline()) {
            checking.remove(uuid);
            return;
        }
        if (plugin.service().busy(uuid)) {
            checking.remove(uuid);
            p.sendMessage(s.message("reroll-busy"));
            return;
        }
        ItemStack cur = p.getInventory().getItem(pd.slot());
        if (cur == null || !cur.isSimilar(pd.snapshot()) || cur.getAmount() < 1) {
            checking.remove(uuid);
            p.sendMessage(s.message("reroll-cancelled"));
            return;
        }
        PortraitService.Job job = PortraitService.jobFor(PortraitService.Kind.REROLL, p, request, pd.token(), List.of(), null);
        if (job.skin().url() == null) {
            checking.remove(uuid);
            p.sendMessage(s.message("reroll-no-skin"));
            return;
        }
        if (cur.getAmount() == 1) {
            p.getInventory().setItem(pd.slot(), null);
        } else {
            cur.setAmount(cur.getAmount() - 1);
            p.getInventory().setItem(pd.slot(), cur);
        }
        byte[] itemBytes = pd.snapshot().serializeAsBytes();
        // 차감한 인벤토리를 바로 저장: 생성 중 서버가 비정상 종료돼도 옛 저장본(아이템 있음) + 반환으로 복제되지 않게
        try {
            p.saveData();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[ChacaPortrait] 플레이어 데이터 저장 실패: " + ex.getMessage());
        }
        plugin.service().then(plugin.db().call(() -> {
            plugin.storage().insertReroll(pd.token(), uuid, s.serverId, itemBytes, request);
            return Boolean.TRUE;
        }), ok -> {
            checking.remove(uuid);
            boolean submitted = plugin.service().submit(job, null);
            if (!submitted) {
                failed(uuid, pd.token());
                if (p.isOnline()) {
                    p.sendMessage(s.message("reroll-busy"));
                }
                return;
            }
            if (p.isOnline()) {
                int pos = plugin.service().waitingCount();
                p.sendMessage(pos > 0 ? s.message("queued", "position", pos) : s.message("started"));
            }
        }, ex -> {
            // 기록 실패 → 아이템 즉시 반환 (작업은 시작하지 않음)
            checking.remove(uuid);
            plugin.getLogger().warning("[ChacaPortrait] 다시 그리기 기록 실패: " + ex.getMessage());
            give(p, pd.snapshot());
            if (p.isOnline()) {
                p.sendMessage(s.message("reroll-failed"));
            }
        });
    }

    /** 생성 실패 (어느 스레드든): PENDING → REFUND_DUE 후, 접속 중이면 돌려준다. */
    public void failed(UUID uuid, String token) {
        plugin.service().then(plugin.db().call(() ->
                plugin.storage().moveReroll(token, PortraitStorage.R_PENDING, PortraitStorage.R_REFUND)), moved -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                deliverRefunds(p, true);
            }
        }, ex -> plugin.getLogger().warning("[ChacaPortrait] 반환 표시 실패 (" + token + "): " + ex.getMessage()));
    }

    /** 접속·실패 시: 돌려줄 아이템 지급 (메인 스레드에서 호출). REFUND_DUE → REFUNDED 전이가 성공한 것만 지급. */
    public void deliverRefunds(Player p, boolean failureMessage) {
        UUID uuid = p.getUniqueId();
        plugin.service().then(plugin.db().call(() -> plugin.storage().refundsDue(uuid)), list -> {
            for (PortraitStorage.Reroll r : list) {
                plugin.service().then(plugin.db().call(() ->
                        plugin.storage().moveReroll(r.token(), PortraitStorage.R_REFUND, PortraitStorage.R_REFUNDED)), moved -> {
                    if (!Boolean.TRUE.equals(moved)) {
                        return;
                    }
                    Player now = Bukkit.getPlayer(uuid);
                    if (now == null) {
                        // 그 사이 나감 → 다시 돌려줄 대상으로
                        plugin.db().call(() -> plugin.storage().moveReroll(r.token(), PortraitStorage.R_REFUNDED, PortraitStorage.R_REFUND));
                        return;
                    }
                    try {
                        give(now, ItemStack.deserializeBytes(r.item()));
                        now.sendMessage(plugin.settings().message(failureMessage ? "reroll-failed" : "refund"));
                    } catch (RuntimeException ex) {
                        plugin.getLogger().severe("[ChacaPortrait] 아이템 반환 실패 " + now.getName() + " (토큰 " + r.token()
                                + ") — 관리자 확인 필요: " + ex.getMessage());
                    }
                }, ex -> plugin.getLogger().warning("[ChacaPortrait] 반환 처리 실패: " + ex.getMessage()));
            }
        }, ex -> plugin.getLogger().warning("[ChacaPortrait] 반환 목록 조회 실패: " + ex.getMessage()));
    }

    private void give(Player p, ItemStack item) {
        if (!p.isOnline()) {
            plugin.getLogger().severe("[ChacaPortrait] 아이템 반환 불가 (접속 종료): " + p.getName() + " " + item.getType());
            return;
        }
        for (ItemStack left : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        clickGate.remove(e.getPlayer().getUniqueId());
    }

    /** 만료된 입력 대기 정리 (주기 호출). */
    public void sweep() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(en -> {
            if (now <= en.getValue().expiresAt()) {
                return false;
            }
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) {
                p.sendMessage(plugin.settings().message("reroll-cancelled"));
                if (!en.getValue().chat()) {
                    plugin.channel().closePrompt(p);
                }
            }
            return true;
        });
    }
}
