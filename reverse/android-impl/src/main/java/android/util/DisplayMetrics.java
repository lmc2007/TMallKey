package android.util;

public class DisplayMetrics {

    public static final int DENSITY_LOW = 120;
    public static final int DENSITY_MEDIUM = 160;
    public static final int DENSITY_HIGH = 240;
    public static final int DENSITY_XHIGH = 320;
    public static final int DENSITY_XXHIGH = 480;

    public int widthPixels = 1080;
    public int heightPixels = 2340;
    public float density = 2.75f;
    public int densityDpi = 440;
    public float scaledDensity = 2.75f;
    public float xdpi = 400f;
    public float ydpi = 400f;

    public void setToDefaults() {
    }
}
