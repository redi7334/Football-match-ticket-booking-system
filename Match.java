import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a football match (the "product" being sold).
 * Holds information about the teams playing, stadium, date, ticket price,
 * and available seats. Provides methods for managing seat availability.
 */
public class Match {

    private static int idCounter = 0;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final int id;
    private Team homeTeam;
    private Team awayTeam;
    private String stadium;
    private LocalDateTime dateTime;
    private double ticketPrice;
    private int totalSeats;
    private int availableSeats;

    public Match(Team homeTeam, Team awayTeam, String stadium,
                 LocalDateTime dateTime, double ticketPrice, int totalSeats) {
        this.id = ++idCounter;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.stadium = stadium;
        this.dateTime = dateTime;
        this.ticketPrice = ticketPrice;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
    }

    // --- Getters ---
    public int getId() { return id; }
    public Team getHomeTeam() { return homeTeam; }
    public Team getAwayTeam() { return awayTeam; }
    public String getStadium() { return stadium; }
    public LocalDateTime getDateTime() { return dateTime; }
    public double getTicketPrice() { return ticketPrice; }
    public int getTotalSeats() { return totalSeats; }
    public int getAvailableSeats() { return availableSeats; }

    // --- Setters (for modifying existing details) ---
    public void setHomeTeam(Team homeTeam) { this.homeTeam = homeTeam; }
    public void setAwayTeam(Team awayTeam) { this.awayTeam = awayTeam; }
    public void setStadium(String stadium) { this.stadium = stadium; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public void setTicketPrice(double ticketPrice) { this.ticketPrice = ticketPrice; }

    public void setTotalSeats(int totalSeats) {
        int sold = this.totalSeats - this.availableSeats;
        if (totalSeats < sold) {
            throw new IllegalArgumentException(
                    "Cannot set total seats below already sold tickets (" + sold + ").");
        }
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats - sold;
    }

    // --- Behavior ---
    public boolean hasAvailableSeats() {
        return availableSeats > 0;
    }

    public boolean reserveSeat() {
        if (!hasAvailableSeats()) return false;
        availableSeats--;
        return true;
    }

    public void releaseSeat() {
        if (availableSeats < totalSeats) {
            availableSeats++;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "Match #%d | %s vs %s | %s | %s | Price: %.2f | Seats: %d/%d available",
                id, homeTeam.getName(), awayTeam.getName(),
                stadium, dateTime.format(FORMATTER),
                ticketPrice, availableSeats, totalSeats);
    }
}
