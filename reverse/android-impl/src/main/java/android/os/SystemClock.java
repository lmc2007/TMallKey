package android.os;

public final class SystemClock {

    private static final long BASE = System.currentTimeMillis();

    private SystemClock() {
    }

    public static long elapsedRealtime() {
        return System.currentTimeMillis() - BASE;
    }

    public static long uptimeMillis() {
        return System.currentTimeMillis() - BASE;
    }

    public static long currentThreadTimeMillis() {
        return elapsedRealtime();
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
