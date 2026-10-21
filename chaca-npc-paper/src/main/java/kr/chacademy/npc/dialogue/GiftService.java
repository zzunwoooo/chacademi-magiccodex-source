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
 * <p>순서 (A08, 2026-10-21): (1) MagicCodex에 하루 횟수 예약 + pending 기록(시작한 서버 origin 포함)
 * → (2) MagicCodex takeGift: 같은 칸에 같은 아이템이 그대로 있으면 1개 차감 + 영수증(PDC) + saveData 를 같은 틱에
 * → (3) 확정. 차감 전에 아이템이 바뀌면 취소(결과 확인, 실패하면 다음 접속 때 영수증 없음 → 취소).
 * 확정이 실패하면 환불하지 않고 확정을 다시 시도한다 (차감은 이미 확정이므로). 끝내 실패하면 pending+영수증이 남고,
 * 다음 접속 때 시작한 서버에서 영수증으로 확정된다. 같은 플레이어의 선물은 한 번에 하나만, 토큰당 점수는 한 번만.
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
        if (!plugin.link().available() || !plugin.link().giftReceipts()) {
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
            int taken = p.isOnline() ? plugin.link().takeGift(p, slot, one, token) : 0;
            if (taken == 0) {
                // 아이템이 바뀌어 차감하지 않음: 취소 결과를 확인한다. 실패해도 영수증이 없으니 다음 접속 때 취소된다.
                plugin.link().cancelGift(token).whenComplete((cancelled, exc) -> {
                    inFlight.remove(uuid);
                    if (!Boolean.TRUE.equals(cancelled)) {
                        plugin.getLogger().warning("[ChacaNPC] 선물 취소 실패 (토큰 " + token + ") — 영수증이 없으므로 다음 접속 때 취소됩니다");
                    }
                });
                return;
            }
            if (taken < 0) {
                // 차감 결과를 모름: 취소도 확정도 하지 않는다. 다음 접속 때 영수증으로 정해진다.
                inFlight.remove(uuid);
                if (p.isOnline()) {
                    view.info(p, plugin.dialogue().session(uuid), "(선물 처리를 확인 중이에요. 다시 접속하면 반영돼요)");
                }
                return;
            }
            commit(p, c, token, reaction, key, 0);
        });
    }

    /** 차감이 확정된 선물의 확정. 실패하면 환불하지 않고 3초 뒤 다시 확정한다 (최대 3번). */
    private void commit(Player p, CharacterSheet c, String token, String reaction, String key, int attempt) {
        UUID uuid = p.getUniqueId();
        Settings st = plugin.settings();
        plugin.link().commitGift(uuid, c.id(), token).whenComplete((r2, ex2) -> {
            if (ex2 != null) {
                if (attempt < 2 && plugin.isEnabled()) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> commit(p, c, token, reaction, key, attempt + 1), 60L);
                    return;
                }
                inFlight.remove(uuid);
                plugin.getLogger().warning("[ChacaNPC] 선물 확정 실패 (토큰 " + token
                        + ") — 아이템은 차감됨, 영수증이 남아 다음 접속 때 이 서버에서 확정됩니다 (환불 없음)");
                if (p.isOnline()) {
                    plugin.dialogue().viewFor(p).info(p, plugin.dialogue().session(uuid),
                            "(선물 처리를 확인 중이에요. 다시 접속하면 반영돼요)");
                }
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
