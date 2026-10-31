package android.net.wifi;

public class WifiManager {

    private final WifiInfo info = new WifiInfo();

    public WifiInfo getConnectionInfo() {
        return info;
    }

    public boolean isWifiEnabled() {
        return false;
    }

    public int getWifiState() {
        return 1; // WIFI_STATE_DISABLED
    }

    public int getIpAddress() {
        return 0;
    }
}
