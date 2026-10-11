public class Vehicle extends Item {
    private String vinNumber;
    private double mileage;
    private int manufacturingYear;

    public Vehicle(String id, String name, String description, double startingPrice, String sellerId, 
                   String vinNumber, double mileage, int manufacturingYear) {
        super(id, name, description, startingPrice, sellerId);
        this.vinNumber = vinNumber;
        this.mileage = mileage;
        this.manufacturingYear = manufacturingYear;
    }

    public String getVinNumber() { return vinNumber; }
    public void setVinNumber(String vinNumber) { this.vinNumber = vinNumber; }

    public double getMileage() { return mileage; }
    public void setMileage(double mileage) { this.mileage = mileage; }

    public int getManufacturingYear() { return manufacturingYear; }
    public void setManufacturingYear(int manufacturingYear) { this.manufacturingYear = manufacturingYear; }

    @Override
    public String getItemDetails() {
        return getName() + " (" + manufacturingYear + ")";
    }
}