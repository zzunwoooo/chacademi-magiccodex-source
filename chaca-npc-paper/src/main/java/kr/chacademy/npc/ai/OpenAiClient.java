package kr.chacademy.npc.ai;

import kr.chacademy.npc.core.Json;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenAI Responses API 호출 (스트리밍). Bukkit과 무관하게 백그라운드 스레드에서만 돈다.
 */
public final class OpenAiClient {

    public record Request(String instructions, List<Map<String, Object>> input, Map<String, Object> schema,
                          String schemaName, int maxOutputTokens, String cacheKey) {
    }

    public record Usage(long input, long cached, long output) {
        public static final Usage ZERO = new Usage(0, 0, 0);
    }

    public enum Status { OK, TIMEOUT, ERROR, NO_KEY }

    /**
     * usage = 이번 요청(재시도 포함)에서 확인된 사용량 합계. usageKnown=false 면 스트림이 끊겨 사용량을 모름
     * (호출자는 예약 금액으로 정산해야 한다).
     */
    public record Result(Status status, String text, Usage usage, String error, long firstChunkMs, long totalMs,
                         boolean usageKnown) {
        /** 200 응답 전 결과(키 없음·HTTP 오류): 과금 없음 = 사용량 0으로 확정. */
        Result(Status status, String text, Usage usage, String error, long firstChunkMs, long totalMs) {
            this(status, text, usage, error, firstChunkMs, totalMs, true);
        }

        Result withUsage(Usage u, boolean known) {
            return new Result(status, text, u, error, firstChunkMs, totalMs, known);
        }

        public boolean ok() {
            return status == Status.OK;
        }
    }

    public static final class Config {
        public String apiKey;
        public String baseUrl;
        public String model;
        public String reasoningEffort;
        /** 이 시간이 지나면 스트림을 끊는다 (화면 타임아웃과 별개, 뒤늦은 응답의 비용도 받기 위해 길게). */
        public int hardTimeoutSeconds;
        public int maxConcurrent;
        public String moderationModel;
    }

    private final Logger log;
    private volatile Config config;
    private final HttpClient http;
    private final ExecutorService pool;
    private final ScheduledExecutorService watchdog;
    private final AtomicInteger waiting = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private volatile boolean sendReasoning = true;
    private volatile boolean sendCacheKey = true;

    public OpenAiClient(Config config, Logger log) {
        this.config = config;
        this.log = log;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.pool = Executors.newFixedThreadPool(config.maxConcurrent, r -> {
            Thread t = new Thread(r, "ChacaNPC-AI");
            t.setDaemon(true);
            return t;
        });
        this.watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChacaNPC-AI-watchdog");
            t.setDaemon(true);
            return t;
        });
    }

    /** reload 때 키·모델 등 교체 (동시 요청 수는 재시작해야 바뀜). */
    public void updateConfig(Config c) {
        this.config = c;
        this.sendReasoning = true;
        this.sendCacheKey = true;
    }

    public int queueSize() {
        return waiting.get();
    }

    public int runningCount() {
        return running.get();
    }

    /** 진행 중 요청을 끊고 최대 3초 기다린다 (정산 콜백이 DB 종료 전에 돌 수 있게). */
    public void shutdown() {
        pool.shutdownNow();
        watchdog.shutdownNow();
        try {
            pool.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 스트리밍 요청. onDelta는 AI 스레드에서 호출된다(받은 원문 조각).
     */
    public CompletableFuture<Result> stream(Request req, Consumer<String> onDelta) {
        waiting.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            waiting.decrementAndGet();
            running.incrementAndGet();
            try {
                long start = System.currentTimeMillis();
                Result r = doStream(req, onDelta, start);
                long in = r.usage().input(), cached = r.usage().cached(), out = r.usage().output();
                boolean unknown = !r.usageKnown();
                // 재시도는 200 응답을 받기 전(과금 없음)에 실패한 경우에만, 꺼지는 중이 아닐 때만
                boolean preBilling = r.status() != Status.OK && r.usageKnown() && r.usage().equals(Usage.ZERO) && r.text().isEmpty();
                if (preBilling && !pool.isShutdown() && r.error() != null && r.error().startsWith("RETRY_PARAM")) {
                    r = doStream(req, onDelta, start);
                    in += r.usage().input(); cached += r.usage().cached(); out += r.usage().output();
                    unknown |= !r.usageKnown();
                } else if (preBilling && !pool.isShutdown() && r.status() == Status.ERROR
                        && System.currentTimeMillis() - start < 3000 && r.error() != null && !r.error().startsWith("HTTP 4")) {
                    sleep(400);
                    if (!pool.isShutdown()) {
                        r = doStream(req, onDelta, start);
                        in += r.usage().input(); cached += r.usage().cached(); out += r.usage().output();
                        unknown |= !r.usageKnown();
                    }
                }
                return r.withUsage(new Usage(in, cached, out), !unknown);
            } finally {
                running.decrementAndGet();
            }
        }, pool);
    }

    private Result doStream(Request req, Consumer<String> onDelta, long start) {
        Config c = config;
        if (c.apiKey == null || c.apiKey.isBlank()) {
            return new Result(Status.NO_KEY, "", Usage.ZERO, "API 키가 없습니다", -1, 0);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", c.model);
        body.put("instructions", req.instructions());
        body.put("input", req.input());
        body.put("max_output_tokens", req.maxOutputTokens());
        body.put("stream", Boolean.TRUE);
        body.put("store", Boolean.FALSE);
        if (sendReasoning && c.reasoningEffort != null && !c.reasoningEffort.isBlank()) {
            body.put("reasoning", Json.map("effort", c.reasoningEffort));
        }
        if (sendCacheKey && req.cacheKey() != null) {
            body.put("prompt_cache_key", req.cacheKey());
        }
        if (req.schema() != null) {
            body.put("text", Json.map("format", Json.map(
                    "type", "json_schema",
                    "name", req.schemaName() == null ? "reply" : req.schemaName(),
                    "schema", req.schema(),
                    "strict", Boolean.TRUE)));
        }

        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(c.baseUrl + "/responses"))
                .timeout(Duration.ofSeconds(Math.max(5, c.hardTimeoutSeconds)))
                .header("Authorization", "Bearer " + c.apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                .build();

        AtomicReference<InputStream> streamRef = new AtomicReference<>();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        Thread worker = Thread.currentThread();
        long hardMs = Math.max(5000, c.hardTimeoutSeconds * 1000L - (System.currentTimeMillis() - start));
        ScheduledFuture<?> totalGuard = watchdog.schedule(() -> {
            timedOut.set(true);
            closeQuietly(streamRef.get());
            worker.interrupt();
        }, hardMs, TimeUnit.MILLISECONDS);

        StringBuilder text = new StringBuilder();
        Usage usage = Usage.ZERO;
        long firstMs = -1;
        // 200 응답을 받은 뒤에는 response.completed 가 오기 전까지 사용량을 "모름"으로 본다 (과금됐을 수 있음)
        boolean got200 = false;
        try {
            HttpResponse<InputStream> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
            streamRef.set(resp.body());
            if (timedOut.get()) {
                closeQuietly(resp.body());
                return new Result(Status.TIMEOUT, "", Usage.ZERO, "timeout", -1, System.currentTimeMillis() - start, false);
            }
            if (resp.statusCode() != 200) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                return handleHttpError(resp.statusCode(), err, start);
            }
            got200 = true;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || data.equals("[DONE]")) {
                        continue;
                    }
                    Map<String, Object> ev;
                    try {
                        ev = Json.parseObject(data);
                    } catch (RuntimeException ex) {
                        continue;
                    }
                    String type = Json.str(ev, "type");
                    if (type == null) {
                        continue;
                    }
                    switch (type) {
                        case "response.output_text.delta" -> {
                            String delta = Json.str(ev, "delta");
                            if (delta != null && !delta.isEmpty()) {
                                if (firstMs < 0) {
                                    firstMs = System.currentTimeMillis() - start;
                                }
                                text.append(delta);
                                if (onDelta != null) {
                                    try {
                                        onDelta.accept(delta);
                                    } catch (RuntimeException ex) {
                                        log.log(Level.FINE, "onDelta error", ex);
                                    }
                                }
                            }
                        }
                        case "response.completed", "response.incomplete" -> {
                            usage = readUsage(Json.obj(ev.get("response")));
                            if ("response.incomplete".equals(type)) {
                                Map<String, Object> det = Json.obj(Json.obj(ev.get("response")).get("incomplete_details"));
                                log.fine("[ChacaNPC] 응답이 잘림: " + Json.str(det, "reason")
                                        + " — max-output-tokens를 늘려 보세요");
                            }
                            return new Result(Status.OK, text.toString(), usage, null, firstMs,
                                    System.currentTimeMillis() - start, true);
                        }
                        case "response.failed", "error" -> {
                            String msg = data.length() > 300 ? data.substring(0, 300) : data;
                            return new Result(Status.ERROR, text.toString(), usage, "API: " + msg, firstMs,
                                    System.currentTimeMillis() - start, false);
                        }
                        default -> {
                            // 다른 이벤트는 무시
                        }
                    }
                }
            }
            if (timedOut.get()) {
                return new Result(Status.TIMEOUT, text.toString(), usage, "timeout", firstMs,
                        System.currentTimeMillis() - start, false);
            }
            // completed 이벤트 없이 끝남: 받은 만큼이라도 돌려주되 사용량은 모름
            return new Result(text.length() > 0 ? Status.OK : Status.ERROR, text.toString(), usage,
                    text.length() > 0 ? null : "stream ended", firstMs, System.currentTimeMillis() - start, false);
        } catch (Exception ex) {
            if (timedOut.get()) {
                return new Result(Status.TIMEOUT, text.toString(), usage, "timeout", firstMs,
                        System.currentTimeMillis() - start, !got200);
            }
            return new Result(Status.ERROR, text.toString(), usage, ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    firstMs, System.currentTimeMillis() - start, !got200);
        } finally {
            totalGuard.cancel(false);
            Thread.interrupted(); // 워치독이 건 인터럽트 정리
        }
    }

    private Result handleHttpError(int code, String body, long start) {
        String lower = body.toLowerCase();
        if (code == 400 && sendReasoning && lower.contains("reasoning")) {
            sendReasoning = false;
            log.warning("[ChacaNPC] 이 모델은 reasoning 설정을 받지 않아 빼고 다시 보냅니다.");
            return new Result(Status.ERROR, "", Usage.ZERO, "RETRY_PARAM reasoning", -1, System.currentTimeMillis() - start);
        }
        if (code == 400 && sendCacheKey && lower.contains("prompt_cache_key")) {
            sendCacheKey = false;
            log.warning("[ChacaNPC] prompt_cache_key를 받지 않아 빼고 다시 보냅니다.");
            return new Result(Status.ERROR, "", Usage.ZERO, "RETRY_PARAM prompt_cache_key", -1, System.currentTimeMillis() - start);
        }
        String shortBody = body.length() > 400 ? body.substring(0, 400) : body;
        String prefix = code >= 500 || code == 429 ? "HTTP5 " : "HTTP 4 ";
        if (code == 401) {
            log.warning("[ChacaNPC] OpenAI API 키가 잘못됐습니다 (401). config.yml의 openai.api-key를 확인하세요.");
        }
        return new Result(Status.ERROR, "", Usage.ZERO, prefix + code + " " + shortBody, -1,
                System.currentTimeMillis() - start);
    }

    private static Usage readUsage(Map<String, Object> response) {
        Map<String, Object> u = response == null ? null : Json.obj(response.get("usage"));
        if (u == null) {
            return Usage.ZERO;
        }
        long in = Json.num(u, "input_tokens", 0);
        long out = Json.num(u, "output_tokens", 0);
        long cached = Json.num(Json.obj(u.get("input_tokens_details")), "cached_tokens", 0);
        return new Usage(in, cached, out);
    }

    /** 입력 검사 (moderation). 실패하면 통과로 본다. */
    public CompletableFuture<Boolean> moderate(String text) {
        Config c = config;
        if (c.apiKey == null || c.apiKey.isBlank() || c.moderationModel == null || c.moderationModel.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> body = Json.map("model", c.moderationModel, "input", text);
        HttpRequest req = HttpRequest.newBuilder(URI.create(c.baseUrl + "/moderations"))
                .timeout(Duration.ofSeconds(3))
                .header("Authorization", "Bearer " + c.apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) {
                        return false;
                    }
                    List<Object> results = Json.arr(Json.parseObject(resp.body()).get("results"));
                    if (results == null || results.isEmpty()) {
                        return false;
                    }
                    return Boolean.TRUE.equals(Json.obj(results.get(0)).get("flagged"));
                })
                .exceptionally(ex -> false);
    }

    /** 메시지 하나 만들기 도우미. */
    public static Map<String, Object> message(String role, String content) {
        return Json.map("role", role, "content", content);
    }

    public static List<Map<String, Object>> messages() {
        return new ArrayList<>();
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (Exception ignored) {
                // 무시
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
