package noticeboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** The web server: routing, login sessions and all request handlers. */
final class Server {
    static final List<String> CATEGORIES =
            List.of("General", "Academic", "Events", "Exams", "Placement", "Sports", "Holiday");
    static final List<String> PRIORITIES = List.of("Normal", "Important", "Urgent");

    /** Short messages shown after a redirect (?flash=key). Only these fixed texts can ever be displayed. */
    private static final Map<String, String> FLASH = Map.of(
            "posted", "Notice published.",
            "updated", "Notice updated.",
            "deleted", "Notice deleted.",
            "pinned", "Notice pinned to the top.",
            "unpinned", "Notice unpinned.",
            "loggedin", "Welcome back! You are logged in.",
            "loggedout", "You have been logged out.",
            "registered", "Account created. Welcome!",
            "login-required", "Please log in to continue.");

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,20}");
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long SESSION_MS = 12L * 60 * 60 * 1000;
    private static final long LOCK_WINDOW_MS = 10L * 60 * 1000;
    private static final int MAX_FAILED_LOGINS = 8;
    private static final String DUMMY_SALT = Util.salt();

    record Session(String username, String csrf, long created) {}

    /** Everything a handler needs about the current request. */
    private record Ctx(HttpExchange ex, User user, Session session,
                       Map<String, String> query, Map<String, String> form) {
        String csrf() {
            return session == null ? "" : session.csrf();
        }

        String q(String key) {
            return query.getOrDefault(key, "");
        }

        String f(String key) {
            return form.getOrDefault(key, "");
        }
    }

    private final Store store;
    private final HttpServer http;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, long[]> failedLogins = new ConcurrentHashMap<>(); // ip -> {count, windowStart}

    Server(Store store, int port) throws IOException {
        this.store = store;
        this.http = HttpServer.create(new InetSocketAddress(port), 0);
        this.http.createContext("/", this::handle);
        this.http.setExecutor(Executors.newFixedThreadPool(16));
    }

    void start() {
        http.start();
    }

    static boolean canManage(User user, Notice notice) {
        return user != null && (user.isAdmin() || user.username().equalsIgnoreCase(notice.author()));
    }

    // ---------- routing ----------

    private void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            Map<String, String> query = Util.parseForm(ex.getRequestURI().getRawQuery());

            String sid = sessionId(ex);
            Session session = lookup(sid);
            User user = session == null ? null : store.user(session.username());
            if (session != null && user == null) {
                sessions.remove(sid);
                session = null;
            }
            Map<String, String> form = method.equals("POST") ? Util.parseForm(Util.readBody(ex.getRequestBody())) : Map.of();
            Ctx c = new Ctx(ex, user, session, query, form);

            if (method.equals("POST") && session != null && !Util.sameText(session.csrf(), c.f("csrf"))) {
                error(c, 403, "Request rejected", "Your security token did not match. Please reload the page and try again.");
                return;
            }

            switch (method + " " + path) {
                case "GET /" -> board(c);
                case "GET /login" -> loginPage(c);
                case "POST /login" -> doLogin(c);
                case "GET /register" -> registerPage(c);
                case "POST /register" -> doRegister(c);
                case "POST /logout" -> doLogout(c);
                case "GET /notice/view" -> viewPage(c);
                case "GET /notice/new" -> newPage(c);
                case "POST /notice/new" -> doCreate(c);
                case "GET /notice/edit" -> editPage(c);
                case "POST /notice/edit" -> doEdit(c);
                case "GET /notice/delete" -> deletePage(c);
                case "POST /notice/delete" -> doDelete(c);
                case "POST /notice/pin" -> doPin(c);
                case "GET /favicon.ico" -> ex.sendResponseHeaders(204, -1);
                default -> error(c, 404, "Page not found", "The page you are looking for does not exist.");
            }
        } catch (IllegalArgumentException e) {
            fallback(ex, 400, "Bad request");
        } catch (Exception e) {
            e.printStackTrace();
            fallback(ex, 500, "Internal server error");
        } finally {
            ex.close();
        }
    }

    // ---------- notice board ----------

    private void board(Ctx c) throws IOException {
        String q = c.q("q").trim();
        String cat = c.q("cat").trim();
        if (!CATEGORIES.contains(cat)) cat = "";
        String needle = q.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        List<Notice> items = new ArrayList<>();
        for (Notice n : store.allNotices()) {
            if (n.expired(now) && !canManage(c.user(), n)) continue; // expired: only owner/admin can still see it
            if (!cat.isEmpty() && !n.category().equals(cat)) continue;
            if (!needle.isEmpty() && !(n.title().toLowerCase(Locale.ROOT).contains(needle)
                    || n.content().toLowerCase(Locale.ROOT).contains(needle)
                    || n.author().toLowerCase(Locale.ROOT).contains(needle))) continue;
            items.add(n);
        }
        items.sort((a, b) -> {
            if (a.pinned() != b.pinned()) return a.pinned() ? -1 : 1; // pinned first
            return Long.compare(b.created(), a.created());            // then newest first
        });
        page(c, 200, "Home", Html.board(items, c.user(), c.csrf(), q, cat, now));
    }

    private void viewPage(Ctx c) throws IOException {
        Notice n = store.notice(c.q("id"));
        long now = System.currentTimeMillis();
        if (n == null || (n.expired(now) && !canManage(c.user(), n))) {
            error(c, 404, "Notice not found", "This notice does not exist or has expired.");
            return;
        }
        page(c, 200, n.title(), Html.detail(n, c.user(), c.csrf(), now));
    }

    private void newPage(Ctx c) throws IOException {
        if (!requireLogin(c)) return;
        page(c, 200, "Post Notice", Html.noticeForm(new Html.NoticeForm("", "", "", "General", "Normal", "0"), c.csrf(), null));
    }

    private void doCreate(Ctx c) throws IOException {
        if (!requireLogin(c)) return;
        Html.NoticeForm f = readNoticeForm(c);
        String err = validate(f);
        if (err != null) {
            page(c, 400, "Post Notice", Html.noticeForm(f, c.csrf(), err));
            return;
        }
        Notice n = new Notice(Util.newId(), f.title(), f.content(), f.category(), f.priority(),
                c.user().username(), System.currentTimeMillis(), expiryFrom(f.days()), false);
        store.addNotice(n);
        redirect(c, "/notice/view?id=" + Util.enc(n.id()) + "&flash=posted");
    }

    private void editPage(Ctx c) throws IOException {
        if (!requireLogin(c)) return;
        Notice n = store.notice(c.q("id"));
        if (!mayChange(c, n)) return;
        long daysLeft = 0;
        if (n.expires() > 0) {
            daysLeft = (long) Math.ceil((n.expires() - System.currentTimeMillis()) / (double) DAY_MS);
            if (daysLeft < 0) daysLeft = 0;
        }
        Html.NoticeForm f = new Html.NoticeForm(n.id(), n.title(), n.content(), n.category(), n.priority(),
                Long.toString(daysLeft));
        page(c, 200, "Edit Notice", Html.noticeForm(f, c.csrf(), null));
    }

    private void doEdit(Ctx c) throws IOException {
        if (!requireLogin(c)) return;
        Html.NoticeForm f = readNoticeForm(c);
        Notice n = store.notice(f.id());
        if (!mayChange(c, n)) return;
        String err = validate(f);
        if (err != null) {
            page(c, 400, "Edit Notice", Html.noticeForm(f, c.csrf(), err));
            return;
        }
        if (!store.updateNotice(n.withEdit(f.title(), f.content(), f.category(), f.priority(), expiryFrom(f.days())))) {
            error(c, 404, "Notice not found", "This notice was deleted.");
            return;
        }
        redirect(c, "/notice/view?id=" + Util.enc(n.id()) + "&flash=updated");
    }

    private void deletePage(Ctx c) throws IOException {
        if (!requireLogin(c)) return;
        Notice n = store.notice(c.q("id"));
        if (!mayChange(c, n)) return;
        page(c, 200, "Delete Notice", Html.confirmDelete(n, c.csrf()));
    }

    private void doDelete(Ctx c) throws IOException {
        if (!requireLogin(c)) return;
        Notice n = store.notice(c.f("id"));
        if (!mayChange(c, n)) return;
        store.deleteNotice(n.id());
        redirect(c, "/?flash=deleted");
    }

    private void doPin(Ctx c) throws IOException {
        if (!requireLogin(c)) return;
        if (!c.user().isAdmin()) {
            error(c, 403, "Not allowed", "Only admins can pin notices.");
            return;
        }
        Notice n = store.notice(c.f("id"));
        if (n == null) {
            error(c, 404, "Notice not found", "This notice does not exist.");
            return;
        }
        boolean pin = !n.pinned();
        store.updateNotice(n.withPinned(pin));
        redirect(c, "/?flash=" + (pin ? "pinned" : "unpinned"));
    }

    // ---------- accounts ----------

    private void loginPage(Ctx c) throws IOException {
        if (c.user() != null) {
            redirect(c, "/");
            return;
        }
        page(c, 200, "Login", Html.loginForm(null, ""));
    }

    private void doLogin(Ctx c) throws IOException {
        if (c.user() != null) {
            redirect(c, "/");
            return;
        }
        String ip = clientIp(c.ex());
        String name = c.f("username").trim();
        String password = c.f("password");
        if (isLocked(ip)) {
            page(c, 429, "Login", Html.loginForm("Too many failed attempts. Please try again in a few minutes.", name));
            return;
        }
        User u = store.user(name);
        boolean ok = false;
        if (u != null) {
            ok = Util.sameText(Util.hash(password, u.salt()), u.hash());
        } else {
            Util.hash(password, DUMMY_SALT); // same work either way, so response time does not reveal valid usernames
        }
        if (!ok) {
            recordFailure(ip);
            page(c, 401, "Login", Html.loginForm("Invalid username or password.", name));
            return;
        }
        failedLogins.remove(ip);
        startSession(c, u);
        redirect(c, "/?flash=loggedin");
    }

    private void registerPage(Ctx c) throws IOException {
        if (c.user() != null) {
            redirect(c, "/");
            return;
        }
        page(c, 200, "Register", Html.registerForm(null, ""));
    }

    private void doRegister(Ctx c) throws IOException {
        if (c.user() != null) {
            redirect(c, "/");
            return;
        }
        String name = c.f("username").trim();
        String password = c.f("password");
        String err = null;
        if (!USERNAME.matcher(name).matches()) {
            err = "Username must be 3-20 characters: letters, numbers or underscore.";
        } else if (password.length() < 6 || password.length() > 100) {
            err = "Password must be between 6 and 100 characters.";
        } else if (!password.equals(c.f("confirm"))) {
            err = "The two passwords do not match.";
        }
        if (err != null) {
            page(c, 400, "Register", Html.registerForm(err, name));
            return;
        }
        String salt = Util.salt();
        User u = new User(name, salt, Util.hash(password, salt), "USER", System.currentTimeMillis());
        if (!store.addUser(u)) {
            page(c, 409, "Register", Html.registerForm("That username is already taken.", name));
            return;
        }
        startSession(c, u);
        redirect(c, "/?flash=registered");
    }

    private void doLogout(Ctx c) throws IOException {
        String sid = sessionId(c.ex());
        if (sid != null) sessions.remove(sid);
        c.ex().getResponseHeaders().add("Set-Cookie", "SID=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        redirect(c, "/?flash=loggedout");
    }

    // ---------- sessions & login throttling ----------

    private void startSession(Ctx c, User u) {
        String sid = Util.token();
        sessions.put(sid, new Session(u.username(), Util.token(), System.currentTimeMillis()));
        c.ex().getResponseHeaders().add("Set-Cookie",
                "SID=" + sid + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + (SESSION_MS / 1000));
    }

    private static String sessionId(HttpExchange ex) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) return null;
        for (String header : headers) {
            for (String part : header.split(";")) {
                String p = part.trim();
                if (p.startsWith("SID=")) return p.substring(4);
            }
        }
        return null;
    }

    private Session lookup(String sid) {
        if (sid == null) return null;
        Session s = sessions.get(sid);
        if (s != null && System.currentTimeMillis() - s.created() > SESSION_MS) {
            sessions.remove(sid);
            return null;
        }
        return s;
    }

    private static String clientIp(HttpExchange ex) {
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    private boolean isLocked(String ip) {
        long[] f = failedLogins.get(ip);
        if (f == null) return false;
        if (System.currentTimeMillis() - f[1] > LOCK_WINDOW_MS) {
            failedLogins.remove(ip);
            return false;
        }
        return f[0] >= MAX_FAILED_LOGINS;
    }

    private void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        failedLogins.compute(ip, (k, f) -> {
            if (f == null || now - f[1] > LOCK_WINDOW_MS) return new long[] {1, now};
            f[0]++;
            return f;
        });
    }

    // ---------- helpers ----------

    private boolean requireLogin(Ctx c) throws IOException {
        if (c.user() != null) return true;
        redirect(c, "/login?flash=login-required");
        return false;
    }

    /** True if the notice exists and the current user may edit/delete it; otherwise sends an error page. */
    private boolean mayChange(Ctx c, Notice n) throws IOException {
        if (n == null) {
            error(c, 404, "Notice not found", "This notice does not exist or was removed.");
            return false;
        }
        if (!canManage(c.user(), n)) {
            error(c, 403, "Not allowed", "You can only change your own notices.");
            return false;
        }
        return true;
    }

    private static Html.NoticeForm readNoticeForm(Ctx c) {
        return new Html.NoticeForm(c.f("id"), c.f("title").trim(), c.f("content").replace("\r\n", "\n").trim(),
                c.f("category"), c.f("priority"), c.form().getOrDefault("days", "0").trim());
    }

    private static String validate(Html.NoticeForm f) {
        if (f.title().isEmpty()) return "Please enter a title.";
        if (f.title().length() > 100) return "The title can have at most 100 characters.";
        if (f.content().isEmpty()) return "Please enter the notice content.";
        if (f.content().length() > 2000) return "The content can have at most 2000 characters.";
        if (!CATEGORIES.contains(f.category())) return "Please choose a valid category.";
        if (!PRIORITIES.contains(f.priority())) return "Please choose a valid priority.";
        try {
            int days = Integer.parseInt(f.days());
            if (days < 0 || days > 365) return "Expiry must be between 0 and 365 days.";
        } catch (NumberFormatException e) {
            return "Expiry days must be a number.";
        }
        return null;
    }

    private static long expiryFrom(String days) {
        int d = Integer.parseInt(days);
        return d > 0 ? System.currentTimeMillis() + d * DAY_MS : 0;
    }

    private void page(Ctx c, int status, String title, String body) throws IOException {
        String flash = FLASH.get(c.q("flash"));
        send(c, status, Html.layout(title, c.user(), c.csrf(), flash, body));
    }

    private void error(Ctx c, int status, String heading, String text) throws IOException {
        page(c, status, heading, Html.message(heading, text));
    }

    private void send(Ctx c, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        var h = c.ex().getResponseHeaders();
        h.set("Content-Type", "text/html; charset=utf-8");
        h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "same-origin");
        // No scripts, images or frames are used anywhere, so everything except inline CSS is blocked.
        h.set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
        c.ex().sendResponseHeaders(status, bytes.length);
        try (OutputStream os = c.ex().getResponseBody()) {
            os.write(bytes);
        }
    }

    private void redirect(Ctx c, String location) throws IOException {
        c.ex().getResponseHeaders().set("Location", location);
        c.ex().sendResponseHeaders(303, -1);
    }

    private static void fallback(HttpExchange ex, int status, String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException | RuntimeException ignored) {
            // response was already started - nothing more we can do
        }
    }
}
