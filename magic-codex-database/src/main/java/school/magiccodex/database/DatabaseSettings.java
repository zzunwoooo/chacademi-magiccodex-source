package school.magiccodex.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Storage choice shared by our Paper plugins. No database traffic occurs on the game thread. */
public record DatabaseSettings(boolean mariaDb, String url, String user, String password) {
    public static DatabaseSettings load(Path file) throws IOException {
        if (!Files.exists(file)) return new DatabaseSettings(false, "", "", "");
        var p = new Properties();
        try (var input = Files.newInputStream(file)) { p.load(input); }
        String mode = p.getProperty("mode", "sqlite").trim();
        if (mode.equalsIgnoreCase("sqlite")) return new DatabaseSettings(false, "", "", "");
        if (!mode.equalsIgnoreCase("mariadb")) throw new IOException("Unknown database mode: " + mode);
        String host = p.getProperty("host", "127.0.0.1").trim();
        String database = p.getProperty("database", "chacademia").trim();
        String port = p.getProperty("port", "3306").trim();
        String user = p.getProperty("user", "").trim();
        String password = p.getProperty("password", "");
        // Optional TLS for hosted MariaDB: disable | trust | verify-ca | verify-full (MariaDB Connector/J sslMode).
        String sslMode = p.getProperty("ssl-mode", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (!host.matches("[A-Za-z0-9.:-]{1,253}") || !port.matches("[0-9]{1,5}")
                || !database.matches("[A-Za-z0-9_]{1,64}") || user.isBlank() || password.isBlank())
            throw new IOException("Invalid MariaDB configuration");
        if (!sslMode.isEmpty() && !sslMode.matches("disable|trust|verify-ca|verify-full"))
            throw new IOException("Invalid ssl-mode: " + sslMode);
        return new DatabaseSettings(true,
                "jdbc:mariadb://" + host + ":" + port + "/" + database
                        + "?connectTimeout=5000&socketTimeout=10000"
                        + (sslMode.isEmpty() ? "" : "&sslMode=" + sslMode), user, password);
    }

    public Connection connect(Path sqliteFile) throws Exception {
        if (mariaDb) {
            Class.forName("org.mariadb.jdbc.Driver");
            return DriverManager.getConnection(url, user, password);
        }
        Files.createDirectories(sqliteFile.toAbsolutePath().getParent());
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toAbsolutePath());
    }

    /** Connection with reconnect-on-demand; {@link #configure} is re-applied to every reopened connection. */
    public ConnectionHolder holder(Path sqliteFile) throws SQLException {
        return new ConnectionHolder(() -> connect(sqliteFile), this::configure, mariaDb);
    }

    public String table(String suffix, String sqliteName) {
        return mariaDb ? "codex_" + suffix : sqliteName;
    }

    public void configure(Connection db) throws SQLException {
        if (mariaDb) return;
        try (var s = db.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA busy_timeout=5000");
        }
    }
}
