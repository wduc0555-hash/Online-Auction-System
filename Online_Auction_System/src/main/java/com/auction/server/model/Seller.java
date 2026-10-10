import java.util.ArrayList;
import java.util.List;

public class Bidder extends User {
    private double balance;
    private List<Bid> bidHistory;

    public Bidder(String id, String username, String password, String fullName, String email, double balance) {
        super(id, username, password, fullName, email, Role.BIDDER);
        this.balance = balance;
        this.bidHistory = new ArrayList<>();
    }

    public double getBalance() {
        return balance;
    }

    public List<Bid> getBidHistory() {
        return bidHistory;
    }

    public boolean placeBid(Auction auction, double amount) {
        if (auction == null || amount <= 0 || this.balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }

    public void deductBalance(double amount) {
        if (amount > 0 && this.balance >= amount) {
            this.balance -= amount;
        }
    }

    public void refundBalance(double amount) {
        if (amount > 0) {
            this.balance += amount;
        }
    }

    @Override
    public void displayInfo() {
        System.out.println("Bidder: " + getFullName() + " - " + "Balance: " + balance);
    }
}
