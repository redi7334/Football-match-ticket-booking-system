/**
 * Entry point of the Football Match Ticket Booking System (web version).
 *
 * Port resolution (in order):
 *   1. First command-line argument (e.g., `java Main 9090`)
 *   2. PORT environment variable (used by Render, Railway, Heroku, etc.)
 *   3. Default 8080 (for local development)
 *
 * Local usage:
 *     javac *.java
 *     java Main           # http://localhost:8080
 *     java Main 9090      # http://localhost:9090
 *
 * Cloud usage:
 *     The platform sets PORT automatically. Just `java Main`.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = resolvePort(args);

        BookingSystem system = new BookingSystem();
        system.loadSampleData();

        WebServer server = new WebServer(system, port);
        server.start();
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            try { return Integer.parseInt(args[0]); }
            catch (NumberFormatException e) {
                System.err.println("Invalid port argument: " + args[0]);
            }
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try { return Integer.parseInt(envPort.trim()); }
            catch (NumberFormatException e) {
                System.err.println("Invalid PORT env var: " + envPort);
            }
        }
        return 8080;
    }
}
