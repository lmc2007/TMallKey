package android.os;

public class Looper {

    private static final Looper MAIN = new Looper();

    public static Looper getMainLooper() {
        return MAIN;
    }

    public static Looper myLooper() {
        return MAIN;
    }

    public static void prepare() {
    }

    public static void prepareMainLooper() {
    }

    public static void loop() {
    }

    public void quit() {
    }

    public void quitSafely() {
    }

    public Thread getThread() {
        return Thread.currentThread();
    }
}
