import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a ticket booked by a customer for a specific match.
 * Each ticket has a unique seat number within the match.
 */
public class Ticket {

    private static int idCounter = 5000;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final int id;
    private final Match match;
    private final Customer customer;
    private final int seatNumber;
    private final double price;
    private final LocalDateTime bookingDate;

    public Ticket(Match match, Customer customer, int seatNumber) {
        this.id = ++idCounter;
        this.match = match;
        this.customer = customer;
        this.seatNumber = seatNumber;
        this.price = match.getTicketPrice();
        this.bookingDate = LocalDateTime.now();
    }

    public int getId() { return id; }
    public Match getMatch() { return match; }
    public Customer getCustomer() { return customer; }
    public int getSeatNumber() { return seatNumber; }
    public double getPrice() { return price; }
    public LocalDateTime getBookingDate() { return bookingDate; }

    @Override
    public String toString() {
        return String.format(
                "Ticket #%d | %s vs %s | Seat: %d | Customer: %s | Price: %.2f | Booked: %s",
                id,
                match.getHomeTeam().getName(),
                match.getAwayTeam().getName(),
                seatNumber,
                customer.getName(),
                price,
                bookingDate.format(FORMATTER));
    }
}
