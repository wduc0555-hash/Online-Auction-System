import java.util.ArrayList;
import java.util.List;

public class Seller extends User {
    private double rating;
    private List<Item> listedItems;

    public Seller(String id, String username, String password, String fullName, String email, double rating) {
        super(id, username, password, fullName, email, Role.SELLER);
        this.rating = rating;
        this.listedItems = new ArrayList<>();
    }

    public double getRating() {
        return rating;
    }

    public List<Item> getListedItems() {
        return listedItems;
    }

    // tạo phiên đấu giá
    public Auction createAuction(Item item, double startingPrice, long durationMinutes) {
        listedItems.add(item);
        Auction auction = new Auction(
                generateAuctionId(),
                item.getId(),
                startingPrice,
                durationMinutes
        );
        return auction;
    }

    // Xem danh sách các phiên đấu giá đang hoạt động của Seller
    public List<Auction> viewActiveAuctions() {
        List<Auction> activeAuctions = new ArrayList<>();
        return activeAuctions;
    }

    @Override
    public void displayInfo() {
        System.out.println("+) Seller: " + getFullName() + " - Rating: " + rating);
    }

    private String generateAuctionId() {
        return "AUC_" + System.currentTimeMillis();
    }
}