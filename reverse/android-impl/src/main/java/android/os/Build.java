package android.os;

/**
 * 字段全部对齐操作员的真机（OnePlus PKR110 / Android 16 / serialno dfe6d8a8）。
 *
 * 为什么必须对齐：SG 会把设备指纹算进签名，而且 {@code <data>/app_SGLib} 里的缓存数据
 * （含 AVMP 与密钥）是**设备绑定**的。宿主 JVM 里如果还是"小米 thyme"，
 * 缓存解不开，SG 就会回退去读 APK 安全图片，最终表现为 x-sign 报 608/611。
 *
 * VERSION.SDK_INT 暂时保持 30：android-impl 没有实现 Android 12+ 的新 API，
 * 调到 36 会让 SG 走我们没桩的分支。先只对齐"身份类"字段。
 */
public class Build {

    private Build() {
    }

    public static final String MANUFACTURER = "OnePlus";
    public static final String BRAND = "OnePlus";
    public static final String MODEL = "PKR110";
    public static final String DEVICE = "OP60EBL1";
    public static final String PRODUCT = "PKR110";
    public static final String HARDWARE = "qcom";
    public static final String BOARD = "sun";
    public static final String DISPLAY = "PKR110_16.0.10.500(CN01)";
    public static final String ID = "AP3A.240617.008";
    public static final String TAGS = "release-keys";
    public static final String TYPE = "user";
    public static final String USER = "root";
    public static final String HOST = "kvm-slave-build-s-system-12839331";
    public static final String FINGERPRINT =
            "OnePlus/PKR110/OP60EBL1:16/AP3A.240617.008/V.49a177f-24777f8-24a1827:user/release-keys";
    public static final String BOOTLOADER = "unknown";
    public static final String SERIAL = "dfe6d8a8";
    /** ro.build.date.utc */
    public static final long TIME = 1786442564000L;
    public static final String RADIO = "unknown";
    public static final String SOC_MANUFACTURER = "Qualcomm";
    public static final String SOC_MODEL = "SM8750";

    /** 设备序列号（部分 SDK 走 Build.getSerial()）。 */
    public static String getSerial() {
        return SERIAL;
    }

    public static class VERSION {
        public static final int SDK_INT = 30;
        public static final String RELEASE = "11";
        public static final String CODENAME = "REL";
        public static final String INCREMENTAL = "V.49a177f-24777f8-24a1827";
        public static final String SDK = "30";
        public static final String SECURITY_PATCH = "2026-08-01";
        public static final String BASE_OS = "";
        public static final String PREVIEW_SDK_INT = "0";
    }

    public static class VERSION_CODES {
        public static final int BASE = 1;
        public static final int LOLLIPOP = 21;
        public static final int M = 23;
        public static final int N = 24;
        public static final int O = 26;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int TIRAMISU = 33;
    }
}
