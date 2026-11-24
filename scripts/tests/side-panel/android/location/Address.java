package android.location;

public final class Address {
    private final String province, city, subAdmin, subLocality;
    public Address(String province, String city, String subAdmin, String subLocality) {
        this.province = province; this.city = city; this.subAdmin = subAdmin; this.subLocality = subLocality;
    }
    public String getCountryCode() { return "CN"; }
    public String getAdminArea() { return province; }
    public String getLocality() { return city; }
    public String getSubAdminArea() { return subAdmin; }
    public String getSubLocality() { return subLocality; }
}
