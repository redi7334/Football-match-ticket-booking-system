/**
 * Small helper class for building HTML pages.
 * Provides escaping and a shared page layout (navigation, styling).
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

    /** Wrap inner HTML in the shared page layout (header + nav + body). */
    public static String page(String title, String body) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <title>" + esc(title) + " - Football Tickets</title>\n"
                + "  <style>" + css() + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <header>\n"
                + "    <h1>Football Match Ticket Booking System</h1>\n"
                + "    <nav>\n"
                + "      <a href=\"/\">Home</a>\n"
                + "      <a href=\"/customers\">Customers</a>\n"
                + "      <a href=\"/teams\">Teams</a>\n"
                + "      <a href=\"/matches\">Matches</a>\n"
                + "      <a href=\"/tickets\">Tickets</a>\n"
                + "      <a href=\"/reports\">Reports</a>\n"
                + "    </nav>\n"
                + "  </header>\n"
                + "  <main>\n"
                + "    <h2>" + esc(title) + "</h2>\n"
                + body + "\n"
                + "  </main>\n"
                + "</body>\n"
                + "</html>";
    }

    /** Render a simple flash message banner if msg is not null. */
    public static String flash(String msg, boolean error) {
        if (msg == null || msg.isBlank()) return "";
        String cls = error ? "flash error" : "flash ok";
        return "<div class=\"" + cls + "\">" + esc(msg) + "</div>";
    }

    /** Inline CSS for the whole site. */
    private static String css() {
        return "body{font-family:Arial,Helvetica,sans-serif;margin:0;background:#f4f6f8;color:#222;}"
             + "header{background:#1b4d3e;color:#fff;padding:16px 24px;}"
             + "header h1{margin:0 0 8px;font-size:20px;}"
             + "nav a{color:#fff;text-decoration:none;margin-right:16px;font-weight:bold;}"
             + "nav a:hover{text-decoration:underline;}"
             + "main{max-width:980px;margin:24px auto;background:#fff;padding:24px;"
             + "border-radius:8px;box-shadow:0 1px 4px rgba(0,0,0,.08);}"
             + "h2{margin-top:0;color:#1b4d3e;}"
             + "table{width:100%;border-collapse:collapse;margin:12px 0;}"
             + "th,td{padding:8px 10px;border-bottom:1px solid #e5e7eb;text-align:left;}"
             + "th{background:#f0f4f1;}"
             + "tr:hover{background:#fafafa;}"
             + "form.inline{display:inline;}"
             + "label{display:block;margin:10px 0 4px;font-weight:bold;}"
             + "input[type=text],input[type=email],input[type=number],input[type=tel],"
             + "input[type=datetime-local],select{width:100%;max-width:420px;padding:8px;"
             + "border:1px solid #cbd5e0;border-radius:4px;font-size:14px;}"
             + "button,.btn{background:#1b4d3e;color:#fff;border:none;padding:8px 16px;"
             + "border-radius:4px;cursor:pointer;font-size:14px;text-decoration:none;"
             + "display:inline-block;margin:4px 4px 4px 0;}"
             + "button:hover,.btn:hover{background:#236654;}"
             + ".btn.secondary{background:#6b7280;}"
             + ".btn.danger{background:#b91c1c;}"
             + ".flash{padding:10px;border-radius:4px;margin-bottom:12px;}"
             + ".flash.ok{background:#d1fae5;color:#065f46;}"
             + ".flash.error{background:#fee2e2;color:#991b1b;}"
             + ".muted{color:#6b7280;font-size:13px;}"
             + ".cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));"
             + "gap:12px;margin-top:12px;}"
             + ".card{background:#f0f4f1;padding:16px;border-radius:6px;}"
             + ".card .num{font-size:28px;font-weight:bold;color:#1b4d3e;}";
    }
}
