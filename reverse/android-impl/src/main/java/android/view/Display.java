package android.view;

import android.util.DisplayMetrics;

public class Display {

    public int getWidth() {
        return 1080;
    }

    public int getHeight() {
        return 2340;
    }

    public int getRotation() {
        return 0;
    }

    public void getMetrics(DisplayMetrics outMetrics) {
        if (outMetrics != null) {
            outMetrics.widthPixels = 1080;
            outMetrics.heightPixels = 2340;
            outMetrics.density = 2.75f;
            outMetrics.densityDpi = 440;
            outMetrics.scaledDensity = 2.75f;
            outMetrics.xdpi = 400f;
            outMetrics.ydpi = 400f;
        }
    }

    public void getRealMetrics(DisplayMetrics outMetrics) {
        getMetrics(outMetrics);
    }
}
