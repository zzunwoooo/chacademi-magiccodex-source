package kr.chacademy.npc.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 장난·탈옥 시도 누적. 일정 시간 안에 여러 번 걸리면 그 NPC와 대화를 잠근다.
 */
public final class StrikeTracker {

    private final Map<String, Deque<Long>> strikes = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();
    private final long windowMillis;
    private final int maxStrikes;
    private final long lockMillis;

    public StrikeTracker(long windowMillis, int maxStrikes, long lockMillis) {
        this.windowMillis = windowMillis;
        this.maxStrikes = maxStrikes;
        this.lockMillis = lockMillis;
    }

    private static String key(String player, String npc) {
        return player + "|" + npc;
    }

    /** 한 번 걸림을 기록. 이번에 잠기면 true. */
    public boolean addStrike(String player, String npc, long now) {
        String k = key(player, npc);
        Deque<Long> q = strikes.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(now);
            while (!q.isEmpty() && now - q.peekFirst() > windowMillis) {
                q.removeFirst();
            }
            if (q.size() >= maxStrikes) {
                q.clear();
                lockedUntil.put(k, now + lockMillis);
                return true;
            }
        }
        return false;
    }

    public boolean isLocked(String player, String npc, long now) {
        Long until = lockedUntil.get(key(player, npc));
        if (until == null) {
            return false;
        }
        if (now >= until) {
            lockedUntil.remove(key(player, npc));
            return false;
        }
        return true;
    }
}
