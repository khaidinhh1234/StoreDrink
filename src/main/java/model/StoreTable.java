package model;

public class StoreTable {
    private int id;
    private String name;
    private String qrCode;
    private String status;

    public int getId() { return id; }
    public String getName() { return name; }
    public String getQrCode() { return qrCode; }
    public String getStatus() { return status; }
    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public void setStatus(String status) { this.status = status; }
}
