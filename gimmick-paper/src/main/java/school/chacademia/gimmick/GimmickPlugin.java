package school.chacademia.gimmick;

import java.io.File;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ChacaGimmick - dungeon gimmick models and puzzles, independent of MythicMobs.
 * Models are implemented by ModelEngine 4 (ItemsAdder only ships the merged resource pack); this plugin places,
 * saves, animates and wires them into puzzles. An ItemsAdder custom-entity renderer remains as an alternative. Spell hits arrive from ChAcademiaMagicMythic when it is installed.
 */
public final class GimmickPlugin extends JavaPlugin {
    private final GimmickProtection protection=new GimmickProtection();
    GimmickProtection protection(){return protection;}
    private final ModelCatalog catalog = new ModelCatalog();
    private boolean initialized;
    private Renderer renderer;
    private GimmickWorld world;
    private Puzzles puzzles;
    private Solids solids;
    private DebugTool tool;
    private Collision collision;
    private ManaGrid mana;
    private Devices devices;
    private Placer placer;
    private final GimmickVfx vfx = new GimmickVfx(this);
    private Object spellActions; // SpellActions when ChAcademiaMagicMythic is present (class not loaded otherwise)

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
        devices = new Devices(this);
        collision = new Collision(this);
        mana = new ManaGrid(this);
        placer = new Placer(this);
        world.load();
        solids.load(world.all().stream().map(g -> g.id).collect(java.util.stream.Collectors.toSet()));
        puzzles.load().forEach(w -> getLogger().warning("퍼즐 설정: " + w));
        world.applyOffsets();
        collision.load();

        initialized=true;
        var pm = getServer().getPluginManager();
        pm.registerEvents(world, this);
        pm.registerEvents(solids, this);
        pm.registerEvents(new Inputs(this), this);
        pm.registerEvents(tool, this);
        pm.registerEvents(collision, this);
        pm.registerEvents(placer, this);
        if (pm.isPluginEnabled("ChAcademiaMagicMythic")) {
            try { var hook = new MagicHook(this); pm.registerEvents(hook, this); spellActions = hook.actions(); getLogger().info("마법 연동 켜짐 (ChAcademiaMagicMythic, 기믹 마법 239~250)"); }
            catch (LinkageError e) { getLogger().warning("마법 연동 실패: ChAcademiaMagicMythic 버전을 확인하세요 (" + e + ")"); }
        } else getLogger().info("ChAcademiaMagicMythic 이 없어 마법 연동은 꺼져 있습니다.");

        var cmd = getCommand("기믹");
        if (cmd != null) { var c = new GimmickCommand(this); cmd.setExecutor(c); cmd.setTabCompleter(c); }

        // ModelEngine loads blueprints a moment after start; spawn a little later.
        Bukkit.getScheduler().runTaskLater(this, () -> { world.spawnLoaded(); for (Gimmick g : world.all()) collision.refresh(g); mana.dirty(); }, 40);
        getLogger().info("기믹 " + world.all().size() + "개, 퍼즐 " + puzzles.ids().size() + "개, 렌더러 " + renderer.name()
            + (renderer.available() ? "" : " (사용 불가 - 플러그인 설치 확인)"));
    }

    @Override public void onDisable() {
        if(!initialized)return;
        if (spellActions instanceof SpellActions a) a.shutdown();
        if (puzzles != null) puzzles.saveState(false);
        if (world != null) { world.despawnAll(); world.save(); }
    }

    private Renderer pickRenderer() {
        String want = getConfig().getString("renderer", "modelengine").toLowerCase(Locale.ROOT);
        if (want.equals("itemsadder")) return new ItemsAdderRenderer(getLogger());
        try { return new ModelEngineRenderer(getLogger()); }
        catch (LinkageError e) {
            getLogger().severe("ModelEngine 을 불러오지 못했습니다. ModelEngine 4 설치를 확인하세요: " + e);
            return new ItemsAdderRenderer(getLogger());
        }
    }

    /** config/models/puzzles reload; live models are rebuilt so renderer or prefix changes apply. */
    List<String> reloadAll() {
        try{for(String name:List.of("config.yml","models.yml","puzzles.yml","puzzle-state.yml","gimmicks.yml","solids.yml"))AtomicYaml.load(new File(getDataFolder(),name));
            File[] extra=new File(getDataFolder(),"puzzles").listFiles((dir,name)->name.endsWith(".yml"));if(extra!=null)for(File f:extra)AtomicYaml.load(f);
        }catch(IllegalStateException e){return List.of("Reload rejected; current state retained: "+e.getMessage());}
        reloadConfig();
        if(!puzzles.saveState())return List.of("Reload rejected: puzzle state could not be saved.");
        world.despawnAll();
        catalog.load(new File(getDataFolder(), "models.yml"));
        renderer = pickRenderer();
        var warnings = puzzles.load();
        world.applyOffsets();
        vfx.reload();
        world.spawnLoaded();
        for (Gimmick g : world.all()) collision.refresh(g);
        mana.dirty();
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
    Collision collision() { return collision; }
    ManaGrid mana() { return mana; }
    Devices devices() { return devices; }
    Placer placer() { return placer; }
    GimmickVfx vfx() { return vfx; }
}
