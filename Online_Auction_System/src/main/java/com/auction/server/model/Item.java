public abstract class Item {
    private String id;
    private String name;
    private String description;
    private double startingPrice;
    private String sellerId;
    public Item(String id, String name, String description, double startingPrice, String sellerId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.sellerId = sellerId;
    }
    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public double getStartingPrice() {
        return startingPrice;
    }
    public String getSellerId() {
        return sellerId;
    }
    public abstract String getItemDetails();
}