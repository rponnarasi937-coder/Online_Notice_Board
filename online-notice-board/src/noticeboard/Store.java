package noticeboard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps users and notices in memory and saves them to plain text files in the data folder.
 * One line per record, tab-separated, text fields URL-encoded. All methods are thread-safe.
 */
final class Store {
    private final Path usersFile;
    private final Path noticesFile;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Notice> notices = new LinkedHashMap<>();

    Store(Path dir) throws IOException {
        Files.createDirectories(dir);
        this.usersFile = dir.resolve("users.txt");
        this.noticesFile = dir.resolve("notices.txt");
        load();
    }

    // ---------- users ----------

    synchronized User user(String username) {
        return username == null ? null : users.get(key(username));
    }

    synchronized int userCount() {
        return users.size();
    }

    /** Returns false if the username is already taken (case-insensitive). */
    synchronized boolean addUser(User user) {
        if (users.containsKey(key(user.username()))) return false;
        users.put(key(user.username()), user);
        saveUsers();
        return true;
    }

    // ---------- notices ----------

    synchronized List<Notice> allNotices() {
        return new ArrayList<>(notices.values());
    }

    synchronized Notice notice(String id) {
        return id == null ? null : notices.get(id);
    }

    synchronized int noticeCount() {
        return notices.size();
    }

    synchronized void addNotice(Notice notice) {
        notices.put(notice.id(), notice);
        saveNotices();
    }

    /** Replaces an existing notice. Returns false if it no longer exists. */
    synchronized boolean updateNotice(Notice notice) {
        if (!notices.containsKey(notice.id())) return false;
        notices.put(notice.id(), notice);
        saveNotices();
        return true;
    }

    synchronized boolean deleteNotice(String id) {
        if (notices.remove(id) == null) return false;
        saveNotices();
        return true;
    }

    // ---------- file handling ----------

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private void load() throws IOException {
        if (Files.exists(usersFile)) {
            for (String line : Files.readAllLines(usersFile, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length < 5) continue;
                User u = new User(Util.dec(p[0]), p[1], p[2], p[3], Long.parseLong(p[4]));
                users.put(key(u.username()), u);
            }
        }
        if (Files.exists(noticesFile)) {
            for (String line : Files.readAllLines(noticesFile, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length < 9) continue;
                Notice n = new Notice(Util.dec(p[0]), Util.dec(p[1]), Util.dec(p[2]), Util.dec(p[3]),
                        Util.dec(p[4]), Util.dec(p[5]), Long.parseLong(p[6]), Long.parseLong(p[7]),
                        Boolean.parseBoolean(p[8]));
                notices.put(n.id(), n);
            }
        }
    }

    private void saveUsers() {
        List<String> lines = new ArrayList<>();
        for (User u : users.values()) {
            lines.add(String.join("\t", Util.enc(u.username()), u.salt(), u.hash(), u.role(),
                    Long.toString(u.created())));
        }
        writeAtomic(usersFile, lines);
    }

    private void saveNotices() {
        List<String> lines = new ArrayList<>();
        for (Notice n : notices.values()) {
            lines.add(String.join("\t", Util.enc(n.id()), Util.enc(n.title()), Util.enc(n.content()),
                    Util.enc(n.category()), Util.enc(n.priority()), Util.enc(n.author()),
                    Long.toString(n.created()), Long.toString(n.expires()), Boolean.toString(n.pinned())));
        }
        writeAtomic(noticesFile, lines);
    }

    /** Write to a temp file first, then move it over the real file so a crash never leaves half a file. */
    private static void writeAtomic(Path target, List<String> lines) {
        try {
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
