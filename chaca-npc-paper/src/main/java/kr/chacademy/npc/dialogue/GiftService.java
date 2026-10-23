package kr.chacademy.npc.dialogue;

import kr.chacademy.npc.ChacaNpcPlugin;
import kr.chacademy.npc.config.Settings;
import kr.chacademy.npc.core.CharacterSheet;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC에게 선물하기 — 대화 버튼 3번 "선물 줄 거 있어" → 선물 창 → 칸 선택 (2026-10-23, Shift+우클릭 선물은 없앰).
 *
 * <p>선물 아이템 = lore 첫 줄 "선물", 둘째 줄 등급(일반·고급·유일·전설), 색 코드 무시. 창에 보여 줄 칸 목록도,
 * 고른 칸의 아이템·수량도 서버가 다시 읽어 판단한다 (클라가 보낸 것은 칸 번호뿐).
 * 점수: 그 NPC 의 gift_likes.liked 에 있으면 등급 점수(config gifts.grades), 아니면 gifts.not-liked.
 * 호감도 상한(30·60·90, 그 NPC 퀘스트 미완료)이면 받지 않고 gift_full 대사 — 아이템·하루 횟수 차감 없음 (MagicCodexBridge 가 FULL 로 판정).
 *
 * <p>순서: (1) MagicCodex에 하루 횟수 예약 + pending 기록 → (2) 같은 칸에 같은 아이템이 그대로 있으면 1개 차감
 * → (3) 확정. 차감 전에 아이템이 바뀌면 취소, 확정이 DB 오류로 실패하면 아이템을 돌려주고 취소한다.
 * 같은 플레이어의 선물은 한 번에 하나만 처리한다 (중복 클릭 방지). 토큰당 점수는 한 번만 오른다.
 * 서버가 (2)와 (3) 사이에 꺼지면 MagicCodex가 다음 시작 때 pending을 확정한다 (아이템 복제 없음).
 */
public final class GiftService {

    /** 선물 창 한 칸: 인벤토리 칸 번호, 등급(1 일반 · 2 고급 · 3 유일 · 4 전설), 이름(채팅 화면용). */
    public record GiftItem(int slot, int grade, String name) {
    }

    static final List<String> GRADES = List.of("일반", "고급", "유일", "전설");
    private static final int OFFHAND = 40;

    private final ChacaNpcPlugin plugin;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();
    private static java.lang.reflect.Method iaByItem, iaId;
    private static boolean iaTried;

    public GiftService(ChacaNpcPlugin plugin) {
        this.plugin = plugin;
    }

    /** 이 캐릭터가 선물을 받는지. */
    public boolean accepts(CharacterSheet c) {
        return plugin.link().available();
    }

    /** 점수 상한 — MagicCodexBridge AffinityStore.gate 와 같아야 한다 (0 → 30, 1 → 60, 2 → 90, 3 이상 → 100). */
    static int gate(int heart) {
        int h = Math.max(0, Math.min(3, heart));
        return h >= 3 ? 100 : 30 * (h + 1);
    }

    /** 선물 아이템이면 등급(1~4), 아니면 0. lore 첫 줄 "선물", 둘째 줄 등급 — 색 코드 제외. */
    @SuppressWarnings("deprecation")
    static int gradeOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() < 1 || !item.hasItemMeta()) {
            return 0;
        }
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null || lore.size() < 2) {
            return 0;
        }
        if (!"선물".equals(plain(lore.get(0)))) {
            return 0;
        }
        return GRADES.indexOf(plain(lore.get(1))) + 1;
    }

    static String plain(String s) {
        String t = org.bukkit.ChatColor.stripColor(s == null ? "" : s);
        return t == null ? "" : t.replaceAll("(?i)&[0-9a-fk-orx]", "").strip();
    }

    @SuppressWarnings("deprecation")
    private static String nameOf(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return plain(item.getItemMeta().getDisplayName());
        }
        return item.getType().getKey().getKey();
    }

    /** gift_likes 에 적는 아이템 키: ItemsAdder 아이템이면 "namespace:id", 아니면 마크 아이템 이름(apple). */
    static String keyOf(ItemStack item) {
        if (!iaTried) {
            iaTried = true;
            try {
                Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
                iaByItem = cs.getMethod("byItemStack", ItemStack.class);
                iaId = cs.getMethod("getNamespacedID");
            } catch (Throwable ignored) {
                iaByItem = null;
            }
        }
        if (iaByItem != null) {
            try {
                Object stack = iaByItem.invoke(null, item);
                if (stack != null) {
                    return String.valueOf(iaId.invoke(stack)).toLowerCase(Locale.ROOT);
                }
            } catch (Throwable ignored) {
                // ItemsAdder 오류: 마크 아이템 이름으로
            }
        }
        return item.getType().getKey().getKey().toLowerCase(Locale.ROOT);
    }

    /** 대화 버튼 3번: 상한이면 gift_full, 줄 게 없으면 안내, 있으면 선물 창. */
    public void offer(Player p, DialogueSession s, int seq) {
        CharacterSheet c = s.character();
        if (!plugin.link().available()) {
            s.view.info(p, s, "(지금은 선물을 줄 수 없어요)");
            return;
        }
        List<GiftItem> items = new java.util.ArrayList<>();
        PlayerInventory inv = p.getInventory();
        for (int slot = 0; slot <= OFFHAND; slot++) {
            if (slot > 35 && slot != OFFHAND) {
                continue;
            }
            ItemStack it = inv.getItem(slot);
            int g = gradeOf(it);
            if (g > 0) {
                items.add(new GiftItem(slot, g, nameOf(it)));
            }
        }
        UUID uuid = p.getUniqueId();
        plugin.link().affinity(uuid, c.id()).whenComplete((row, ex) -> plugin.sync(() -> {
            if (!p.isOnline() || plugin.dialogue().session(uuid) != s) {
                return;
            }
            if (ex == null && row != null && row.length >= 2 && row[0] >= gate(row[1])) {
                s.view.line(p, s, seq, c.fallback("gift_full", plugin.settings().defaultFallbacks, random));
                return;
            }
            if (items.isEmpty()) {
                s.view.info(p, s, "(줄 수 있는 선물이 없어요 — 설명 첫 줄에 '선물'이라고 적힌 아이템만 줄 수 있어요)");
                return;
            }
            s.view.gifts(p, s, seq, items);
        }));
    }

    /** 선물 창에서 고른 칸. 서버가 아이템·등급·수량을 다시 읽는다. */
    public void giveSlot(Player p, CharacterSheet c, int slot) {
        DialogueView view = plugin.dialogue().viewFor(p);
        if (!plugin.link().available()) {
            view.info(p, plugin.dialogue().session(p.getUniqueId()), "(지금은 선물을 줄 수 없어요)");
            return;
        }
        UUID uuid = p.getUniqueId();
        PlayerInventory inv = p.getInventory();
        if (slot < 0 || (slot > 35 && slot != OFFHAND)) {
            return;
        }
        ItemStack hand = inv.getItem(slot);
        int grade = gradeOf(hand);
        if (grade == 0) {
            view.info(p, plugin.dialogue().session(uuid), "(선물할 수 없는 아이템이에요)");
            return;
        }
        if (!inFlight.add(uuid)) {
            return; // 이전 선물 처리 중
        }
        ItemStack one = hand.clone();
        one.setAmount(1);
        Settings st = plugin.settings();
        String key = keyOf(hand);
        boolean liked = contains(c.giftLikes(), "liked", key) || contains(c.giftLikes(), "loved", key);
        int delta = liked ? st.giftGrades.getOrDefault(GRADES.get(grade - 1), 1) : st.giftNotLiked;
        String reaction = !liked ? "gift_disliked" : grade >= 3 ? "gift_loved" : "gift_liked";
        String token = UUID.randomUUID().toString();
        plugin.link().beginGift(uuid, c.id(), token, delta).whenComplete((res, ex) -> {
            if (ex != null || res == null) {
                inFlight.remove(uuid);
                if (p.isOnline()) {
                    view.info(p, plugin.dialogue().session(uuid), "(선물을 전하지 못했어요. 잠시 후 다시 해 주세요)");
                }
                return;
            }
            switch (res) {
                case "CAP" -> {
                    inFlight.remove(uuid);
                    say(p, c, c.fallback("gift_cap", st.defaultFallbacks, random));
                    return;
                }
                case "FULL" -> {
                    // 호감도 상한 (그 NPC 퀘스트를 아직 안 깸): 받지 않음, 아이템·횟수 그대로
                    inFlight.remove(uuid);
                    say(p, c, c.fallback("gift_full", st.defaultFallbacks, random));
                    return;
                }
                case "OK" -> {
                    // 아래에서 계속
                }
                default -> {
                    inFlight.remove(uuid);
                    return;
                }
            }
            ItemStack cur = p.isOnline() ? p.getInventory().getItem(slot) : null;
            if (cur == null || !cur.isSimilar(one) || cur.getAmount() < 1 || gradeOf(cur) != grade) {
                plugin.link().cancelGift(token);
                inFlight.remove(uuid);
                return;
            }
            if (cur.getAmount() == 1) {
                p.getInventory().setItem(slot, null);
            } else {
                cur.setAmount(cur.getAmount() - 1);
                p.getInventory().setItem(slot, cur);
            }
            plugin.link().commitGift(uuid, c.id(), token).whenComplete((r2, ex2) -> {
                if (ex2 != null) {
                    // 확정 실패: 취소가 확실히 성공했을 때만 아이템을 돌려준다.
                    // 취소도 실패하면 pending으로 남고, 다음 시작 때 "아이템 차감됨"으로 확정된다 (환불+점수 중복 없음).
                    plugin.link().cancelGift(token).whenComplete((cancelled, ex3) -> {
                        inFlight.remove(uuid);
                        if (Boolean.TRUE.equals(cancelled)) {
                            refund(p, one, token);
                        } else {
                            plugin.getLogger().warning("[ChacaNPC] 선물 확정·취소 모두 실패 (토큰 " + token
                                    + ") — 아이템은 차감된 상태로 두고 다음 시작 때 확정됩니다");
                            if (p.isOnline()) {
                                plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(uuid),
                                        "(선물 처리를 확인 중이에요. 잠시 후 반영돼요)");
                            }
                        }
                    });
                    return;
                }
                inFlight.remove(uuid);
                if (r2 == null) {
                    return; // 이미 처리된 토큰 (중복 확정 없음)
                }
                DialogueSession s = plugin.dialogue().session(uuid);
                if (s != null && s.character().id().equals(c.id()) && r2.length >= 2) {
                    s.score = r2[0];
                    s.heart = r2[1];
                }
                say(p, c, c.fallback(reaction, st.defaultFallbacks, random));
                plugin.social().recordEvent(uuid, p.getName(), c.id(), "gift",
                        p.getName() + "이(가) " + c.name() + "에게 선물(" + key + ")을 줌");
            });
        });
    }

    private void refund(Player p, ItemStack item, String token) {
        if (p.isOnline() && plugin.isEnabled()) {
            for (ItemStack left : p.getInventory().addItem(item).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
            plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(p.getUniqueId()),
                    "(선물을 전하지 못해 아이템을 돌려드렸어요)");
        } else {
            plugin.getLogger().severe("[ChacaNPC] 선물 확정 실패 후 아이템 반환 불가: " + p.getName() + " " + item.getType()
                    + " x1 (토큰 " + token + ") — 관리자 확인 필요");
        }
    }

    private void say(Player p, CharacterSheet c, String line) {
        if (!p.isOnline()) {
            return;
        }
        DialogueSession s = plugin.dialogue().session(p.getUniqueId());
        if (s != null && s.character().id().equals(c.id())) {
            s.view.line(p, s, s.replySeq, line);
        } else {
            p.sendMessage("[" + c.name() + "] " + line);
        }
    }

    private static boolean contains(Map<String, List<String>> likes, String k, String key) {
        List<String> l = likes == null ? null : likes.get(k);
        return l != null && l.contains(key);
    }
}
