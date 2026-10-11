public class Electronics extends Item {
    private String brand;
    private String model;
    private int warrantyMonths;
    public Electronics(String id, String name, String description, double startingPrice, String sellerId,
                       String brand, String model, int warrantyMonths) {
        super(id, name, description, startingPrice, sellerId);
        this.brand = brand;
        this.model = model;
        this.warrantyMonths = warrantyMonths;
    }
    public String getBrand() {
        return brand;
    }
    public String getModel() {
        return model;
    }
    public int getWarrantyMonths() {
        return warrantyMonths;
    }
    @Override
    public String getItemDetails() {
        return "Brand: " + brand + " - Model: " + model + " - Warranty Months: " + warrantyMonths;
    }
}