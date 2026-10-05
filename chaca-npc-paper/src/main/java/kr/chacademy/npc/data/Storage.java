package kr.chacademy.npc.data;

import kr.chacademy.npc.core.Defs.RumorView;
import kr.chacademy.npc.core.Defs.Turn;
import kr.chacademy.npc.core.Json;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 모든 SQL. 메서드는 동기식이므로 반드시 Database.async(...) 안에서 호출한다.
 */
public final class Storage {

    private final Database db;

    public Storage(Database db) {
        this.db = db;
    }

    public Database db() {
        return db;
    }

    // ------------------------------------------------------------------ schema

    public void createTables() {
        String id = db.autoId();
        String text = db.textType();
        String k36 = db.keyType(36);
        String k64 = db.keyType(64);
        String suffix = db.isMariaDb() ? " DEFAULT CHARSET=utf8mb4" : "";
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS cnpc_memory (id " + id + ", player_uuid " + k36 + " NOT NULL, npc_id " + k64
                        + " NOT NULL, memo " + text + " NOT NULL, created_at BIGINT NOT NULL)" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_last_chat (player_uuid " + k36 + " NOT NULL, npc_id " + k64
                        + " NOT NULL, turns_json " + text + " NOT NULL, ended_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, npc_id))" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_hint_log (id " + id + ", player_uuid " + k36 + " NOT NULL, hint_id " + k64
                        + " NOT NULL, npc_id " + k64 + " NOT NULL, given_at BIGINT NOT NULL)" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_usage (day VARCHAR(10) NOT NULL, player_uuid VARCHAR(40) NOT NULL, calls INT NOT NULL,"
                        + " input_tokens BIGINT NOT NULL, cached_tokens BIGINT NOT NULL, output_tokens BIGINT NOT NULL,"
                        + " cost_usd DOUBLE NOT NULL, PRIMARY KEY (day, player_uuid))" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_chat_log (id " + id + ", player_uuid " + k36 + " NOT NULL, player_name VARCHAR(32),"
                        + " npc_id " + k64 + " NOT NULL, player_text " + text + ", npc_text " + text + ", flagged INT NOT NULL,"
                        + " created_at BIGINT NOT NULL)" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_events (id " + id + ", player_uuid " + k36 + " NOT NULL, player_name VARCHAR(32),"
                        + " npc_id " + k64 + " NOT NULL, type VARCHAR(32) NOT NULL, text " + text + " NOT NULL,"
                        + " created_at BIGINT NOT NULL, propagated INT NOT NULL)" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_rumors (id " + id + ", event_id BIGINT NOT NULL, heard_by " + k64 + " NOT NULL,"
                        + " player_uuid " + k36 + " NOT NULL, text " + text + " NOT NULL, type VARCHAR(32) NOT NULL,"
                        + " event_created_at BIGINT NOT NULL, heard_at BIGINT NOT NULL, mentioned INT NOT NULL)" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_vibe (id " + id + ", note " + text + " NOT NULL, keyword VARCHAR(100) NOT NULL,"
                        + " status VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL, last_seen BIGINT NOT NULL)" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_nickname (player_uuid " + k36 + " NOT NULL, npc_id " + k64 + " NOT NULL,"
                        + " nickname VARCHAR(32) NOT NULL, PRIMARY KEY (player_uuid, npc_id))" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_quest_log (id " + id + ", player_uuid " + k36 + " NOT NULL, npc_id " + k64
                        + " NOT NULL, quest_id " + k64 + " NOT NULL, offered_at BIGINT NOT NULL, accepted INT NOT NULL,"
                        + " accepted_at BIGINT NOT NULL)" + suffix,
                "CREATE TABLE IF NOT EXISTS cnpc_meta (k VARCHAR(64) NOT NULL PRIMARY KEY, v " + text + ")" + suffix
        };
        String[] idx = {
                "CREATE INDEX IF NOT EXISTS idx_cnpc_memory ON cnpc_memory (player_uuid, npc_id)",
                "CREATE INDEX IF NOT EXISTS idx_cnpc_hint ON cnpc_hint_log (player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_cnpc_chat_time ON cnpc_chat_log (created_at)",
                "CREATE INDEX IF NOT EXISTS idx_cnpc_events_prop ON cnpc_events (propagated)",
                "CREATE INDEX IF NOT EXISTS idx_cnpc_events_player ON cnpc_events (player_uuid, npc_id, type)",
                "CREATE INDEX IF NOT EXISTS idx_cnpc_rumors ON cnpc_rumors (heard_by, player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_cnpc_quest ON cnpc_quest_log (player_uuid)"
        };
        exec(c -> {
            try (Statement st = c.createStatement()) {
                for (String s : ddl) {
                    st.execute(s);
                }
                for (String s : idx) {
                    try {
                        st.execute(s);
                    } catch (SQLException ignored) {
                        // 일부 MariaDB 버전은 IF NOT EXISTS 인덱스를 지원하지 않음 — 이미 있으면 무시
                    }
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ memory

    public List<String> memos(String player, String npc) {
        return exec(c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT memo FROM cnpc_memory WHERE player_uuid=? AND npc_id=? ORDER BY id DESC LIMIT 10")) {
                ps.setString(1, player);
                ps.setString(2, npc);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
            }
            Collections.reverse(out);
            return out;
        });
    }

    public void addMemo(String player, String npc, String memo, int maxLines, long now) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cnpc_memory (player_uuid, npc_id, memo, created_at) VALUES (?,?,?,?)")) {
                ps.setString(1, player);
                ps.setString(2, npc);
                ps.setString(3, memo);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM cnpc_memory WHERE player_uuid=? AND npc_id=? ORDER BY id DESC")) {
                ps.setString(1, player);
                ps.setString(2, npc);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                    }
                }
            }
            for (int i = maxLines; i < ids.size(); i++) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM cnpc_memory WHERE id=?")) {
                    ps.setLong(1, ids.get(i));
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ last chat

    public record LastChat(List<Turn> turns, long endedAt) {
    }

    public LastChat lastChat(String player, String npc) {
        return exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT turns_json, ended_at FROM cnpc_last_chat WHERE player_uuid=? AND npc_id=?")) {
                ps.setString(1, player);
                ps.setString(2, npc);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    List<Turn> turns = new ArrayList<>();
                    try {
                        List<Object> arr = Json.arr(Json.parse(rs.getString(1)));
                        if (arr != null) {
                            for (Object o : arr) {
                                Map<String, Object> m = Json.obj(o);
                                if (m != null) {
                                    turns.add(new Turn(Json.str(m, "s"), Json.str(m, "t")));
                                }
                            }
                        }
                    } catch (RuntimeException ignored) {
                        // 깨진 기록은 무시
                    }
                    return new LastChat(turns, rs.getLong(2));
                }
            }
        });
    }

    public void saveLastChat(String player, String npc, List<Turn> turns, long endedAt) {
        List<Object> arr = new ArrayList<>();
        for (Turn t : turns) {
            arr.add(Json.map("s", t.speaker(), "t", t.text()));
        }
        String json = Json.stringify(arr);
        exec(c -> {
            int n;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE cnpc_last_chat SET turns_json=?, ended_at=? WHERE player_uuid=? AND npc_id=?")) {
                ps.setString(1, json);
                ps.setLong(2, endedAt);
                ps.setString(3, player);
                ps.setString(4, npc);
                n = ps.executeUpdate();
            }
            if (n == 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO cnpc_last_chat (player_uuid, npc_id, turns_json, ended_at) VALUES (?,?,?,?)")) {
                    ps.setString(1, player);
                    ps.setString(2, npc);
                    ps.setString(3, json);
                    ps.setLong(4, endedAt);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ hints

    public record HintState(Set<String> given, int today) {
    }

    public HintState hintState(String player, long dayStart) {
        return exec(c -> {
            Set<String> given = new HashSet<>();
            int today = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT hint_id, given_at FROM cnpc_hint_log WHERE player_uuid=?")) {
                ps.setString(1, player);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        given.add(rs.getString(1));
                        if (rs.getLong(2) >= dayStart) {
                            today++;
                        }
                    }
                }
            }
            return new HintState(given, today);
        });
    }

    public void addHint(String player, String hint, String npc, long now) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cnpc_hint_log (player_uuid, hint_id, npc_id, given_at) VALUES (?,?,?,?)")) {
                ps.setString(1, player);
                ps.setString(2, hint);
                ps.setString(3, npc);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ usage

    public void addUsage(String day, String player, int calls, long in, long cached, long out, double cost) {
        String sql = db.isMariaDb()
                ? "INSERT INTO cnpc_usage (day, player_uuid, calls, input_tokens, cached_tokens, output_tokens, cost_usd)"
                + " VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE calls=calls+VALUES(calls), input_tokens=input_tokens+VALUES(input_tokens),"
                + " cached_tokens=cached_tokens+VALUES(cached_tokens), output_tokens=output_tokens+VALUES(output_tokens),"
                + " cost_usd=cost_usd+VALUES(cost_usd)"
                : "INSERT INTO cnpc_usage (day, player_uuid, calls, input_tokens, cached_tokens, output_tokens, cost_usd)"
                + " VALUES (?,?,?,?,?,?,?) ON CONFLICT(day, player_uuid) DO UPDATE SET calls=calls+excluded.calls,"
                + " input_tokens=input_tokens+excluded.input_tokens, cached_tokens=cached_tokens+excluded.cached_tokens,"
                + " output_tokens=output_tokens+excluded.output_tokens, cost_usd=cost_usd+excluded.cost_usd";
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, day);
                ps.setString(2, player);
                ps.setInt(3, calls);
                ps.setLong(4, in);
                ps.setLong(5, cached);
                ps.setLong(6, out);
                ps.setDouble(7, cost);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public record UsageSnapshot(double spentBeforeToday, double spentToday, Map<String, Integer> callsToday) {
    }

    public UsageSnapshot usageSnapshot(String today) {
        return exec(c -> {
            double before = 0;
            double todaySpent = 0;
            Map<String, Integer> calls = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT day, player_uuid, calls, cost_usd FROM cnpc_usage")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String day = rs.getString(1);
                        double cost = rs.getDouble(4);
                        int cmp = day.compareTo(today);
                        if (cmp < 0) {
                            before += cost;
                        } else if (cmp == 0) {
                            todaySpent += cost;
                            calls.put(rs.getString(2), rs.getInt(3));
                        }
                    }
                }
            }
            return new UsageSnapshot(before, todaySpent, calls);
        });
    }

    // ------------------------------------------------------------------ chat log

    public void addChat(String player, String playerName, String npc, String playerText, String npcText,
                        boolean flagged, long now) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cnpc_chat_log (player_uuid, player_name, npc_id, player_text, npc_text, flagged, created_at)"
                            + " VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, player);
                ps.setString(2, playerName);
                ps.setString(3, npc);
                ps.setString(4, playerText);
                ps.setString(5, npcText);
                ps.setInt(6, flagged ? 1 : 0);
                ps.setLong(7, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public record PlayerLine(String player, String text) {
    }

    public List<PlayerLine> recentPlayerLines(long since, int limit) {
        return exec(c -> {
            List<PlayerLine> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT player_uuid, player_text FROM cnpc_chat_log WHERE created_at>=? AND flagged=0"
                            + " AND player_text IS NOT NULL ORDER BY id DESC LIMIT ?")) {
                ps.setLong(1, since);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new PlayerLine(rs.getString(1), rs.getString(2)));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ events & rumors

    public record EventRow(long id, String player, String playerName, String npc, String type, String text, long createdAt) {
    }

    public void addEvent(String player, String playerName, String npc, String type, String text, long now) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cnpc_events (player_uuid, player_name, npc_id, type, text, created_at, propagated)"
                            + " VALUES (?,?,?,?,?,?,0)")) {
                ps.setString(1, player);
                ps.setString(2, playerName);
                ps.setString(3, npc);
                ps.setString(4, type);
                ps.setString(5, text);
                ps.setLong(6, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<EventRow> unpropagatedEvents(int limit) {
        return exec(c -> {
            List<EventRow> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, player_uuid, player_name, npc_id, type, text, created_at FROM cnpc_events"
                            + " WHERE propagated=0 ORDER BY id LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new EventRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                rs.getString(5), rs.getString(6), rs.getLong(7)));
                    }
                }
            }
            return out;
        });
    }

    public void markPropagated(long eventId) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE cnpc_events SET propagated=1 WHERE id=?")) {
                ps.setLong(1, eventId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void addRumor(EventRow e, String heardBy, long heardAt) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cnpc_rumors (event_id, heard_by, player_uuid, text, type, event_created_at, heard_at, mentioned)"
                            + " VALUES (?,?,?,?,?,?,?,0)")) {
                ps.setLong(1, e.id());
                ps.setString(2, heardBy);
                ps.setString(3, e.player());
                ps.setString(4, e.text());
                ps.setString(5, e.type());
                ps.setLong(6, e.createdAt());
                ps.setLong(7, heardAt);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<RumorView> rumorsFor(String npc, String player, long now, int limit) {
        return exec(c -> {
            List<RumorView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, text, type, event_created_at FROM cnpc_rumors WHERE heard_by=? AND player_uuid=?"
                            + " AND heard_at<=? AND mentioned=0 ORDER BY event_created_at DESC LIMIT ?")) {
                ps.setString(1, npc);
                ps.setString(2, player);
                ps.setLong(3, now);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new RumorView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
                    }
                }
            }
            return out;
        });
    }

    /** 잡담 연출용: 이 NPC가 들은 소문 중 아무거나 (플레이어 무관). */
    public List<RumorView> anyRumorsHeardBy(String npc, Set<String> players, long now, int limit) {
        if (players.isEmpty()) {
            return List.of();
        }
        return exec(c -> {
            List<RumorView> out = new ArrayList<>();
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < players.size(); i++) {
                in.append(i == 0 ? "?" : ",?");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, text, type, event_created_at FROM cnpc_rumors WHERE heard_by=? AND heard_at<=?"
                            + " AND player_uuid IN (" + in + ") ORDER BY event_created_at DESC LIMIT ?")) {
                int i = 1;
                ps.setString(i++, npc);
                ps.setLong(i++, now);
                for (String p : players) {
                    ps.setString(i++, p);
                }
                ps.setInt(i, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new RumorView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
                    }
                }
            }
            return out;
        });
    }

    public void markRumorMentioned(long id) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE cnpc_rumors SET mentioned=1 WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** 이 플레이어가 이 NPC에게 했던 특별한 말(promise) 목록. */
    public List<String> promises(String player, String npc, int limit) {
        return exec(c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT text FROM cnpc_events WHERE player_uuid=? AND npc_id=? AND type='promise' ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, player);
                ps.setString(2, npc);
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------ vibe

    public record VibeRow(long id, String note, String keyword, String status, long lastSeen) {
    }

    public List<VibeRow> vibes(String status) {
        return exec(c -> {
            List<VibeRow> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, note, keyword, status, last_seen FROM cnpc_vibe WHERE status=? ORDER BY id")) {
                ps.setString(1, status);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new VibeRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5)));
                    }
                }
            }
            return out;
        });
    }

    /** 같은 키워드가 이미 있으면 last_seen만 갱신하고 false, 새로 넣으면 true. */
    public boolean upsertVibe(String note, String keyword, String status, long now) {
        return exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE cnpc_vibe SET last_seen=? WHERE keyword=? AND status IN ('candidate','approved')")) {
                ps.setLong(1, now);
                ps.setString(2, keyword);
                if (ps.executeUpdate() > 0) {
                    return false;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cnpc_vibe (note, keyword, status, created_at, last_seen) VALUES (?,?,?,?,?)")) {
                ps.setString(1, note);
                ps.setString(2, keyword);
                ps.setString(3, status);
                ps.setLong(4, now);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
            return true;
        });
    }

    public void setVibeStatus(long id, String status) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE cnpc_vibe SET status=? WHERE id=?")) {
                ps.setString(1, status);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void setAllVibeStatus(String from, String to) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE cnpc_vibe SET status=? WHERE status=?")) {
                ps.setString(1, to);
                ps.setString(2, from);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void expireVibes(long before) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE cnpc_vibe SET status='expired' WHERE status IN ('candidate','approved') AND last_seen<?")) {
                ps.setLong(1, before);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ nickname

    public String nickname(String player, String npc) {
        return exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nickname, npc_id FROM cnpc_nickname WHERE player_uuid=? AND (npc_id=? OR npc_id='*')")) {
                ps.setString(1, player);
                ps.setString(2, npc);
                String global = null;
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if ("*".equals(rs.getString(2))) {
                            global = rs.getString(1);
                        } else {
                            return rs.getString(1);
                        }
                    }
                }
                return global;
            }
        });
    }

    public void setNickname(String player, String npc, String nickname) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM cnpc_nickname WHERE player_uuid=? AND npc_id=?")) {
                ps.setString(1, player);
                ps.setString(2, npc);
                ps.executeUpdate();
            }
            if (nickname != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO cnpc_nickname (player_uuid, npc_id, nickname) VALUES (?,?,?)")) {
                    ps.setString(1, player);
                    ps.setString(2, npc);
                    ps.setString(3, nickname);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ quests

    public record QuestState(long lastAcceptedFromNpc, Set<String> accepted, int acceptedLastDay) {
    }

    public QuestState questState(String player, String npc, long dayAgo) {
        return exec(c -> {
            long last = 0;
            Set<String> accepted = new HashSet<>();
            int recent = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT npc_id, quest_id, accepted_at FROM cnpc_quest_log WHERE player_uuid=? AND accepted=1")) {
                ps.setString(1, player);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        accepted.add(rs.getString(2));
                        long at = rs.getLong(3);
                        if (npc.equals(rs.getString(1)) && at > last) {
                            last = at;
                        }
                        if (at >= dayAgo) {
                            recent++;
                        }
                    }
                }
            }
            return new QuestState(last, accepted, recent);
        });
    }

    public void addQuestLog(String player, String npc, String quest, long offeredAt, boolean accepted, long acceptedAt) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cnpc_quest_log (player_uuid, npc_id, quest_id, offered_at, accepted, accepted_at)"
                            + " VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, player);
                ps.setString(2, npc);
                ps.setString(3, quest);
                ps.setLong(4, offeredAt);
                ps.setInt(5, accepted ? 1 : 0);
                ps.setLong(6, acceptedAt);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ meta

    public String meta(String key) {
        return exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT v FROM cnpc_meta WHERE k=?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    public void setMeta(String key, String value) {
        exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM cnpc_meta WHERE k=?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO cnpc_meta (k, v) VALUES (?,?)")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ util

    private <T> T exec(Database.SqlFunction<T> fn) {
        try {
            return db.with(fn);
        } catch (SQLException ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    private static void noop(Connection c) {
    }
}
