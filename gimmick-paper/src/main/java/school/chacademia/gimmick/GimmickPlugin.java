package school.chacademia.gimmick;

import java.io.File;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ChacaGimmick - dungeon gimmick models and puzzles, independent of MythicMobs.
 * Models are drawn by ItemsAdder custom entities (default) or ModelEngine; this plugin only places, saves,
 * animates and wires them into puzzles. Spell hits arrive from ChAcademiaMagicMythic when it is installed.
 */
public final class GimmickPlugin extends JavaPlugin {
    private final ModelCatalog catalog = new ModelCatalog();
    private Renderer renderer;
    private GimmickWorld world;
    private Puzzles puzzles;
    private Solids solids;
    private DebugTool tool;

    @Override public void onEnable() {
        saveDefaultConfig();
        for (String res : List.of("models.yml", "puzzles.yml"))
            if (!new File(getDataFolder(), res).exists()) saveResource(res, false);
        catalog.load(new File(getDataFolder(), "models.yml"));
        renderer = pickRenderer();
        world = new GimmickWorld(this);
        puzzles = new Puzzles(this);
        solids = new Solids(this);
        tool = new DebugTool(this);
        world.load();
        solids.load(world.all().stream().map(g -> g.id).collect(java.util.stream.Collectors.toSet()));
        puzzles.load().forEach(w -> getLogger().warning("퍼즐 설정: " + w));

        var pm = getServer().getPluginManager();
        pm.registerEvents(world, this);
        pm.registerEvents(new Inputs(this), this);
        pm.registerEvents(tool, this);
        if (pm.isPluginEnabled("ChAcademiaMagicMythic")) {
            try { pm.registerEvents(new MagicHook(this), this); getLogger().info("마법 연동 켜짐 (ChAcademiaMagicMythic)"); }
            catch (LinkageError e) { getLogger().warning("마법 연동 실패: ChAcademiaMagicMythic 버전을 확인하세요 (" + e + ")"); }
        } else getLogger().info("ChAcademiaMagicMythic 이 없어 마법 연동은 꺼져 있습니다.");

        var cmd = getCommand("기믹");
        if (cmd != null) { var c = new GimmickCommand(this); cmd.setExecutor(c); cmd.setTabCompleter(c); }

        // Renderers register their models a moment after start; spawn on the next tick.
        Bukkit.getScheduler().runTaskLater(this, () -> world.spawnLoaded(), 40);
        getLogger().info("기믹 " + world.all().size() + "개, 퍼즐 " + puzzles.ids().size() + "개, 렌더러 " + renderer.name()
            + (renderer.available() ? "" : " (사용 불가 - 플러그인 설치 확인)"));
    }

    @Override public void onDisable() {
        if (puzzles != null) puzzles.saveState();
        if (world != null) { world.despawnAll(); world.save(); }
    }

    private Renderer pickRenderer() {
        String want = getConfig().getString("renderer", "itemsadder").toLowerCase(Locale.ROOT);
        if (want.equals("modelengine")) {
            try { return new ModelEngineRenderer(getLogger()); }
            catch (LinkageError e) { getLogger().warning("ModelEngine 을 불러오지 못해 ItemsAdder 로 대신합니다: " + e); }
        }
        return new ItemsAdderRenderer(getLogger());
    }

    /** config/models/puzzles reload; live models are rebuilt so renderer or prefix changes apply. */
    List<String> reloadAll() {
        reloadConfig();
        puzzles.saveState();
        world.despawnAll();
        catalog.load(new File(getDataFolder(), "models.yml"));
        renderer = pickRenderer();
        var warnings = puzzles.load();
        world.spawnLoaded();
        return warnings;
    }

    String fullId(String model) { return getConfig().getString("model-prefix", "") + model; }
    boolean debug() { return getConfig().getBoolean("debug", false); }
    ModelCatalog catalog() { return catalog; }
    Renderer renderer() { return renderer; }
    GimmickWorld world() { return world; }
    Puzzles puzzles() { return puzzles; }
    Solids solids() { return solids; }
    DebugTool tool() { return tool; }
}
