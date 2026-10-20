package school.chacademia.housing;

import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 차카데미 하우징 (기숙사 내 방 꾸미기). 인계 문서: docs/handoff/CLAUDE_HOUSING_20261017.md */
public final class HousingPlugin extends JavaPlugin implements Listener {
    private Settings settings;
    private Catalog catalog = Catalog.EMPTY;
    private HousingStore store;
    private Rooms rooms;
    private Net net;
    private Doors doors;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "furniture.yml").exists()) saveResource("furniture.yml", false);
        settings = Settings.from(getConfig(), getLogger());
        catalog = loadCatalog();
        store = new HousingStore(this);
        try { store.open(getDataFolder().toPath()); }
        catch (Exception e) { getLogger().severe("하우징 DB를 열 수 없어 플러그인을 끕니다: " + e); Bukkit.getPluginManager().disablePlugin(this); return; }
        rooms = new Rooms(this);
        net = new Net(this); net.register();
        doors = new Doors(this); doors.load();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(doors, this);
        new HousingCommands(this).register();
        for (var p : Bukkit.getOnlinePlayers()) rooms.join(p);
        Bukkit.getScheduler().runTaskTimer(this, () -> rooms.sync(), settings.presenceTicks(), settings.presenceTicks());
        Bukkit.getScheduler().runTaskTimer(this, () -> rooms.heartbeat(), 20 * 30, 20 * 30);
        if (!Economy.available()) getLogger().warning("Vault 경제를 찾지 못했어요. 방 확장은 경제 플러그인이 켜진 뒤에 됩니다.");
        getLogger().info("하우징 준비 완료 · 가구 " + catalog.all().size() + "개 · 문 " + doors.all().size() + "개 · 서버 이름 " + settings.server());
    }

    @Override public void onDisable() {
        if (rooms != null) rooms.shutdown();
        if (net != null) net.unregister();
        if (store != null) store.close();
    }

    void reload() {
        reloadConfig();
        settings = Settings.from(getConfig(), getLogger());
        catalog = loadCatalog();
        rooms.catalogChanged();
        doors.load();
    }

    private Catalog loadCatalog() {
        var yaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "furniture.yml"));
        return Catalog.parse(yaml, getLogger());
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { rooms.join(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { rooms.quit(e.getPlayer()); doors.forget(e.getPlayer().getUniqueId()); }

    Settings settings() { return settings; }
    Catalog catalog() { return catalog; }
    HousingStore store() { return store; }
    Rooms rooms() { return rooms; }
    Net net() { return net; }
    Doors doors() { return doors; }
}
