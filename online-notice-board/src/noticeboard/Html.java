package noticeboard;

import java.util.List;

/** Builds every HTML page. All user text is escaped with Util.esc before it is written. */
final class Html {
    private Html() {}

    /** Values of the "post / edit notice" form. */
    record NoticeForm(String id, String title, String content, String category, String priority, String days) {}

    private static final String CSS = """
            :root{--bg:#f3f5fa;--card:#ffffff;--ink:#1f2937;--muted:#6b7280;--brand:#2455d6;--line:#e3e7ef;--danger:#d62d2d;--warn:#c76a00}
            @media (prefers-color-scheme:dark){:root{--bg:#10141c;--card:#1a2030;--ink:#e6e9f0;--muted:#98a2b3;--brand:#4b7bff;--line:#2a3247;--danger:#e04848;--warn:#ffb454}}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,Arial,sans-serif}
            .wrap{max-width:920px;margin:0 auto;padding:0 16px}
            header{background:var(--brand);color:#fff;box-shadow:0 2px 8px rgba(0,0,0,.15)}
            nav{display:flex;align-items:center;gap:16px;flex-wrap:wrap;padding:12px 0}
            nav a{color:#fff;text-decoration:none;font-weight:500}
            nav a:hover{text-decoration:underline}
            nav .brand{font-size:1.2rem;font-weight:700;margin-right:auto}
            nav form{margin:0}
            main{padding-top:20px;padding-bottom:40px}
            h1{margin:8px 0 16px;font-size:1.6rem}
            h2{margin:0 0 6px;font-size:1.2rem}
            a{color:var(--brand)}
            .card{background:var(--card);border:1px solid var(--line);border-left:5px solid var(--line);border-radius:10px;padding:16px 18px;margin:0 0 14px}
            .card.prio-important{border-left-color:var(--warn)}
            .card.prio-urgent{border-left-color:var(--danger)}
            .card.expired{opacity:.65}
            .card h2 a{text-decoration:none;color:var(--ink)}
            .card h2 a:hover{color:var(--brand)}
            .meta{display:flex;flex-wrap:wrap;gap:6px 14px;align-items:center;color:var(--muted);font-size:.88rem;margin-bottom:8px}
            .badge{display:inline-block;padding:1px 9px;border-radius:999px;border:1px solid var(--line);font-size:.78rem;font-weight:600;color:var(--ink)}
            .badge.p-important{color:var(--warn);border-color:var(--warn)}
            .badge.p-urgent{color:var(--danger);border-color:var(--danger)}
            .badge.exp{color:var(--muted)}
            .badge.admin{background:#fff;color:#2455d6;border-color:#fff}
            .preview{margin:0 0 10px}
            .content{white-space:pre-wrap;overflow-wrap:anywhere;margin:12px 0 16px}
            .actions{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
            .actions form{margin:0}
            .btn{display:inline-block;border:1px solid transparent;border-radius:8px;padding:8px 14px;background:var(--brand);color:#fff;font:inherit;font-weight:600;font-size:.92rem;cursor:pointer;text-decoration:none}
            .btn:hover{filter:brightness(1.1);text-decoration:none}
            .btn-light{background:transparent;color:var(--brand);border-color:var(--brand)}
            nav .btn-light{color:#fff;border-color:#fff;padding:4px 12px}
            .btn-danger{background:var(--danger);color:#fff}
            .search{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:10px}
            input,select,textarea{font:inherit;color:var(--ink);background:var(--card);border:1px solid var(--line);border-radius:8px;padding:9px 11px;width:100%}
            .search input,.search select{width:auto}
            .search input[type=search]{flex:1 1 220px}
            input:focus,select:focus,textarea:focus{outline:2px solid var(--brand);outline-offset:1px}
            label{display:block;font-weight:600;margin-bottom:14px}
            label input,label select,label textarea{display:block;margin-top:6px;font-weight:400}
            .form{max-width:640px}
            .muted{color:var(--muted)}
            .empty{text-align:center;color:var(--muted)}
            .flash{background:#e8f7ee;color:#0f5c2c;border:1px solid #b6e3c6;border-radius:8px;padding:10px 14px;margin-bottom:16px}
            .error{background:#fdeaea;color:#8f1d1d;border:1px solid #f3bcbc;border-radius:8px;padding:10px 14px;margin-bottom:16px}
            @media (prefers-color-scheme:dark){.flash{background:#12301f;color:#9be6b8;border-color:#1f5a38}.error{background:#3a1717;color:#ffb4b4;border-color:#6b2a2a}}
            footer{text-align:center;color:var(--muted);font-size:.85rem;padding-bottom:28px}
            """;

    // ---------- page layout ----------

    static String layout(String title, User user, String csrf, String flash, String body) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(Util.esc(title)).append(" - Online Notice Board</title>")
                .append("<style>").append(CSS).append("</style></head><body>");
        sb.append("<header><div class=\"wrap\"><nav>")
                .append("<a class=\"brand\" href=\"/\">&#128204; Online Notice Board</a>")
                .append("<a href=\"/\">Home</a>");
        if (user != null) {
            sb.append("<a href=\"/notice/new\">Post Notice</a>")
                    .append("<span>").append(Util.esc(user.username()));
            if (user.isAdmin()) sb.append(" <span class=\"badge admin\">Admin</span>");
            sb.append("</span>")
                    .append("<form method=\"post\" action=\"/logout\">").append(csrfField(csrf))
                    .append("<button class=\"btn btn-light\">Logout</button></form>");
        } else {
            sb.append("<a href=\"/login\">Login</a><a href=\"/register\">Register</a>");
        }
        sb.append("</nav></div></header><main class=\"wrap\">");
        if (flash != null) sb.append("<div class=\"flash\">").append(Util.esc(flash)).append("</div>");
        sb.append(body);
        sb.append("</main><footer class=\"wrap\">Online Notice Board &middot; built with pure Java</footer>")
                .append("</body></html>");
        return sb.toString();
    }

    // ---------- notice pages ----------

    static String board(List<Notice> items, User user, String csrf, String q, String cat, long now) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<h1>Notice Board</h1>");
        sb.append("<form class=\"search\" method=\"get\" action=\"/\">")
                .append("<input type=\"search\" name=\"q\" maxlength=\"100\" placeholder=\"Search notices...\" value=\"")
                .append(Util.esc(q)).append("\">")
                .append("<select name=\"cat\"><option value=\"\">All categories</option>");
        options(sb, Server.CATEGORIES, cat);
        sb.append("</select><button class=\"btn\">Search</button>");
        if (!q.isEmpty() || !cat.isEmpty()) sb.append("<a class=\"btn btn-light\" href=\"/\">Clear</a>");
        sb.append("</form>");
        sb.append("<p class=\"muted\">").append(items.size()).append(items.size() == 1 ? " notice" : " notices")
                .append("</p>");
        if (items.isEmpty()) {
            sb.append("<div class=\"card empty\">No notices found.</div>");
        }
        for (Notice n : items) {
            boolean expired = n.expired(now);
            sb.append("<article class=\"card prio-").append(n.priority().toLowerCase())
                    .append(expired ? " expired" : "").append("\"><h2>");
            if (n.pinned()) sb.append("<span title=\"Pinned\">&#128204;</span> ");
            sb.append("<a href=\"/notice/view?id=").append(Util.enc(n.id())).append("\">")
                    .append(Util.esc(n.title())).append("</a></h2>");
            meta(sb, n, expired);
            sb.append("<p class=\"preview\">").append(Util.esc(preview(n.content(), 220))).append("</p>");
            actions(sb, n, user, csrf, false);
            sb.append("</article>");
        }
        return sb.toString();
    }

    static String detail(Notice n, User user, String csrf, long now) {
        StringBuilder sb = new StringBuilder(2048);
        boolean expired = n.expired(now);
        sb.append("<article class=\"card prio-").append(n.priority().toLowerCase())
                .append(expired ? " expired" : "").append("\"><h1>");
        if (n.pinned()) sb.append("<span title=\"Pinned\">&#128204;</span> ");
        sb.append(Util.esc(n.title())).append("</h1>");
        meta(sb, n, expired);
        sb.append("<div class=\"content\">").append(Util.esc(n.content())).append("</div>");
        actions(sb, n, user, csrf, true);
        sb.append("</article><p><a href=\"/\">&larr; Back to the notice board</a></p>");
        return sb.toString();
    }

    static String noticeForm(NoticeForm f, String csrf, String error) {
        boolean edit = f.id() != null && !f.id().isEmpty();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<h1>").append(edit ? "Edit Notice" : "Post a New Notice").append("</h1>");
        if (error != null) sb.append(errorBox(error));
        sb.append("<form class=\"card form\" method=\"post\" action=\"")
                .append(edit ? "/notice/edit" : "/notice/new").append("\">")
                .append(csrfField(csrf));
        if (edit) sb.append("<input type=\"hidden\" name=\"id\" value=\"").append(Util.esc(f.id())).append("\">");
        sb.append("<label>Title<input name=\"title\" maxlength=\"100\" required value=\"")
                .append(Util.esc(f.title())).append("\"></label>");
        sb.append("<label>Category<select name=\"category\">");
        options(sb, Server.CATEGORIES, f.category());
        sb.append("</select></label>");
        sb.append("<label>Priority<select name=\"priority\">");
        options(sb, Server.PRIORITIES, f.priority());
        sb.append("</select></label>");
        sb.append("<label>Expire after (days, 0 = never expires)")
                .append("<input type=\"number\" name=\"days\" min=\"0\" max=\"365\" value=\"")
                .append(Util.esc(f.days())).append("\"></label>");
        sb.append("<label>Content<textarea name=\"content\" rows=\"8\" maxlength=\"2000\" required>")
                .append(Util.esc(f.content())).append("</textarea></label>");
        sb.append("<div class=\"actions\"><button class=\"btn\">").append(edit ? "Save changes" : "Publish")
                .append("</button><a class=\"btn btn-light\" href=\"/\">Cancel</a></div></form>");
        return sb.toString();
    }

    static String confirmDelete(Notice n, String csrf) {
        return "<h1>Delete notice?</h1>"
                + "<form class=\"card form\" method=\"post\" action=\"/notice/delete\">"
                + csrfField(csrf)
                + "<input type=\"hidden\" name=\"id\" value=\"" + Util.esc(n.id()) + "\">"
                + "<p>This will permanently delete <strong>" + Util.esc(n.title()) + "</strong>.</p>"
                + "<div class=\"actions\"><button class=\"btn btn-danger\">Yes, delete</button>"
                + "<a class=\"btn btn-light\" href=\"/notice/view?id=" + Util.enc(n.id()) + "\">Cancel</a></div>"
                + "</form>";
    }

    // ---------- account pages ----------

    static String loginForm(String error, String username) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<h1>Login</h1>");
        if (error != null) sb.append(errorBox(error));
        sb.append("<form class=\"card form\" method=\"post\" action=\"/login\">")
                .append("<label>Username<input name=\"username\" required maxlength=\"20\" ")
                .append("autocomplete=\"username\" value=\"").append(Util.esc(username)).append("\"></label>")
                .append("<label>Password<input type=\"password\" name=\"password\" required maxlength=\"100\" ")
                .append("autocomplete=\"current-password\"></label>")
                .append("<div class=\"actions\"><button class=\"btn\">Login</button>")
                .append("<a href=\"/register\">Create an account</a></div></form>");
        return sb.toString();
    }

    static String registerForm(String error, String username) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<h1>Create an account</h1>");
        if (error != null) sb.append(errorBox(error));
        sb.append("<form class=\"card form\" method=\"post\" action=\"/register\">")
                .append("<label>Username (3-20 letters, numbers or _)")
                .append("<input name=\"username\" required minlength=\"3\" maxlength=\"20\" ")
                .append("autocomplete=\"username\" value=\"").append(Util.esc(username)).append("\"></label>")
                .append("<label>Password (at least 6 characters)")
                .append("<input type=\"password\" name=\"password\" required minlength=\"6\" maxlength=\"100\" ")
                .append("autocomplete=\"new-password\"></label>")
                .append("<label>Confirm password")
                .append("<input type=\"password\" name=\"confirm\" required minlength=\"6\" maxlength=\"100\" ")
                .append("autocomplete=\"new-password\"></label>")
                .append("<div class=\"actions\"><button class=\"btn\">Register</button>")
                .append("<a href=\"/login\">I already have an account</a></div></form>");
        return sb.toString();
    }

    static String message(String heading, String text) {
        return "<div class=\"card\"><h1>" + Util.esc(heading) + "</h1><p>" + Util.esc(text) + "</p>"
                + "<p><a href=\"/\">&larr; Back to the notice board</a></p></div>";
    }

    // ---------- small helpers ----------

    private static void meta(StringBuilder sb, Notice n, boolean expired) {
        sb.append("<div class=\"meta\"><span class=\"badge\">").append(Util.esc(n.category())).append("</span>");
        if (!"Normal".equals(n.priority())) {
            sb.append("<span class=\"badge p-").append(n.priority().toLowerCase()).append("\">")
                    .append(Util.esc(n.priority())).append("</span>");
        }
        if (expired) sb.append("<span class=\"badge exp\">Expired</span>");
        sb.append("<span>By ").append(Util.esc(n.author())).append("</span>")
                .append("<span>").append(Util.date(n.created())).append("</span>");
        if (n.expires() > 0 && !expired) {
            sb.append("<span>Expires ").append(Util.date(n.expires())).append("</span>");
        }
        sb.append("</div>");
    }

    private static void actions(StringBuilder sb, Notice n, User user, String csrf, boolean detail) {
        String id = Util.enc(n.id());
        sb.append("<div class=\"actions\">");
        if (!detail) {
            sb.append("<a class=\"btn btn-light\" href=\"/notice/view?id=").append(id).append("\">Read more</a>");
        }
        if (Server.canManage(user, n)) {
            sb.append("<a class=\"btn btn-light\" href=\"/notice/edit?id=").append(id).append("\">Edit</a>")
                    .append("<a class=\"btn btn-danger\" href=\"/notice/delete?id=").append(id).append("\">Delete</a>");
        }
        if (user != null && user.isAdmin()) {
            sb.append("<form method=\"post\" action=\"/notice/pin\">").append(csrfField(csrf))
                    .append("<input type=\"hidden\" name=\"id\" value=\"").append(Util.esc(n.id())).append("\">")
                    .append("<button class=\"btn btn-light\">").append(n.pinned() ? "Unpin" : "Pin")
                    .append("</button></form>");
        }
        sb.append("</div>");
    }

    private static void options(StringBuilder sb, List<String> values, String selected) {
        for (String v : values) {
            sb.append("<option value=\"").append(Util.esc(v)).append("\"")
                    .append(v.equals(selected) ? " selected" : "").append(">").append(Util.esc(v)).append("</option>");
        }
    }

    private static String csrfField(String csrf) {
        return "<input type=\"hidden\" name=\"csrf\" value=\"" + Util.esc(csrf) + "\">";
    }

    private static String errorBox(String error) {
        return "<div class=\"error\">" + Util.esc(error) + "</div>";
    }

    private static String preview(String text, int max) {
        String flat = text.replace('\n', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max).trim() + "...";
    }
}
