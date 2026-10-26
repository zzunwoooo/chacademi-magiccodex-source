package school.magiccodex.paper;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 뽑기 상자 개봉 영수증 (아티팩트 소환 ArtifactPurchaseJournal·낚시 FishMailOutbox 와 같은 방식: tmp → fsync → ATOMIC_MOVE).
 * 서버 로컬 plugins/MagicCodexBridge/lootbox-receipts/ 에 개봉 한 건 = 파일 하나. Bukkit 없음 (JUnit 대상).
 *
 * <p>개봉 순서: ① 추첨 확정 + 이 기록 저장(IO 스레드) → ② 같은 틱에 열쇠 차감·보상 넣기·영수증(PDC lootbox_&lt;번호&gt;)·saveData
 * → ③ 남은 것(mail)만 우편(source "lootbox:&lt;번호&gt;:&lt;쪽&gt;", 우편 source_key UNIQUE 라 다시 보내도 한 통) → ④ 우편 저장 확인 뒤 이 기록 삭제.</p>
 * <p>복구: 기록이 남아 있으면 플레이어 데이터에 영수증이 있는지로 판정한다. 있음 = ②가 끝났음 → 우편만 다시 보낸다(재추첨·재지급 없음).
 * 없음 = ②가 일어나지 않았음(열쇠도 그대로) → 기록을 버린다. 결과를 클라에 보내는 것은 ② 뒤라서 버려진 추첨은 아무도 보지 못한다.</p>
 */
final class LootBoxJournal {
    record Opening(String operation, UUID player, String box, String title, int count, int grade,
                   List<byte[]> results, int[] grades, List<byte[]> mail, long created, Path file) {
        String receipt() { return receiptName(operation); }
    }

    private static final long MAX_FILE = 2_097_152;
    private final Path root;

    LootBoxJournal(Path root) throws IOException { this.root = root; Files.createDirectories(root); }

    static String receiptName(String operation) { return "lootbox_" + operation.replace("-", ""); }

    private Path path(String op) {
        if (!op.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("operation");
        return root.resolve(op + ".properties");
    }

    synchronized Opening save(String operation, UUID player, String box, String title, int count, int grade,
                              List<byte[]> results, int[] grades, List<byte[]> mail) throws IOException {
        if (player == null || box == null || title == null || results.isEmpty() || results.size() != grades.length || results.size() > 10)
            throw new IOException("Invalid lootbox receipt");
        long created = System.currentTimeMillis();
        var p = new Properties();
        p.setProperty("operation", operation); p.setProperty("player", player.toString()); p.setProperty("box", box);
        p.setProperty("title", title); p.setProperty("count", Integer.toString(count)); p.setProperty("grade", Integer.toString(grade));
        p.setProperty("results", Integer.toString(results.size()));
        for (int i = 0; i < results.size(); i++) {
            p.setProperty("result." + i, Base64.getEncoder().encodeToString(results.get(i)));
            p.setProperty("grade." + i, Integer.toString(grades[i]));
        }
        p.setProperty("mail", Integer.toString(mail.size()));
        for (int i = 0; i < mail.size(); i++) p.setProperty("mail." + i, Base64.getEncoder().encodeToString(mail.get(i)));
        p.setProperty("created", Long.toString(created));
        Path target = path(operation), tmp = Files.createTempFile(root, "lootbox-", ".tmp");
        try {
            try (var w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { p.store(w, "Private loot box receipt; never publish"); }
            try (var ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) { ch.force(true); }
            try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
        return new Opening(operation, player, box, title, count, grade, List.copyOf(results), grades.clone(), List.copyOf(mail), created, target);
    }

    /** 읽을 수 있는 기록. 깨진·너무 큰·심볼릭 파일은 건드리지 않고 broken 에 담는다(관리자 확인, 자동 처리 안 함). */
    synchronized List<Opening> pending(List<Path> broken) throws IOException {
        var out = new ArrayList<Opening>();
        try (var files = Files.newDirectoryStream(root, "*.properties")) {
            for (Path f : files) {
                try {
                    if (Files.isSymbolicLink(f) || Files.size(f) > MAX_FILE) throw new IOException("unsafe");
                    var p = new Properties();
                    try (var r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) { p.load(r); }
                    String op = Objects.requireNonNull(p.getProperty("operation"));
                    if (!f.getFileName().toString().equals(op + ".properties")) throw new IOException("name");
                    int n = Integer.parseInt(p.getProperty("results")), m = Integer.parseInt(p.getProperty("mail"));
                    if (n < 1 || n > 10 || m < 0 || m > 200) throw new IOException("counts");
                    var results = new ArrayList<byte[]>(); int[] grades = new int[n];
                    for (int i = 0; i < n; i++) {
                        results.add(Base64.getDecoder().decode(Objects.requireNonNull(p.getProperty("result." + i))));
                        grades[i] = Integer.parseInt(p.getProperty("grade." + i));
                    }
                    var mail = new ArrayList<byte[]>();
                    for (int i = 0; i < m; i++) mail.add(Base64.getDecoder().decode(Objects.requireNonNull(p.getProperty("mail." + i))));
                    out.add(new Opening(op, UUID.fromString(p.getProperty("player")), p.getProperty("box"), p.getProperty("title"),
                            Integer.parseInt(p.getProperty("count")), Integer.parseInt(p.getProperty("grade")), List.copyOf(results), grades,
                            List.copyOf(mail), Long.parseLong(p.getProperty("created")), f));
                } catch (IOException | RuntimeException e) { if (broken != null) broken.add(f); }
            }
        }
        return out;
    }

    synchronized void remove(String operation) throws IOException { Files.deleteIfExists(path(operation)); }
}
