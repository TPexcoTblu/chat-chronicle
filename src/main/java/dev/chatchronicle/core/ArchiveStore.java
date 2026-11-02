package dev.chatchronicle.core;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/** Append-only checksummed daily journal is authoritative; SQLite/FTS is replaceable. */
public final class ArchiveStore implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private static final int MAX_LINE = 4 * 1024 * 1024;
    private final Path root;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Chat Chronicle archive"); t.setDaemon(true); return t;
    });
    private final Consumer<String> errors;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Connection db;
    private PreparedStatement insertMessage, insertSearch, insertRun, assignRun, extendRun;
    private FileChannel lockChannel;
    private FileLock archiveLock;
    private final Map<Path, RandomAccessFile> dirty = new HashMap<>();
    private final List<ChatRecord> pending = new ArrayList<>();
    private volatile UiMessage status = new UiMessage("archive.loading");
    public String statusText() { return status.text(); }
    public volatile int damagedRecords;
    private volatile boolean ready;
    private volatile int flushInterval;
    private long lastFlush = System.nanoTime();
    public volatile boolean previousInterrupted;
    private ChatRecord tailRecord;
    private long tailRun;
    private boolean tailDeleted;
    public volatile long dataRevision;
    public volatile long maintenanceRevision;
    private RetentionPolicy retention = RetentionPolicy.UNLIMITED;
    private long lastRetention;
    public record RetentionResult(long removed, long remaining) {}

    public record Row(long sequence, ChatRecord message, long firstSequence, long lastSequence, long repeatCount) {
        public Row(long sequence, ChatRecord message) { this(sequence, message, sequence, sequence, 1); }
    }
    public record Day(String date, long count) {}
    public record Page(List<Row> rows, boolean more, long cursor) {}
    public record RecentSnapshot(List<Row> originals, List<Row> groups) {}

    public ArchiveStore(Path root, int intervalSeconds, Consumer<String> errors) {
        this.root = root; this.errors = errors;
        this.flushInterval = Math.clamp(intervalSeconds, 1, 60);
        worker.execute(() -> {
            try {
                initialize(); ready = true;
                status = damagedRecords > 0 ? new UiMessage("archive.skipped", damagedRecords)
                        : previousInterrupted ? new UiMessage("archive.recovered") : UiMessage.EMPTY;
            }
            catch (Exception ex) { fail(ex); }
        });
        worker.scheduleWithFixedDelay(() -> {
            try {
                if (ready && System.nanoTime() - lastFlush >= TimeUnit.SECONDS.toNanos(flushInterval)) {
                    flush(); lastFlush = System.nanoTime();
                }
                if (ready && retention.enabled() && System.nanoTime() - lastRetention >= TimeUnit.MINUTES.toNanos(1)) {
                    applyRetention(); lastRetention = System.nanoTime();
                }
            } catch (Exception ex) { fail(ex); }
        }, 1, 1, TimeUnit.SECONDS);
    }
    public void setFlushInterval(int seconds) { flushInterval = Math.clamp(seconds, 1, 60); }

    private void initialize() throws Exception {
        Files.createDirectories(root);
        lockChannel = FileChannel.open(root.resolve("archive.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        archiveLock = lockChannel.tryLock();
        if (archiveLock == null) throw new IOException(Messages.text("archive.locked"));
        Path active = root.resolve(".running");
        if (Files.exists(active)) {
            previousInterrupted = true;
            String previous;
            try { previous = Files.size(active) < 512 ? Files.readString(active) : "unknown time"; }
            catch (java.nio.charset.CharacterCodingException ex) { previous = "invalid shutdown marker"; }
            Files.writeString(root.resolve("recovery-notes.log"), java.time.Instant.now() + " interrupted near " + previous + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Class.forName("org.sqlite.JDBC");
        try {
            openIndex();
            try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
                if (!rs.next() || !"ok".equals(rs.getString(1))) throw new SQLException(Messages.text("archive.integrity"));
            }
        } catch (SQLException ex) {
            if (db != null) db.close();
            String suffix = ".broken-" + System.currentTimeMillis();
            for (String name : List.of("index.sqlite", "index.sqlite-wal", "index.sqlite-shm")) {
                Path path = root.resolve(name);
                if (Files.exists(path)) Files.move(path, path.resolveSibling(name + suffix));
            }
            openIndex();
        }
        resetTail();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".journal")).sorted().toList()) recover(file);
        }
        writeActiveMarker();
    }
    private void writeActiveMarker() throws IOException {
        Path temp = root.resolve(".running.tmp");
        Files.writeString(temp, java.time.ZonedDateTime.now().toString());
        try { Files.move(temp, root.resolve(".running"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, root.resolve(".running"), StandardCopyOption.REPLACE_EXISTING); }
    }
    private void openIndex() throws SQLException {
        db = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("index.sqlite").toAbsolutePath());
        try (Statement st = db.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL"); st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000"); st.execute("PRAGMA cache_size=-8192");
            int schema;
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) { rs.next(); schema = rs.getInt(1); }
            if (schema != 3) {
                // Only the derived index is migrated. Original journals remain untouched.
                db.setAutoCommit(false);
                try {
                    st.execute("DROP TABLE IF EXISTS search"); st.execute("DROP TABLE IF EXISTS messages");
                    st.execute("DROP TABLE IF EXISTS checkpoints"); st.execute("DROP TABLE IF EXISTS runs");
                    st.execute("PRAGMA user_version=3"); db.commit();
                } catch (SQLException ex) { db.rollback(); throw ex; }
                finally { db.setAutoCommit(true); }
            }
            st.execute("CREATE TABLE IF NOT EXISTS messages (seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT UNIQUE NOT NULL, day TEXT NOT NULL, time INTEGER NOT NULL, text TEXT NOT NULL, json TEXT NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, run_id INTEGER NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS messages_day ON messages(day,seq)");
            st.execute("CREATE INDEX IF NOT EXISTS messages_run ON messages(run_id,deleted,seq)");
            st.execute("CREATE TABLE IF NOT EXISTS runs (id INTEGER PRIMARY KEY, day TEXT NOT NULL, first_seq INTEGER NOT NULL, last_seq INTEGER NOT NULL, count INTEGER NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS runs_last ON runs(last_seq)");
            st.execute("CREATE INDEX IF NOT EXISTS runs_day_last ON runs(day,last_seq)");
            st.execute("CREATE VIRTUAL TABLE IF NOT EXISTS search USING fts5(text, tokenize='trigram')");
            st.execute("CREATE TABLE IF NOT EXISTS checkpoints (path TEXT PRIMARY KEY, offset INTEGER NOT NULL)");
        }
        prepareWriters();
    }
    private void prepareWriters() throws SQLException {
        insertMessage = db.prepareStatement("INSERT OR IGNORE INTO messages(id,day,time,text,json,run_id) VALUES(?,?,?,?,?,?) RETURNING seq");
        insertSearch = db.prepareStatement("INSERT INTO search(rowid,text) VALUES(?,?)");
        insertRun = db.prepareStatement("INSERT INTO runs VALUES(?,?,?,?,1)");
        assignRun = db.prepareStatement("UPDATE messages SET run_id=? WHERE seq=?");
        extendRun = db.prepareStatement("UPDATE runs SET last_seq=?,count=count+1 WHERE id=?");
    }
    private void closeWriters() throws SQLException {
        for (PreparedStatement st : new PreparedStatement[]{insertMessage, insertSearch, insertRun, assignRun, extendRun}) if (st != null) st.close();
    }

    public CompletableFuture<Void> append(ChatRecord record) {
        if (!record.valid()) return CompletableFuture.failedFuture(new IllegalArgumentException(Messages.text("archive.invalidRecord")));
        return submit(() -> { requireReady(); appendEvent(record.day(), JSON.toJson(record)); pending.add(record); return null; });
    }
    private Path journal(String day) {
        LocalDate date = LocalDate.parse(day);
        return root.resolve(String.format("%04d/%02d/%s.journal", date.getYear(), date.getMonthValue(), day));
    }
    private void appendEvent(String day, String json) throws IOException {
        Path path = journal(day);
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_LINE - 32) throw new IOException(Messages.text("archive.tooLarge"));
        RandomAccessFile file = dirty.get(path);
        if (file == null) {
            Files.createDirectories(path.getParent());
            file = new RandomAccessFile(path.toFile(), "rw");
            long length = file.length();
            if (length > 0) {
                file.seek(length - 1);
                if (file.read() != '\n') { file.seek(length); file.write('\n'); }
            }
            file.seek(file.length()); dirty.put(path, file);
        }
        CRC32 crc = new CRC32(); crc.update(payload);
        byte[] header = (Long.toHexString(crc.getValue()) + "\t").getBytes(StandardCharsets.US_ASCII);
        byte[] frame = new byte[header.length + payload.length + 1];
        System.arraycopy(header, 0, frame, 0, header.length); System.arraycopy(payload, 0, frame, header.length, payload.length);
        frame[frame.length - 1] = '\n'; file.write(frame);
    }

    private void flush() throws Exception {
        if (dirty.isEmpty()) return;
        for (RandomAccessFile file : dirty.values()) file.getChannel().force(true);
        db.setAutoCommit(false);
        try {
            for (ChatRecord record : pending) index(record);
            // Deletions are journaled and indexed on this same worker, before committing checkpoints.
            for (var entry : dirty.entrySet()) checkpoint(entry.getKey(), entry.getValue().length());
            db.commit(); pending.clear();
        } catch (Exception ex) { db.rollback(); resetTail(); throw ex; }
        finally { db.setAutoCommit(true); }
        for (RandomAccessFile file : dirty.values()) file.close();
        dirty.clear();
        dataRevision++;
        writeActiveMarker();
    }
    private void index(ChatRecord record) throws SQLException {
        boolean sameRun = !tailDeleted && record.sameContent(tailRecord);
        insertMessage.setString(1, record.id()); insertMessage.setString(2, record.day()); insertMessage.setLong(3, record.time());
        insertMessage.setString(4, record.text()); insertMessage.setString(5, JSON.toJson(record)); insertMessage.setLong(6, sameRun ? tailRun : 0);
        long sequence;
        try (ResultSet rs = insertMessage.executeQuery()) {
            if (!rs.next()) return; sequence = rs.getLong(1);
            while (rs.next()) { /* Complete RETURNING before reusing the prepared statement. */ }
        }
        insertSearch.setLong(1, sequence); insertSearch.setString(2, String.join("\n", SessionMarkers.searchVariants(record)).toLowerCase(Locale.ROOT)); insertSearch.executeUpdate();
        if (!sameRun) {
            tailRun = sequence;
            insertRun.setLong(1, tailRun); insertRun.setString(2, record.day()); insertRun.setLong(3, sequence); insertRun.setLong(4, sequence); insertRun.executeUpdate();
            assignRun.setLong(1, tailRun); assignRun.setLong(2, sequence); assignRun.executeUpdate();
        } else {
            extendRun.setLong(1, sequence); extendRun.setLong(2, tailRun); extendRun.executeUpdate();
        }
        tailRecord = record; tailDeleted = false;
    }
    private void resetTail() throws SQLException {
        tailRecord = null; tailDeleted = false; tailRun = 0;
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("SELECT json,run_id,deleted FROM messages ORDER BY seq DESC LIMIT 1")) {
            if (rs.next()) {
                try { tailRecord = JSON.fromJson(rs.getString(1), ChatRecord.class); }
                catch (RuntimeException ignored) { tailRecord = null; }
                tailRun = rs.getLong(2); tailDeleted = rs.getBoolean(3);
            }
        }
    }
    private String relative(Path path) { return root.relativize(path).toString(); }
    private void checkpoint(Path path, long offset) throws SQLException {
        try (PreparedStatement st = db.prepareStatement("INSERT OR REPLACE INTO checkpoints VALUES(?,?)")) {
            st.setString(1, relative(path)); st.setLong(2, offset); st.executeUpdate();
        }
    }
    private void recover(Path path) throws Exception {
        int beforeDamaged = damagedRecords;
        long start = 0;
        try (PreparedStatement st = db.prepareStatement("SELECT offset FROM checkpoints WHERE path=?")) {
            st.setString(1, relative(path));
            try (ResultSet rs = st.executeQuery()) { if (rs.next()) start = rs.getLong(1); }
        }
        long fileLength = Files.size(path);
        if (start > fileLength) start = 0;
        try (InputStream file = new BufferedInputStream(Files.newInputStream(path), 64 * 1024)) {
            file.skipNBytes(start);
            db.setAutoCommit(false);
            try {
                ByteArrayOutputStream line = new ByteArrayOutputStream();
                boolean oversized = false;
                int next;
                while ((next = file.read()) != -1) {
                    if (next == '\n') {
                        if (oversized || !recoverLine(line.toByteArray())) damagedRecords++;
                        line.reset(); oversized = false;
                    } else if (line.size() < MAX_LINE) line.write(next);
                    else oversized = true;
                }
                if (line.size() > 0 || oversized) damagedRecords++;
                checkpoint(path, fileLength); db.commit();
            } catch (Exception ex) { db.rollback(); resetTail(); throw ex; }
            finally { db.setAutoCommit(true); }
        }
        if (damagedRecords > beforeDamaged) Files.writeString(root.resolve("recovery-notes.log"),
                java.time.Instant.now() + " skipped=" + (damagedRecords - beforeDamaged) + " file=" + relative(path) + " fromOffset=" + start + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private boolean recoverLine(byte[] line) throws SQLException {
        try {
            int tab = -1;
            for (int i = 0; i < Math.min(line.length, 17); i++) if (line[i] == '\t') { tab = i; break; }
            if (tab < 1) return false;
            long expected = Long.parseUnsignedLong(new String(line, 0, tab, StandardCharsets.US_ASCII), 16);
            CRC32 crc = new CRC32(); crc.update(line, tab + 1, line.length - tab - 1);
            if (expected != crc.getValue()) return false;
            String json = new String(line, tab + 1, line.length - tab - 1, StandardCharsets.UTF_8);
            var obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("delete")) { markDeleted(obj.get("delete").getAsString()); return true; }
            ChatRecord record = JSON.fromJson(obj, ChatRecord.class);
            if (!record.valid()) return false;
            index(record); return true;
        } catch (RuntimeException ex) { return false; }
    }

    public CompletableFuture<List<Day>> days() {
        return submit(() -> {
            requireReady(); flush(); List<Day> out = new ArrayList<>();
            try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("SELECT day,count(*) FROM messages WHERE deleted=0 GROUP BY day ORDER BY day DESC")) {
                while (rs.next()) out.add(new Day(rs.getString(1), rs.getLong(2)));
            }
            return out;
        });
    }
    /** Consistent snapshot for refreshing HUD after deletion while new messages keep arriving. */
    public CompletableFuture<RecentSnapshot> recentSnapshot(int limit) {
        return submit(() -> {
            requireReady(); flush(); List<Row> originals = new ArrayList<>(), groups = new ArrayList<>();
            try (PreparedStatement st = db.prepareStatement("SELECT seq,json FROM messages WHERE deleted=0 ORDER BY seq DESC LIMIT ?")) {
                st.setInt(1, limit); try (ResultSet rs = st.executeQuery()) { while (rs.next()) addValid(originals, rs); }
            }
            try (PreparedStatement st = db.prepareStatement("SELECT m.seq,m.json,r.first_seq,r.last_seq,r.count FROM runs r JOIN messages m ON m.seq=r.last_seq WHERE r.count>0 ORDER BY r.last_seq DESC LIMIT ?")) {
                st.setInt(1, limit); try (ResultSet rs = st.executeQuery()) { while (rs.next()) addValid(groups, rs); }
            }
            return new RecentSnapshot(List.copyOf(originals), List.copyOf(groups));
        });
    }
    public CompletableFuture<Page> page(String day, long before, int limit) {
        return submit(() -> {
            requireReady(); flush(); List<Row> out = new ArrayList<>();
            try (PreparedStatement st = db.prepareStatement("SELECT seq,json FROM messages WHERE deleted=0 AND seq<? " +
                    (day == null ? "" : "AND day=? ") + "ORDER BY seq DESC LIMIT ?")) {
                int n = 1; st.setLong(n++, before);
                if (day != null) st.setString(n++, day);
                st.setInt(n, limit + 1);
                try (ResultSet rs = st.executeQuery()) { while (rs.next()) addValid(out, rs); }
            }
            return makePage(out, limit);
        });
    }
    public CompletableFuture<List<Row>> around(long sequence, int radius) {
        return submit(() -> {
            requireReady(); flush(); List<Row> out = new ArrayList<>();
            try (PreparedStatement st = db.prepareStatement("SELECT seq,json FROM (SELECT seq,json FROM messages WHERE deleted=0 AND seq<=? ORDER BY seq DESC LIMIT ?) UNION ALL SELECT seq,json FROM (SELECT seq,json FROM messages WHERE deleted=0 AND seq>? ORDER BY seq LIMIT ?) ORDER BY seq DESC")) {
                st.setLong(1, sequence); st.setInt(2, radius + 1); st.setLong(3, sequence); st.setInt(4, radius);
                try (ResultSet rs = st.executeQuery()) { while (rs.next()) addValid(out, rs); }
            }
            return out;
        });
    }
    public CompletableFuture<Page> after(String day, long after, int limit) {
        return submit(() -> {
            requireReady(); flush(); List<Row> out = new ArrayList<>();
            try (PreparedStatement st = db.prepareStatement("SELECT seq,json FROM messages WHERE deleted=0 AND seq>? " +
                    (day == null ? "" : "AND day=? ") + "ORDER BY seq ASC LIMIT ?")) {
                int n = 1; st.setLong(n++, after);
                if (day != null) st.setString(n++, day);
                st.setInt(n, limit + 1);
                try (ResultSet rs = st.executeQuery()) { while (rs.next()) addValid(out, rs); }
            }
            boolean more = out.size() > limit;
            if (more) out.removeLast();
            Collections.reverse(out);
            return new Page(List.copyOf(out), more, out.isEmpty() ? 0 : out.getFirst().sequence());
        });
    }
    public CompletableFuture<Page> groupedPage(String day, long before, int limit) {
        return grouped(day, before, limit, false);
    }
    public CompletableFuture<Page> groupedAfter(String day, long after, int limit) {
        return grouped(day, after, limit, true);
    }
    private CompletableFuture<Page> grouped(String day, long boundary, int limit, boolean newer) {
        return submit(() -> {
            requireReady(); flush(); List<Row> out = new ArrayList<>();
            String sql = "SELECT m.seq,m.json,r.first_seq,r.last_seq,r.count FROM runs r JOIN messages m ON m.seq=r.last_seq " +
                    "WHERE r.count>0 AND r.last_seq" + (newer ? ">" : "<") + "? " +
                    (day == null ? "" : "AND r.day=? ") + "ORDER BY r.last_seq " + (newer ? "ASC" : "DESC") + " LIMIT ?";
            try (PreparedStatement st = db.prepareStatement(sql)) {
                int n = 1; st.setLong(n++, boundary); if (day != null) st.setString(n++, day); st.setInt(n, limit + 1);
                try (ResultSet rs = st.executeQuery()) { while (rs.next()) addValid(out, rs); }
            }
            if (!newer) return makePage(out, limit);
            boolean more = out.size() > limit; if (more) out.removeLast(); Collections.reverse(out);
            return new Page(List.copyOf(out), more, out.isEmpty() ? 0 : out.getFirst().lastSequence());
        });
    }
    public CompletableFuture<Page> search(SearchQuery query, long before, int limit, AtomicBoolean cancelled) {
        return submit(() -> {
            requireReady(); flush(); var pattern = query.compile(); List<Row> out = new ArrayList<>();
            String literal = query.indexedLiteral();
            String sql = "SELECT m.seq,m.json FROM messages m " + (literal == null ? "" : "JOIN search ON search.rowid=m.seq ") +
                    "WHERE m.deleted=0 AND m.seq<? " + (literal == null ? "" : "AND search MATCH ? ") +
                    (query.fromDay() == null ? "" : "AND m.day>=? ") + (query.toDay() == null ? "" : "AND m.day<=? ") + "ORDER BY m.seq DESC";
            try (PreparedStatement st = db.prepareStatement(sql)) {
                int n = 1; st.setLong(n++, before);
                if (literal != null) st.setString(n++, "\"" + literal.replace("\"", "\"\"") + "\"");
                if (query.fromDay() != null) st.setString(n++, query.fromDay());
                if (query.toDay() != null) st.setString(n, query.toDay());
                try (ResultSet rs = st.executeQuery()) {
                    while (!cancelled.get() && rs.next()) {
                        Row row = parse(rs);
                        if (row != null && SessionMarkers.searchVariants(row.message()).stream().anyMatch(text -> pattern.matcher(text).find())) out.add(row);
                        if (out.size() > limit) break;
                    }
                }
            }
            return makePage(out, limit);
        });
    }
    private Page makePage(List<Row> rows, int limit) {
        boolean more = rows.size() > limit;
        if (more) rows.removeLast();
        return new Page(List.copyOf(rows), more, rows.isEmpty() ? 0 : rows.getLast().firstSequence());
    }
    private Row parse(ResultSet rs) throws SQLException {
        try {
            ChatRecord record = JSON.fromJson(rs.getString(2), ChatRecord.class);
            if (!record.valid()) return null;
            return rs.getMetaData().getColumnCount() >= 5
                    ? new Row(rs.getLong(1), record, rs.getLong(3), rs.getLong(4), rs.getLong(5)) : new Row(rs.getLong(1), record);
        }
        catch (RuntimeException ex) { damagedRecords++; return null; }
    }
    private void addValid(List<Row> out, ResultSet rs) throws SQLException { Row row = parse(rs); if (row != null) out.add(row); }

    public CompletableFuture<Void> delete(Row row) {
        return submit(() -> {
            requireReady(); flush();
            appendEvent(row.message().day(), "{\"delete\":\"" + row.message().id() + "\"}");
            for (RandomAccessFile file : dirty.values()) file.getChannel().force(true);
            // Replay-safe tombstone: preserved when the derived search index is rebuilt.
            db.setAutoCommit(false);
            try { markDeleted(row.message().id()); db.commit(); }
            catch (Exception ex) { db.rollback(); resetTail(); throw ex; }
            finally { db.setAutoCommit(true); }
            flush(); maintenanceRevision++; return null;
        });
    }
    private void markDeleted(String id) throws SQLException {
        long runId = 0;
        try (PreparedStatement st = db.prepareStatement("SELECT run_id FROM messages WHERE id=? AND deleted=0")) {
            st.setString(1, id); try (ResultSet rs = st.executeQuery()) { if (rs.next()) runId = rs.getLong(1); }
        }
        try (PreparedStatement st = db.prepareStatement("UPDATE messages SET deleted=1 WHERE id=?")) { st.setString(1, id); st.executeUpdate(); }
        try (PreparedStatement st = db.prepareStatement("DELETE FROM search WHERE rowid IN (SELECT seq FROM messages WHERE id=?)")) { st.setString(1, id); st.executeUpdate(); }
        if (runId != 0) try (PreparedStatement st = db.prepareStatement("UPDATE runs SET count=count-1,first_seq=coalesce((SELECT min(seq) FROM messages WHERE run_id=? AND deleted=0),first_seq),last_seq=coalesce((SELECT max(seq) FROM messages WHERE run_id=? AND deleted=0),last_seq) WHERE id=?")) {
            for (int i = 1; i <= 3; i++) st.setLong(i, runId); st.executeUpdate();
        }
        if (tailRecord != null && tailRecord.id().equals(id)) tailDeleted = true;
    }
    public CompletableFuture<Long> deleteSelected(List<Row> selection) {
        List<Row> snapshot = List.copyOf(selection);
        return submit(() -> {
            requireReady(); flush(); long removed = 0;
            for (Row target : snapshot) {
                long cursor = target.firstSequence() - 1;
                while (true) {
                    List<String[]> batch = new ArrayList<>();
                    try (PreparedStatement st = db.prepareStatement("SELECT id,day,seq FROM messages WHERE deleted=0 AND seq>? AND seq<=? AND day=? ORDER BY seq LIMIT 256")) {
                        st.setLong(1, cursor); st.setLong(2, target.lastSequence()); st.setString(3, target.message().day());
                        try (ResultSet rs = st.executeQuery()) {
                            while (rs.next()) { batch.add(new String[]{rs.getString(1), rs.getString(2)}); cursor = rs.getLong(3); }
                        }
                    }
                    if (batch.isEmpty()) break;
                    for (String[] record : batch) appendEvent(record[1], "{\"delete\":\"" + record[0] + "\"}");
                    for (RandomAccessFile file : dirty.values()) file.getChannel().force(true);
                    db.setAutoCommit(false);
                    try { for (String[] record : batch) markDeleted(record[0]); db.commit(); }
                    catch (Exception ex) { db.rollback(); resetTail(); throw ex; }
                    finally { db.setAutoCommit(true); }
                    flush(); removed += batch.size();
                }
            }
            if (removed > 0) maintenanceRevision++;
            return removed;
        });
    }
    public CompletableFuture<RetentionResult> configureRetention(RetentionPolicy policy) {
        return submit(() -> { requireReady(); retention = policy; lastRetention = System.nanoTime(); return applyRetention(); });
    }
    private long messageCount() throws SQLException {
        try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM messages WHERE deleted=0")) { rs.next(); return rs.getLong(1); }
    }
    private RetentionResult applyRetention() throws Exception {
        flush();
        if (!retention.enabled()) return new RetentionResult(0, messageCount());
        String cutoffDay = retention.days() == 0 ? "0000-01-01" : LocalDate.now().minusDays(retention.days() - 1L).toString();
        long cutoffSequence = -1;
        if (retention.messages() > 0) try (PreparedStatement st = db.prepareStatement("SELECT seq FROM messages WHERE deleted=0 ORDER BY seq DESC LIMIT 1 OFFSET ?")) {
            st.setLong(1, retention.messages()); try (ResultSet rs = st.executeQuery()) { if (rs.next()) cutoffSequence = rs.getLong(1); }
        }
        long removed = 0;
        while (true) {
            List<String[]> batch = new ArrayList<>();
            try (PreparedStatement st = db.prepareStatement("SELECT id,day FROM messages WHERE deleted=0 AND (day<? OR seq<=?) ORDER BY seq LIMIT 256")) {
                st.setString(1, cutoffDay); st.setLong(2, cutoffSequence);
                try (ResultSet rs = st.executeQuery()) { while (rs.next()) batch.add(new String[]{rs.getString(1), rs.getString(2)}); }
            }
            if (batch.isEmpty()) break;
            for (String[] entry : batch) appendEvent(entry[1], "{\"delete\":\"" + entry[0] + "\"}");
            for (RandomAccessFile file : dirty.values()) file.getChannel().force(true);
            db.setAutoCommit(false);
            try { for (String[] entry : batch) markDeleted(entry[0]); db.commit(); }
            catch (Exception ex) { db.rollback(); resetTail(); throw ex; }
            finally { db.setAutoCommit(true); }
            flush(); removed += batch.size();
        }
        if (removed > 0) maintenanceRevision++;
        return new RetentionResult(removed, messageCount());
    }
    /** Explicit maintenance: rewrite live records atomically; reclaim deleted SQLite rows. */
    public CompletableFuture<Long> compactArchive() {
        return submit(() -> {
            requireReady(); flush(); long rewritten = 0;
            List<Path> journals;
            try (var paths = Files.walk(root)) { journals = paths.filter(p -> p.toString().endsWith(".journal")).sorted().toList(); }
            for (Path journal : journals) {
                String name = journal.getFileName().toString(); String day = name.substring(0, name.length() - ".journal".length());
                LocalDate.parse(day);
                Path temp = journal.resolveSibling(name + ".compact.tmp");
                try (FileOutputStream file = new FileOutputStream(temp.toFile()); BufferedOutputStream output = new BufferedOutputStream(file, 64 * 1024);
                     PreparedStatement st = db.prepareStatement("SELECT json FROM messages WHERE deleted=0 AND day=? ORDER BY seq")) {
                    st.setString(1, day);
                    try (ResultSet rs = st.executeQuery()) {
                        while (rs.next()) {
                            ChatRecord record = JSON.fromJson(rs.getString(1), ChatRecord.class);
                            if (record == null || !record.valid() || !day.equals(record.day())) throw new IOException(Messages.text("archive.invalidIndexed"));
                            byte[] payload = JSON.toJson(record).getBytes(StandardCharsets.UTF_8); CRC32 crc = new CRC32(); crc.update(payload);
                            output.write(Long.toHexString(crc.getValue()).getBytes(StandardCharsets.US_ASCII)); output.write('\t'); output.write(payload); output.write('\n'); rewritten++;
                        }
                    }
                    output.flush(); file.getChannel().force(true);
                }
                // No non-atomic fallback: retain original if the filesystem cannot replace it safely.
                Files.move(temp, journal, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                checkpoint(journal, Files.size(journal));
            }
            closeWriters();
            try {
                try (Statement st = db.createStatement()) {
                    st.executeUpdate("DELETE FROM messages WHERE deleted=1"); st.executeUpdate("DELETE FROM runs WHERE count=0");
                    st.execute("INSERT INTO search(search) VALUES('optimize')");
                    st.execute("PRAGMA wal_checkpoint(TRUNCATE)"); st.execute("VACUUM"); st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
            } finally { prepareWriters(); }
            resetTail(); dataRevision++; maintenanceRevision++; return rewritten;
        });
    }
    private void requireReady() { if (!ready) throw new IllegalStateException(statusText()); }
    private void fail(Exception ex) { status = new UiMessage("archive.error", ex.getMessage()); errors.accept(statusText()); }
    private <T> CompletableFuture<T> submit(Callable<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException(Messages.text("archive.closed")));
        worker.execute(() -> {
            try { future.complete(operation.call()); }
            catch (Exception ex) { fail(ex); future.completeExceptionally(ex); }
        });
        return future;
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try { if (ready) { flush(); Files.deleteIfExists(root.resolve(".running")); } } catch (Exception ex) { fail(ex); }
            finally {
                try { for (RandomAccessFile f : dirty.values()) f.close(); if (db != null) db.close(); }
                catch (Exception ex) { fail(ex); }
                finally { try { if (archiveLock != null) archiveLock.release(); if (lockChannel != null) lockChannel.close(); } catch (Exception ex) { fail(ex); } }
            }
        });
        worker.shutdown();
        try { if (!worker.awaitTermination(10, TimeUnit.SECONDS)) errors.accept("Archive shutdown timed out"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
