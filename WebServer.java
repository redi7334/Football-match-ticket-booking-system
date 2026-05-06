import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP server with role-based access control.
 *
 * Public routes:   /login, /register
 * Authenticated:   /, /matches (browse), /tickets/book, /my-tickets, /profile, /logout
 * Admin only:      /customers, /teams, /matches/new|edit|delete,
 *                  /tickets (all bookings), /users, /reports
 */
public class WebServer {

    private static final String SESSION_COOKIE = "MTSESSION";

    private final BookingSystem system;
    private final AuthService auth;
    private final int port;

    private static final DateTimeFormatter DT_DISPLAY =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter DT_INPUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public WebServer(BookingSystem system, AuthService auth, int port) {
        this.system = system;
        this.auth = auth;
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/",          this::home);
        server.createContext("/login",     this::login);
        server.createContext("/register",  this::register);
        server.createContext("/logout",    this::logout);
        server.createContext("/customers", this::customers);
        server.createContext("/teams",     this::teams);
        server.createContext("/matches",   this::matches);
        server.createContext("/tickets",   this::tickets);
        server.createContext("/my-tickets",this::myTickets);
        server.createContext("/profile",   this::profile);
        server.createContext("/users",     this::users);
        server.createContext("/reports",   this::reports);

        server.setExecutor(null);
        server.start();

        System.out.println("Server running at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    // ============================================================
    // HOME (different per role)
    // ============================================================

    private void home(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            sendNotFound(ex, currentUser(ex));
            return;
        }
        User user = currentUser(ex);
        if (user == null) { redirect(ex, "/login"); return; }

        if (user.isAdmin()) {
            sendHtml(ex, adminHomePage(user));
        } else {
            sendHtml(ex, clientHomePage(user));
        }
    }

    private String adminHomePage(User user) {
        int customers = system.getAllCustomers().size();
        int teams = system.getAllTeams().size();
        int upcoming = system.getAvailableMatches().size();
        int sold = system.getAllTickets().size();
        double revenue = system.getTotalRevenue();

        String body =
                  "<section class=\"hero\">"
                + "  <span class=\"hero-eyebrow\">Admin dashboard</span>"
                + "  <h1>Welcome back, <span class=\"accent\">" + Html.esc(user.getUsername()) + "</span>.</h1>"
                + "  <p>Manage customers, teams, fixtures, bookings, and users.</p>"
                + "  <div class=\"hero-actions\">"
                + "    <a class=\"btn primary\" href=\"/matches/new\">+ Schedule match</a>"
                + "    <a class=\"btn ghost\" href=\"/reports\">View reports</a>"
                + "  </div>"
                + "</section>"
                + "<div class=\"stat-grid\">"
                + statCard("Customers", customers, "/customers")
                + statCard("Teams", teams, "/teams")
                + statCard("Upcoming matches", upcoming, "/matches")
                + statCard("Tickets sold", sold, "/tickets")
                + statCard("Revenue", String.format("%.0f", revenue), "/reports")
                + "</div>";
        return Html.page("Home", body, user);
    }

    private String clientHomePage(User user) {
        Customer me = currentCustomer(user);
        int myTickets = me == null ? 0 : system.getTicketsForCustomer(me.getId()).size();
        int available = system.getAvailableMatches().size();

        String body =
                  "<section class=\"hero\">"
                + "  <span class=\"hero-eyebrow\">Welcome</span>"
                + "  <h1>Hello, <span class=\"accent\">" + Html.esc(me == null ? user.getUsername() : me.getName()) + "</span>.</h1>"
                + "  <p>Browse upcoming fixtures and book your seat in seconds.</p>"
                + "  <div class=\"hero-actions\">"
                + "    <a class=\"btn primary\" href=\"/tickets/book\">Book a ticket</a>"
                + "    <a class=\"btn ghost\" href=\"/matches\">Browse matches</a>"
                + "  </div>"
                + "</section>"
                + "<div class=\"stat-grid\">"
                + statCard("My tickets", myTickets, "/my-tickets")
                + statCard("Available matches", available, "/matches")
                + "</div>";
        return Html.page("Home", body, user);
    }

    private String statCard(String label, Object value, String href) {
        return "<a class=\"stat\" href=\"" + href + "\">"
             + "<div class=\"stat-label\">" + Html.esc(label) + "</div>"
             + "<div class=\"stat-value\">" + Html.esc(value) + "</div>"
             + "</a>";
    }

    // ============================================================
    // LOGIN
    // ============================================================

    private void login(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        if (path.equals("/login") && method.equals("GET")) {
            sendHtml(ex, loginPage(q.get("msg"), "true".equals(q.get("err")), q.get("next")));
        } else if (path.equals("/login") && method.equals("POST")) {
            Map<String,String> form = parseForm(ex);
            String username = form.getOrDefault("username", "").trim();
            String password = form.getOrDefault("password", "");
            String next = form.getOrDefault("next", "/");
            if (next.isBlank() || !next.startsWith("/")) next = "/";
            String sessionId = auth.login(username, password);
            if (sessionId == null) {
                redirect(ex, "/login?err=true&msg=" + urlEncode("Invalid username or password"));
            } else {
                setSessionCookie(ex, sessionId);
                redirect(ex, next);
            }
        } else {
            sendNotFound(ex, currentUser(ex));
        }
    }

    private String loginPage(String msg, boolean err, String next) {
        String nextField = (next == null || next.isBlank()) ? ""
                : "<input type=\"hidden\" name=\"next\" value=\"" + Html.esc(next) + "\">";
        String body = "<div class=\"panel narrow\">"
                + Html.heading("Sign in")
                + Html.flash(msg, err)
                + "<form method=\"POST\" action=\"/login\">"
                + nextField
                + "<label>Username</label>"
                + "<input type=\"text\" name=\"username\" required autofocus>"
                + "<label>Password</label>"
                + "<input type=\"password\" name=\"password\" required>"
                + "<div class=\"form-actions\">"
                + "<button class=\"btn primary block\" type=\"submit\">Sign in</button>"
                + "</div></form>"
                + "<div class=\"auth-hint\">No account yet? <a href=\"/register\">Create one</a></div>"
                + "</div>";
        return Html.page("Login", body, null);
    }

    // ============================================================
    // REGISTER (clients only — self-signup)
    // ============================================================

    private void register(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        if (path.equals("/register") && method.equals("GET")) {
            sendHtml(ex, registerPage(q.get("msg"), "true".equals(q.get("err"))));
        } else if (path.equals("/register") && method.equals("POST")) {
            Map<String,String> form = parseForm(ex);
            try {
                User u = auth.registerClient(
                        form.getOrDefault("username","").trim(),
                        form.getOrDefault("password",""),
                        form.getOrDefault("name","").trim(),
                        form.getOrDefault("email","").trim(),
                        form.getOrDefault("phone","").trim());
                // Auto-login after register
                String sessionId = auth.login(u.getUsername(), form.getOrDefault("password",""));
                if (sessionId != null) setSessionCookie(ex, sessionId);
                redirect(ex, "/");
            } catch (Exception e) {
                redirect(ex, "/register?err=true&msg=" + urlEncode(e.getMessage()));
            }
        } else {
            sendNotFound(ex, currentUser(ex));
        }
    }

    private String registerPage(String msg, boolean err) {
        String body = "<div class=\"panel narrow\">"
                + Html.heading("Create your account")
                + Html.flash(msg, err)
                + "<form method=\"POST\" action=\"/register\">"
                + "<label>Username</label><input type=\"text\" name=\"username\" required autofocus>"
                + "<label>Password</label><input type=\"password\" name=\"password\" required minlength=\"4\">"
                + "<label>Full name</label><input type=\"text\" name=\"name\" required>"
                + "<label>Email</label><input type=\"email\" name=\"email\" required>"
                + "<label>Phone</label><input type=\"tel\" name=\"phone\" required>"
                + "<div class=\"form-actions\">"
                + "<button class=\"btn primary block\" type=\"submit\">Create account</button>"
                + "</div></form>"
                + "<div class=\"auth-hint\">Already have an account? <a href=\"/login\">Sign in</a></div>"
                + "</div>";
        return Html.page("Register", body, null);
    }

    // ============================================================
    // LOGOUT
    // ============================================================

    private void logout(HttpExchange ex) throws IOException {
        String sessionId = readCookie(ex, SESSION_COOKIE);
        if (sessionId != null) auth.logout(sessionId);
        clearSessionCookie(ex);
        redirect(ex, "/login?msg=" + urlEncode("You have been signed out"));
    }

    // ============================================================
    // CUSTOMERS (admin only)
    // ============================================================

    private void customers(HttpExchange ex) throws IOException {
        User user = requireAdmin(ex); if (user == null) return;
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        try {
            if (path.equals("/customers") && method.equals("GET")) {
                sendHtml(ex, customersListPage(user, q.get("msg"), "true".equals(q.get("err"))));
            } else if (path.equals("/customers/new") && method.equals("GET")) {
                sendHtml(ex, customerFormPage(user, null, false));
            } else if (path.equals("/customers/create") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                system.registerCustomer(form.getOrDefault("name",""),
                        form.getOrDefault("email",""), form.getOrDefault("phone",""));
                redirect(ex, "/customers?msg=Customer+registered");
            } else if (path.equals("/customers/edit") && method.equals("GET")) {
                Customer c = system.findCustomerById(parseInt(q.get("id"), -1));
                if (c == null) { redirect(ex, "/customers?msg=Customer+not+found&err=true"); return; }
                sendHtml(ex, customerFormPage(user, c, true));
            } else if (path.equals("/customers/update") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                boolean ok = system.updateCustomer(id, form.get("name"), form.get("email"), form.get("phone"));
                redirect(ex, ok ? "/customers?msg=Customer+updated"
                                : "/customers?msg=Customer+not+found&err=true");
            } else if (path.equals("/customers/delete") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                // also clean up any user account linked to this customer
                auth.removeUserByCustomerId(id);
                boolean ok = system.removeCustomer(id);
                redirect(ex, ok ? "/customers?msg=Customer+removed"
                                : "/customers?msg=Customer+not+found&err=true");
            } else {
                sendNotFound(ex, user);
            }
        } catch (Exception e) {
            redirect(ex, "/customers?msg=" + urlEncode("Error: " + e.getMessage()) + "&err=true");
        }
    }

    private String customersListPage(User user, String msg, boolean err) {
        StringBuilder rows = new StringBuilder();
        for (Customer c : system.getAllCustomers()) {
            User linked = findUserByCustomerId(c.getId());
            String userBadge = linked == null
                    ? "<span class=\"badge navy\">No login</span>"
                    : "<span class=\"badge green\">@" + Html.esc(linked.getUsername()) + "</span>";
            rows.append("<tr>")
                .append("<td>#").append(c.getId()).append("</td>")
                .append("<td><strong>").append(Html.esc(c.getName())).append("</strong></td>")
                .append("<td>").append(Html.esc(c.getEmail())).append("</td>")
                .append("<td>").append(Html.esc(c.getPhone())).append("</td>")
                .append("<td>").append(userBadge).append("</td>")
                .append("<td>")
                .append("<a class=\"btn secondary sm\" href=\"/customers/edit?id=").append(c.getId()).append("\">Edit</a> ")
                .append("<form class=\"inline\" method=\"POST\" action=\"/customers/delete\" "
                      + "onsubmit=\"return confirm('Delete customer?');\">")
                .append("<input type=\"hidden\" name=\"id\" value=\"").append(c.getId()).append("\">")
                .append("<button class=\"btn danger sm\" type=\"submit\">Delete</button>")
                .append("</form></td></tr>");
        }
        String table = rows.length() == 0
                ? emptyState("No customers yet", "Register your first customer to get started.", "/customers/new", "Register customer")
                : "<table><thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Phone</th><th>Account</th><th>Actions</th></tr></thead>"
                  + "<tbody>" + rows + "</tbody></table>";
        String body = "<div class=\"panel\">"
                + Html.heading("Customers")
                + Html.flash(msg, err)
                + "<a class=\"btn primary\" href=\"/customers/new\">+ New customer</a>"
                + table
                + "</div>";
        return Html.page("Customers", body, user);
    }

    private String customerFormPage(User user, Customer c, boolean editing) {
        String action = editing ? "/customers/update" : "/customers/create";
        String idField = editing ? "<input type=\"hidden\" name=\"id\" value=\"" + c.getId() + "\">" : "";
        String name  = editing ? Html.esc(c.getName())  : "";
        String email = editing ? Html.esc(c.getEmail()) : "";
        String phone = editing ? Html.esc(c.getPhone()) : "";
        String body = "<div class=\"panel\">"
                + Html.heading(editing ? "Edit customer" : "New customer")
                + "<form method=\"POST\" action=\"" + action + "\">"
                + idField
                + "<label>Full name</label><input type=\"text\" name=\"name\" required value=\"" + name + "\">"
                + "<label>Email</label><input type=\"email\" name=\"email\" required value=\"" + email + "\">"
                + "<label>Phone</label><input type=\"tel\" name=\"phone\" required value=\"" + phone + "\">"
                + "<div class=\"form-actions\">"
                + "<button class=\"btn primary\" type=\"submit\">" + (editing ? "Save changes" : "Register") + "</button>"
                + "<a class=\"btn secondary\" href=\"/customers\">Cancel</a>"
                + "</div></form></div>";
        return Html.page(editing ? "Edit Customer" : "New Customer", body, user);
    }

    // ============================================================
    // TEAMS (admin only)
    // ============================================================

    private void teams(HttpExchange ex) throws IOException {
        User user = requireAdmin(ex); if (user == null) return;
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        try {
            if (path.equals("/teams") && method.equals("GET")) {
                sendHtml(ex, teamsListPage(user, q.get("msg"), "true".equals(q.get("err"))));
            } else if (path.equals("/teams/new") && method.equals("GET")) {
                sendHtml(ex, teamFormPage(user, null, false));
            } else if (path.equals("/teams/create") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                system.registerTeam(form.getOrDefault("name",""), form.getOrDefault("city",""));
                redirect(ex, "/teams?msg=Team+registered");
            } else if (path.equals("/teams/edit") && method.equals("GET")) {
                Team t = system.findTeamById(parseInt(q.get("id"), -1));
                if (t == null) { redirect(ex, "/teams?msg=Team+not+found&err=true"); return; }
                sendHtml(ex, teamFormPage(user, t, true));
            } else if (path.equals("/teams/update") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                boolean ok = system.updateTeam(id, form.get("name"), form.get("city"));
                redirect(ex, ok ? "/teams?msg=Team+updated"
                                : "/teams?msg=Team+not+found&err=true");
            } else {
                sendNotFound(ex, user);
            }
        } catch (Exception e) {
            redirect(ex, "/teams?msg=" + urlEncode("Error: " + e.getMessage()) + "&err=true");
        }
    }

    private String teamsListPage(User user, String msg, boolean err) {
        StringBuilder rows = new StringBuilder();
        for (Team t : system.getAllTeams()) {
            rows.append("<tr>")
                .append("<td>#").append(t.getId()).append("</td>")
                .append("<td><strong>").append(Html.esc(t.getName())).append("</strong></td>")
                .append("<td>").append(Html.esc(t.getCity())).append("</td>")
                .append("<td><a class=\"btn secondary sm\" href=\"/teams/edit?id=").append(t.getId()).append("\">Edit</a></td>")
                .append("</tr>");
        }
        String table = rows.length() == 0
                ? emptyState("No teams yet", "Add the teams that will play in your matches.", "/teams/new", "Add team")
                : "<table><thead><tr><th>ID</th><th>Name</th><th>City</th><th>Actions</th></tr></thead>"
                  + "<tbody>" + rows + "</tbody></table>";
        String body = "<div class=\"panel\">"
                + Html.heading("Teams")
                + Html.flash(msg, err)
                + "<a class=\"btn primary\" href=\"/teams/new\">+ New team</a>"
                + table
                + "</div>";
        return Html.page("Teams", body, user);
    }

    private String teamFormPage(User user, Team t, boolean editing) {
        String action = editing ? "/teams/update" : "/teams/create";
        String idField = editing ? "<input type=\"hidden\" name=\"id\" value=\"" + t.getId() + "\">" : "";
        String name = editing ? Html.esc(t.getName()) : "";
        String city = editing ? Html.esc(t.getCity()) : "";
        String body = "<div class=\"panel\">"
                + Html.heading(editing ? "Edit team" : "New team")
                + "<form method=\"POST\" action=\"" + action + "\">"
                + idField
                + "<label>Team name</label><input type=\"text\" name=\"name\" required value=\"" + name + "\">"
                + "<label>City</label><input type=\"text\" name=\"city\" required value=\"" + city + "\">"
                + "<div class=\"form-actions\">"
                + "<button class=\"btn primary\" type=\"submit\">" + (editing ? "Save changes" : "Register") + "</button>"
                + "<a class=\"btn secondary\" href=\"/teams\">Cancel</a>"
                + "</div></form></div>";
        return Html.page(editing ? "Edit Team" : "New Team", body, user);
    }

    // ============================================================
    // MATCHES (browse: any logged-in user; create/edit/delete: admin only)
    // ============================================================

    private void matches(HttpExchange ex) throws IOException {
        User user = requireAuth(ex); if (user == null) return;
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        // Reading the list is allowed for both roles
        if (path.equals("/matches") && method.equals("GET")) {
            sendHtml(ex, matchesListPage(user, q.get("msg"), "true".equals(q.get("err"))));
            return;
        }

        // Everything else is admin-only
        if (!user.isAdmin()) { sendForbidden(ex, user); return; }

        try {
            if (path.equals("/matches/new") && method.equals("GET")) {
                sendHtml(ex, matchFormPage(user, null, false));
            } else if (path.equals("/matches/create") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int homeId = parseInt(form.get("homeTeamId"), -1);
                int awayId = parseInt(form.get("awayTeamId"), -1);
                LocalDateTime dt = parseDateTimeLocal(form.get("dateTime"));
                double price = parseDouble(form.get("price"), 0);
                int seats = parseInt(form.get("totalSeats"), 0);
                system.registerMatch(homeId, awayId, form.getOrDefault("stadium",""), dt, price, seats);
                redirect(ex, "/matches?msg=Match+registered");
            } else if (path.equals("/matches/edit") && method.equals("GET")) {
                Match m = system.findMatchById(parseInt(q.get("id"), -1));
                if (m == null) { redirect(ex, "/matches?msg=Match+not+found&err=true"); return; }
                sendHtml(ex, matchFormPage(user, m, true));
            } else if (path.equals("/matches/update") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                LocalDateTime dt = parseDateTimeLocal(form.get("dateTime"));
                Double price = form.get("price") == null || form.get("price").isBlank()
                        ? null : Double.parseDouble(form.get("price"));
                Integer seats = form.get("totalSeats") == null || form.get("totalSeats").isBlank()
                        ? null : Integer.parseInt(form.get("totalSeats"));
                boolean ok = system.updateMatch(id, form.get("stadium"), dt, price, seats);
                redirect(ex, ok ? "/matches?msg=Match+updated"
                                : "/matches?msg=Match+not+found&err=true");
            } else if (path.equals("/matches/delete") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                boolean ok = system.removeMatch(id);
                redirect(ex, ok ? "/matches?msg=Match+removed"
                                : "/matches?msg=Match+not+found&err=true");
            } else {
                sendNotFound(ex, user);
            }
        } catch (Exception e) {
            redirect(ex, "/matches?msg=" + urlEncode("Error: " + e.getMessage()) + "&err=true");
        }
    }

    private String matchesListPage(User user, String msg, boolean err) {
        StringBuilder cards = new StringBuilder();
        for (Match m : system.getAllMatches()) {
            cards.append(matchCard(m, user));
        }
        String content = cards.length() == 0
                ? (user.isAdmin()
                    ? emptyState("No matches yet", "Schedule your first fixture to start selling tickets.",
                                 "/matches/new", "Schedule match")
                    : emptyState("No matches yet", "Check back soon for upcoming fixtures.",
                                 "/", "Back to home"))
                : "<div class=\"match-grid\">" + cards + "</div>";

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"panel\">")
            .append(Html.heading("Matches"))
            .append(Html.flash(msg, err));
        if (user.isAdmin()) {
            body.append("<a class=\"btn primary\" href=\"/matches/new\">+ New match</a>");
        } else {
            body.append("<a class=\"btn primary\" href=\"/tickets/book\">Book a ticket</a>");
        }
        body.append(content).append("</div>");
        return Html.page("Matches", body.toString(), user);
    }

    private String matchCard(Match m, User user) {
        String adminActions = "";
        if (user.isAdmin()) {
            adminActions =
                  "<a class=\"btn secondary sm\" href=\"/matches/edit?id=" + m.getId() + "\">Edit</a> "
                + "<form class=\"inline\" method=\"POST\" action=\"/matches/delete\" "
                +   "onsubmit=\"return confirm('Delete match?');\">"
                + "  <input type=\"hidden\" name=\"id\" value=\"" + m.getId() + "\">"
                + "  <button class=\"btn danger sm\" type=\"submit\">Delete</button>"
                + "</form>";
        } else {
            // CLIENT — show book button, disabled if sold out
            if (m.hasAvailableSeats()) {
                adminActions = "<a class=\"btn primary sm\" href=\"/tickets/book?matchId=" + m.getId() + "\">Book</a>";
            } else {
                adminActions = "<span class=\"badge red\">Unavailable</span>";
            }
        }

        return "<article class=\"ticket\">"
             + "  <div class=\"ticket-head\">"
             + "    <span class=\"ticket-id\">Match #" + m.getId() + "</span>"
             +      Html.availabilityBadge(m.getAvailableSeats(), m.getTotalSeats())
             + "  </div>"
             + "  <div class=\"ticket-teams\">"
             +      Html.esc(m.getHomeTeam().getName())
             + "    <span class=\"ticket-vs\">vs</span>"
             +      Html.esc(m.getAwayTeam().getName())
             + "  </div>"
             + "  <div class=\"ticket-meta\">"
             + "    <span><span class=\"label\">Stadium:</span> " + Html.esc(m.getStadium()) + "</span>"
             + "    <span><span class=\"label\">Kick-off:</span> " + Html.esc(m.getDateTime().format(DT_DISPLAY)) + "</span>"
             + "    <span><span class=\"label\">Seats:</span> " + m.getAvailableSeats() + " / " + m.getTotalSeats() + "</span>"
             + "  </div>"
             + "  <div class=\"ticket-foot\">"
             + "    <div class=\"ticket-price\">" + String.format("%.2f", m.getTicketPrice())
             +      " <small>per seat</small></div>"
             + "    <div>" + adminActions + "</div>"
             + "  </div>"
             + "</article>";
    }

    private String matchFormPage(User user, Match m, boolean editing) {
        String action = editing ? "/matches/update" : "/matches/create";
        String idField = editing ? "<input type=\"hidden\" name=\"id\" value=\"" + m.getId() + "\">" : "";

        StringBuilder homeOptions = new StringBuilder();
        StringBuilder awayOptions = new StringBuilder();
        for (Team t : system.getAllTeams()) {
            String selH = (editing && m.getHomeTeam().getId() == t.getId()) ? " selected" : "";
            String selA = (editing && m.getAwayTeam().getId() == t.getId()) ? " selected" : "";
            homeOptions.append("<option value=\"").append(t.getId()).append("\"").append(selH).append(">")
                       .append(Html.esc(t.getName())).append(" (").append(Html.esc(t.getCity())).append(")</option>");
            awayOptions.append("<option value=\"").append(t.getId()).append("\"").append(selA).append(">")
                       .append(Html.esc(t.getName())).append(" (").append(Html.esc(t.getCity())).append(")</option>");
        }

        String stadium = editing ? Html.esc(m.getStadium()) : "";
        String dateValue = editing ? m.getDateTime().format(DT_INPUT) : "";
        String price = editing ? String.format("%.2f", m.getTicketPrice()) : "";
        String seats = editing ? String.valueOf(m.getTotalSeats()) : "";

        String body = "<div class=\"panel\">"
                + Html.heading(editing ? "Edit match" : "New match")
                + "<form method=\"POST\" action=\"" + action + "\">"
                + idField
                + (editing ? "" : "<label>Home team</label><select name=\"homeTeamId\" required>" + homeOptions + "</select>")
                + (editing ? "" : "<label>Away team</label><select name=\"awayTeamId\" required>" + awayOptions + "</select>")
                + "<label>Stadium</label><input type=\"text\" name=\"stadium\" required value=\"" + stadium + "\">"
                + "<label>Kick-off (date and time)</label><input type=\"datetime-local\" name=\"dateTime\" required value=\"" + dateValue + "\">"
                + "<label>Ticket price</label><input type=\"number\" step=\"0.01\" min=\"0\" name=\"price\" required value=\"" + price + "\">"
                + "<label>Total seats</label><input type=\"number\" min=\"1\" name=\"totalSeats\" required value=\"" + seats + "\">"
                + "<div class=\"form-actions\">"
                + "<button class=\"btn primary\" type=\"submit\">" + (editing ? "Save changes" : "Register match") + "</button>"
                + "<a class=\"btn secondary\" href=\"/matches\">Cancel</a>"
                + "</div></form></div>";
        return Html.page(editing ? "Edit Match" : "New Match", body, user);
    }

    // ============================================================
    // TICKETS — viewing all + booking
    // ============================================================

    private void tickets(HttpExchange ex) throws IOException {
        User user = requireAuth(ex); if (user == null) return;
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        try {
            if (path.equals("/tickets") && method.equals("GET")) {
                if (!user.isAdmin()) { redirect(ex, "/my-tickets"); return; }
                sendHtml(ex, ticketsListPage(user, q.get("msg"), "true".equals(q.get("err"))));
            } else if (path.equals("/tickets/book") && method.equals("GET")) {
                int preselect = parseInt(q.get("matchId"), -1);
                sendHtml(ex, bookTicketFormPage(user, preselect));
            } else if (path.equals("/tickets/create") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int matchId = parseInt(form.get("matchId"), -1);
                int customerId;
                if (user.isAdmin()) {
                    customerId = parseInt(form.get("customerId"), -1);
                } else {
                    // Clients can only book for themselves
                    if (user.getCustomerId() == null) {
                        redirect(ex, "/tickets/book?err=true&msg="
                                + urlEncode("Your account is not linked to a customer profile."));
                        return;
                    }
                    customerId = user.getCustomerId();
                }
                Ticket t = system.bookTicket(customerId, matchId);
                String dest = user.isAdmin() ? "/tickets" : "/my-tickets";
                redirect(ex, dest + "?msg=" + urlEncode(
                        "Ticket booked (#" + t.getId() + ", seat " + t.getSeatNumber() + ")"));
            } else if (path.equals("/tickets/cancel") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                Ticket existing = findTicket(id);
                if (existing == null) {
                    redirect(ex, (user.isAdmin() ? "/tickets" : "/my-tickets") + "?msg=Ticket+not+found&err=true");
                    return;
                }
                // Clients can only cancel their own tickets
                if (!user.isAdmin() && (user.getCustomerId() == null
                        || existing.getCustomer().getId() != user.getCustomerId())) {
                    sendForbidden(ex, user);
                    return;
                }
                boolean ok = system.cancelTicket(id);
                String dest = user.isAdmin() ? "/tickets" : "/my-tickets";
                redirect(ex, ok ? dest + "?msg=Ticket+cancelled"
                                : dest + "?msg=Ticket+not+found&err=true");
            } else {
                sendNotFound(ex, user);
            }
        } catch (Exception e) {
            String dest = user.isAdmin() ? "/tickets" : "/my-tickets";
            redirect(ex, dest + "?msg=" + urlEncode("Error: " + e.getMessage()) + "&err=true");
        }
    }

    private String ticketsListPage(User user, String msg, boolean err) {
        StringBuilder rows = new StringBuilder();
        for (Ticket t : system.getAllTickets()) {
            rows.append(ticketRow(t, true));
        }
        String table = rows.length() == 0
                ? emptyState("No tickets booked yet", "Book your first ticket to see it appear here.",
                        "/tickets/book", "Book a ticket")
                : "<table><thead><tr><th>ID</th><th>Match</th><th>Seat</th>"
                  + "<th>Customer</th><th>Price</th><th>Booked</th><th>Actions</th></tr></thead>"
                  + "<tbody>" + rows + "</tbody></table>";

        String body = "<div class=\"panel\">"
                + Html.heading("All tickets")
                + Html.flash(msg, err)
                + "<a class=\"btn primary\" href=\"/tickets/book\">+ Book ticket</a>"
                + table
                + "</div>";
        return Html.page("Tickets", body, user);
    }

    private String ticketRow(Ticket t, boolean showCustomer) {
        StringBuilder row = new StringBuilder();
        row.append("<tr>")
           .append("<td>#").append(t.getId()).append("</td>")
           .append("<td><strong>").append(Html.esc(t.getMatch().getHomeTeam().getName()))
           .append("</strong> vs <strong>").append(Html.esc(t.getMatch().getAwayTeam().getName())).append("</strong></td>")
           .append("<td>").append(t.getSeatNumber()).append("</td>");
        if (showCustomer) {
            row.append("<td>").append(Html.esc(t.getCustomer().getName())).append("</td>");
        }
        row.append("<td>").append(String.format("%.2f", t.getPrice())).append("</td>")
           .append("<td>").append(Html.esc(t.getBookingDate().format(DT_DISPLAY))).append("</td>")
           .append("<td>")
           .append("<form class=\"inline\" method=\"POST\" action=\"/tickets/cancel\" "
                 + "onsubmit=\"return confirm('Cancel ticket?');\">")
           .append("<input type=\"hidden\" name=\"id\" value=\"").append(t.getId()).append("\">")
           .append("<button class=\"btn danger sm\" type=\"submit\">Cancel</button>")
           .append("</form></td></tr>");
        return row.toString();
    }

    private String bookTicketFormPage(User user, int preselectedMatchId) {
        if (system.getAvailableMatches().isEmpty()) {
            String body = "<div class=\"panel\">" + Html.heading("Book a ticket")
                    + emptyState("No matches available",
                            "All matches are sold out, or none are scheduled yet.",
                            user.isAdmin() ? "/matches/new" : "/",
                            user.isAdmin() ? "Schedule a match" : "Back to home")
                    + "</div>";
            return Html.page("Book Ticket", body, user);
        }

        String customerSection;
        if (user.isAdmin()) {
            if (system.getAllCustomers().isEmpty()) {
                String body = "<div class=\"panel\">" + Html.heading("Book a ticket")
                        + emptyState("No customers yet",
                                "Create a customer or wait for a client to register.",
                                "/customers/new", "Add customer")
                        + "</div>";
                return Html.page("Book Ticket", body, user);
            }
            StringBuilder customerOptions = new StringBuilder();
            for (Customer c : system.getAllCustomers()) {
                customerOptions.append("<option value=\"").append(c.getId()).append("\">")
                               .append(Html.esc(c.getName())).append(" (").append(Html.esc(c.getEmail())).append(")</option>");
            }
            customerSection = "<label>Customer</label><select name=\"customerId\" required>" + customerOptions + "</select>";
        } else {
            Customer me = currentCustomer(user);
            customerSection = "<label>Booking for</label>"
                    + "<input type=\"text\" value=\"" + Html.esc(me == null ? user.getUsername() : me.getName())
                    + "\" disabled>";
        }

        StringBuilder matchOptions = new StringBuilder();
        for (Match m : system.getAvailableMatches()) {
            String sel = (m.getId() == preselectedMatchId) ? " selected" : "";
            matchOptions.append("<option value=\"").append(m.getId()).append("\"").append(sel).append(">")
                        .append(Html.esc(m.getHomeTeam().getName())).append(" vs ")
                        .append(Html.esc(m.getAwayTeam().getName())).append(" — ")
                        .append(Html.esc(m.getDateTime().format(DT_DISPLAY))).append(" (")
                        .append(m.getAvailableSeats()).append(" seats, ")
                        .append(String.format("%.2f", m.getTicketPrice())).append(")</option>");
        }

        String body = "<div class=\"panel\">"
                + Html.heading("Book a ticket")
                + "<form method=\"POST\" action=\"/tickets/create\">"
                + customerSection
                + "<label>Match</label><select name=\"matchId\" required>" + matchOptions + "</select>"
                + "<div class=\"form-actions\">"
                + "<button class=\"btn primary\" type=\"submit\">Confirm booking</button>"
                + "<a class=\"btn secondary\" href=\"" + (user.isAdmin() ? "/tickets" : "/my-tickets") + "\">Cancel</a>"
                + "</div></form></div>";
        return Html.page("Book Ticket", body, user);
    }

    // ============================================================
    // MY TICKETS (clients)
    // ============================================================

    private void myTickets(HttpExchange ex) throws IOException {
        User user = requireAuth(ex); if (user == null) return;
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        if (user.isAdmin()) { redirect(ex, "/tickets"); return; }
        if (user.getCustomerId() == null) { sendForbidden(ex, user); return; }

        StringBuilder rows = new StringBuilder();
        for (Ticket t : system.getTicketsForCustomer(user.getCustomerId())) {
            rows.append(ticketRow(t, false));
        }
        String table = rows.length() == 0
                ? emptyState("You have no tickets yet",
                        "Book a ticket to your favourite match!", "/tickets/book", "Book a ticket")
                : "<table><thead><tr><th>ID</th><th>Match</th><th>Seat</th>"
                  + "<th>Price</th><th>Booked</th><th>Actions</th></tr></thead>"
                  + "<tbody>" + rows + "</tbody></table>";

        String body = "<div class=\"panel\">"
                + Html.heading("My tickets")
                + Html.flash(q.get("msg"), "true".equals(q.get("err")))
                + "<a class=\"btn primary\" href=\"/tickets/book\">+ Book another</a>"
                + table
                + "</div>";
        sendHtml(ex, Html.page("My Tickets", body, user));
    }

    // ============================================================
    // PROFILE (clients edit their own customer record)
    // ============================================================

    private void profile(HttpExchange ex) throws IOException {
        User user = requireAuth(ex); if (user == null) return;
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        if (user.isAdmin()) {
            String body = "<div class=\"panel\">" + Html.heading("Admin profile")
                    + "<p class=\"muted\">You are signed in as <strong>" + Html.esc(user.getUsername())
                    + "</strong> (admin).</p></div>";
            sendHtml(ex, Html.page("Profile", body, user));
            return;
        }

        Customer me = currentCustomer(user);
        if (me == null) { sendForbidden(ex, user); return; }

        if (method.equals("POST")) {
            Map<String,String> form = parseForm(ex);
            system.updateCustomer(me.getId(),
                    form.get("name"), form.get("email"), form.get("phone"));
            redirect(ex, "/profile?msg=Profile+updated");
            return;
        }

        String body = "<div class=\"panel\">"
                + Html.heading("My profile")
                + Html.flash(q.get("msg"), "true".equals(q.get("err")))
                + "<p class=\"muted\">Username: <strong>" + Html.esc(user.getUsername()) + "</strong></p>"
                + "<form method=\"POST\" action=\"/profile\">"
                + "<label>Full name</label><input type=\"text\" name=\"name\" required value=\"" + Html.esc(me.getName()) + "\">"
                + "<label>Email</label><input type=\"email\" name=\"email\" required value=\"" + Html.esc(me.getEmail()) + "\">"
                + "<label>Phone</label><input type=\"tel\" name=\"phone\" required value=\"" + Html.esc(me.getPhone()) + "\">"
                + "<div class=\"form-actions\">"
                + "<button class=\"btn primary\" type=\"submit\">Save changes</button>"
                + "</div></form></div>";
        sendHtml(ex, Html.page("My Profile", body, user));
    }

    // ============================================================
    // USERS (admin only) — list and delete user accounts
    // ============================================================

    private void users(HttpExchange ex) throws IOException {
        User user = requireAdmin(ex); if (user == null) return;
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        if (path.equals("/users") && method.equals("GET")) {
            sendHtml(ex, usersListPage(user, q.get("msg"), "true".equals(q.get("err"))));
        } else if (path.equals("/users/delete") && method.equals("POST")) {
            Map<String,String> form = parseForm(ex);
            int id = parseInt(form.get("id"), -1);
            if (id == user.getId()) {
                redirect(ex, "/users?err=true&msg=" + urlEncode("You cannot delete your own account."));
                return;
            }
            boolean ok = auth.removeUser(id);
            redirect(ex, ok ? "/users?msg=User+removed" : "/users?msg=User+not+found&err=true");
        } else {
            sendNotFound(ex, user);
        }
    }

    private String usersListPage(User user, String msg, boolean err) {
        StringBuilder rows = new StringBuilder();
        for (User u : auth.getAllUsers()) {
            String roleBadge = u.isAdmin()
                    ? "<span class=\"badge amber\">ADMIN</span>"
                    : "<span class=\"badge green\">CLIENT</span>";
            String linkedCustomer = "";
            if (u.getCustomerId() != null) {
                Customer c = system.findCustomerById(u.getCustomerId());
                linkedCustomer = c == null ? "—" : Html.esc(c.getName());
            } else {
                linkedCustomer = "<span class=\"muted\">—</span>";
            }
            String deleteBtn = (u.getId() == user.getId())
                    ? "<span class=\"muted\">(you)</span>"
                    : "<form class=\"inline\" method=\"POST\" action=\"/users/delete\" "
                    + "onsubmit=\"return confirm('Delete this user account?');\">"
                    + "<input type=\"hidden\" name=\"id\" value=\"" + u.getId() + "\">"
                    + "<button class=\"btn danger sm\" type=\"submit\">Delete</button></form>";
            rows.append("<tr>")
                .append("<td>#").append(u.getId()).append("</td>")
                .append("<td><strong>").append(Html.esc(u.getUsername())).append("</strong></td>")
                .append("<td>").append(roleBadge).append("</td>")
                .append("<td>").append(linkedCustomer).append("</td>")
                .append("<td>").append(deleteBtn).append("</td>")
                .append("</tr>");
        }
        String body = "<div class=\"panel\">"
                + Html.heading("User accounts")
                + Html.flash(msg, err)
                + "<p class=\"muted\">Clients self-register at <code>/register</code>. Admins are seeded by the application.</p>"
                + "<table><thead><tr><th>ID</th><th>Username</th><th>Role</th><th>Linked customer</th><th>Actions</th></tr></thead>"
                + "<tbody>" + rows + "</tbody></table>"
                + "</div>";
        return Html.page("Users", body, user);
    }

    // ============================================================
    // REPORTS (admin only)
    // ============================================================

    private void reports(HttpExchange ex) throws IOException {
        User user = requireAdmin(ex); if (user == null) return;

        int adminCount = 0, clientCount = 0;
        for (User u : auth.getAllUsers()) {
            if (u.isAdmin()) adminCount++; else clientCount++;
        }

        String body = "<div class=\"panel\">"
                + Html.heading("Reports")
                + "<p class=\"muted\">A snapshot of the booking system at this moment.</p>"
                + "<div class=\"stat-grid\">"
                + statCard("Customers", system.getAllCustomers().size(), "/customers")
                + statCard("Teams", system.getAllTeams().size(), "/teams")
                + statCard("Matches", system.getAllMatches().size(), "/matches")
                + statCard("Tickets sold", system.getAllTickets().size(), "/tickets")
                + statCard("Total revenue", String.format("%.2f", system.getTotalRevenue()), "/reports")
                + statCard("Admin users", adminCount, "/users")
                + statCard("Client users", clientCount, "/users")
                + "</div></div>";
        sendHtml(ex, Html.page("Reports", body, user));
    }

    // ============================================================
    // AUTH HELPERS
    // ============================================================

    private User currentUser(HttpExchange ex) {
        return auth.userBySession(readCookie(ex, SESSION_COOKIE));
    }

    private Customer currentCustomer(User user) {
        if (user == null || user.getCustomerId() == null) return null;
        return system.findCustomerById(user.getCustomerId());
    }

    /** Returns the user, or redirects to /login and returns null. */
    private User requireAuth(HttpExchange ex) throws IOException {
        User u = currentUser(ex);
        if (u == null) {
            redirect(ex, "/login?next=" + urlEncode(ex.getRequestURI().getPath()));
            return null;
        }
        return u;
    }

    /** Returns the admin user, or redirects/forbids and returns null. */
    private User requireAdmin(HttpExchange ex) throws IOException {
        User u = requireAuth(ex);
        if (u == null) return null;
        if (!u.isAdmin()) { sendForbidden(ex, u); return null; }
        return u;
    }

    private User findUserByCustomerId(int customerId) {
        for (User u : auth.getAllUsers()) {
            if (u.getCustomerId() != null && u.getCustomerId() == customerId) return u;
        }
        return null;
    }

    private Ticket findTicket(int ticketId) {
        for (Ticket t : system.getAllTickets()) {
            if (t.getId() == ticketId) return t;
        }
        return null;
    }

    // ============================================================
    // HTTP / COOKIE UTILITIES
    // ============================================================

    private String emptyState(String title, String subtitle, String href, String cta) {
        return "<div class=\"empty\">"
             + "<div class=\"empty-icon\">&#9917;</div>"
             + "<h3>" + Html.esc(title) + "</h3>"
             + "<p>" + Html.esc(subtitle) + "</p>"
             + "<a class=\"btn primary\" href=\"" + href + "\">" + Html.esc(cta) + "</a>"
             + "</div>";
    }

    private void sendHtml(HttpExchange ex, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void sendNotFound(HttpExchange ex, User user) throws IOException {
        String body = "<div class=\"panel\">"
                + Html.heading("Page not found")
                + "<p class=\"muted\">The page you requested does not exist.</p>"
                + "<a class=\"btn primary\" href=\"/\">Go home</a></div>";
        byte[] bytes = Html.page("Not Found", body, user).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(404, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void sendForbidden(HttpExchange ex, User user) throws IOException {
        String body = "<div class=\"panel\">"
                + Html.heading("Forbidden")
                + "<p class=\"muted\">You do not have permission to view this page.</p>"
                + "<a class=\"btn primary\" href=\"/\">Back to home</a></div>";
        byte[] bytes = Html.page("Forbidden", body, user).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(403, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(303, -1);
        ex.getResponseBody().close();
    }

    private void setSessionCookie(HttpExchange ex, String sessionId) {
        ex.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=" + sessionId
                + "; Path=/; HttpOnly; Max-Age=86400; SameSite=Lax");
    }

    private void clearSessionCookie(HttpExchange ex) {
        ex.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
    }

    private String readCookie(HttpExchange ex, String name) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) return null;
        for (String header : headers) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(name + "=")) {
                    return trimmed.substring(name.length() + 1);
                }
            }
        }
        return null;
    }

    private Map<String,String> parseQuery(String raw) {
        Map<String,String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                map.put(urlDecode(pair), "");
            } else {
                map.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
            }
        }
        return map;
    }

    private Map<String,String> parseForm(HttpExchange ex) throws IOException {
        InputStream is = ex.getRequestBody();
        byte[] buf = is.readAllBytes();
        String body = new String(buf, StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static int parseInt(String s, int defaultValue) {
        if (s == null) return defaultValue;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private static double parseDouble(String s, double defaultValue) {
        if (s == null) return defaultValue;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private static LocalDateTime parseDateTimeLocal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s, DT_INPUT);
        } catch (DateTimeParseException e) {
            try { return LocalDateTime.parse(s, DT_DISPLAY); }
            catch (DateTimeParseException e2) { return null; }
        }
    }
}
