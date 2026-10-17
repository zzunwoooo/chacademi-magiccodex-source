package school.magiccodex.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 큐디치 대기열·대시보드 (Velocity 프록시 ↔ MagicCodex 클라이언트, {@value #QUEUE}) 와
 * 경기 배정 링크 (Velocity 프록시 ↔ 경기장 서버 ChacaKyudichi, {@value #LINK}).
 * Every message: u8 version, u8 kind. Strict like {@link KyudichiProtocol}.
 *
 * <pre>
 * QUEUE S2C 1 STATUS  u8 state(0 없음 1 찾는 중 2 수락 확인 3 이동 중 4 경기 중) · u16 waitedSec · u8 offerLeftSec
 *                     · u8 accepted · u8 total · u8 iAccepted · u16 queuedParties
 *       S2C 2 DASH    u16 queuedParties · u8 n · n×Live
 *       S2C 3 RANKING u8 n · n×Rank · u8 hasMine [· Rank]
 *       S2C 4 RECORD  u8 found [· PlayerRecord]
 *       S2C 5 APPLY   i32 myRating · u16 wins · u16 losses · i32 baseRating · u8 canStart · UTF note · u8 n(≤3) · n×Member   (신청 창)
 *       C2S 1 JOIN · 2 LEAVE · 3 ACCEPT · 4 DECLINE · 5 DASH · 6 RANKING · 7 RECORD UTF nick(빈칸 = 나) · 8 SPECTATE UTF arena
 *                     · 9 HELLO (모드 있음 알림: 접속할 때 한 번, 상태는 액션바 대신 HUD로 받는다) · 10 PRACTICE · 11 APPLY (신청 창 열기)
 * Live     (LINK) UTF arena · u8 phase · u16 scoreA · u16 scoreB · i32 timeLeftMs · u8 spectators · UTF teamA · UTF teamB
 * LiveCard (DASH) UTF arena · u8 phase · u16 scoreA · u16 scoreB · i32 timeLeftMs · u8 spectators · u8 nA · nA×Who · u8 nB · nB×Who
 * Who      UUID · UTF nick · UTF skin(빈칸 = 모름)
 * Member   UUID · UTF nick · UTF skin · i8 house(-1 없음) · i32 rating · u8 leader
 * Rank    u16 rank · UUID · UTF nick · UTF skin · i32 rating · u8 tier · u16 games · u16 wins · u16 losses
 * PlayerRecord  UUID · UTF nick · UTF skin · i32 rating · u8 tier · u16 rank(0 순위 밖) · 11×i32(games wins losses draws goals kills deaths assists snitch deflectHits mvp)
 *         · f32 damage · f32 heal · u8 n · n×(i64 endedAt · u8 outcome(0 패 1 승 2 무) · u8 team · u16 scoreA · u16 scoreB · u8 goals · u8 kills · u8 deaths · u8 mvp · i16 delta)
 *
 * LINK P2A 1 ASSIGN   UUID match · u8 rated · u8 nA · nA×UUID · u8 nB · nB×UUID
 *      P2A 2 SPECTATE UUID player · UTF arena(빈칸 = 아무 경기)
 *      P2A 3 PRACTICE UUID player
 *      A2P 1 HELLO    UUID player                    (경기장 서버에 들어온 사람, 배정을 묻는다)
 *      A2P 2 LIVE     u8 freeArenas · u8 n · n×(UUID match · Live · u8 nA · nA×UUID · u8 nB · nB×UUID)
 *      A2P 3 RETURN   u8 n · n×UUID                  (원래 서버로 돌려보낼 사람)
 *      A2P 4 ENDED    UUID match
 * </pre>
 * Tier: 0 브론즈 1 실버 2 골드 3 플래티넘 4 다이아몬드 5 마스터 6~8 챌린저 1~3.
 */
public final class KyudichiQueueProtocol {
    public static final String QUEUE = "chacademi:kyudichi_queue", LINK = "chacademi:kyudichi_link";
    public static final int VERSION = 1, MAX_BYTES = 16384;
    public static final int S_NONE = 0, S_SEARCHING = 1, S_OFFER = 2, S_MOVING = 3, S_PLAYING = 4;

    private KyudichiQueueProtocol() {}

    // ------------------------------------------------------------------ records
    public record Live(String arena, int phase, int scoreA, int scoreB, int timeLeftMs, int spectators, String teamA, String teamB) {}
    public record Who(UUID id, String nick, String skin) {}
    public record LiveCard(String arena, int phase, int scoreA, int scoreB, int timeLeftMs, int spectators, List<Who> teamA, List<Who> teamB) {}
    public record Member(UUID id, String nick, String skin, int house, int rating, boolean leader) {}
    public record Rank(int rank, UUID id, String nick, String skin, int rating, int tier, int games, int wins, int losses) {}
    public record Recent(long endedAt, int outcome, int team, int scoreA, int scoreB, int goals, int kills, int deaths, boolean mvp, int delta) {}
    public record PlayerRecord(UUID id, String nick, String skin, int rating, int tier, int rank, int games, int wins, int losses, int draws, int goals, int kills,
                         int deaths, int assists, int snitch, int deflectHits, int mvp, float damage, float heal, List<Recent> recent) {}

    public sealed interface ToClient permits Status, Dash, Ranking, RecordReply, Apply {}
    public record Status(int state, int waitedSec, int offerLeftSec, int accepted, int total, boolean iAccepted, int queuedParties) implements ToClient {}
    public record Dash(int queuedParties, List<LiveCard> live) implements ToClient {}
    public record Ranking(List<Rank> top, Rank mine) implements ToClient {}
    public record RecordReply(PlayerRecord record) implements ToClient {}
    public record Apply(int myRating, int wins, int losses, int baseRating, boolean canStart, String note, List<Member> members) implements ToClient {}

    public sealed interface FromClient permits Join, Leave, Accept, Decline, DashRequest, RankingRequest, RecordRequest, SpectateRequest, ClientHello, PracticeRequest, ApplyRequest {}
    public record Join() implements FromClient {}
    public record Leave() implements FromClient {}
    public record Accept() implements FromClient {}
    public record Decline() implements FromClient {}
    public record DashRequest() implements FromClient {}
    public record RankingRequest() implements FromClient {}
    public record RecordRequest(String nick) implements FromClient {}
    public record SpectateRequest(String arena) implements FromClient {}
    public record ClientHello() implements FromClient {}
    public record PracticeRequest() implements FromClient {}
    public record ApplyRequest() implements FromClient {}

    public sealed interface ToArena permits Assign, Spectate, Practice {}
    public record Assign(UUID match, boolean rated, List<UUID> teamA, List<UUID> teamB) implements ToArena {}
    public record Spectate(UUID player, String arena) implements ToArena {}
    public record Practice(UUID player) implements ToArena {}

    public sealed interface ToProxy permits Hello, LiveList, Return, Ended {}
    public record Hello(UUID player) implements ToProxy {}
    public record LiveMatch(UUID match, Live live, List<UUID> teamA, List<UUID> teamB) {}
    public record LiveList(int freeArenas, List<LiveMatch> matches) implements ToProxy {}
    public record Return(List<UUID> players) implements ToProxy {}
    public record Ended(UUID match) implements ToProxy {}

    // ------------------------------------------------------------------ queue channel
    public static byte[] encode(ToClient m) {
        return write(o -> {
            switch (m) {
                case Status x -> {
                    o.writeByte(1); u8(o, x.state, 4); u16(o, x.waitedSec); u8(o, Math.min(255, Math.max(0, x.offerLeftSec)), 255);
                    u8(o, x.accepted, 6); u8(o, x.total, 6); o.writeByte(x.iAccepted ? 1 : 0); u16(o, x.queuedParties);
                }
                case Dash x -> { o.writeByte(2); u16(o, x.queuedParties); u8(o, x.live.size(), 16); for (LiveCard l : x.live) card(o, l); }
                case Apply x -> {
                    o.writeByte(5); o.writeInt(x.myRating); u16(o, x.wins); u16(o, x.losses); o.writeInt(x.baseRating); o.writeByte(x.canStart ? 1 : 0);
                    utf(o, x.note, 64); u8(o, x.members.size(), 3);
                    for (Member mm : x.members) { uuid(o, mm.id); utf(o, mm.nick, 32); utf(o, mm.skin, 64); o.writeByte(Math.clamp(mm.house, -1, 3)); o.writeInt(mm.rating); o.writeByte(mm.leader ? 1 : 0); }
                }
                case Ranking x -> {
                    o.writeByte(3); u8(o, x.top.size(), 100); for (Rank r : x.top) rank(o, r);
                    o.writeByte(x.mine == null ? 0 : 1); if (x.mine != null) rank(o, x.mine);
                }
                case RecordReply x -> {
                    o.writeByte(4); o.writeByte(x.record == null ? 0 : 1);
                    if (x.record != null) {
                        PlayerRecord r = x.record;
                        uuid(o, r.id); utf(o, r.nick, 32); utf(o, r.skin, 64); o.writeInt(r.rating); u8(o, r.tier, 8); u16(o, r.rank);
                        for (int v : new int[]{r.games, r.wins, r.losses, r.draws, r.goals, r.kills, r.deaths, r.assists, r.snitch, r.deflectHits, r.mvp}) o.writeInt(Math.max(0, v));
                        f(o, r.damage); f(o, r.heal); u8(o, r.recent.size(), 10);
                        for (Recent c : r.recent) {
                            o.writeLong(c.endedAt); u8(o, c.outcome, 2); u8(o, c.team, 1); u16(o, c.scoreA); u16(o, c.scoreB);
                            o.writeByte(Math.clamp(c.goals, 0, 255)); o.writeByte(Math.clamp(c.kills, 0, 255)); o.writeByte(Math.clamp(c.deaths, 0, 255));
                            o.writeByte(c.mvp ? 1 : 0); i16(o, c.delta);
                        }
                    }
                }
            }
        });
    }

    public static ToClient decodeToClient(byte[] bytes) {
        return read(bytes, i -> switch (i.readUnsignedByte()) {
            case 1 -> new Status(r8(i, 4), i.readUnsignedShort(), i.readUnsignedByte(), r8(i, 6), r8(i, 6), rflag(i), i.readUnsignedShort());
            case 2 -> {
                int q = i.readUnsignedShort(), n = r8(i, 16); var ls = new ArrayList<LiveCard>();
                for (int k = 0; k < n; k++) ls.add(rcard(i));
                yield new Dash(q, List.copyOf(ls));
            }
            case 3 -> {
                int n = r8(i, 100); var rs = new ArrayList<Rank>();
                for (int k = 0; k < n; k++) rs.add(rrank(i));
                yield new Ranking(List.copyOf(rs), rflag(i) ? rrank(i) : null);
            }
            case 4 -> {
                if (!rflag(i)) yield new RecordReply(null);
                UUID id = ruuid(i); String nick = rutf(i, 32), skin = rutf(i, 64); int rating = i.readInt(), tier = r8(i, 8), rank = i.readUnsignedShort();
                int[] v = new int[11]; for (int k = 0; k < 11; k++) { v[k] = i.readInt(); if (v[k] < 0) throw new IllegalArgumentException("stat"); }
                float dmg = rf(i), heal = rf(i); int n = r8(i, 10); var rs = new ArrayList<Recent>();
                for (int k = 0; k < n; k++) rs.add(new Recent(i.readLong(), r8(i, 2), r8(i, 1), i.readUnsignedShort(), i.readUnsignedShort(),
                        i.readUnsignedByte(), i.readUnsignedByte(), i.readUnsignedByte(), rflag(i), i.readShort()));
                yield new RecordReply(new PlayerRecord(id, nick, skin, rating, tier, rank, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], dmg, heal, List.copyOf(rs)));
            }
            case 5 -> {
                int my = i.readInt(), w = i.readUnsignedShort(), l = i.readUnsignedShort(), base = i.readInt(); boolean can = rflag(i); String note = rutf(i, 64);
                int n = r8(i, 3); var ms = new ArrayList<Member>();
                for (int k = 0; k < n; k++) {
                    UUID id = ruuid(i); String nick = rutf(i, 32), skin = rutf(i, 64); int house = i.readByte();
                    if (house < -1 || house > 3) throw new IllegalArgumentException("house");
                    ms.add(new Member(id, nick, skin, house, i.readInt(), rflag(i)));
                }
                yield new Apply(my, w, l, base, can, note, List.copyOf(ms));
            }
            default -> throw new IllegalArgumentException("kind");
        });
    }

    public static byte[] encode(FromClient m) {
        return write(o -> {
            switch (m) {
                case Join x -> o.writeByte(1);
                case Leave x -> o.writeByte(2);
                case Accept x -> o.writeByte(3);
                case Decline x -> o.writeByte(4);
                case DashRequest x -> o.writeByte(5);
                case RankingRequest x -> o.writeByte(6);
                case RecordRequest x -> { o.writeByte(7); utf(o, x.nick, 32); }
                case SpectateRequest x -> { o.writeByte(8); utf(o, x.arena, 32); }
                case ClientHello x -> o.writeByte(9);
                case PracticeRequest x -> o.writeByte(10);
                case ApplyRequest x -> o.writeByte(11);
            }
        });
    }

    public static FromClient decodeFromClient(byte[] bytes) {
        return read(bytes, i -> switch (i.readUnsignedByte()) {
            case 1 -> new Join(); case 2 -> new Leave(); case 3 -> new Accept(); case 4 -> new Decline();
            case 5 -> new DashRequest(); case 6 -> new RankingRequest();
            case 7 -> new RecordRequest(rutf(i, 32));
            case 8 -> new SpectateRequest(rutf(i, 32));
            case 9 -> new ClientHello();
            case 10 -> new PracticeRequest();
            case 11 -> new ApplyRequest();
            default -> throw new IllegalArgumentException("kind");
        });
    }

    // ------------------------------------------------------------------ link channel
    public static byte[] encode(ToArena m) {
        return write(o -> {
            switch (m) {
                case Assign x -> { o.writeByte(1); uuid(o, x.match); o.writeByte(x.rated ? 1 : 0); uuids(o, x.teamA, 3); uuids(o, x.teamB, 3); }
                case Spectate x -> { o.writeByte(2); uuid(o, x.player); utf(o, x.arena, 32); }
                case Practice x -> { o.writeByte(3); uuid(o, x.player); }
            }
        });
    }

    public static ToArena decodeToArena(byte[] bytes) {
        return read(bytes, i -> switch (i.readUnsignedByte()) {
            case 1 -> new Assign(ruuid(i), rflag(i), ruuids(i, 3), ruuids(i, 3));
            case 2 -> new Spectate(ruuid(i), rutf(i, 32));
            case 3 -> new Practice(ruuid(i));
            default -> throw new IllegalArgumentException("kind");
        });
    }

    public static byte[] encode(ToProxy m) {
        return write(o -> {
            switch (m) {
                case Hello x -> { o.writeByte(1); uuid(o, x.player); }
                case LiveList x -> {
                    o.writeByte(2); u8(o, Math.clamp(x.freeArenas, 0, 255), 255); u8(o, x.matches.size(), 16);
                    for (LiveMatch lm : x.matches) { uuid(o, lm.match); live(o, lm.live); uuids(o, lm.teamA, 6); uuids(o, lm.teamB, 6); }
                }
                case Return x -> { o.writeByte(3); uuids(o, x.players, 64); }
                case Ended x -> { o.writeByte(4); uuid(o, x.match); }
            }
        });
    }

    public static ToProxy decodeToProxy(byte[] bytes) {
        return read(bytes, i -> switch (i.readUnsignedByte()) {
            case 1 -> new Hello(ruuid(i));
            case 2 -> {
                int free = i.readUnsignedByte(), n = r8(i, 16); var ms = new ArrayList<LiveMatch>();
                for (int k = 0; k < n; k++) ms.add(new LiveMatch(ruuid(i), rlive(i), ruuids(i, 6), ruuids(i, 6)));
                yield new LiveList(free, List.copyOf(ms));
            }
            case 3 -> new Return(ruuids(i, 64));
            case 4 -> new Ended(ruuid(i));
            default -> throw new IllegalArgumentException("kind");
        });
    }

    // ------------------------------------------------------------------ parts
    private static void live(DataOutputStream o, Live l) throws IOException {
        utf(o, l.arena, 32); u8(o, l.phase, 6); u16(o, l.scoreA); u16(o, l.scoreB); o.writeInt(Math.max(0, l.timeLeftMs));
        o.writeByte(Math.clamp(l.spectators, 0, 255)); utf(o, l.teamA, 64); utf(o, l.teamB, 64);
    }
    private static Live rlive(DataInputStream i) throws IOException {
        String a = rutf(i, 32); int ph = r8(i, 6), sa = i.readUnsignedShort(), sb = i.readUnsignedShort(), t = i.readInt();
        if (t < 0) throw new IllegalArgumentException("time");
        return new Live(a, ph, sa, sb, t, i.readUnsignedByte(), rutf(i, 64), rutf(i, 64));
    }
    private static void card(DataOutputStream o, LiveCard l) throws IOException {
        utf(o, l.arena, 32); u8(o, l.phase, 6); u16(o, l.scoreA); u16(o, l.scoreB); o.writeInt(Math.max(0, l.timeLeftMs)); o.writeByte(Math.clamp(l.spectators, 0, 255));
        whos(o, l.teamA); whos(o, l.teamB);
    }
    private static LiveCard rcard(DataInputStream i) throws IOException {
        String a = rutf(i, 32); int ph = r8(i, 6), sa = i.readUnsignedShort(), sb = i.readUnsignedShort(), t = i.readInt();
        if (t < 0) throw new IllegalArgumentException("time");
        int spec = i.readUnsignedByte();
        return new LiveCard(a, ph, sa, sb, t, spec, rwhos(i), rwhos(i));
    }
    private static void whos(DataOutputStream o, List<Who> l) throws IOException { u8(o, l.size(), 6); for (Who w : l) { uuid(o, w.id); utf(o, w.nick, 32); utf(o, w.skin, 64); } }
    private static List<Who> rwhos(DataInputStream i) throws IOException {
        int n = r8(i, 6); var l = new ArrayList<Who>(n); for (int k = 0; k < n; k++) l.add(new Who(ruuid(i), rutf(i, 32), rutf(i, 64))); return List.copyOf(l);
    }
    private static void rank(DataOutputStream o, Rank r) throws IOException {
        u16(o, r.rank); uuid(o, r.id); utf(o, r.nick, 32); utf(o, r.skin, 64); o.writeInt(r.rating); u8(o, r.tier, 8); u16(o, r.games); u16(o, r.wins); u16(o, r.losses);
    }
    private static Rank rrank(DataInputStream i) throws IOException {
        return new Rank(i.readUnsignedShort(), ruuid(i), rutf(i, 32), rutf(i, 64), i.readInt(), r8(i, 8), i.readUnsignedShort(), i.readUnsignedShort(), i.readUnsignedShort());
    }
    private static void uuids(DataOutputStream o, List<UUID> l, int max) throws IOException { u8(o, l.size(), max); for (UUID u : l) uuid(o, u); }
    private static List<UUID> ruuids(DataInputStream i, int max) throws IOException {
        int n = r8(i, max); var l = new ArrayList<UUID>(n); for (int k = 0; k < n; k++) l.add(ruuid(i)); return List.copyOf(l);
    }

    // ------------------------------------------------------------------ helpers
    private interface Writer { void write(DataOutputStream o) throws IOException; }
    private interface Reader<T> { T read(DataInputStream i) throws IOException; }

    private static byte[] write(Writer w) {
        var bytes = new ByteArrayOutputStream(128);
        try (var o = new DataOutputStream(bytes)) { o.writeByte(VERSION); w.write(o); } catch (IOException e) { throw new UncheckedIOException(e); }
        if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("too large");
        return bytes.toByteArray();
    }
    private static <T> T read(byte[] bytes, Reader<T> r) {
        if (bytes == null || bytes.length < 2 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("size");
        try (var i = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (i.readUnsignedByte() != VERSION) throw new IllegalArgumentException("version");
            T t = r.read(i);
            if (i.available() != 0) throw new IllegalArgumentException("trailing bytes");
            return t;
        } catch (IOException e) { throw new IllegalArgumentException("truncated", e); }
    }
    private static void u8(DataOutputStream o, int v, int max) throws IOException { if (v < 0 || v > max) throw new IllegalArgumentException("value " + v); o.writeByte(v); }
    private static void u16(DataOutputStream o, int v) throws IOException { o.writeShort(Math.clamp(v, 0, 65535)); }
    private static void i16(DataOutputStream o, int v) throws IOException { o.writeShort(Math.clamp(v, Short.MIN_VALUE, Short.MAX_VALUE)); }
    private static void f(DataOutputStream o, float v) throws IOException { if (!Float.isFinite(v)) throw new IllegalArgumentException("float"); o.writeFloat(v); }
    private static void uuid(DataOutputStream o, UUID u) throws IOException { o.writeLong(u.getMostSignificantBits()); o.writeLong(u.getLeastSignificantBits()); }
    private static void utf(DataOutputStream o, String s, int max) throws IOException { String v = s == null ? "" : s; o.writeUTF(v.length() > max ? v.substring(0, max) : v); }
    private static int r8(DataInputStream i, int max) throws IOException { int v = i.readUnsignedByte(); if (v > max) throw new IllegalArgumentException("value " + v); return v; }
    private static float rf(DataInputStream i) throws IOException { float v = i.readFloat(); if (!Float.isFinite(v)) throw new IllegalArgumentException("float"); return v; }
    private static UUID ruuid(DataInputStream i) throws IOException { return new UUID(i.readLong(), i.readLong()); }
    private static boolean rflag(DataInputStream i) throws IOException { int v = i.readUnsignedByte(); if (v > 1) throw new IllegalArgumentException("flag"); return v == 1; }
    private static String rutf(DataInputStream i, int max) throws IOException { String s = i.readUTF(); if (s.length() > max) throw new IllegalArgumentException("text"); return s; }
}
