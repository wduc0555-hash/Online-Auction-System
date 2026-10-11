public class Art extends Item {
    private String artist;
    private int yearCreated;
    private boolean certificateOfAuthenticity;
    public Art(String id, String name, String description, double startingPrice,
               String sellerId, String artist, int yearCreated, boolean certificateOfAuthenticity){
        super(id, name, description, startingPrice, sellerId);
        this.artist = artist;
        this.yearCreated = yearCreated;
        this.certificateOfAuthenticity = certificateOfAuthenticity;
    }
    public String getArtist(){
        return artist;
    }
    public int getyearYearCreated(){
        return yearCreated;
    }
    public boolean hasCertificate(){
        return certificateOfAuthenticity;
    }
    @Override
    public String getItemDetails(){
        return "Artist" + artist + "- Year Created: " + "- Certificate" + certificateOfAuthenticity;
    }
}

