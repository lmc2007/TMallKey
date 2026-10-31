package android.view;

public class WindowManager {

    private final Display display = new Display();

    public Display getDefaultDisplay() {
        return display;
    }

    public static class LayoutParams {
        public static final int FLAG_FULLSCREEN = 1024;
        public int flags;
        public int width;
        public int height;
    }
}
