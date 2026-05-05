/**
 * Represents a customer (client) of the ticket booking system.
 * All fields are encapsulated and accessed through getters and setters.
 */
public class Customer {

    private static int idCounter = 1000;

    private final int id;
    private String name;
    private String email;
    private String phone;

    public Customer(String name, String email, String phone) {
        this.id = ++idCounter;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }

    @Override
    public String toString() {
        return String.format("Customer #%d | %s | %s | %s", id, name, email, phone);
    }
}
