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
import java.util.Map;

/**
 * HTTP server that exposes the BookingSystem through HTML pages.
 * Built on the JDK's com.sun.net.httpserver.HttpServer (no external libraries).
 */
public class WebServer {

    private final BookingSystem system;
    private final int port;
    private static final DateTimeFormatter DT_DISPLAY =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter DT_INPUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public WebServer(BookingSystem system, int port) {
        this.system = system;
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/",          this::home);
        server.createContext("/customers", this::customers);
        server.createContext("/teams",     this::teams);
        server.createContext("/matches",   this::matches);
        server.createContext("/tickets",   this::tickets);
        server.createContext("/reports",   this::reports);

        server.setExecutor(null);
        server.start();

        System.out.println("Server running at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    // ============================================================
    // HOME
    // ============================================================

    private void home(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            sendNotFound(ex);
            return;
        }
        int customers = system.getAllCustomers().size();
        int teams = system.getAllTeams().size();
        int upcoming = system.getAvailableMatches().size();
        int sold = system.getAllTickets().size();
        double revenue = system.getTotalRevenue();

        String body =
                  "<section class=\"hero\">"
                + "  <span class=\"hero-eyebrow\">Football Tickets</span>"
                + "  <h1>Book your seat for the <span class=\"accent\">next big match</span>.</h1>"
                + "  <p>Manage customers, teams, fixtures, and bookings — all in one place.</p>"
                + "  <div class=\"hero-actions\">"
                + "    <a class=\"btn primary\" href=\"/tickets/book\">Book a ticket</a>"
                + "    <a class=\"btn ghost\" href=\"/matches\">Browse matches</a>"
                + "  </div>"
                + "</section>"
                + "<div class=\"stat-grid\">"
                + statCard("Customers", customers, "/customers")
                + statCard("Teams", teams, "/teams")
                + statCard("Upcoming matches", upcoming, "/matches")
                + statCard("Tickets sold", sold, "/tickets")
                + statCard("Revenue", String.format("%.0f", revenue), "/reports")
                + "</div>";
        sendHtml(ex, Html.page("Home", body));
    }

    private String statCard(String label, Object value, String href) {
        return "<a class=\"stat\" href=\"" + href + "\">"
             + "<div class=\"stat-label\">" + Html.esc(label) + "</div>"
             + "<div class=\"stat-value\">" + Html.esc(value) + "</div>"
             + "</a>";
    }

    // ============================================================
    // CUSTOMERS
    // ============================================================

    private void customers(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        try {
            if (path.equals("/customers") && method.equals("GET")) {
                sendHtml(ex, customersListPage(q.get("msg"), "true".equals(q.get("err"))));
            } else if (path.equals("/customers/new") && method.equals("GET")) {
                sendHtml(ex, customerFormPage(null, false));
            } else if (path.equals("/customers/create") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                system.registerCustomer(form.getOrDefault("name",""),
                        form.getOrDefault("email",""), form.getOrDefault("phone",""));
                redirect(ex, "/customers?msg=Customer+registered");
            } else if (path.equals("/customers/edit") && method.equals("GET")) {
                Customer c = system.findCustomerById(parseInt(q.get("id"), -1));
                if (c == null) { redirect(ex, "/customers?msg=Customer+not+found&err=true"); return; }
                sendHtml(ex, customerFormPage(c, true));
            } else if (path.equals("/customers/update") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                boolean ok = system.updateCustomer(id, form.get("name"), form.get("email"), form.get("phone"));
                redirect(ex, ok ? "/customers?msg=Customer+updated"
                                : "/customers?msg=Customer+not+found&err=true");
            } else if (path.equals("/customers/delete") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                boolean ok = system.removeCustomer(id);
                redirect(ex, ok ? "/customers?msg=Customer+removed"
                                : "/customers?msg=Customer+not+found&err=true");
            } else {
                sendNotFound(ex);
            }
        } catch (Exception e) {
            redirect(ex, "/customers?msg=" + urlEncode("Error: " + e.getMessage()) + "&err=true");
        }
    }

    private String customersListPage(String msg, boolean err) {
        StringBuilder rows = new StringBuilder();
        for (Customer c : system.getAllCustomers()) {
            rows.append("<tr>")
                .append("<td>#").append(c.getId()).append("</td>")
                .append("<td><strong>").append(Html.esc(c.getName())).append("</strong></td>")
                .append("<td>").append(Html.esc(c.getEmail())).append("</td>")
                .append("<td>").append(Html.esc(c.getPhone())).append("</td>")
                .append("<td>")
                .append("<a class=\"btn secondary sm\" href=\"/customers/edit?id=").append(c.getId()).append("\">Edit</a> ")
                .append("<form class=\"inline\" method=\"POST\" action=\"/customers/delete\" "
                      + "onsubmit=\"return confirm('Delete customer?');\">")
                .append("<input type=\"hidden\" name=\"id\" value=\"").append(c.getId()).append("\">")
                .append("<button class=\"btn danger sm\" type=\"submit\">Delete</button>")
                .append("</form>")
                .append("</td></tr>");
        }
        String table = rows.length() == 0
                ? emptyState("No customers yet", "Register your first customer to get started.", "/customers/new", "Register customer")
                : "<table><thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Phone</th><th>Actions</th></tr></thead>"
                  + "<tbody>" + rows + "</tbody></table>";
        String body = "<div class=\"panel\">"
                + Html.heading("Customers")
                + Html.flash(msg, err)
                + "<a class=\"btn primary\" href=\"/customers/new\">+ New customer</a>"
                + table
                + "</div>";
        return Html.page("Customers", body);
    }

    private String customerFormPage(Customer c, boolean editing) {
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
        return Html.page(editing ? "Edit Customer" : "New Customer", body);
    }

    // ============================================================
    // TEAMS
    // ============================================================

    private void teams(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        try {
            if (path.equals("/teams") && method.equals("GET")) {
                sendHtml(ex, teamsListPage(q.get("msg"), "true".equals(q.get("err"))));
            } else if (path.equals("/teams/new") && method.equals("GET")) {
                sendHtml(ex, teamFormPage(null, false));
            } else if (path.equals("/teams/create") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                system.registerTeam(form.getOrDefault("name",""), form.getOrDefault("city",""));
                redirect(ex, "/teams?msg=Team+registered");
            } else if (path.equals("/teams/edit") && method.equals("GET")) {
                Team t = system.findTeamById(parseInt(q.get("id"), -1));
                if (t == null) { redirect(ex, "/teams?msg=Team+not+found&err=true"); return; }
                sendHtml(ex, teamFormPage(t, true));
            } else if (path.equals("/teams/update") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                boolean ok = system.updateTeam(id, form.get("name"), form.get("city"));
                redirect(ex, ok ? "/teams?msg=Team+updated"
                                : "/teams?msg=Team+not+found&err=true");
            } else {
                sendNotFound(ex);
            }
        } catch (Exception e) {
            redirect(ex, "/teams?msg=" + urlEncode("Error: " + e.getMessage()) + "&err=true");
        }
    }

    private String teamsListPage(String msg, boolean err) {
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
        return Html.page("Teams", body);
    }

    private String teamFormPage(Team t, boolean editing) {
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
        return Html.page(editing ? "Edit Team" : "New Team", body);
    }

    // ============================================================
    // MATCHES (ticket-style cards)
    // ============================================================

    private void matches(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        try {
            if (path.equals("/matches") && method.equals("GET")) {
                sendHtml(ex, matchesListPage(q.get("msg"), "true".equals(q.get("err"))));
            } else if (path.equals("/matches/new") && method.equals("GET")) {
                sendHtml(ex, matchFormPage(null, false));
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
                sendHtml(ex, matchFormPage(m, true));
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
                sendNotFound(ex);
            }
        } catch (Exception e) {
            redirect(ex, "/matches?msg=" + urlEncode("Error: " + e.getMessage()) + "&err=true");
        }
    }

    private String matchesListPage(String msg, boolean err) {
        StringBuilder cards = new StringBuilder();
        for (Match m : system.getAllMatches()) {
            cards.append(matchCard(m));
        }
        String content = cards.length() == 0
                ? emptyState("No matches yet", "Schedule your first fixture to start selling tickets.", "/matches/new", "Schedule match")
                : "<div class=\"match-grid\">" + cards + "</div>";

        String body = "<div class=\"panel\">"
                + Html.heading("Matches")
                + Html.flash(msg, err)
                + "<a class=\"btn primary\" href=\"/matches/new\">+ New match</a>"
                + content
                + "</div>";
        return Html.page("Matches", body);
    }

    private String matchCard(Match m) {
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
             + "    <div>"
             + "      <a class=\"btn secondary sm\" href=\"/matches/edit?id=" + m.getId() + "\">Edit</a> "
             + "      <form class=\"inline\" method=\"POST\" action=\"/matches/delete\" "
             +        "onsubmit=\"return confirm('Delete match?');\">"
             + "        <input type=\"hidden\" name=\"id\" value=\"" + m.getId() + "\">"
             + "        <button class=\"btn danger sm\" type=\"submit\">Delete</button>"
             + "      </form>"
             + "    </div>"
             + "  </div>"
             + "</article>";
    }

    private String matchFormPage(Match m, boolean editing) {
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
        return Html.page(editing ? "Edit Match" : "New Match", body);
    }

    // ============================================================
    // TICKETS
    // ============================================================

    private void tickets(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String,String> q = parseQuery(ex.getRequestURI().getRawQuery());

        try {
            if (path.equals("/tickets") && method.equals("GET")) {
                sendHtml(ex, ticketsListPage(q.get("msg"), "true".equals(q.get("err"))));
            } else if (path.equals("/tickets/book") && method.equals("GET")) {
                sendHtml(ex, bookTicketFormPage());
            } else if (path.equals("/tickets/create") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int customerId = parseInt(form.get("customerId"), -1);
                int matchId = parseInt(form.get("matchId"), -1);
                Ticket t = system.bookTicket(customerId, matchId);
                redirect(ex, "/tickets?msg=" + urlEncode(
                        "Ticket booked (#" + t.getId() + ", seat " + t.getSeatNumber() + ")"));
            } else if (path.equals("/tickets/cancel") && method.equals("POST")) {
                Map<String,String> form = parseForm(ex);
                int id = parseInt(form.get("id"), -1);
                boolean ok = system.cancelTicket(id);
                redirect(ex, ok ? "/tickets?msg=Ticket+cancelled"
                                : "/tickets?msg=Ticket+not+found&err=true");
            } else {
                sendNotFound(ex);
            }
        } catch (Exception e) {
            redirect(ex, "/tickets?msg=" + urlEncode("Error: " + e.getMessage()) + "&err=true");
        }
    }

    private String ticketsListPage(String msg, boolean err) {
        StringBuilder rows = new StringBuilder();
        for (Ticket t : system.getAllTickets()) {
            rows.append("<tr>")
                .append("<td>#").append(t.getId()).append("</td>")
                .append("<td><strong>").append(Html.esc(t.getMatch().getHomeTeam().getName()))
                .append("</strong> vs <strong>").append(Html.esc(t.getMatch().getAwayTeam().getName())).append("</strong></td>")
                .append("<td>").append(t.getSeatNumber()).append("</td>")
                .append("<td>").append(Html.esc(t.getCustomer().getName())).append("</td>")
                .append("<td>").append(String.format("%.2f", t.getPrice())).append("</td>")
                .append("<td>").append(Html.esc(t.getBookingDate().format(DT_DISPLAY))).append("</td>")
                .append("<td>")
                .append("<form class=\"inline\" method=\"POST\" action=\"/tickets/cancel\" "
                      + "onsubmit=\"return confirm('Cancel ticket?');\">")
                .append("<input type=\"hidden\" name=\"id\" value=\"").append(t.getId()).append("\">")
                .append("<button class=\"btn danger sm\" type=\"submit\">Cancel</button>")
                .append("</form>")
                .append("</td></tr>");
        }
        String table = rows.length() == 0
                ? emptyState("No tickets booked yet", "Book your first ticket to see it appear here.", "/tickets/book", "Book a ticket")
                : "<table><thead><tr><th>ID</th><th>Match</th><th>Seat</th>"
                  + "<th>Customer</th><th>Price</th><th>Booked</th><th>Actions</th></tr></thead>"
                  + "<tbody>" + rows + "</tbody></table>";

        String body = "<div class=\"panel\">"
                + Html.heading("Tickets")
                + Html.flash(msg, err)
                + "<a class=\"btn primary\" href=\"/tickets/book\">+ Book ticket</a>"
                + table
                + "</div>";
        return Html.page("Tickets", body);
    }

    private String bookTicketFormPage() {
        if (system.getAllCustomers().isEmpty()) {
            return Html.page("Book Ticket",
                    "<div class=\"panel\">" + Html.heading("Book a ticket")
                  + emptyState("No customers registered",
                        "You need at least one customer before you can book a ticket.",
                        "/customers/new", "Register customer")
                  + "</div>");
        }
        if (system.getAvailableMatches().isEmpty()) {
            return Html.page("Book Ticket",
                    "<div class=\"panel\">" + Html.heading("Book a ticket")
                  + emptyState("No matches available",
                        "All matches are sold out, or none are scheduled yet.",
                        "/matches/new", "Schedule a match")
                  + "</div>");
        }

        StringBuilder customerOptions = new StringBuilder();
        for (Customer c : system.getAllCustomers()) {
            customerOptions.append("<option value=\"").append(c.getId()).append("\">")
                           .append(Html.esc(c.getName())).append(" (").append(Html.esc(c.getEmail())).append(")</option>");
        }

        StringBuilder matchOptions = new StringBuilder();
        for (Match m : system.getAvailableMatches()) {
            matchOptions.append("<option value=\"").append(m.getId()).append("\">")
                        .append(Html.esc(m.getHomeTeam().getName())).append(" vs ")
                        .append(Html.esc(m.getAwayTeam().getName())).append(" — ")
                        .append(Html.esc(m.getDateTime().format(DT_DISPLAY))).append(" (")
                        .append(m.getAvailableSeats()).append(" seats)").append("</option>");
        }

        String body = "<div class=\"panel\">"
                + Html.heading("Book a ticket")
                + "<form method=\"POST\" action=\"/tickets/create\">"
                + "<label>Customer</label><select name=\"customerId\" required>" + customerOptions + "</select>"
                + "<label>Match</label><select name=\"matchId\" required>" + matchOptions + "</select>"
                + "<div class=\"form-actions\">"
                + "<button class=\"btn primary\" type=\"submit\">Confirm booking</button>"
                + "<a class=\"btn secondary\" href=\"/tickets\">Cancel</a>"
                + "</div></form></div>";
        return Html.page("Book Ticket", body);
    }

    // ============================================================
    // REPORTS
    // ============================================================

    private void reports(HttpExchange ex) throws IOException {
        String body = "<div class=\"panel\">"
                + Html.heading("Reports")
                + "<p class=\"muted\">A snapshot of the booking system at this moment.</p>"
                + "<div class=\"stat-grid\">"
                + statCard("Customers", system.getAllCustomers().size(), "/customers")
                + statCard("Teams", system.getAllTeams().size(), "/teams")
                + statCard("Matches", system.getAllMatches().size(), "/matches")
                + statCard("Tickets sold", system.getAllTickets().size(), "/tickets")
                + statCard("Total revenue", String.format("%.2f", system.getTotalRevenue()), "/reports")
                + "</div></div>";
        sendHtml(ex, Html.page("Reports", body));
    }

    // ============================================================
    // HELPERS
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

    private void sendNotFound(HttpExchange ex) throws IOException {
        String body = "<div class=\"panel\">"
                + Html.heading("Page not found")
                + "<p class=\"muted\">The page you requested does not exist.</p>"
                + "<a class=\"btn primary\" href=\"/\">Go home</a></div>";
        byte[] bytes = Html.page("Not Found", body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(404, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(303, -1);
        ex.getResponseBody().close();
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
