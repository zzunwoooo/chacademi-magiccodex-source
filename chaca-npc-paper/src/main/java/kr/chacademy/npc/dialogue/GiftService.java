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
 * NPC에게 선물하기 (Shift+우클릭, 손에 든 아이템 1개).
 *
 * <p>순서: (1) MagicCodex에 하루 횟수 예약 + pending 기록 → (2) 같은 칸에 같은 아이템이 그대로 있으면 1개 차감
 * → (3) 확정. 차감 전에 아이템이 바뀌면 취소, 확정이 DB 오류로 실패하면 아이템을 돌려주고 취소한다.
 * 같은 플레이어의 선물은 한 번에 하나만 처리한다 (중복 클릭 방지). 토큰당 점수는 한 번만 오른다.
 * 서버가 (2)와 (3) 사이에 꺼지면 MagicCodex가 다음 시작 때 pending을 확정한다 (아이템 복제 없음).
 */
public final class GiftService {

    private final ChacaNpcPlugin plugin;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    public GiftService(ChacaNpcPlugin plugin) {
        this.plugin = plugin;
    }

    /** 이 캐릭터가 선물을 받는지 (선물 목록이 있거나 기본 반응 사용). */
    public boolean accepts(CharacterSheet c) {
        return plugin.link().available();
    }

    public void give(Player p, CharacterSheet c) {
        DialogueView view = plugin.dialogue().viewFor(p);
        if (!plugin.link().available()) {
            view.info(p, plugin.dialogue().session(p.getUniqueId()), "(지금은 선물을 줄 수 없어요)");
            return;
        }
        UUID uuid = p.getUniqueId();
        PlayerInventory inv = p.getInventory();
        int slot = inv.getHeldItemSlot();
        ItemStack hand = inv.getItem(slot);
        if (hand == null || hand.getType() == Material.AIR || hand.getAmount() < 1) {
            return;
        }
        if (!inFlight.add(uuid)) {
            return; // 이전 선물 처리 중
        }
        ItemStack one = hand.clone();
        one.setAmount(1);
        Settings st = plugin.settings();
        String key = hand.getType().getKey().getKey().toLowerCase(Locale.ROOT);
        String reaction;
        int delta;
        Map<String, List<String>> likes = c.giftLikes();
        if (contains(likes, "loved", key)) {
            delta = st.giftLoved;
            reaction = "gift_loved";
        } else if (contains(likes, "liked", key)) {
            delta = st.giftLiked;
            reaction = "gift_liked";
        } else if (contains(likes, "disliked", key)) {
            delta = st.giftDisliked;
            reaction = "gift_disliked";
        } else {
            delta = st.giftNeutral;
            reaction = "gift_neutral";
        }
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
                case "OK" -> {
                    // 아래에서 계속
                }
                default -> {
                    inFlight.remove(uuid);
                    return;
                }
            }
            ItemStack cur = p.isOnline() ? p.getInventory().getItem(slot) : null;
            if (cur == null || !cur.isSimilar(one) || cur.getAmount() < 1) {
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
