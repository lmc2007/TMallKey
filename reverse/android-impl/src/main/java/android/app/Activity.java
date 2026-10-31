package android.app;

import android.os.Bundle;

/** 宿主上没有真实 Activity，只作为类型占位。 */
public class Activity {

    public void onCreate(Bundle savedInstanceState) {
    }

    public void onResume() {
    }

    public void onPause() {
    }

    public void onStop() {
    }

    public void runOnUiThread(Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
