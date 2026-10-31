package android.util;

public final class Log {

    public static boolean enabled = true;

    private Log() {
    }

    public static int v(String tag, String msg) {
        return p("V", tag, msg, null);
    }

    public static int d(String tag, String msg) {
        return p("D", tag, msg, null);
    }

    public static int i(String tag, String msg) {
        return p("I", tag, msg, null);
    }

    public static int w(String tag, String msg) {
        return p("W", tag, msg, null);
    }

    public static int e(String tag, String msg) {
        return p("E", tag, msg, null);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return p("E", tag, msg, tr);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return p("W", tag, msg, tr);
    }

    private static int p(String level, String tag, String msg, Throwable tr) {
        if (enabled) {
            System.out.println("[android/" + level + "] " + tag + ": " + msg
                    + (tr == null ? "" : " (" + tr + ")"));
        }
        return 0;
    }

    public static boolean isLoggable(String tag, int level) {
        return true;
    }

    public static String getStackTraceString(Throwable tr) {
        return String.valueOf(tr);
    }
}
