/**
 * HTML rendering helpers: page layout, escaping, role-aware navigation,
 * and shared CSS. Keeps WebServer.java focused on routes; visual styling
 * lives here.
 */
public class Html {

    /** Escape user-provided text so it is safe to embed in HTML. */
    public static String esc(Object o) {
        if (o == null) return "";
        String s = o.toString();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;"); break;
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Wrap inner HTML in the shared page layout.
     * @param user current logged-in user, or null if not logged in
     */
    public static String page(String title, String body, User user) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "  <title>" + esc(title) + " · MatchTicket</title>\n"
                + "  <link rel=\"icon\" href=\"" + favicon() + "\">\n"
                + "  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n"
                + "  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n"
                + "  <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap\" rel=\"stylesheet\">\n"
                + "  <style>" + css() + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <header class=\"site-header\">\n"
                + "    <div class=\"site-header-inner\">\n"
                + "      <a class=\"brand\" href=\"" + brandHref(user) + "\">"
                +        logoSvg()
                +        "<span class=\"brand-name\">MatchTicket</span>"
                +      "</a>\n"
                +      nav(user)
                + "    </div>\n"
                + "  </header>\n"
                + "  <main class=\"site-main\">\n"
                +      body + "\n"
                + "  </main>\n"
                + "  <footer class=\"site-footer\">\n"
                + "    <div>MatchTicket &middot; Football match ticket booking</div>\n"
                + "    <div class=\"muted\">Java course project &copy; 2026</div>\n"
                + "  </footer>\n"
                + "</body>\n"
                + "</html>";
    }

    private static String brandHref(User user) {
        return user == null ? "/login" : "/";
    }

    /** Role-aware navigation. */
    private static String nav(User user) {
        StringBuilder nav = new StringBuilder("<nav class=\"site-nav\">");
        if (user == null) {
            nav.append("<a href=\"/login\">Login</a>");
            nav.append("<a href=\"/register\">Register</a>");
        } else if (user.isAdmin()) {
            nav.append("<a href=\"/\">Home</a>");
            nav.append("<a href=\"/customers\">Customers</a>");
            nav.append("<a href=\"/teams\">Teams</a>");
            nav.append("<a href=\"/matches\">Matches</a>");
            nav.append("<a href=\"/tickets\">Tickets</a>");
            nav.append("<a href=\"/users\">Users</a>");
            nav.append("<a href=\"/reports\">Reports</a>");
            nav.append(userBadge(user, "ADMIN"));
            nav.append("<a class=\"nav-logout\" href=\"/logout\">Logout</a>");
        } else { // CLIENT
            nav.append("<a href=\"/\">Home</a>");
            nav.append("<a href=\"/matches\">Matches</a>");
            nav.append("<a href=\"/tickets/book\">Book ticket</a>");
            nav.append("<a href=\"/my-tickets\">My tickets</a>");
            nav.append("<a href=\"/profile\">My profile</a>");
            nav.append(userBadge(user, "CLIENT"));
            nav.append("<a class=\"nav-logout\" href=\"/logout\">Logout</a>");
        }
        nav.append("</nav>");
        return nav.toString();
    }

    private static String userBadge(User user, String roleLabel) {
        return "<span class=\"user-badge\">"
             + "<span class=\"role-pill\">" + esc(roleLabel) + "</span>"
             + esc(user.getUsername())
             + "</span>";
    }

    /** Page heading used inside the white content card. */
    public static String heading(String title) {
        return "<h2 class=\"page-title\">" + esc(title) + "</h2>";
    }

    /** Render a flash message banner if msg is not blank. */
    public static String flash(String msg, boolean error) {
        if (msg == null || msg.isBlank()) return "";
        String cls = error ? "flash error" : "flash ok";
        String icon = error ? "&#9888;" : "&#10003;";
        return "<div class=\"" + cls + "\"><span class=\"flash-icon\">"
                + icon + "</span>" + esc(msg) + "</div>";
    }

    /** Coloured pill showing seat availability. */
    public static String availabilityBadge(int available, int total) {
        if (available <= 0) {
            return "<span class=\"badge red\">Sold out</span>";
        } else if (available <= Math.max(1, total / 5)) {
            return "<span class=\"badge amber\">Few seats left</span>";
        } else {
            return "<span class=\"badge green\">Available</span>";
        }
    }

    /** SVG football/soccer ball logo for the header. */
    private static String logoSvg() {
        return "<svg class=\"logo\" viewBox=\"0 0 32 32\" xmlns=\"http://www.w3.org/2000/svg\" "
             + "fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.6\" "
             + "stroke-linecap=\"round\" stroke-linejoin=\"round\">"
             + "<circle cx=\"16\" cy=\"16\" r=\"13\" fill=\"#fff\" stroke=\"#0f172a\"/>"
             + "<polygon points=\"16,9 21,12 19,18 13,18 11,12\" fill=\"#0f172a\"/>"
             + "<path d=\"M16 3 V9 M16 23 V29 M3 16 H9 M23 16 H29 "
             + "M21 12 L26 9 M11 12 L6 9 M13 18 L9 23 M19 18 L23 23\" stroke=\"#0f172a\"/>"
             + "</svg>";
    }

    /** Inline SVG favicon as a data URL (no extra file needed). */
    private static String favicon() {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'>"
                   + "<circle cx='16' cy='16' r='13' fill='%23fff' stroke='%230f172a' stroke-width='2'/>"
                   + "<polygon points='16,9 21,12 19,18 13,18 11,12' fill='%230f172a'/>"
                   + "</svg>";
        return "data:image/svg+xml," + svg;
    }

    /** Inline CSS for the whole site. */
    private static String css() {
        return ""
            + ":root{"
            + "--navy:#0f172a;--navy-2:#1e293b;"
            + "--emerald:#0f766e;--emerald-2:#0d9488;"
            + "--amber:#f59e0b;--amber-2:#d97706;"
            + "--green:#10b981;--red:#ef4444;"
            + "--bg:#f1f5f9;--card:#ffffff;"
            + "--text:#0f172a;--muted:#64748b;--line:#e2e8f0;"
            + "--shadow-sm:0 1px 2px rgba(15,23,42,.06);"
            + "--shadow:0 6px 24px -8px rgba(15,23,42,.18);"
            + "--shadow-lg:0 12px 40px -10px rgba(15,23,42,.25);"
            + "--radius:14px;--radius-sm:10px;"
            + "}"
            + "*,*::before,*::after{box-sizing:border-box;}"
            + "html,body{margin:0;padding:0;}"
            + "body{font-family:'Inter',-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;"
                + "background:var(--bg);color:var(--text);min-height:100vh;display:flex;flex-direction:column;"
                + "-webkit-font-smoothing:antialiased;}"
            + "a{color:inherit;}"
            + "h1,h2,h3{margin:0;}"
            + ".muted{color:var(--muted);}"
            + ".site-header{background:linear-gradient(135deg,#0f172a 0%,#0b3b3a 60%,#0f766e 100%);"
                + "color:#fff;box-shadow:var(--shadow);position:sticky;top:0;z-index:10;}"
            + ".site-header-inner{max-width:1100px;margin:0 auto;padding:18px 24px;"
                + "display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;}"
            + ".brand{display:flex;align-items:center;gap:10px;color:#fff;text-decoration:none;font-weight:800;}"
            + ".brand-name{font-size:18px;letter-spacing:.3px;}"
            + ".logo{width:30px;height:30px;color:#fff;}"
            + ".site-nav{display:flex;gap:6px;flex-wrap:wrap;align-items:center;}"
            + ".site-nav a{color:rgba(255,255,255,.85);text-decoration:none;font-weight:500;font-size:14px;"
                + "padding:8px 14px;border-radius:999px;transition:background .2s ease,color .2s ease;}"
            + ".site-nav a:hover{background:rgba(255,255,255,.12);color:#fff;}"
            + ".site-nav a.nav-logout{background:rgba(239,68,68,.18);color:#fecaca;}"
            + ".site-nav a.nav-logout:hover{background:rgba(239,68,68,.32);color:#fff;}"
            + ".user-badge{display:inline-flex;align-items:center;gap:8px;padding:6px 12px;"
                + "background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.12);"
                + "border-radius:999px;font-size:13px;color:#fff;font-weight:500;}"
            + ".role-pill{font-size:10px;font-weight:700;letter-spacing:.08em;padding:2px 8px;"
                + "background:var(--amber);color:#0f172a;border-radius:999px;}"
            + ".site-main{max-width:1100px;width:100%;margin:28px auto;padding:0 24px;flex:1;}"
            + ".site-footer{max-width:1100px;margin:32px auto 24px;padding:16px 24px;"
                + "display:flex;justify-content:space-between;flex-wrap:wrap;gap:8px;font-size:13px;color:var(--muted);}"
            + ".panel{background:var(--card);border-radius:var(--radius);padding:28px;"
                + "box-shadow:var(--shadow-sm);border:1px solid var(--line);}"
            + ".panel.narrow{max-width:460px;margin:40px auto;}"
            + ".page-title{margin-bottom:18px;font-size:24px;font-weight:700;letter-spacing:-.01em;}"
            + ".hero{position:relative;border-radius:var(--radius);overflow:hidden;"
                + "background:linear-gradient(135deg,#0f172a 0%,#0b3b3a 50%,#0f766e 100%);"
                + "color:#fff;padding:48px 36px;margin-bottom:24px;box-shadow:var(--shadow-lg);}"
            + ".hero::after{content:'';position:absolute;inset:0;pointer-events:none;"
                + "background:radial-gradient(800px circle at 90% -10%,rgba(245,158,11,.18),transparent 40%),"
                + "radial-gradient(600px circle at 10% 110%,rgba(16,185,129,.18),transparent 40%);}"
            + ".hero-eyebrow{display:inline-block;background:rgba(255,255,255,.12);color:#fff;"
                + "padding:6px 14px;border-radius:999px;font-size:12px;font-weight:600;letter-spacing:.08em;"
                + "text-transform:uppercase;margin-bottom:18px;}"
            + ".hero h1{font-size:42px;font-weight:800;line-height:1.1;letter-spacing:-.02em;margin-bottom:14px;max-width:680px;}"
            + ".hero h1 .accent{color:var(--amber);}"
            + ".hero p{margin:0 0 24px;color:rgba(255,255,255,.78);font-size:16px;max-width:560px;}"
            + ".hero-actions{display:flex;gap:10px;flex-wrap:wrap;position:relative;z-index:1;}"
            + ".stat-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:14px;margin-top:24px;}"
            + ".stat{background:var(--card);border:1px solid var(--line);border-radius:var(--radius-sm);"
                + "padding:18px;transition:transform .15s ease,box-shadow .15s ease;text-decoration:none;color:inherit;display:block;}"
            + ".stat:hover{transform:translateY(-2px);box-shadow:var(--shadow);}"
            + ".stat-label{font-size:13px;color:var(--muted);font-weight:500;}"
            + ".stat-value{font-size:32px;font-weight:800;color:var(--navy);margin-top:6px;letter-spacing:-.02em;}"
            + ".btn{display:inline-flex;align-items:center;gap:6px;background:var(--navy);color:#fff;border:none;"
                + "padding:10px 18px;border-radius:var(--radius-sm);cursor:pointer;font-size:14px;font-weight:600;"
                + "text-decoration:none;transition:background .15s ease,transform .15s ease,box-shadow .15s ease;"
                + "font-family:inherit;}"
            + ".btn:hover{background:var(--navy-2);transform:translateY(-1px);box-shadow:var(--shadow-sm);}"
            + ".btn.primary{background:var(--amber);color:#0f172a;}"
            + ".btn.primary:hover{background:var(--amber-2);color:#0f172a;}"
            + ".btn.secondary{background:#e2e8f0;color:var(--navy);}"
            + ".btn.secondary:hover{background:#cbd5e1;}"
            + ".btn.danger{background:var(--red);color:#fff;}"
            + ".btn.danger:hover{background:#b91c1c;}"
            + ".btn.ghost{background:transparent;color:#fff;border:1px solid rgba(255,255,255,.4);}"
            + ".btn.ghost:hover{background:rgba(255,255,255,.12);}"
            + ".btn.sm{padding:6px 12px;font-size:13px;}"
            + ".btn.block{display:flex;width:100%;justify-content:center;}"
            + "table{width:100%;border-collapse:separate;border-spacing:0;margin:16px 0;}"
            + "thead th{background:#f8fafc;color:var(--muted);font-size:12px;font-weight:600;"
                + "text-transform:uppercase;letter-spacing:.06em;text-align:left;padding:12px 14px;"
                + "border-bottom:1px solid var(--line);}"
            + "tbody td{padding:14px;border-bottom:1px solid var(--line);font-size:14px;}"
            + "tbody tr:hover{background:#f8fafc;}"
            + "tbody tr:last-child td{border-bottom:none;}"
            + "label{display:block;margin:14px 0 6px;font-weight:600;font-size:13px;color:var(--navy);}"
            + "input[type=text],input[type=email],input[type=number],input[type=tel],"
            + "input[type=password],input[type=datetime-local],select,textarea{width:100%;max-width:480px;padding:10px 12px;"
                + "border:1px solid var(--line);border-radius:var(--radius-sm);font-size:14px;"
                + "background:#fff;color:var(--text);font-family:inherit;"
                + "transition:border-color .15s ease,box-shadow .15s ease;}"
            + "input:focus,select:focus,textarea:focus{outline:none;border-color:var(--emerald);"
                + "box-shadow:0 0 0 3px rgba(15,118,110,.15);}"
            + "form.inline{display:inline;}"
            + ".form-actions{margin-top:22px;display:flex;gap:8px;flex-wrap:wrap;}"
            + ".flash{display:flex;align-items:center;gap:10px;padding:12px 16px;border-radius:var(--radius-sm);"
                + "margin-bottom:16px;font-size:14px;font-weight:500;}"
            + ".flash.ok{background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0;}"
            + ".flash.error{background:#fef2f2;color:#991b1b;border:1px solid #fecaca;}"
            + ".flash-icon{font-size:18px;}"
            + ".badge{display:inline-block;padding:4px 10px;border-radius:999px;font-size:12px;"
                + "font-weight:600;letter-spacing:.02em;}"
            + ".badge.green{background:#d1fae5;color:#065f46;}"
            + ".badge.amber{background:#fef3c7;color:#92400e;}"
            + ".badge.red{background:#fee2e2;color:#991b1b;}"
            + ".badge.navy{background:#e2e8f0;color:#0f172a;}"
            + ".match-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:18px;margin-top:18px;}"
            + ".ticket{position:relative;background:var(--card);border-radius:var(--radius);"
                + "border:1px solid var(--line);padding:22px;display:flex;flex-direction:column;gap:14px;"
                + "transition:transform .15s ease,box-shadow .15s ease;overflow:hidden;}"
            + ".ticket:hover{transform:translateY(-3px);box-shadow:var(--shadow);}"
            + ".ticket::before{content:'';position:absolute;top:50%;left:-12px;width:24px;height:24px;"
                + "background:var(--bg);border-radius:50%;transform:translateY(-50%);}"
            + ".ticket::after{content:'';position:absolute;top:50%;right:-12px;width:24px;height:24px;"
                + "background:var(--bg);border-radius:50%;transform:translateY(-50%);}"
            + ".ticket-head{display:flex;justify-content:space-between;align-items:flex-start;gap:8px;}"
            + ".ticket-id{font-size:11px;color:var(--muted);letter-spacing:.1em;text-transform:uppercase;}"
            + ".ticket-teams{display:flex;align-items:center;gap:14px;font-weight:700;font-size:18px;"
                + "letter-spacing:-.01em;color:var(--navy);}"
            + ".ticket-vs{font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.15em;"
                + "background:#f1f5f9;padding:3px 8px;border-radius:6px;font-weight:600;}"
            + ".ticket-meta{display:flex;gap:18px;flex-wrap:wrap;color:var(--muted);font-size:13px;}"
            + ".ticket-meta .label{font-weight:600;color:var(--navy);}"
            + ".ticket-foot{display:flex;justify-content:space-between;align-items:center;border-top:1px dashed var(--line);"
                + "padding-top:14px;margin-top:auto;}"
            + ".ticket-price{font-size:22px;font-weight:800;color:var(--navy);letter-spacing:-.02em;}"
            + ".ticket-price small{font-size:11px;font-weight:500;color:var(--muted);text-transform:uppercase;letter-spacing:.08em;}"
            + ".empty{text-align:center;padding:48px 20px;color:var(--muted);}"
            + ".empty-icon{font-size:48px;margin-bottom:8px;opacity:.5;}"
            + ".empty h3{color:var(--navy);font-weight:700;font-size:18px;margin-bottom:4px;}"
            + ".auth-hint{margin-top:18px;text-align:center;font-size:14px;color:var(--muted);}"
            + ".auth-hint a{color:var(--emerald);font-weight:600;text-decoration:none;}"
            + ".auth-hint a:hover{text-decoration:underline;}"
            + "@media (max-width:640px){.hero{padding:32px 24px;}.hero h1{font-size:30px;}"
                + ".site-header-inner{padding:14px 16px;}.site-main{padding:0 16px;}.panel{padding:18px;}"
                + ".site-nav a{padding:6px 10px;font-size:13px;}"
                + ".ticket-teams{font-size:16px;}}";
    }
}
