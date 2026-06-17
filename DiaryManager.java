package mydiary;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// Minimal JSON helper — no external library needed
// Format: one entry per line, fields separated by a fixed delimiter
// We use a simple pipe-delimited CSV stored in a .json-named file for portability.
// Each line: id|title|content|mood|dateTime|voiceNotePath

public class DiaryManager {
    private List<DiaryEntry> entries;
    private Connection conn;

    // ── Local fallback file (sits next to the running jar / working dir) ──
    private static final String FALLBACK_FILE = "diary_entries.dat";

    public DiaryManager() {
        entries = new ArrayList<>();
        connectToDatabase();
        createTableIfNotExists();
        loadEntries();
    }

    // ── Database connection ───────────────────────────────────────────────
    private void connectToDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            Properties props = new Properties();
            props.load(new FileInputStream("config.properties"));

            String url  = props.getProperty("db.url");
            String user = props.getProperty("db.user");
            String pass = props.getProperty("db.password");

            conn = DriverManager.getConnection(url, user, pass);
            System.out.println("Connected to MySQL!");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL JDBC driver not found — using local file storage instead.");
        } catch (Exception e) {
            System.out.println("DB connection failed (" + e.getMessage() + ") — using local file storage instead.");
        }
    }

    private void createTableIfNotExists() {
        if (conn == null) return;
        String sql = """
            CREATE TABLE IF NOT EXISTS diary_entries (
                id VARCHAR(36) PRIMARY KEY,
                title VARCHAR(255),
                content TEXT,
                mood VARCHAR(50),
                date_time VARCHAR(50),
                voice_note_path VARCHAR(500)
            )
        """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println("Could not create table: " + e.getMessage());
        }
    }

    // ── Load entries (DB first, then file fallback) ───────────────────────
    private void loadEntries() {
        if (conn != null) {
            loadFromDatabase();
        } else {
            loadFromFile();
        }
    }

    private void loadFromDatabase() {
        String sql = "SELECT * FROM diary_entries";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                entries.add(new DiaryEntry(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("content"),
                    rs.getString("mood"),
                    LocalDateTime.parse(rs.getString("date_time")),
                    rs.getString("voice_note_path")
                ));
            }
            System.out.println("Loaded " + entries.size() + " entries from database.");
        } catch (SQLException e) {
            System.out.println("Failed to load from DB: " + e.getMessage() + " — falling back to file.");
            loadFromFile();
        }
    }

    // ── File persistence ──────────────────────────────────────────────────
    // Format per line:  id<TAB>title<TAB>content<TAB>mood<TAB>dateTime<TAB>voiceNotePath
    // Newlines inside content are escaped as \n before saving.

    private static final String SEP = "\t";   // field separator
    private static final String NL  = "\\n";  // escaped newline inside a field

    private void loadFromFile() {
        File f = new File(FALLBACK_FILE);
        if (!f.exists()) {
            System.out.println("No local diary file found — starting fresh.");
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            int loaded = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] p = line.split(SEP, 6);
                if (p.length < 6) continue;   // skip malformed lines
                try {
                    entries.add(new DiaryEntry(
                        p[0],
                        unescape(p[1]),
                        unescape(p[2]),
                        unescape(p[3]),
                        LocalDateTime.parse(p[4]),
                        p[5].equals("null") ? null : unescape(p[5])
                    ));
                    loaded++;
                } catch (Exception ignored) {}  // skip corrupted line
            }
            System.out.println("Loaded " + loaded + " entries from local file.");
        } catch (IOException e) {
            System.out.println("Could not read diary file: " + e.getMessage());
        }
    }

    /** Rewrite the entire file from the current in-memory list. */
    private void saveToFile() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(FALLBACK_FILE, false))) {
            for (DiaryEntry e : entries) {
                pw.println(
                    e.getId()                          + SEP +
                    escape(e.getTitle())               + SEP +
                    escape(e.getContent())             + SEP +
                    escape(e.getMood())                + SEP +
                    e.getDateTime().toString()         + SEP +
                    (e.getVoiceNotePath() == null ? "null" : escape(e.getVoiceNotePath()))
                );
            }
        } catch (IOException e) {
            System.out.println("Could not save to local file: " + e.getMessage());
        }
    }

    /** Replace real newlines/tabs with escape sequences so each entry stays one line. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace(SEP, "\\t")
                .replace("\n", NL)
                .replace("\r", "\\r");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\r", "\r")
                .replace(NL, "\n")
                .replace("\\t", SEP)
                .replace("\\\\", "\\");
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    public void addEntry(DiaryEntry entry) {
        entries.add(entry);

        if (conn != null) {
            String sql = "INSERT INTO diary_entries VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entry.getId());
                ps.setString(2, entry.getTitle());
                ps.setString(3, entry.getContent());
                ps.setString(4, entry.getMood());
                ps.setString(5, entry.getDateTime().toString());
                ps.setString(6, entry.getVoiceNotePath());
                ps.executeUpdate();
            } catch (SQLException e) {
                System.out.println("DB insert failed: " + e.getMessage());
            }
        } else {
            saveToFile();   // ← persist immediately
        }
    }

    public void updateEntry(String id, String title, String content, String mood) {
        for (DiaryEntry e : entries) {
            if (e.getId().equals(id)) {
                e.setTitle(title);
                e.setContent(content);
                e.setMood(mood);
                break;
            }
        }

        if (conn != null) {
            String sql = "UPDATE diary_entries SET title=?, content=?, mood=? WHERE id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, title);
                ps.setString(2, content);
                ps.setString(3, mood);
                ps.setString(4, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.out.println("DB update failed: " + e.getMessage());
            }
        } else {
            saveToFile();
        }
    }

    public void deleteEntry(String id) {
        entries.removeIf(e -> e.getId().equals(id));

        if (conn != null) {
            String sql = "DELETE FROM diary_entries WHERE id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.out.println("DB delete failed: " + e.getMessage());
            }
        } else {
            saveToFile();
        }
    }

    public void setVoiceNote(String entryId, String path) {
        for (DiaryEntry e : entries) {
            if (e.getId().equals(entryId)) {
                e.setVoiceNotePath(path);
                break;
            }
        }

        if (conn != null) {
            String sql = "UPDATE diary_entries SET voice_note_path=? WHERE id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, path);
                ps.setString(2, entryId);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.out.println("DB voice note update failed: " + e.getMessage());
            }
        } else {
            saveToFile();
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public List<DiaryEntry> getAllEntries() {
        return new ArrayList<>(entries);
    }

    public List<DiaryEntry> searchByKeyword(String kw) {
        String lower = kw.toLowerCase();
        return entries.stream()
            .filter(e -> e.getTitle().toLowerCase().contains(lower)
                      || e.getContent().toLowerCase().contains(lower))
            .collect(Collectors.toList());
    }

    public List<DiaryEntry> searchByDate(String date) {
        return entries.stream()
            .filter(e -> e.getDateOnly().equals(date))
            .collect(Collectors.toList());
    }

    public boolean isConnected() {
        try {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}