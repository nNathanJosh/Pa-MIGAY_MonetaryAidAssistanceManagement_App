package ph.pamigay.app;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Small JDBC repository. Every operation owns its connection; writes use prepared statements. */
public final class Database {
    public record TableData(List<String> columns, List<Object[]> rows) {}
    public record Option(Object value, String label) {
        @Override public String toString() { return label; }
    }
    public record Dashboard(long beneficiaries, long payouts, long claimed, long receipts,
                            long upcoming, BigDecimal totalAmount, List<Object[]> recentPayouts) {}
    public enum ReportType {
        BENEFICIARIES("Beneficiaries by barangay"),
        PAYOUTS("Payout totals by district"),
        CLAIMS("Claims by payout"),
        ANNOUNCEMENTS("Announcements by date");
        private final String label;
        ReportType(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    private final DbConfig config;

    public Database(DbConfig config) { this.config = config; }

    public void validateSchema() throws SQLException {
        try (Connection c = open()) {
            Map<String, Set<String>> required = requiredSchema();
            List<String> problems = new ArrayList<>();
            DatabaseMetaData meta = c.getMetaData();
            for (var entry : required.entrySet()) {
                Set<String> actual = new HashSet<>();
                try (ResultSet rs = meta.getColumns(c.getCatalog(), null, entry.getKey(), null)) {
                    while (rs.next()) actual.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
                if (actual.isEmpty()) {
                    try (ResultSet rs = meta.getColumns(c.getCatalog(), null,
                            entry.getKey().toUpperCase(Locale.ROOT), null)) {
                        while (rs.next()) actual.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                if (actual.isEmpty()) problems.add("Missing table: " + entry.getKey());
                else {
                    Set<String> missing = new TreeSet<>(entry.getValue());
                    missing.removeAll(actual);
                    if (!missing.isEmpty()) problems.add(entry.getKey() + " is missing columns: " + String.join(", ", missing));
                }
            }
            if (!problems.isEmpty()) {
                throw new SQLException("The connected database does not match the Pa-MIGAY schema.\n\n"
                        + String.join("\n", problems) + "\n\nSelect the database that contains the tables shown in the project screenshots.");
            }
        }
    }

    private Map<String, Set<String>> requiredSchema() {
        Map<String, Set<String>> r = new LinkedHashMap<>();
        r.put("district", Set.of("district_id", "district_name"));
        r.put("barangay", Set.of("barangay_id", "barangay_name", "district_id"));
        r.put("crisis", Set.of("crisis_id", "crisis_type", "date_occurred"));
        r.put("staff", Set.of("staff_id", "first_name", "last_name", "role", "contact_info"));
        r.put("beneficiary", Set.of("beneficiary_id", "first_name", "last_name", "contact_number", "category", "barangay_id"));
        r.put("payout", Set.of("payout_id", "payout_date", "amount", "distribution_location", "target_group", "district_id", "crisis_id", "staff_id"));
        r.put("receipt", Set.of("receipt_id", "date_issued", "amount_received", "status", "payout_id", "beneficiary_id"));
        r.put("announcement", Set.of("announcement_id", "content", "date_posted", "platform", "payout_id"));
        return r;
    }

    private Connection open() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", config.username());
        properties.setProperty("password", config.password() == null ? "" : config.password());
        Connection c = DriverManager.getConnection(config.jdbcUrl(), properties);
        try {
            c.setCatalog(config.database());
            return c;
        } catch (SQLException e) {
            try { c.close(); } catch (SQLException ignored) { }
            throw e;
        }
    }

    public String serverDescription() throws SQLException {
        try (Connection c = open()) {
            return c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion();
        }
    }

    public Dashboard dashboard() throws SQLException {
        try (Connection c = open()) {
            long beneficiaries = scalarLong(c, "SELECT COUNT(*) FROM beneficiary");
            long payouts = scalarLong(c, "SELECT COUNT(*) FROM payout");
            long claimed = scalarLong(c, "SELECT COUNT(*) FROM receipt WHERE LOWER(status) = 'claimed'");
            long receipts = scalarLong(c, "SELECT COUNT(*) FROM receipt");
            long upcoming = scalarLong(c, "SELECT COUNT(*) FROM payout WHERE payout_date >= CURRENT_DATE()");
            BigDecimal total = scalarDecimal(c, "SELECT COALESCE(SUM(amount), 0) FROM payout");
            TableData recent = query(c, "SELECT p.payout_id AS ID, p.payout_date AS `Date`, p.distribution_location AS Location, "
                    + "d.district_name AS District, p.amount AS Amount "
                    + "FROM payout p LEFT JOIN district d ON d.district_id=p.district_id "
                    + "ORDER BY p.payout_date DESC, p.payout_id DESC LIMIT 8");
            return new Dashboard(beneficiaries, payouts, claimed, receipts, upcoming, total, recent.rows());
        }
    }

    public TableData rows(EntityType type) throws SQLException {
        String sql = switch (type) {
            case BENEFICIARY -> "SELECT b.beneficiary_id AS ID, CONCAT_WS(' ', b.first_name, b.last_name) AS Beneficiary, "
                    + "b.category AS Category, b.contact_number AS Contact, g.barangay_name AS Barangay, d.district_name AS District "
                    + "FROM beneficiary b LEFT JOIN barangay g ON g.barangay_id=b.barangay_id "
                    + "LEFT JOIN district d ON d.district_id=g.district_id ORDER BY b.last_name, b.first_name";
            case DISTRICT -> "SELECT district_id AS ID, district_name AS District FROM district ORDER BY district_name";
            case BARANGAY -> "SELECT g.barangay_id AS ID, g.barangay_name AS Barangay, d.district_name AS District "
                    + "FROM barangay g LEFT JOIN district d ON d.district_id=g.district_id ORDER BY d.district_name, g.barangay_name";
            case CRISIS -> "SELECT crisis_id AS ID, crisis_type AS Crisis, date_occurred AS `Date` FROM crisis ORDER BY date_occurred DESC";
            case STAFF -> "SELECT staff_id AS ID, CONCAT_WS(' ', first_name, last_name) AS Staff, `role` AS Role, contact_info AS Contact "
                    + "FROM staff ORDER BY last_name, first_name";
            case PAYOUT -> "SELECT p.payout_id AS ID, p.payout_date AS `Date`, p.amount AS Amount, p.distribution_location AS Location, p.target_group AS `Target group`, "
                    + "d.district_name AS District, c.crisis_type AS Crisis, CONCAT_WS(' ', s.first_name, s.last_name) AS Staff "
                    + "FROM payout p LEFT JOIN district d ON d.district_id=p.district_id "
                    + "LEFT JOIN crisis c ON c.crisis_id=p.crisis_id LEFT JOIN staff s ON s.staff_id=p.staff_id "
                    + "ORDER BY p.payout_date DESC, p.payout_id DESC";
            case RECEIPT -> "SELECT r.receipt_id AS ID, r.date_issued AS Issued, CONCAT_WS(' ', b.first_name, b.last_name) AS Beneficiary, "
                    + "r.payout_id AS Payout, r.amount_received AS Amount, r.status AS Status "
                    + "FROM receipt r LEFT JOIN beneficiary b ON b.beneficiary_id=r.beneficiary_id "
                    + "ORDER BY r.date_issued DESC, r.receipt_id DESC";
            case ANNOUNCEMENT -> "SELECT a.announcement_id AS ID, a.date_posted AS Posted, a.platform AS Platform, "
                    + "LEFT(a.content, 120) AS Announcement, a.payout_id AS Payout "
                    + "FROM announcement a ORDER BY a.date_posted DESC, a.announcement_id DESC";
        };
        try (Connection c = open()) { return query(c, sql); }
    }

    public Map<String, Object> find(EntityType type, Object id) throws SQLException {
        String table = table(type), key = key(type);
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT * FROM " + table + " WHERE " + key + "=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("That record no longer exists. Refresh the list and try again.");
                ResultSetMetaData md = rs.getMetaData();
                Map<String, Object> values = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    Object value = rs.getObject(i);
                    if (value instanceof java.sql.Date d) value = d.toLocalDate().toString();
                    else if (value instanceof Timestamp t) value = t.toLocalDateTime().toString().replace('T', ' ');
                    values.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), value);
                }
                return values;
            }
        }
    }

    public Map<String, List<Option>> formOptions(EntityType type) throws SQLException {
        Map<String, List<Option>> result = new HashMap<>();
        try (Connection c = open()) {
            switch (type) {
                case BENEFICIARY -> {
                    result.put("barangay_id", options(c, "SELECT g.barangay_id, CONCAT(g.barangay_name, ' — ', d.district_name) "
                            + "FROM barangay g LEFT JOIN district d ON d.district_id=g.district_id ORDER BY d.district_name, g.barangay_name"));
                    result.put("category", distinct(c, "SELECT DISTINCT category FROM beneficiary WHERE category IS NOT NULL AND category<>'' ORDER BY category"));
                }
                case BARANGAY -> result.put("district_id", options(c,
                        "SELECT district_id, district_name FROM district ORDER BY district_name"));
                case STAFF -> result.put("role", distinct(c, "SELECT DISTINCT `role` FROM staff WHERE `role` IS NOT NULL AND `role`<>'' ORDER BY `role`"));
                case PAYOUT -> {
                    result.put("district_id", options(c, "SELECT district_id, district_name FROM district ORDER BY district_name"));
                    result.put("crisis_id", options(c, "SELECT crisis_id, CONCAT(crisis_type, ' — ', date_occurred) FROM crisis ORDER BY date_occurred DESC"));
                    result.put("staff_id", options(c, "SELECT staff_id, CONCAT_WS(' ', first_name, last_name, CONCAT(' · ', `role`)) FROM staff ORDER BY last_name, first_name"));
                }
                case RECEIPT -> {
                    result.put("payout_id", options(c, "SELECT payout_id, CONCAT(payout_date, ' — ', distribution_location) FROM payout ORDER BY payout_date DESC"));
                    result.put("beneficiary_id", options(c, "SELECT beneficiary_id, CONCAT_WS(' ', first_name, last_name, CONCAT(' · ', category)) FROM beneficiary ORDER BY last_name, first_name"));
                    result.put("status", distinct(c, "SELECT DISTINCT status FROM receipt WHERE status IS NOT NULL AND status<>'' ORDER BY status"));
                }
                case ANNOUNCEMENT -> {
                    result.put("payout_id", options(c, "SELECT payout_id, CONCAT(payout_date, ' — ', distribution_location) FROM payout ORDER BY payout_date DESC"));
                    result.put("platform", distinct(c, "SELECT DISTINCT platform FROM announcement WHERE platform IS NOT NULL AND platform<>'' ORDER BY platform"));
                }
                default -> { }
            }
        }
        return result;
    }

    public List<String> duplicateWarnings(EntityType type, Map<String, Object> values, Object id) throws SQLException {
        List<String> warnings = new ArrayList<>();
        try (Connection c = open()) {
            if (type == EntityType.BENEFICIARY) {
                String contact = text(values, "contact_number").trim();
                if (!contact.isEmpty()) {
                    String sql = "SELECT COUNT(*) FROM beneficiary WHERE LOWER(TRIM(first_name))=LOWER(TRIM(?)) "
                            + "AND LOWER(TRIM(last_name))=LOWER(TRIM(?)) AND contact_number=? AND beneficiary_id<>COALESCE(?, -1)";
                    if (count(c, sql, text(values, "first_name"), text(values, "last_name"), contact, id) > 0)
                        warnings.add("A beneficiary already has this name and contact number.");
                }
            } else if (type == EntityType.RECEIPT) {
                String sql = "SELECT COUNT(*) FROM receipt WHERE payout_id=? AND beneficiary_id=? AND receipt_id<>COALESCE(?, -1)";
                if (count(c, sql, raw(values.get("payout_id")), raw(values.get("beneficiary_id")), id) > 0)
                    warnings.add("This beneficiary already has a claim recorded for the selected payout.");
            }
        }
        return warnings;
    }

    /** Enforces alternate-key uniqueness before a form reaches the database insert/update. */
    public void validateUniqueKeys(EntityType type, Map<String, Object> values, Object id) throws SQLException {
        try (Connection c = open()) {
            if (type == EntityType.DISTRICT
                    && count(c, "SELECT COUNT(*) FROM district WHERE LOWER(TRIM(district_name))=LOWER(TRIM(?)) AND district_id<>COALESCE(?, -1)",
                    text(values, "district_name"), id) > 0) {
                throw new SQLException("That district name is already in use. District names must be unique.");
            }
            if (type == EntityType.BARANGAY
                    && count(c, "SELECT COUNT(*) FROM barangay WHERE LOWER(TRIM(barangay_name))=LOWER(TRIM(?)) AND barangay_id<>COALESCE(?, -1)",
                    text(values, "barangay_name"), id) > 0) {
                throw new SQLException("That barangay name is already in use. Choose a unique name.");
            }
            String contact = text(values, "contact_info").trim();
            if (type == EntityType.STAFF && !contact.isEmpty()
                    && count(c, "SELECT COUNT(*) FROM staff WHERE LOWER(TRIM(contact_info))=LOWER(TRIM(?)) AND staff_id<>COALESCE(?, -1)",
                    contact, id) > 0) {
                throw new SQLException("That staff contact information is already in use.");
            }
        }
    }

    public void save(EntityType type, Map<String, Object> values, Object id) throws SQLException {
        String columns;
        String table = table(type);
        String update;
        List<Object> params = new ArrayList<>();
        switch (type) {
            case BENEFICIARY -> { columns = "first_name,last_name,contact_number,category,barangay_id";
                add(params, values, "first_name", "last_name", "contact_number", "category", "barangay_id"); }
            case DISTRICT -> { columns = "district_name"; add(params, values, "district_name"); }
            case BARANGAY -> { columns = "barangay_name,district_id"; add(params, values, "barangay_name", "district_id"); }
            case CRISIS -> { columns = "crisis_type,date_occurred"; add(params, values, "crisis_type", "date_occurred"); }
            case STAFF -> { columns = "first_name,last_name,`role`,contact_info"; add(params, values, "first_name", "last_name", "role", "contact_info"); }
            case PAYOUT -> { columns = "payout_date,amount,distribution_location,target_group,district_id,crisis_id,staff_id";
                add(params, values, "payout_date", "amount", "distribution_location", "target_group", "district_id", "crisis_id", "staff_id"); }
            case RECEIPT -> { columns = "date_issued,amount_received,status,payout_id,beneficiary_id";
                add(params, values, "date_issued", "amount_received", "status", "payout_id", "beneficiary_id"); }
            case ANNOUNCEMENT -> { columns = "content,date_posted,platform,payout_id"; add(params, values, "content", "date_posted", "platform", "payout_id"); }
            default -> throw new SQLException("Unsupported record type.");
        }
        params = normalize(params, type);
        String[] names = columns.split(",");
        if (id == null) {
            String placeholders = String.join(",", Collections.nCopies(names.length, "?"));
            update = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
        } else {
            StringJoiner setters = new StringJoiner(",");
            for (String name : names) setters.add(name + "=?");
            update = "UPDATE " + table + " SET " + setters + " WHERE " + key(type) + "=?";
            params.add(id);
        }
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(update)) {
            validateRelations(c, type, params);
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ps.executeUpdate();
        }
    }

    private static void validateRelations(Connection c, EntityType type, List<Object> params) throws SQLException {
        switch (type) {
            case BARANGAY -> ensureExists(c, "district", "district_id", params.get(1), "District");
            case BENEFICIARY -> ensureExists(c, "barangay", "barangay_id", params.get(4), "Barangay");
            case PAYOUT -> {
                ensureExists(c, "district", "district_id", params.get(4), "District");
                ensureExists(c, "crisis", "crisis_id", params.get(5), "Crisis");
                ensureExists(c, "staff", "staff_id", params.get(6), "Staff member");
            }
            case RECEIPT -> {
                ensureExists(c, "payout", "payout_id", params.get(3), "Payout");
                ensureExists(c, "beneficiary", "beneficiary_id", params.get(4), "Beneficiary");
            }
            case ANNOUNCEMENT -> ensureExists(c, "payout", "payout_id", params.get(3), "Payout");
            default -> { }
        }
    }

    private static void ensureExists(Connection c, String table, String key, Object id, String label) throws SQLException {
        if (scalarForId(c, "SELECT COUNT(*) FROM " + table + " WHERE " + key + "=?", id) == 0)
            throw new SQLException(label + " no longer exists. Refresh the form and choose an available record.");
    }

    public void delete(EntityType type, Object id) throws SQLException {
        String[] checks = switch (type) {
            case DISTRICT -> new String[]{"SELECT COUNT(*) FROM barangay WHERE district_id=?", "barangays"};
            case BARANGAY -> new String[]{"SELECT COUNT(*) FROM beneficiary WHERE barangay_id=?", "beneficiaries"};
            case CRISIS -> new String[]{"SELECT COUNT(*) FROM payout WHERE crisis_id=?", "payouts"};
            case STAFF -> new String[]{"SELECT COUNT(*) FROM payout WHERE staff_id=?", "payouts"};
            case BENEFICIARY -> new String[]{"SELECT COUNT(*) FROM receipt WHERE beneficiary_id=?", "claims"};
            case PAYOUT -> new String[]{"SELECT COUNT(*) FROM receipt WHERE payout_id=?", "claims"};
            case RECEIPT -> new String[]{null, ""};
            case ANNOUNCEMENT -> new String[]{null, ""};
        };
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                List<String> linked = new ArrayList<>();
                if (checks[0] != null && scalarForId(c, checks[0], id) > 0) linked.add(checks[1]);
                if (type == EntityType.DISTRICT && scalarForId(c, "SELECT COUNT(*) FROM payout WHERE district_id=?", id) > 0)
                    linked.add("payouts");
                if (type == EntityType.PAYOUT && scalarForId(c, "SELECT COUNT(*) FROM announcement WHERE payout_id=?", id) > 0)
                    linked.add("announcements");
                if (!linked.isEmpty()) throw new SQLException("This record is still used by " + String.join(" and ", linked)
                        + ". Reassign or resolve those records before deleting it.");
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table(type) + " WHERE " + key(type) + "=?")) {
                    ps.setObject(1, id);
                    if (ps.executeUpdate() == 0) throw new SQLException("That record no longer exists. Refresh the list and try again.");
                }
                c.commit();
            } catch (SQLException e) {
                try { c.rollback(); } catch (SQLException ignored) { }
                throw e;
            } finally {
                try { c.setAutoCommit(true); } catch (SQLException ignored) { }
            }
        }
    }

    public TableData report(ReportType type, LocalDate from, LocalDate to) throws SQLException {
        String sql;
        String dateColumn;
        switch (type) {
            case BENEFICIARIES -> {
                sql = "SELECT d.district_name AS District, g.barangay_name AS Barangay, COUNT(b.beneficiary_id) AS Beneficiaries "
                        + "FROM barangay g LEFT JOIN district d ON d.district_id=g.district_id "
                        + "LEFT JOIN beneficiary b ON b.barangay_id=g.barangay_id GROUP BY d.district_name,g.barangay_name "
                        + "ORDER BY d.district_name,g.barangay_name";
                dateColumn = null;
            }
            case PAYOUTS -> {
                sql = "SELECT d.district_name AS District, COUNT(p.payout_id) AS Payouts, COALESCE(SUM(p.amount),0) AS Total "
                        + "FROM payout p LEFT JOIN district d ON d.district_id=p.district_id WHERE 1=1";
                dateColumn = "p.payout_date";
                sql += " GROUP BY d.district_name ORDER BY d.district_name";
            }
            case CLAIMS -> {
                sql = "SELECT p.payout_id AS Payout, p.payout_date AS `Date`, p.distribution_location AS Location, "
                        + "COUNT(r.receipt_id) AS Claims, COALESCE(SUM(r.amount_received),0) AS Amount, "
                        + "SUM(CASE WHEN LOWER(r.status)='claimed' THEN 1 ELSE 0 END) AS Claimed "
                        + "FROM payout p LEFT JOIN receipt r ON r.payout_id=p.payout_id WHERE 1=1";
                dateColumn = "p.payout_date";
                sql += " GROUP BY p.payout_id,p.payout_date,p.distribution_location ORDER BY p.payout_date DESC";
            }
            case ANNOUNCEMENTS -> {
                sql = "SELECT announcement_id AS ID,date_posted AS Posted,platform AS Platform,content AS Content,payout_id AS Payout "
                        + "FROM announcement WHERE 1=1";
                dateColumn = "date_posted";
                sql += " ORDER BY date_posted DESC";
            }
            default -> throw new SQLException("Unknown report.");
        }
        List<LocalDate> params = new ArrayList<>();
        if (dateColumn != null) {
            String predicate = " WHERE 1=1";
            int insertAt = sql.indexOf(predicate) + predicate.length();
            if (from != null) { sql = sql.substring(0, insertAt) + " AND " + dateColumn + ">=?" + sql.substring(insertAt); params.add(from); insertAt += (" AND " + dateColumn + ">=?").length(); }
            if (to != null) { sql = sql.substring(0, insertAt) + " AND " + dateColumn + "<?" + sql.substring(insertAt); params.add(to.plusDays(1)); }
        }
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setDate(i + 1, java.sql.Date.valueOf(params.get(i)));
            try (ResultSet rs = ps.executeQuery()) { return collect(rs); }
        }
    }

    private static String table(EntityType type) {
        return switch (type) {
            case BENEFICIARY -> "beneficiary"; case DISTRICT -> "district"; case BARANGAY -> "barangay";
            case CRISIS -> "crisis"; case STAFF -> "staff"; case PAYOUT -> "payout";
            case RECEIPT -> "receipt"; case ANNOUNCEMENT -> "announcement";
        };
    }
    private static String key(EntityType type) {
        return switch (type) {
            case BENEFICIARY -> "beneficiary_id"; case DISTRICT -> "district_id"; case BARANGAY -> "barangay_id";
            case CRISIS -> "crisis_id"; case STAFF -> "staff_id"; case PAYOUT -> "payout_id";
            case RECEIPT -> "receipt_id"; case ANNOUNCEMENT -> "announcement_id";
        };
    }
    private static void add(List<Object> values, Map<String, Object> source, String... keys) {
        for (String key : keys) values.add(source.get(key));
    }
    private static String text(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? "" : value.toString();
    }
    private static Object raw(Object value) { return value instanceof Option option ? option.value() : value; }
    private static List<Object> normalize(List<Object> input, EntityType type) throws SQLException {
        List<Object> out = new ArrayList<>(input);
        try {
            switch (type) {
                case CRISIS -> out.set(1, java.sql.Date.valueOf(LocalDate.parse(out.get(1).toString().trim())));
                case PAYOUT -> { out.set(0, java.sql.Date.valueOf(LocalDate.parse(out.get(0).toString().trim()))); out.set(1, positiveAmount(out.get(1), "Payout amount")); }
                case RECEIPT -> { out.set(0, Timestamp.valueOf(LocalDateTime.parse(out.get(0).toString().trim().replace('T', ' ')))); out.set(1, positiveAmount(out.get(1), "Claim amount")); }
                case ANNOUNCEMENT -> out.set(1, Timestamp.valueOf(LocalDateTime.parse(out.get(1).toString().trim().replace('T', ' '))));
                default -> { }
            }
            for (int i = 0; i < out.size(); i++) {
                Object value = out.get(i);
                if (value instanceof Option option) out.set(i, option.value());
                if (value == null || value.toString().isBlank()) throw new SQLException("Please complete every required field.");
            }
        } catch (java.time.DateTimeException | NumberFormatException e) {
            throw new SQLException("Check the date and amount formats. Use yyyy-MM-dd for dates and yyyy-MM-dd HH:mm for date and time.", e);
        }
        return out;
    }
    private static BigDecimal positiveAmount(Object value, String label) throws SQLException {
        BigDecimal amount = new BigDecimal(value.toString().trim());
        if (amount.signum() <= 0) throw new SQLException(label + " must be greater than zero.");
        return amount;
    }
    private static long scalarLong(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }
    private static BigDecimal scalarDecimal(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { rs.next(); return rs.getBigDecimal(1); }
    }
    private static long scalarForId(Connection c, String sql, Object id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) { ps.setObject(1, id); try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); } }
    }
    private static long count(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }
    private static List<Option> options(Connection c, String sql) throws SQLException {
        List<Option> items = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) items.add(new Option(rs.getObject(1), Objects.toString(rs.getObject(2), "")));
        }
        return items;
    }
    private static List<Option> distinct(Connection c, String sql) throws SQLException {
        List<Option> items = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) { Object value = rs.getObject(1); items.add(new Option(value, Objects.toString(value, ""))); }
        }
        return items;
    }
    private static TableData query(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { return collect(rs); }
    }
    private static TableData collect(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= md.getColumnCount(); i++) columns.add(md.getColumnLabel(i));
        List<Object[]> rows = new ArrayList<>();
        while (rs.next()) {
            Object[] row = new Object[columns.size()];
            for (int i = 0; i < row.length; i++) row[i] = rs.getObject(i + 1);
            rows.add(row);
        }
        return new TableData(List.copyOf(columns), List.copyOf(rows));
    }
}
