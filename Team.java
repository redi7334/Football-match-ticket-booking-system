/**
 * Represents a football team that can play in matches.
 */
public class Team {

    private static int idCounter = 100;

    private final int id;
    private String name;
    private String city;

    public Team(String name, String city) {
        this.id = ++idCounter;
        this.name = name;
        this.city = city;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }

    public void setName(String name) { this.name = name; }
    public void setCity(String city) { this.city = city; }

    @Override
    public String toString() {
        return String.format("%s (%s)", name, city);
    }
}
