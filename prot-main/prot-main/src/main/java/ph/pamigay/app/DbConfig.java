package ph.pamigay.app;

import java.util.prefs.Preferences;

/** Connection profile. The password is deliberately kept in memory only. */
public record DbConfig(String host, int port, String database, String username, String password) {
    private static final Preferences PREFS = Preferences.userNodeForPackage(DbConfig.class);

    public static DbConfig load() {
        return new DbConfig(PREFS.get("host", "localhost"), PREFS.getInt("port", 3306),
                PREFS.get("database", "pamigay_db"), PREFS.get("username", "root"), "");
    }

    public void save() {
        PREFS.put("host", host.trim());
        PREFS.putInt("port", port);
        PREFS.put("database", database.trim());
        PREFS.put("username", username.trim());
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host.trim() + ":" + port + "/?connectTimeout=5000&socketTimeout=15000"
                + "&useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=LOCAL";
    }
}
