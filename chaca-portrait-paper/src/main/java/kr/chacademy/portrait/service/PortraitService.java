package kr.chacademy.portrait.service;

import kr.chacademy.portrait.ChacaPortraitPlugin;
import kr.chacademy.portrait.ai.OpenAiImageClient;
import kr.chacademy.portrait.core.CostModel;
import kr.chacademy.portrait.core.PortraitSettings;
import kr.chacademy.portrait.core.PromptBuilder;
import kr.chacademy.portrait.data.PortraitStorage;
import kr.chacademy.portrait.skin.SkinFetcher;
import kr.chacademy.portrait.skin.SkinRenderer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * 일러스트 생성 작업. 스킨 → 앞/뒤 그림 → (선택) 외형 정리 → 이미지 모델(레퍼런스 + 스킨 그림) → 검사 → 저장 → 전송.
 * 모든 OpenAI·DB 작업은 작업 스레드에서, 플레이어 메시지·아이템은 메인 스레드에서 처리한다.
 */
public final class PortraitService {

    public enum Kind { AUTO, REROLL, ADMIN, TEST }

    /** 작업 하나. TEST는 저장하지 않고 결과 파일만 남긴다 (models 여러 개 가능). */
    public record Job(Kind kind, UUID player, String name, SkinFetcher.SkinRef skin, String request,
                      String rerollToken, List<String> models, CommandSender reporter) {
    }

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final long LOCK_STALE_MS = 20 * 60 * 1000L;
    private static final int MAX_RESULT_BYTES = 8 * 1024 * 1024;

    private final ChacaPortraitPlugin plugin;
    private final OpenAiImageClient ai = new OpenAiImageClient();
    private final SkinFetcher skins = new SkinFetcher();
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingQueue<Runnable> waiting = new LinkedBlockingQueue<>();
    private final AtomicInteger threadNo = new AtomicInteger();
    private volatile ThreadPoolExecutor pool;
    private final kr.chacademy.portrait.core.GenerationGate gate = new kr.chacademy.portrait.core.GenerationGate();
    private volatile List<byte[]> references = List.of();
    private volatile String referenceError = "레퍼런스를 아직 읽지 않았습니다";

    public PortraitService(ChacaPortraitPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        PortraitSettings s = plugin.settings();
        pool = new ThreadPoolExecutor(s.workers, s.workers, 30, TimeUnit.SECONDS, waiting, r -> {
            Thread t = new Thread(r, "ChacaPortrait-Job-" + threadNo.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        CompletableFuture.runAsync(this::loadReferences);
    }

    /** /portrait reload: 작업 수·레퍼런스 다시 읽기. */
    public void reload() {
        PortraitSettings s = plugin.settings();
        ThreadPoolExecutor p = pool;
        if (p != null) {
            if (s.workers > p.getMaximumPoolSize()) {
                p.setMaximumPoolSize(s.workers);
                p.setCorePoolSize(s.workers);
            } else {
                p.setCorePoolSize(s.workers);
                p.setMaximumPoolSize(s.workers);
            }
        }
        CompletableFuture.runAsync(this::loadReferences);
    }

    public void stop() {
        gate.stop();
        ThreadPoolExecutor p = pool;
        if (p != null) {
            p.shutdownNow();
            try {
                p.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void loadReferences() {
        PortraitSettings s = plugin.settings();
        List<byte[]> out = new ArrayList<>();
        String error = null;
        for (String rel : s.references) {
            Path base = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
            Path file = base.resolve(rel).normalize();
            if (!file.startsWith(base)) {
                error = "레퍼런스 경로가 플러그인 폴더 밖입니다: " + rel;
                break;
            }
            try {
                byte[] b = Files.readAllBytes(file);
                checkPng(b, 4096);
                out.add(b);
            } catch (IOException e) {
                error = "레퍼런스를 읽지 못했습니다: " + rel + " (" + e.getMessage() + ")";
                break;
            }
        }
        if (error == null && out.isEmpty()) {
            error = "레퍼런스가 설정되지 않았습니다";
        }
        if (error != null) {
            references = List.of();
            referenceError = error;
            plugin.getLogger().warning("[ChacaPortrait] " + error);
        } else {
            references = List.copyOf(out);
            referenceError = null;
            plugin.getLogger().info("[ChacaPortrait] 레퍼런스 " + out.size() + "장 준비됨");
        }
    }

    public String referenceError() {
        return referenceError;
    }

    public boolean busy(UUID id) {
        return active.contains(id);
    }

    public int waitingCount() {
        return waiting.size();
    }

    public int runningCount() {
        ThreadPoolExecutor p = pool;
        return p == null ? 0 : p.getActiveCount();
    }

    /**
     * 작업 넣기 (메인 스레드). 같은 플레이어 작업이 이미 있으면 false (TEST 제외).
     * @param onRejected 대기열이 가득 차는 등으로 못 넣었을 때 (메인 스레드에서 호출)
     */
    public boolean submit(Job job, Runnable onRejected) {
        ThreadPoolExecutor p = pool;
        if (!gate.running() || p == null || p.isShutdown()) {
            return false;
        }
        if (job.kind() != Kind.TEST && !active.add(job.player())) {
            return false;
        }
        if (waiting.size() >= plugin.settings().maxWaiting) {
            if (job.kind() != Kind.TEST) {
                active.remove(job.player());
            }
            return false;
        }
        try {
            p.execute(() -> {
                try {
                    run(job);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "[ChacaPortrait] 작업 오류: " + t, t);
                    finishFailure(job, "내부 오류: " + t.getClass().getSimpleName(), true, false);
                } finally {
                    if (job.kind() != Kind.TEST) {
                        active.remove(job.player());
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            if (job.kind() != Kind.TEST) {
                active.remove(job.player());
            }
            if (onRejected != null) {
                onRejected.run();
            }
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ 작업 본체 (작업 스레드)

    /** 작업 실패 (포장용). countAttempt = 자동 생성 시도 횟수에 넣을지 (플레이어 탓이 아닌 실패는 넣지 않음). */
    private static final class JobFailure extends Exception {
        final boolean countAttempt;
        final boolean quiet;

        JobFailure(String message, boolean countAttempt, boolean quiet) {
            super(message);
            this.countAttempt = countAttempt;
            this.quiet = quiet;
        }
    }

    /** DB 작업을 기다린다. 쓰기는 시간 제한 없이 끝까지 기다린다 (시간 초과 후 뒤늦게 커밋되는 일 방지). */
    private <T> T await(java.util.concurrent.CompletableFuture<T> f) throws Exception {
        try {
            return f.get();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            throw c instanceof Exception ex ? ex : new RuntimeException(c);
        }
    }

    private void run(Job job) throws Exception {
        PortraitSettings s = plugin.settings();
        PortraitStorage st = plugin.storage();
        String server = s.serverId;
        boolean locked = false;
        try {
            if (job.kind() != Kind.TEST) {
                locked = await(plugin.db().call(() -> st.claim(job.player(), server, LOCK_STALE_MS)));
                if (!locked) {
                    throw new JobFailure("다른 서버에서 이미 그리는 중", false, true);
                }
            }
            if (!gate.running()) return;
            if (job.kind() == Kind.AUTO && (!s.automaticGenerationAllowed()
                    || await(plugin.db().call(() -> st.sha(job.player()) != null
                        || st.autoAttempts(job.player()) >= s.autoMaxAttempts)))) return;
            Committed done = produce(job, s, st);
            if (done == null) {
                return; // TEST
            }
            // ---- 여기부터는 이미 확정됨: 실패해도 반환·재시도하지 않는다 ----
            try {
                plugin.getLogger().info("[ChacaPortrait] " + job.name() + " 일러스트 완료 (" + done.model + ", "
                        + CostModel.usd(done.cost) + ", " + done.millis / 1000 + "초)");
                plugin.main(() -> {
                    Player p = Bukkit.getPlayer(job.player());
                    if (p != null) {
                        plugin.channel().push(p, done.sha, done.png);
                        p.sendMessage(s.message("done"));
                    }
                    if (job.reporter() != null && !(job.reporter() instanceof Player rp && rp.getUniqueId().equals(job.player()))) {
                        job.reporter().sendMessage("§a[ChacaPortrait] " + job.name() + " 완료 — " + done.model + ", " + CostModel.usd(done.cost));
                    }
                });
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[ChacaPortrait] 완료 알림 실패: " + e);
            }
        } catch (JobFailure f) {
            finishFailure(job, f.getMessage(), f.quiet, f.countAttempt);
        } catch (DiscardedResult d) {
            plugin.getLogger().info("[ChacaPortrait] " + job.name() + " 결과 폐기 (다시 그리기 기록이 이미 다른 상태)");
        } finally {
            if (locked) {
                try {
                    await(plugin.db().call(() -> {
                        st.release(job.player(), server);
                        return null;
                    }));
                } catch (Exception e) {
                    plugin.getLogger().warning("[ChacaPortrait] 잠금 해제 실패: " + e.getMessage());
                }
            }
        }
    }

    private static final class DiscardedResult extends Exception {
    }

    private record Committed(String sha, byte[] png, String model, long cost, long millis) {
    }

    /** 생성부터 확정까지. TEST면 null. 실패는 JobFailure, 확정 전 내부 오류는 그대로 던짐(→ 반환 처리). */
    private Committed produce(Job job, PortraitSettings s, PortraitStorage st) throws Exception {
        String server = s.serverId;
        if (!gate.running() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Portrait generation stopped");
        if (!s.enabled) {
            throw new JobFailure("기능 꺼짐", false, true);
        }
        if (!s.hasKey()) {
            throw new JobFailure("OpenAI 키 없음 (환경변수 CHACAPORTRAIT_OPENAI_KEY 또는 CHACANPC_OPENAI_KEY)", false, true);
        }
        List<byte[]> refs = references;
        if (refs.isEmpty()) {
            throw new JobFailure(String.valueOf(referenceError), false, true);
        }
        List<String> models = job.models() == null || job.models().isEmpty() ? List.of(s.imageModel) : job.models();
        for (String m : models) {
            if (!PortraitSettings.supportedImageModel(m)) {
                throw new JobFailure("지원하지 않는 이미지 모델: " + m, false, true);
            }
        }
        CostModel cost = new CostModel(s.prices, s.estimate);

        // 1) 스킨
        byte[] render;
        try {
            byte[] skinPng = skins.download(job.skin());
            render = SkinRenderer.renderPng(skinPng, job.skin().slim());
        } catch (IOException e) {
            throw new JobFailure("스킨: " + e.getMessage(), false, true);
        }
        String user = s.sendUserHash ? userHash(job.player()) : null;

        // 2) 외형 정리 (선택, 실패해도 계속)
        String appearance = "";
        if (s.describeEnabled && s.describeModel != null && !s.describeModel.isBlank()) {
            String rid = newId();
            long reserve = cost.reserveDescribe(s.describeModel, s.describeMaxTokens);
            if (await(plugin.db().call(() -> st.reserve(rid, server, reserve, budgetCap(s))))) {
                long settle = reserve; // 요청이 나갔을 수 있으면 예약액 (누락 방지)
                long in = -1, out = -1;
                boolean ok = false;
                try {
                    if (!gate.running() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Generation stopped");
                    OpenAiImageClient.TextResult r = ai.describe(s, render, user);
                    appearance = PromptBuilder.clean(r.text(), 600);
                    in = r.inputTokens();
                    out = r.outputTokens();
                    settle = cost.settleDescribe(s.describeModel, in, out, reserve);
                    ok = true;
                } catch (OpenAiImageClient.ApiException e) {
                    settle = e.billedUnknown ? reserve : 0;
                    plugin.getLogger().info("[ChacaPortrait] 외형 정리 생략 (" + e.getMessage() + ")");
                } finally {
                    settleQuietly(job, rid, settle, "describe", s.describeModel, 0, Math.max(0, in), Math.max(0, out), ok);
                }
            }
        }
        String prompt = PromptBuilder.build(s.promptBase, s.promptAppearance, s.promptRequest, appearance, job.request());
        List<byte[]> inputs = new ArrayList<>(refs);
        inputs.add(render);

        // 3) 이미지 (TEST는 모델별로, 저장 안 함)
        if (job.kind() == Kind.TEST) {
            runTest(job, s, cost, models, inputs, prompt, render, user);
            return null;
        }
        String model = models.get(0);
        Generated g = generate(job, s, cost, model, inputs, prompt, user);
        String sha = sha256(g.png);
        String source = job.kind() == Kind.REROLL ? "reroll" : job.kind() == Kind.ADMIN ? "admin" : "auto";
        String day = LocalDate.now(ZONE).toString();
        boolean committed = await(plugin.db().call(() -> gate.commit(() -> st.commitResult(job.player(), job.rerollToken(), sha, g.png, model,
                source, job.request(), g.cost, day))));
        if (!committed) {
            throw new DiscardedResult();
        }
        return new Committed(sha, g.png, model, g.cost, g.millis);
    }

    private record Generated(byte[] png, long cost, long millis, CostModel.ImageUsage usage) {
    }

    /** 예약 → 호출 → 검사 → 정산 (정산은 어떤 경우에도 한 번). 실패는 JobFailure. */
    private Generated generate(Job job, PortraitSettings s, CostModel cost, String model, List<byte[]> inputs,
                               String prompt, String user) throws Exception {
        if (!gate.running() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Generation stopped");
        PortraitStorage st = plugin.storage();
        if (!cost.hasPrices(model)) {
            throw new JobFailure("단가표(prices)에 " + model + " 없음 — 예산을 지킬 수 없어 중단", false, true);
        }
        String rid = newId();
        long reserve = cost.reserveImage(model, s.quality);
        if (!await(plugin.db().call(() -> st.reserve(rid, s.serverId, reserve, budgetCap(s))))) {
            notifyBudget(job);
            throw new JobFailure("예산 소진", false, true);
        }
        long started = System.currentTimeMillis();
        long settle = reserve; // 기본값: 요청이 나갔을 수 있으므로 예약액
        CostModel.ImageUsage usage = null;
        byte[] png = null;
        String error = null;
        try {
            if (!gate.running() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Generation stopped");
            OpenAiImageClient.ImageResult r = ai.edit(s, model, inputs, prompt, user);
            usage = r.usage();
            settle = cost.settleImage(model, usage, reserve);
            String invalid = validateResult(r.png(), s.requireTransparent);
            if (invalid != null) {
                error = invalid;
            } else {
                png = r.png();
            }
        } catch (OpenAiImageClient.ApiException e) {
            settle = e.billedUnknown ? reserve : 0;
            error = e.getMessage();
        } finally {
            CostModel.ImageUsage u = usage;
            settleQuietly(job, rid, settle, job.kind().name().toLowerCase(java.util.Locale.ROOT), model,
                    u == null ? 0 : Math.max(0, u.textInput()), u == null ? 0 : Math.max(0, u.imageInput()),
                    u == null ? 0 : Math.max(0, u.output()), png != null);
        }
        if (png == null) {
            throw new JobFailure(error == null ? "알 수 없는 오류" : error, true, false);
        }
        return new Generated(png, settle, System.currentTimeMillis() - started, usage);
    }

    /** 정산 + 사용 기록. 실패해도 예외를 던지지 않는다 (남은 예약은 다음 시작 때 예약액으로 정산됨). */
    private void settleQuietly(Job job, String rid, long amount, String kind, String model, long textIn, long imageIn,
                               long output, boolean ok) {
        try {
            await(plugin.db().call(() -> {
                plugin.storage().settle(rid, amount);
                plugin.storage().logUsage(job.player(), kind, model, textIn, imageIn, output, amount, ok);
                return null;
            }));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("[ChacaPortrait] 정산 중 중단 (" + rid + ") — 다음 시작 때 예약액으로 정산됩니다");
        } catch (Exception e) {
            plugin.getLogger().warning("[ChacaPortrait] 정산 실패 (" + rid + "): " + e.getMessage());
        }
    }

    private void runTest(Job job, PortraitSettings s, CostModel cost, List<String> models, List<byte[]> inputs,
                         String prompt, byte[] render, String user) throws Exception {
        Path dir = plugin.getDataFolder().toPath().resolve("tests");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String base = job.name().replaceAll("[^A-Za-z0-9_]", "_") + "_" + stamp;
        Files.write(dir.resolve(base + "_skin.png"), render);
        Files.writeString(dir.resolve(base + "_prompt.txt"), prompt, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        for (String model : models) {
            String line;
            try {
                Generated g = generate(job, s, cost, model, inputs, prompt, user);
                Path out = dir.resolve(base + "_" + model.replaceAll("[^A-Za-z0-9.-]", "_") + ".png");
                Files.write(out, g.png);
                CostModel.ImageUsage u = g.usage;
                line = "§a" + model + "§f " + CostModel.usd(g.cost) + " (" + g.millis / 1000 + "초"
                        + (u != null && u.known() ? ", 입력 텍스트 " + u.textInput() + " / 이미지 " + u.imageInput()
                        + " / 출력 " + u.output() + " 토큰" : ", 사용량 미표시 → 예약액으로 계산") + ") → tests/" + out.getFileName();
            } catch (JobFailure f) {
                line = "§c" + model + " 실패: " + f.getMessage();
            }
            lines.add(line);
            plugin.getLogger().info("[ChacaPortrait] 시험 " + job.name() + ": " + line.replaceAll("§.", ""));
        }
        plugin.main(() -> {
            CommandSender r = job.reporter();
            if (r != null) {
                r.sendMessage("§b[ChacaPortrait] 시험 생성 결과 (" + job.name() + ", 품질 " + s.quality + ")");
                lines.forEach(r::sendMessage);
                r.sendMessage("§7스킨 그림·지시문: tests/" + base + "_skin.png, _prompt.txt");
            }
        });
    }

    private long budgetCap(PortraitSettings s) {
        return (long) Math.floor(s.budgetUsd * 1_000_000L);
    }

    private void notifyBudget(Job job) {
        plugin.main(() -> {
            if (job.reporter() != null) {
                job.reporter().sendMessage("§c[ChacaPortrait] 예산이 부족합니다 (/portrait budget)");
            }
        });
    }

    /**
     * 확정 전 실패 처리 (어느 스레드든). 다시 그리기면 아이템 반환.
     * quiet=true면 플레이어에게 실패 안내를 하지 않음. countAttempt=true일 때만 자동 시도 횟수에 넣는다.
     */
    private void finishFailure(Job job, String error, boolean quiet, boolean countAttempt) {
        PortraitSettings s = plugin.settings();
        plugin.getLogger().info("[ChacaPortrait] " + job.name() + " " + job.kind() + " 실패: " + error);
        if (job.kind() != Kind.TEST) {
            plugin.db().call(() -> {
                plugin.storage().recordAttempt(job.player(), job.kind() == Kind.AUTO && countAttempt, error);
                return null;
            });
        }
        if (job.rerollToken() != null) {
            plugin.rerolls().failed(job.player(), job.rerollToken());
        }
        plugin.main(() -> {
            if (job.reporter() != null) {
                job.reporter().sendMessage("§c[ChacaPortrait] " + job.name() + " 실패: " + error);
            }
            if (job.kind() == Kind.AUTO && !quiet) {
                Player p = Bukkit.getPlayer(job.player());
                if (p != null) {
                    p.sendMessage(s.message("failed"));
                }
            }
        });
    }

    // ------------------------------------------------------------------ 검사·도구

    /** PNG인지, 크기 상한, (선택) 투명 배경인지. 문제 없으면 null. */
    static String validateResult(byte[] png, boolean requireTransparent) {
        if (png == null || png.length < 64 || png.length > MAX_RESULT_BYTES) {
            return "결과 이미지 크기 오류";
        }
        try {
            BufferedImage img = checkPng(png, 2048);
            if (requireTransparent && !img.getColorModel().hasAlpha()) {
                return "투명 배경이 아님";
            }
            if (requireTransparent) {
                // 네 귀퉁이 중 하나라도 불투명하면 배경이 남은 것으로 본다
                int w = img.getWidth() - 1, h = img.getHeight() - 1;
                int[][] corners = {{0, 0}, {w, 0}, {0, h}, {w, h}};
                for (int[] c : corners) {
                    if ((img.getRGB(c[0], c[1]) >>> 24) > 16) {
                        return "투명 배경이 아님";
                    }
                }
            }
            return null;
        } catch (IOException e) {
            return "결과 이미지 오류: " + e.getMessage();
        }
    }

    /** PNG 헤더·크기 확인 후 디코드. 너무 큰 이미지는 디코드 전에 거부. */
    static BufferedImage checkPng(byte[] png, int maxSide) throws IOException {
        if (png.length < 8 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
            throw new IOException("PNG가 아님");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("읽을 수 없는 이미지");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > maxSide || h > maxSide) {
                    throw new IOException("이미지 크기 " + w + "x" + h);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    public static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String userHash(UUID id) {
        return sha256(("chacademi-portrait:" + id).getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 메인 스레드: 플레이어 스킨을 읽어 작업을 만든다. */
    public static Job jobFor(Kind kind, Player p, String request, String token, List<String> models, CommandSender reporter) {
        return new Job(kind, p.getUniqueId(), p.getName(), SkinFetcher.refOf(p), request, token, models, reporter);
    }

    /** 비동기 완료 후 메인 스레드에서 이어서. */
    public <T> void then(CompletableFuture<T> f, Consumer<T> onMain, Consumer<Throwable> onError) {
        f.whenComplete((v, ex) -> plugin.main(() -> {
            if (ex != null) {
                if (onError != null) {
                    onError.accept(ex);
                }
            } else {
                onMain.accept(v);
            }
        }));
    }
}
