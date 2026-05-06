import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user accounts and login sessions.
 *
 * Encapsulates:
 *   - The list of registered users (admin + clients)
 *   - Active sessions (sessionId -> userId), kept in memory
 *   - Password hashing (salted SHA-256)
 *
 * Self-registration creates a CLIENT user AND a linked Customer in
 * BookingSystem so the same person can book tickets immediately.
 */
public class AuthService {

    private final List<User> users = new ArrayList<>();
    private final Map<String, Integer> sessions = new ConcurrentHashMap<>();
    private final BookingSystem booking;
    private final SecureRandom random = new SecureRandom();

    public AuthService(BookingSystem booking) {
        this.booking = booking;
    }

    // ============================================================
    // ACCOUNT CREATION
    // ============================================================

    /** Create the initial admin account if it does not already exist. */
    public User seedAdmin(String username, String password) {
        User existing = findByUsername(username);
        if (existing != null) return existing;
        String salt = randomSalt();
        String hash = hash(password, salt);
        User u = new User(username, hash, salt, User.Role.ADMIN, null);
        users.add(u);
        return u;
    }

    /**
     * Self-register a new client account.
     * Also creates a linked Customer record in BookingSystem.
     */
    public User registerClient(String username, String password,
                               String name, String email, String phone) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username is required.");
        if (password == null || password.length() < 4)
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Full name is required.");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email is required.");
        if (findByUsername(username) != null)
            throw new IllegalArgumentException("Username already taken.");

        Customer customer = booking.registerCustomer(name, email, phone);
        String salt = randomSalt();
        String hash = hash(password, salt);
        User u = new User(username, hash, salt, User.Role.CLIENT, customer.getId());
        users.add(u);
        return u;
    }

    /**
     * Remove a user account.
     * If it's a client, also removes their linked Customer (and tickets).
     */
    public boolean removeUser(int userId) {
        User u = findById(userId);
        if (u == null) return false;
        if (u.isClient() && u.getCustomerId() != null) {
            booking.removeCustomer(u.getCustomerId());
        }
        // Drop any active sessions for this user
        sessions.entrySet().removeIf(e -> e.getValue().equals(userId));
        return users.remove(u);
    }

    /** Convenience used when admin deletes a Customer that has a linked User. */
    public void removeUserByCustomerId(int customerId) {
        User found = null;
        for (User u : users) {
            if (u.getCustomerId() != null && u.getCustomerId() == customerId) {
                found = u;
                break;
            }
        }
        if (found == null) return;
        final int userId = found.getId();
        sessions.entrySet().removeIf(e -> e.getValue().equals(userId));
        users.remove(found);
    }

    // ============================================================
    // AUTHENTICATION
    // ============================================================

    /** Verify credentials. Returns a new session ID if OK, otherwise null. */
    public String login(String username, String password) {
        User u = findByUsername(username);
        if (u == null) return null;
        String attempt = hash(password, u.getSalt());
        if (!attempt.equals(u.getPasswordHash())) return null;
        String sessionId = randomSession();
        sessions.put(sessionId, u.getId());
        return sessionId;
    }

    public void logout(String sessionId) {
        if (sessionId != null) sessions.remove(sessionId);
    }

    /** Look up the user attached to a session id (or null). */
    public User userBySession(String sessionId) {
        if (sessionId == null) return null;
        Integer userId = sessions.get(sessionId);
        if (userId == null) return null;
        return findById(userId);
    }

    // ============================================================
    // QUERIES
    // ============================================================

    public User findById(int id) {
        for (User u : users) if (u.getId() == id) return u;
        return null;
    }

    public User findByUsername(String username) {
        if (username == null) return null;
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(username)) return u;
        }
        return null;
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users);
    }

    public BookingSystem getBookingSystem() {
        return booking;
    }

    // ============================================================
    // CRYPTO HELPERS
    // ============================================================

    private static String hash(String plain, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            byte[] digest = md.digest(plain.getBytes());
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String randomSalt() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return bytesToHex(b);
    }

    private String randomSession() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return bytesToHex(b);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
