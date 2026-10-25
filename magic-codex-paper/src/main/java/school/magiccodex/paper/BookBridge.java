package school.magiccodex.paper;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.File;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.messaging.PluginMessageListener;
import school.magiccodex.protocol.BookProtocol;

/**
 * 책 읽기 (2026-10-23). A book is an id with a title, pages (left / right text of one open spread) and, optionally,
 * commands that run when the reader closes it.
 *
 * <ul>
 *   <li>books.yml — book definitions (edit, then /서버관리 새로고침 책).</li>
 *   <li>book-blocks.yml — which block opens which book on THIS server (block coordinates differ per server), set with
 *       /컨텐츠관리 책 연결 &lt;책&gt; while looking at the block. Same idea as the housing doors (블록 우클릭 → 창).</li>
 * </ul>
 *
 * Owner rules (2026-10-23): reading = opening and closing (no page or time requirement); ESC and the X button both count;
 * commands run at most once per player and book, ever, shared by school and wild ({@link BookReadStore}). Books without
 * commands only show text. Magic discovery is NOT done here: a book's command is e.g. "유저관리 {player} 마법조건 주기 …"
 * and the existing discovery flow shows the existing "새로운 마법 발견" notice.
 *
 * The client never chooses a book or a command: OPEN carries a random token for the session the server opened, CLOSE
 * returns it, and a token is consumed by its first CLOSE.
 */
final class BookBridge implements Listener, PluginMessageListener, AutoCloseable {
    /** requiredIntelligence: 펼치는 데 필요한 지능 (0 = 제한 없음). books.&lt;id&gt;.required-intelligence */
    record Book(String id, String title, List<BookProtocol.Page> pages, List<String> commands, int requiredIntelligence) {}
    private record Session(long token, String book) {}

    static final int MAX_COMMANDS = 20, MAX_COMMAND_LENGTH = 256, MAX_REQUIRED_INTELLIGENCE = 1_000_000;
    private final MagicCodexBridge plugin;
    private final File file, blockFile;
    private final BookReadStore store;
    private final SecureRandom random = new SecureRandom();
    private Map<String, Book> books = Map.of();
    /** "world,x,y,z" → book id */
    private Map<String, String> links = Map.of();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    /** "uuid|book" whose claim is in flight (a second close never starts a second claim). */
    private final Set<String> inFlight = new HashSet<>();
    /** Books whose commands are known to have run for an online player (skips the DB; cleared on quit / admin reset). */
    private final Map<UUID, Set<String>> known = new HashMap<>();

    BookBridge(MagicCodexBridge plugin) throws Exception {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "books.yml");
        blockFile = new File(plugin.getDataFolder(), "book-blocks.yml");
        if (!file.exists()) plugin.saveResource("books.yml", false);
        reload();
        store = new BookReadStore(plugin);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, BookProtocol.REQUEST, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, BookProtocol.RESPONSE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        CmdKit.register(plugin, "/컨텐츠관리 책·/유저관리 책기록", this::commands);
    }

    @Override public void close() { sessions.clear(); inFlight.clear(); known.clear(); store.close(); }

    // ------------------------------------------------------------------ config

    void reload() throws Exception {
        var y = new YamlConfiguration();
        y.load(file);
        var next = new LinkedHashMap<String, Book>();
        ConfigurationSection root = y.getConfigurationSection("books");
        if (root != null) for (String id : root.getKeys(false)) next.put(id, parse(id, root.getConfigurationSection(id)));
        var nextLinks = new LinkedHashMap<String, String>();
        if (blockFile.exists()) {
            var b = new YamlConfiguration();
            b.load(blockFile);
            for (String line : b.getStringList("links")) {
                String[] p = line.split(",");
                if (p.length != 5) throw new IllegalArgumentException("book-blocks.yml 줄 형식은 월드,x,y,z,책ID: " + line);
                String key = key(p[0], Integer.parseInt(p[1].strip()), Integer.parseInt(p[2].strip()), Integer.parseInt(p[3].strip()));
                String book = p[4].strip();
                if (!next.containsKey(book)) plugin.getLogger().warning("book-blocks.yml: books.yml 에 없는 책 " + book + " (" + key + ") — 눌러도 열리지 않아요");
                nextLinks.put(key, book);
            }
        }
        books = Collections.unmodifiableMap(next);
        links = Collections.unmodifiableMap(nextLinks);
    }

    static Book parse(String id, ConfigurationSection s) {
        if (!BookProtocol.validId(id)) throw new IllegalArgumentException("책 ID는 영문 소문자·숫자·_·- 1~48자: " + id);
        if (s == null) throw new IllegalArgumentException("books." + id + " 형식 확인");
        String title = s.getString("title", "").strip();
        if (title.isEmpty() || title.length() > BookProtocol.MAX_TITLE) throw new IllegalArgumentException("books." + id + ".title 은 1~" + BookProtocol.MAX_TITLE + "자");
        var pages = new ArrayList<BookProtocol.Page>();
        for (Object raw : s.getList("pages", List.of())) {
            String left, right;
            if (raw instanceof Map<?, ?> m) { left = text(m.get("left")); right = text(m.get("right")); }
            else if (raw instanceof String only) { left = text(only); right = ""; }
            else throw new IllegalArgumentException("books." + id + ".pages 항목은 left/right 형식");
            if (left.length() > BookProtocol.MAX_SIDE || right.length() > BookProtocol.MAX_SIDE)
                throw new IllegalArgumentException("books." + id + " " + (pages.size() + 1) + "페이지: 한쪽 본문은 " + BookProtocol.MAX_SIDE + "자까지");
            pages.add(new BookProtocol.Page(left, right));
        }
        if (pages.isEmpty() || pages.size() > BookProtocol.MAX_PAGES) throw new IllegalArgumentException("books." + id + ".pages 는 1~" + BookProtocol.MAX_PAGES + "페이지");
        var commands = new ArrayList<String>();
        for (String c : s.getStringList("commands")) {
            String line = c.strip();
            if (line.startsWith("/")) line = line.substring(1).strip();
            if (line.isEmpty()) continue;
            if (line.length() > MAX_COMMAND_LENGTH) throw new IllegalArgumentException("books." + id + ".commands 한 줄은 " + MAX_COMMAND_LENGTH + "자까지");
            commands.add(line);
        }
        if (commands.size() > MAX_COMMANDS) throw new IllegalArgumentException("books." + id + ".commands 는 " + MAX_COMMANDS + "줄까지");
        int required = s.getInt("required-intelligence", 0);
        if (required < 0 || required > MAX_REQUIRED_INTELLIGENCE) throw new IllegalArgumentException("books." + id + ".required-intelligence 는 0~" + MAX_REQUIRED_INTELLIGENCE + " 정수 (0 = 제한 없음)");
        return new Book(id, title, List.copyOf(pages), List.copyOf(commands), required);
    }
    private static String text(Object o) { return o == null ? "" : String.valueOf(o).replace("\r\n", "\n").stripTrailing(); }

    private static String key(String world, int x, int y, int z) { return world + "," + x + "," + y + "," + z; }
    private static String key(Block b) { return key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }

    private void saveLinks(Map<String, String> next) throws Exception {
        var y = new YamlConfiguration();
        var lines = new ArrayList<String>();
        next.forEach((k, book) -> lines.add(k + "," + book));
        y.set("links", lines);
        Path path = blockFile.toPath(), tmp = path.resolveSibling(blockFile.getName() + ".tmp");
        Files.createDirectories(path.getParent());
        Files.writeString(tmp, "# 책 연결 블록 (이 서버만). /컨텐츠관리 책 연결·해제 로 바꾸는 걸 권장. 형식: 월드,x,y,z,책ID\n" + y.saveToString());
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        links = Collections.unmodifiableMap(new LinkedHashMap<>(next));
    }

    // ------------------------------------------------------------------ opening

    private static boolean modded(Player p) { return p.getListeningPluginChannels().contains(BookProtocol.RESPONSE); }

    /** Opens the book for p (replacing any book p still had open). Returns false with a notice when it can't. */
    boolean open(Player p, String id) {
        Book book = books.get(id);
        if (book == null) { CmdKit.notify(p, "warning", "book-missing", "책을 펼칠 수 없어요", "관리자에게 알려 주세요", 3000); return false; }
        if (!modded(p)) { CmdKit.notify(p, "warning", "book-mod", "책을 펼칠 수 없어요", "차카데미 모드를 업데이트해 주세요", 3000); return false; }
        if (!intelligenceMet(book.requiredIntelligence(), book.requiredIntelligence() > 0 ? plugin.magicPower(p) : 0)) {
            CmdKit.notify(p, "warning", "book-intelligence", "이 책은 아직 읽을 조건이 충족되지 않았어요", "필요 지능 " + book.requiredIntelligence(), 3000);
            return false;
        }
        long token;
        do token = random.nextLong(); while (token == 0);
        byte[] bytes;
        try { bytes = BookProtocol.encode(new BookProtocol.Open(token, book.title(), book.pages())); }
        catch (IllegalArgumentException tooLarge) {
            plugin.getLogger().warning("책 " + id + " 을 보낼 수 없어요 (본문이 너무 김): " + tooLarge.getMessage());
            CmdKit.notify(p, "warning", "book-size", "책을 펼칠 수 없어요", "관리자에게 알려 주세요", 3000);
            return false;
        }
        sessions.put(p.getUniqueId(), new Session(token, id));
        p.sendPluginMessage(plugin, BookProtocol.RESPONSE, bytes);
        return true;
    }

    /** 지능 = 스탯창과 같은 값 (MagicCodexBridge.magicPower: 기본 + 장신구 + 손에 든 마법봉). 0 이하 요구치는 제한 없음. */
    static boolean intelligenceMet(int required, double intelligence) { return required <= 0 || intelligence >= required; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        String id = links.get(key(e.getClickedBlock()));
        if (id == null) return;
        e.setCancelled(true);
        var p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (now - lastClick.getOrDefault(p.getUniqueId(), 0L) < 500) return;
        lastClick.put(p.getUniqueId(), now);
        open(p, id);
    }

    @Override public void onPluginMessageReceived(String channel, Player p, byte[] data) {
        if (!BookProtocol.REQUEST.equals(channel) || data == null || data.length > BookProtocol.MAX_REQUEST_BYTES) return;
        BookProtocol.Close close;
        try { close = BookProtocol.request(data); } catch (IllegalArgumentException invalid) { return; }
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.token() != close.token()) return;   // not a book this server opened (or already closed)
        sessions.remove(p.getUniqueId());
        finish(p, s.book());
    }

    /** The reader closed the book: run its commands if it has any and they never ran for this player. */
    private void finish(Player p, String id) {
        Book book = books.get(id);
        if (book == null || book.commands().isEmpty()) return;
        UUID uuid = p.getUniqueId();
        if (known.getOrDefault(uuid, Set.of()).contains(id)) return;
        String flight = uuid + "|" + id;
        if (!inFlight.add(flight)) return;
        store.claim(uuid, id, claim -> {
            inFlight.remove(flight);
            switch (claim) {
                case ALREADY -> remember(uuid, id);
                case ERROR -> { if (p.isOnline()) CmdKit.notify(p, "warning", "book-store", "책 내용을 기록하지 못했어요", "잠시 후 다시 읽어 주세요", 3000); }
                case CLAIMED -> {
                    Book current = books.get(id);
                    if (!p.isOnline() || current == null || current.commands().isEmpty()) { store.release(uuid, id, null); return; } // left before the commands could run: may read again
                    remember(uuid, id);
                    run(p, current);
                }
            }
        });
    }
    private void remember(UUID uuid, String id) { if (Bukkit.getPlayer(uuid) != null) known.computeIfAbsent(uuid, k -> new HashSet<>()).add(id); }

    private void run(Player p, Book book) {
        var console = Bukkit.getConsoleSender();
        int failed = 0;
        for (String c : book.commands()) {
            String line = c.replace("{player}", p.getName()).replace("{uuid}", p.getUniqueId().toString()).replace("{book}", book.id());
            try { if (!Bukkit.dispatchCommand(console, line)) { failed++; plugin.getLogger().warning("책 " + book.id() + " 명령 실행 실패: " + line); } }
            catch (RuntimeException | LinkageError error) { failed++; plugin.getLogger().warning("책 " + book.id() + " 명령 오류: " + line + " → " + error); }
        }
        plugin.getLogger().info("책 " + book.id() + ": " + p.getName() + " 님 읽음 → 명령 " + book.commands().size() + "개 실행" + (failed > 0 ? " (실패 " + failed + ")" : ""));
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        sessions.remove(id); lastClick.remove(id); known.remove(id);
    }

    // ------------------------------------------------------------------ commands

    private Collection<String> ids() { return books.keySet(); }

    private void commands() {
        var root = CmdKit.literal("책")
                .then(CmdKit.literal("목록").executes(CmdKit.run(ctx -> list(CmdKit.sender(ctx)))))
                .then(CmdKit.literal("열기").then(CmdKit.word("책", this::ids)
                        .executes(CmdKit.run(ctx -> adminOpen(ctx, CmdKit.self(ctx))))
                        .then(CmdKit.player("닉네임").executes(CmdKit.run(ctx -> adminOpen(ctx, ChacaApi.resolveOnline(ctx, "닉네임")))))))
                .then(CmdKit.literal("연결").then(CmdKit.word("책", this::ids).executes(CmdKit.run(this::link))))
                .then(CmdKit.literal("해제").executes(CmdKit.run(this::unlink)));
        ChacaApi.branch(plugin, ChacaApi.컨텐츠관리, root, "책 목록·열기(미리보기)·바라보는 블록에 책 연결·해제", "책 목록");
        ChacaApi.branch(plugin, ChacaApi.유저관리, CmdKit.literal("책기록")
                .then(CmdKit.literal("보기").executes(CmdKit.run(this::history)))
                .then(CmdKit.literal("초기화").then(CmdKit.confirm(CmdKit.word("책", this::ids), this::reset))),
                "책 읽음(명령 실행) 기록 보기·초기화", "책기록 보기");
        ChacaApi.reload(plugin, "책", "books.yml·book-blocks.yml (책 내용·명령, 책 연결 블록)", this::reload);
    }

    private String pickBook(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = CmdKit.text(ctx, "책");
        if (!books.containsKey(id)) throw ChacaApi.error("books.yml 에 없는 책이에요: " + id);
        return id;
    }

    private void list(CommandSender sender) {
        var out = new ArrayList<String>();
        out.add("── 책 " + books.size() + "권 ──");
        var counts = new HashMap<String, Integer>();
        links.values().forEach(b -> counts.merge(b, 1, Integer::sum));
        for (Book b : books.values())
            out.add(" " + b.id() + " · " + b.title() + " · " + b.pages().size() + "페이지 · 명령 " + b.commands().size() + "개 · 연결 블록 " + counts.getOrDefault(b.id(), 0) + "개" + (b.requiredIntelligence() > 0 ? " · 필요 지능 " + b.requiredIntelligence() : ""));
        if (books.isEmpty()) out.add(" (books.yml 에 책이 없어요)");
        CmdKit.lines(sender, out);
    }

    private void adminOpen(CommandContext<CommandSourceStack> ctx, Player target) throws CommandSyntaxException {
        String id = pickBook(ctx);
        if (open(target, id)) CmdKit.ok(CmdKit.sender(ctx), CmdKit.name(target.getUniqueId()) + " 님에게 책 " + id + " 을 펼쳤어요");
    }

    private Block looked(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player p = CmdKit.self(ctx);
        Block block = p.getTargetBlockExact(6);
        if (block == null) throw ChacaApi.error("6칸 안의 블록을 바라보고 써 주세요");
        return block;
    }

    private void link(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = pickBook(ctx);
        Block block = looked(ctx);
        var next = new LinkedHashMap<>(links);
        String before = next.put(key(block), id);
        try { saveLinks(next); } catch (Exception e) { CmdKit.fail(CmdKit.sender(ctx), "book-blocks.yml 저장 실패", e); return; }
        CmdKit.ok(CmdKit.sender(ctx), key(block) + " 블록에 책 " + id + " 연결" + (before != null && !before.equals(id) ? " (전에는 " + before + ")" : ""));
    }

    private void unlink(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Block block = looked(ctx);
        var next = new LinkedHashMap<>(links);
        String before = next.remove(key(block));
        if (before == null) throw ChacaApi.error("이 블록에는 연결된 책이 없어요");
        try { saveLinks(next); } catch (Exception e) { CmdKit.fail(CmdKit.sender(ctx), "book-blocks.yml 저장 실패", e); return; }
        CmdKit.ok(CmdKit.sender(ctx), key(block) + " 블록의 책 " + before + " 연결을 풀었어요");
    }

    private void history(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        UUID target = ChacaApi.target(ctx);
        var sender = CmdKit.sender(ctx);
        String name = CmdKit.name(target);
        store.list(target, rows -> {
            if (rows == null) { CmdKit.userFail(sender, "책 기록을 읽지 못했어요 (콘솔 확인)"); return; }
            var out = new ArrayList<String>();
            out.add("── " + name + " 님 책 읽음 기록 " + rows.size() + "권 (명령 실행됨) ──");
            var fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault());
            rows.forEach((book, at) -> out.add(" " + book + (books.containsKey(book) ? " · " + books.get(book).title() : " · (지금 books.yml 에 없음)") + " · " + fmt.format(java.time.Instant.ofEpochMilli(at))));
            if (rows.isEmpty()) out.add(" (기록 없음)");
            CmdKit.lines(sender, out);
        });
    }

    private void reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        UUID target = ChacaApi.target(ctx);
        String id = CmdKit.text(ctx, "책");
        if (!BookProtocol.validId(id)) throw ChacaApi.error("책 ID 형식이 아니에요");
        var sender = CmdKit.sender(ctx);
        String name = CmdKit.name(target);
        store.release(target, id, deleted -> {
            var set = known.get(target); if (set != null) set.remove(id);
            if (deleted) CmdKit.ok(sender, name + " 님 책 " + id + " 기록을 지웠어요 (다시 읽으면 명령이 한 번 더 실행돼요)");
            else CmdKit.userFail(sender, name + " 님은 책 " + id + " 기록이 없어요 (또는 DB 오류, 콘솔 확인)");
        });
    }
}
