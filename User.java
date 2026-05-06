/**
 * Represents a login account in the system.
 * Has a role (ADMIN or CLIENT) that determines what the user can do.
 * Clients also have a linked Customer profile (the "person" record used for bookings).
 */
public class User {

    public enum Role { ADMIN, CLIENT }

    private static int idCounter = 0;

    private final int id;
    private final String username;
    private String passwordHash;
    private String salt;
    private final Role role;
    /** ID of the linked Customer in BookingSystem; null for admin accounts. */
    private final Integer customerId;

    public User(String username, String passwordHash, String salt,
                Role role, Integer customerId) {
        this.id = ++idCounter;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.customerId = customerId;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public Role getRole() { return role; }
    public Integer getCustomerId() { return customerId; }

    public boolean isAdmin()  { return role == Role.ADMIN; }
    public boolean isClient() { return role == Role.CLIENT; }

    public void setPassword(String newHash, String newSalt) {
        this.passwordHash = newHash;
        this.salt = newSalt;
    }
}
