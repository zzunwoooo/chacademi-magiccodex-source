package kr.chacademy.portrait.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** config.yml 스냅샷 (불변). /portrait reload 때 새로 만든다. */
public final class PortraitSettings {

    public final String serverId;
    public final boolean enabled;
    public final String apiKey;
    public final String baseUrl;
    public final int timeoutSeconds;
    public final boolean sendUserHash;

    public final String imageModel;
    public final String quality;
    public final String size;
    public final String background;
    public final String inputFidelity;
    public final String imageModeration;
    public final List<String> references;
    public final boolean requireTransparent;

    public final boolean describeEnabled;
    public final String describeModel;
    public final int describeMaxTokens;

    public final boolean moderationEnabled;
    public final String moderationModel;

    public final int workers;
    public final int maxWaiting;

    public final boolean autoFirstJoin;
    public final int autoDelaySeconds;
    public final int autoMaxAttempts;

    public final String rerollItemId;
    public final int promptTimeoutSeconds;
    public final int rerollCooldownSeconds;
    public final int rerollDailyLimit;

    public final double budgetUsd;
    public final Map<String, CostModel.Prices> prices;
    public final CostModel.Estimate estimate;

    public final String promptBase;
    public final String promptAppearance;
    public final String promptRequest;
    public final String promptDescribe;

    private final Map<String, String> messages = new HashMap<>();

    public PortraitSettings(FileConfiguration c) {
        this(c, System::getenv);
    }

    PortraitSettings(FileConfiguration c, java.util.function.Function<String, String> environment) {
        serverId = safeId(c.getString("server-id", "school"));
        enabled = c.getBoolean("enabled", true);
        String key = "";
        String env = environment.apply("CHACAPORTRAIT_OPENAI_KEY");
        if (env == null || env.isBlank()) {
            env = environment.apply("CHACANPC_OPENAI_KEY");
        }
        if (env != null && !env.isBlank()) {
            key = env;
        }
        apiKey = key == null ? "" : key.trim();
        String base = c.getString("openai.base-url", "https://api.openai.com/v1");
        baseUrl = base == null ? "https://api.openai.com/v1" : base.replaceAll("/+$", "");
        timeoutSeconds = clamp(c.getInt("openai.timeout-seconds", 240), 30, 600);
        sendUserHash = c.getBoolean("openai.send-user-hash", true);

        imageModel = c.getString("image.model", "gpt-image-2").trim();
        quality = oneOf(c.getString("image.quality", "medium"), "medium", "low", "medium", "high", "auto");
        String sz = c.getString("image.size", "1024x1536");
        size = sz != null && sz.matches("\\d{3,4}x\\d{3,4}") ? sz : "1024x1536";
        background = oneOf(c.getString("image.background", "transparent"), "transparent", "transparent", "opaque", "auto");
        inputFidelity = oneOf(c.getString("image.input-fidelity", "high"), "high", "high", "low", "");
        imageModeration = oneOf(c.getString("image.moderation", "auto"), "auto", "auto", "low");
        List<String> refs = c.getStringList("image.references");
        references = refs.isEmpty() ? List.of("reference/style-reference.png") : List.copyOf(refs);
        requireTransparent = c.getBoolean("image.require-transparent", false);

        describeEnabled = c.getBoolean("describe.enabled", true);
        describeModel = c.getString("describe.model", "gpt-6-luna");
        describeMaxTokens = clamp(c.getInt("describe.max-output-tokens", 300), 50, 2000);

        moderationEnabled = c.getBoolean("moderation.enabled", true);
        moderationModel = c.getString("moderation.model", "omni-moderation-latest");

        workers = clamp(c.getInt("queue.workers", 2), 1, 8);
        maxWaiting = clamp(c.getInt("queue.max-waiting", 300), 1, 5000);

        autoFirstJoin = c.getBoolean("auto.first-join", true);
        autoDelaySeconds = clamp(c.getInt("auto.delay-seconds", 20), 0, 600);
        autoMaxAttempts = clamp(c.getInt("auto.max-attempts", 3), 1, 50);

        rerollItemId = c.getString("reroll.item-id", "item:reroll").trim();
        promptTimeoutSeconds = clamp(c.getInt("reroll.prompt-timeout-seconds", 180), 15, 1800);
        rerollCooldownSeconds = clamp(c.getInt("reroll.cooldown-seconds", 60), 0, 86400);
        rerollDailyLimit = Math.max(0, c.getInt("reroll.daily-limit", 0));

        budgetUsd = Math.max(0, c.getDouble("budget.total-usd", 30.0));
        Map<String, CostModel.Prices> p = new HashMap<>();
        ConfigurationSection ps = c.getConfigurationSection("prices");
        if (ps != null) {
            for (String model : ps.getKeys(true)) {
                ConfigurationSection m = ps.getConfigurationSection(model);
                if (m == null || !m.isSet("text-input")) {
                    continue;
                }
                p.put(model, new CostModel.Prices(m.getDouble("text-input", 0), m.getDouble("image-input", 0),
                        m.getDouble("image-output", 0), m.getDouble("text-output", 0)));
            }
        }
        prices = Map.copyOf(p);
        estimate = new CostModel.Estimate(
                c.getInt("estimate.output-tokens.low", 600),
                c.getInt("estimate.output-tokens.medium", 2400),
                c.getInt("estimate.output-tokens.high", 9000),
                c.getInt("estimate.input-image-tokens", 9000),
                c.getInt("estimate.input-text-tokens", 1500),
                c.getInt("estimate.describe-tokens", 3000));

        promptBase = c.getString("prompt.base", "");
        promptAppearance = c.getString("prompt.appearance", "Appearance notes from the skin: {appearance}");
        promptRequest = c.getString("prompt.request", "Optional player request: {request}");
        promptDescribe = c.getString("prompt.describe", "Describe this Minecraft skin character in at most 60 words.");

        ConfigurationSection ms = c.getConfigurationSection("messages");
        if (ms != null) {
            for (String k : ms.getKeys(false)) {
                messages.put(k, ms.getString(k, ""));
            }
        }
    }

    /** 이 모델이 지원되는 이미지 모델인지 (config 오타 방지). */
    public static boolean supportedImageModel(String model) {
        return model != null && (model.equals("gpt-image-2") || model.startsWith("gpt-image-2-")
                || model.equals("gpt-image-1.5"));
    }

    /** gpt-image-1.5 처럼 input_fidelity를 받는 모델인지. gpt-image-2 는 보내면 안 된다. */
    public static boolean acceptsInputFidelity(String model) {
        return model != null && model.startsWith("gpt-image-1");
    }

    public String message(String key, Object... kv) {
        String m = messages.getOrDefault(key, "");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m = m.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return m;
    }

    public boolean hasKey() {
        return !apiKey.isBlank();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String oneOf(String v, String def, String... allowed) {
        if (v == null) {
            return def;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        for (String a : allowed) {
            if (a.equals(s)) {
                return s;
            }
        }
        return def;
    }

    private static String safeId(String s) {
        return s != null && s.matches("[A-Za-z0-9_-]{1,32}") ? s : "server";
    }
}
