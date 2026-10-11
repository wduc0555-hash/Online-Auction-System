import java.time.LocalDateTime;

public class Bid implements Comparable<Bid> {
    private String id;
    private String auctionId;
    private String bidderId;
    private double amount;
    private LocalDateTime timestamp;

    public Bid(String id, String auctionId, String bidderId, double amount, LocalDateTime timestamp) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getBidder() {
        return bidderId;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public int compareTo(Bid other) {
        if (other == null) return 1;
      
        int priceCompare = Double.compare(this.amount, other.amount);
        if (priceCompare != 0) {
            return priceCompare;
        }
        return this.timestamp.compareTo(other.timestamp);
    }
}
