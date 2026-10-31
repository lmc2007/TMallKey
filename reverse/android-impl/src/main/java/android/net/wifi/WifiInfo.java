package android.net.wifi;

public class WifiInfo {

    public String getMacAddress() {
        return "02:00:00:00:00:00";
    }

    public String getSSID() {
        return "<unknown ssid>";
    }

    public String getBSSID() {
        return "02:00:00:00:00:00";
    }

    public int getIpAddress() {
        return 0;
    }

    public int getNetworkId() {
        return -1;
    }

    public int getRssi() {
        return -50;
    }

    public int getLinkSpeed() {
        return 72;
    }
}
