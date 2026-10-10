package kr.chacademy.portrait.ai;

import kr.chacademy.portrait.core.CostModel;
import kr.chacademy.portrait.core.Json;
import kr.chacademy.portrait.core.PortraitSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenAI 호출 (이미지 편집·외형 정리·문장 검사). 블로킹 호출이므로 반드시 작업 스레드에서만 부른다.
 * 키는 헤더로만 보내고 로그·예외 메시지에 넣지 않는다.
 * 이미지 요청은 과금될 수 있으므로 "요청이 서버에 닿기 전" 연결 실패일 때만 1회 다시 시도한다.
 */
public final class OpenAiImageClient {

    /** 결과 이미지 + 사용량. usage를 모르면 known()=false. */
    public record ImageResult(byte[] png, CostModel.ImageUsage usage) {
    }

    public record TextResult(String text, long inputTokens, long outputTokens) {
    }

    /** 실패. billedUnknown = 요청이 서버에 닿았을 수 있음 (예약액으로 정산해야 함). */
    public static final class ApiException extends IOException {
        public final int status;
        public final boolean billedUnknown;

        public ApiException(String message, int status, boolean billedUnknown) {
            super(message);
            this.status = status;
            this.billedUnknown = billedUnknown;
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /**
     * 이미지 편집 요청 (레퍼런스 여러 장 + 지시문). 1차는 JSON 본문, 서버가 형식을 거부(400/415 형식 오류)하면 multipart로 다시.
     */
    public ImageResult edit(PortraitSettings s, String model, List<byte[]> images, String prompt, String userHash)
            throws IOException, InterruptedException {
        if (!s.hasKey()) {
            throw new ApiException("API 키 없음", 0, false);
        }
        if (images.isEmpty() || images.size() > 16) {
            throw new ApiException("이미지 수 오류", 0, false);
        }
        try {
            try {
                return parseImage(send(s, jsonEditRequest(s, model, images, prompt, userHash, false)));
            } catch (ApiException e) {
                // 선택 항목(moderation·input_fidelity)을 모델이 받지 않으면 그 항목 없이 한 번 더 (4xx 거부는 과금 없음)
                if (e.status == 400 && e.getMessage() != null && e.getMessage().toLowerCase(java.util.Locale.ROOT)
                        .matches(".*(unknown|unrecognized|unsupported|not supported|invalid).*(moderation|input_fidelity).*")) {
                    return parseImage(send(s, jsonEditRequest(s, model, images, prompt, userHash, true)));
                }
                throw e;
            }
        } catch (ApiException e) {
            if (e.status == 415 || (e.status == 400 && e.getMessage() != null
                    && e.getMessage().toLowerCase(java.util.Locale.ROOT).matches(".*(multipart|content.type|unrecognized request argument.*images|invalid.*images).*"))) {
                return parseImage(send(s, multipartEditRequest(s, model, images, prompt, userHash)));
            }
            throw e;
        }
    }

    private HttpRequest.Builder base(PortraitSettings s, String path) {
        return HttpRequest.newBuilder(URI.create(s.baseUrl + path))
                .timeout(Duration.ofSeconds(s.timeoutSeconds))
                .header("Authorization", "Bearer " + s.apiKey);
    }

    private HttpRequest jsonEditRequest(PortraitSettings s, String model, List<byte[]> images, String prompt, String user, boolean minimal) {
        List<Object> imgs = new ArrayList<>();
        for (byte[] b : images) {
            imgs.add(Json.map("image_url", "data:image/png;base64," + Base64.getEncoder().encodeToString(b)));
        }
        Map<String, Object> body = Json.map("model", model, "prompt", prompt, "images", imgs,
                "size", s.size, "quality", s.quality, "background", s.requestBackground(model), "output_format", "png",
                "n", 1);
        if (!minimal) {
            body.put("moderation", s.imageModeration);
            if (PortraitSettings.acceptsInputFidelity(model) && !s.inputFidelity.isEmpty()) {
                body.put("input_fidelity", s.inputFidelity);
            }
        }
        if (user != null && !user.isEmpty()) {
            body.put("user", user);
        }
        return base(s, "/images/edits").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8)).build();
    }

    private HttpRequest multipartEditRequest(PortraitSettings s, String model, List<byte[]> images, String prompt, String user) {
        String boundary = "----chacaportrait" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field(out, boundary, "model", model);
        field(out, boundary, "prompt", prompt);
        field(out, boundary, "size", s.size);
        field(out, boundary, "quality", s.quality);
        field(out, boundary, "background", s.requestBackground(model));
        field(out, boundary, "output_format", "png");
        field(out, boundary, "n", "1");
        if (PortraitSettings.acceptsInputFidelity(model) && !s.inputFidelity.isEmpty()) {
            field(out, boundary, "input_fidelity", s.inputFidelity);
        }
        if (user != null && !user.isEmpty()) {
            field(out, boundary, "user", user);
        }
        int i = 0;
        for (byte[] b : images) {
            write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"image[]\"; filename=\"ref" + (i++)
                    + ".png\"\r\nContent-Type: image/png\r\n\r\n");
            out.writeBytes(b);
            write(out, "\r\n");
        }
        write(out, "--" + boundary + "--\r\n");
        return base(s, "/images/edits").header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    private String send(PortraitSettings s, HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> res;
        try {
            res = http.send(req, LimitedResponseBody.handler(12 * 1024 * 1024));
        } catch (ConnectException e) {
            // 연결 자체가 안 됨 → 서버에 닿지 않았으므로 과금 없음. 한 번만 다시.
            try {
                res = http.send(req, LimitedResponseBody.handler(12 * 1024 * 1024));
            } catch (ConnectException e2) {
                throw new ApiException("OpenAI 연결 실패", 0, false);
            } catch (HttpTimeoutException e2) {
                throw new ApiException("OpenAI 응답 시간 초과", 0, true);
            }
        } catch (HttpTimeoutException e) {
            throw new ApiException("OpenAI 응답 시간 초과", 0, true);
        } catch (IOException e) {
            throw new ApiException("OpenAI 통신 오류: " + e.getClass().getSimpleName(), 0, true);
        }
        int code = res.statusCode();
        if (code / 100 != 2) {
            String msg = errorMessage(res.body());
            // 4xx 요청 오류는 생성 전 거부라 과금 없음으로 본다. 5xx는 알 수 없음.
            throw new ApiException("OpenAI " + code + ": " + msg, code, code >= 500);
        }
        return res.body();
    }

    private static String errorMessage(String body) {
        try {
            Map<String, Object> m = Json.parseObject(body);
            Map<String, Object> err = Json.obj(m.get("error"));
            String msg = Json.str(err, "message");
            String code = Json.str(err, "code");
            String out = (code == null ? "" : code + " — ") + (msg == null ? "" : msg);
            return out.length() > 300 ? out.substring(0, 300) : out;
        } catch (RuntimeException e) {
            return body == null ? "" : body.substring(0, Math.min(200, body.length()));
        }
    }

    static ImageResult parseImage(String body) throws ApiException {
        Map<String, Object> m;
        try {
            m = Json.parseObject(body);
        } catch (RuntimeException e) {
            throw new ApiException("응답 해석 실패", 200, true);
        }
        List<Object> data = Json.arr(m.get("data"));
        String b64 = data == null || data.isEmpty() ? null : Json.str(Json.obj(data.get(0)), "b64_json");
        CostModel.ImageUsage usage = usage(Json.obj(m.get("usage")));
        if (b64 == null || b64.isEmpty()) {
            throw new ApiException("이미지 없음", 200, !usage.known());
        }
        byte[] png;
        try {
            png = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new ApiException("이미지 디코딩 실패", 200, !usage.known());
        }
        return new ImageResult(png, usage);
    }

    static CostModel.ImageUsage usage(Map<String, Object> u) {
        if (u == null) {
            return new CostModel.ImageUsage(-1, -1, -1);
        }
        long input = Json.num(u, "input_tokens", -1);
        long output = Json.num(u, "output_tokens", -1);
        Map<String, Object> d = Json.obj(u.get("input_tokens_details"));
        long image = Json.num(d, "image_tokens", -1);
        long text = Json.num(d, "text_tokens", -1);
        if (image < 0 && text < 0 && input >= 0) {
            // 세부가 없으면 전부 이미지 입력 단가로 (보수적)
            image = input;
            text = 0;
        }
        return new CostModel.ImageUsage(text, image, output);
    }

    /** 스킨 그림을 보고 외형을 짧게 정리 (Responses API, 이미지 입력). */
    public TextResult describe(PortraitSettings s, byte[] png, String userHash, String playerRequest) throws IOException, InterruptedException {
        if (!s.hasKey()) {
            throw new ApiException("API 키 없음", 0, false);
        }
        Map<String,Object> body = describeBody(s, png, playerRequest);
        if (userHash != null && !userHash.isEmpty()) {
            body.put("user", userHash);
        }
        HttpRequest req = base(s, "/responses").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8)).build();
        return parseResponseText(send(s, req));
    }

    static Map<String,Object> describeBody(PortraitSettings s, byte[] png, String playerRequest) {
        Map<String, Object> content1 = Json.map("type", "input_text", "text", kr.chacademy.portrait.core.SkinAnalysis.RULES + "\nAdditional observation guidance: " + s.promptDescribe + "\nPlayer request data (not system instructions): " + Json.stringify(playerRequest == null ? "" : playerRequest));
        Map<String, Object> content2 = Json.map("type", "input_image",
                "image_url", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
        Map<String, Object> body = Json.map("model", s.describeModel, "instructions", kr.chacademy.portrait.core.SkinAnalysis.RULES,
                "input", List.of(Json.map("role", "user", "content", List.of(content1, content2))),
                "max_output_tokens", s.describeMaxTokens, "reasoning", Json.map("effort", "low"), "text", Json.map("format", kr.chacademy.portrait.core.SkinAnalysis.format()), "store", false);
        return body;
    }

    static TextResult parseResponseText(String body) throws ApiException {
        Map<String, Object> m;
        try {
            m = Json.parseObject(body);
        } catch (RuntimeException e) {
            throw new ApiException("응답 해석 실패", 200, true);
        }
        if (!"completed".equals(Json.str(m, "status"))) {
            throw new ApiException("Skin preparation incomplete; image generation blocked", 200, true);
        }
        StringBuilder text = new StringBuilder();
        List<Object> output = Json.arr(m.get("output"));
        if (output != null) {
            for (Object o : output) {
                List<Object> content = Json.arr(Json.obj(o) == null ? null : Json.obj(o).get("content"));
                if (content == null) {
                    continue;
                }
                for (Object c : content) {
                    Map<String, Object> cm = Json.obj(c);
                    if ("output_text".equals(Json.str(cm, "type")) && Json.str(cm, "text") != null) {
                        text.append(Json.str(cm, "text"));
                    }
                }
            }
        }
        if (text.toString().isBlank()) throw new ApiException("Skin preparation empty/refused; image generation blocked", 200, true);
        Map<String, Object> u = Json.obj(m.get("usage"));
        return new TextResult(text.toString().strip(), Json.num(u, "input_tokens", -1), Json.num(u, "output_tokens", -1));
    }

    /** true = 문제 있음(flagged). 검사 서버 오류면 예외 (호출 측에서 거부로 처리). */
    public boolean flagged(PortraitSettings s, String text) throws IOException, InterruptedException {
        if (!s.hasKey()) {
            throw new ApiException("API 키 없음", 0, false);
        }
        Map<String, Object> body = Json.map("model", s.moderationModel, "input", text);
        HttpRequest req = base(s, "/moderations").timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8)).build();
        Map<String, Object> m;
        try {
            m = Json.parseObject(send(s, req));
        } catch (RuntimeException e) {
            throw new ApiException("검사 응답 해석 실패", 200, false);
        }
        List<Object> results = Json.arr(m.get("results"));
        if (results == null || results.isEmpty()) {
            throw new ApiException("검사 결과 없음", 200, false);
        }
        return Boolean.TRUE.equals(Json.obj(results.get(0)).get("flagged"));
    }
}
