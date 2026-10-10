public abstract class Item{
    private String id, name, description, sellerId;
    private double startingPrice;
    public Item(String id,String name,String description,String sellerId,double startingPrice){
        this.id = id;
        this.name = name;
        this.description = description;
        this.sellerId = sellerId;
        this.startingPrice = startingPrice;
    }
    public String getId(){return id;}
    public String getName(){return name;}
    public String getDescription(){return  description;}
    public String getSellerId(){return sellerId;}
    public double getStartingPrice(){return startingPrice;}
    public abstract String getItemDetails();
}