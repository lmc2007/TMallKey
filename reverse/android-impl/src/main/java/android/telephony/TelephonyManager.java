package android.telephony;

public class TelephonyManager {

    public static final int PHONE_TYPE_GSM = 1;
    public static final int SIM_STATE_READY = 5;
    public static final int NETWORK_TYPE_LTE = 13;

    public String getDeviceId() {
        return "863000000000001";
    }

    public String getImei() {
        return getDeviceId();
    }

    public String getMeid() {
        return "";
    }

    public String getSubscriberId() {
        return "460000000000001";
    }

    public String getSimSerialNumber() {
        return "89860000000000000000";
    }

    public String getLine1Number() {
        return "";
    }

    public String getSimOperator() {
        return "46000";
    }

    public String getSimOperatorName() {
        return "CHINA MOBILE";
    }

    public String getNetworkOperator() {
        return "46000";
    }

    public String getNetworkOperatorName() {
        return "CHINA MOBILE";
    }

    public int getPhoneType() {
        return PHONE_TYPE_GSM;
    }

    public int getSimState() {
        return SIM_STATE_READY;
    }

    public int getNetworkType() {
        return NETWORK_TYPE_LTE;
    }

    public String getNetworkCountryIso() {
        return "cn";
    }

    public String getSimCountryIso() {
        return "cn";
    }
}
