import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Central service that manages all data and operations of the
 * football match ticket booking system.
 *
 * Holds collections of customers, teams, matches, and tickets,
 * and provides methods to register, modify, search, and book.
 *
 * All data and behavior are encapsulated within this object,
 * accessed only through its public methods.
 */
public class BookingSystem {

    private final List<Customer> customers = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final List<Match> matches = new ArrayList<>();
    private final List<Ticket> tickets = new ArrayList<>();

    // ============================================================
    // CUSTOMER MANAGEMENT
    // ============================================================

    public Customer registerCustomer(String name, String email, String phone) {
        Customer c = new Customer(name, email, phone);
        customers.add(c);
        return c;
    }

    public Customer findCustomerById(int id) {
        for (Customer c : customers) {
            if (c.getId() == id) return c;
        }
        return null;
    }

    public boolean updateCustomer(int id, String name, String email, String phone) {
        Customer c = findCustomerById(id);
        if (c == null) return false;
        if (name != null && !name.isBlank())  c.setName(name);
        if (email != null && !email.isBlank()) c.setEmail(email);
        if (phone != null && !phone.isBlank()) c.setPhone(phone);
        return true;
    }

    public boolean removeCustomer(int id) {
        Customer c = findCustomerById(id);
        if (c == null) return false;
        // also remove their tickets and free up seats
        List<Ticket> toRemove = new ArrayList<>();
        for (Ticket t : tickets) {
            if (t.getCustomer().getId() == id) {
                t.getMatch().releaseSeat();
                toRemove.add(t);
            }
        }
        tickets.removeAll(toRemove);
        return customers.remove(c);
    }

    public List<Customer> getAllCustomers() {
        return new ArrayList<>(customers);
    }

    // ============================================================
    // TEAM MANAGEMENT
    // ============================================================

    public Team registerTeam(String name, String city) {
        Team t = new Team(name, city);
        teams.add(t);
        return t;
    }

    public Team findTeamById(int id) {
        for (Team t : teams) {
            if (t.getId() == id) return t;
        }
        return null;
    }

    public boolean updateTeam(int id, String name, String city) {
        Team t = findTeamById(id);
        if (t == null) return false;
        if (name != null && !name.isBlank()) t.setName(name);
        if (city != null && !city.isBlank()) t.setCity(city);
        return true;
    }

    public List<Team> getAllTeams() {
        return new ArrayList<>(teams);
    }

    // ============================================================
    // MATCH MANAGEMENT (the "products")
    // ============================================================

    public Match registerMatch(int homeTeamId, int awayTeamId, String stadium,
                               LocalDateTime dateTime, double price, int totalSeats) {
        Team home = findTeamById(homeTeamId);
        Team away = findTeamById(awayTeamId);
        if (home == null || away == null) {
            throw new IllegalArgumentException("Both teams must exist before creating a match.");
        }
        if (home.getId() == away.getId()) {
            throw new IllegalArgumentException("Home team and away team must be different.");
        }
        Match m = new Match(home, away, stadium, dateTime, price, totalSeats);
        matches.add(m);
        return m;
    }

    public Match findMatchById(int id) {
        for (Match m : matches) {
            if (m.getId() == id) return m;
        }
        return null;
    }

    public boolean updateMatch(int id, String stadium, LocalDateTime dateTime,
                               Double price, Integer totalSeats) {
        Match m = findMatchById(id);
        if (m == null) return false;
        if (stadium != null && !stadium.isBlank()) m.setStadium(stadium);
        if (dateTime != null) m.setDateTime(dateTime);
        if (price != null) m.setTicketPrice(price);
        if (totalSeats != null) m.setTotalSeats(totalSeats);
        return true;
    }

    public boolean removeMatch(int id) {
        Match m = findMatchById(id);
        if (m == null) return false;
        // also remove tickets associated with this match
        tickets.removeIf(t -> t.getMatch().getId() == id);
        return matches.remove(m);
    }

    public List<Match> getAllMatches() {
        return new ArrayList<>(matches);
    }

    public List<Match> getAvailableMatches() {
        List<Match> available = new ArrayList<>();
        for (Match m : matches) {
            if (m.hasAvailableSeats()) available.add(m);
        }
        return available;
    }

    // ============================================================
    // TICKET BOOKING
    // ============================================================

    public Ticket bookTicket(int customerId, int matchId) {
        Customer c = findCustomerById(customerId);
        Match m = findMatchById(matchId);
        if (c == null) throw new IllegalArgumentException("Customer not found.");
        if (m == null) throw new IllegalArgumentException("Match not found.");
        if (!m.hasAvailableSeats()) {
            throw new IllegalStateException("No seats available for this match.");
        }
        // Compute next seat number based on the highest seat already assigned
        // for this match (so cancellations don't produce duplicates).
        int maxSeat = 0;
        for (Ticket existing : tickets) {
            if (existing.getMatch().getId() == m.getId()
                    && existing.getSeatNumber() > maxSeat) {
                maxSeat = existing.getSeatNumber();
            }
        }
        int seatNumber = maxSeat + 1;
        m.reserveSeat();
        Ticket t = new Ticket(m, c, seatNumber);
        tickets.add(t);
        return t;
    }

    public boolean cancelTicket(int ticketId) {
        Ticket target = null;
        for (Ticket t : tickets) {
            if (t.getId() == ticketId) {
                target = t;
                break;
            }
        }
        if (target == null) return false;
        target.getMatch().releaseSeat();
        return tickets.remove(target);
    }

    public List<Ticket> getAllTickets() {
        return new ArrayList<>(tickets);
    }

    public List<Ticket> getTicketsForCustomer(int customerId) {
        List<Ticket> result = new ArrayList<>();
        for (Ticket t : tickets) {
            if (t.getCustomer().getId() == customerId) result.add(t);
        }
        return result;
    }

    public double getTotalRevenue() {
        double total = 0;
        for (Ticket t : tickets) total += t.getPrice();
        return total;
    }

    // ============================================================
    // SAMPLE DATA (helpful for testing the menu)
    // ============================================================

    public void loadSampleData() {
        Team t1 = registerTeam("KF Tirana", "Tirana");
        Team t2 = registerTeam("KF Partizani", "Tirana");
        Team t3 = registerTeam("KF Vllaznia", "Shkoder");
        Team t4 = registerTeam("KF Skenderbeu", "Korce");

        registerMatch(t1.getId(), t2.getId(), "Air Albania Stadium",
                LocalDateTime.of(2026, 5, 20, 19, 30), 800.0, 50);
        registerMatch(t3.getId(), t4.getId(), "Loro Borici Stadium",
                LocalDateTime.of(2026, 5, 25, 18, 0), 600.0, 30);
        registerMatch(t1.getId(), t3.getId(), "Air Albania Stadium",
                LocalDateTime.of(2026, 6, 1, 20, 0), 1000.0, 40);

        registerCustomer("Redi Mema", "redimema2211@gmail.com", "0691234567");
        registerCustomer("Ana Hoxha", "ana.hoxha@example.com", "0687654321");
    }
}
