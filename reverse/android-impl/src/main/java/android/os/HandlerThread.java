package android.os;

public class HandlerThread extends Thread {

    private Looper looper = Looper.getMainLooper();
    private final int priority;

    public HandlerThread(String name) {
        this(name, 0);
    }

    public HandlerThread(String name, int priority) {
        super(name);
        this.priority = priority;
        setDaemon(true);
    }

    public synchronized void start() {
        this.looper = Looper.getMainLooper();
    }

    public Looper getLooper() {
        return looper;
    }

    public int getThreadId() {
        return (int) getId();
    }

    public boolean quit() {
        return true;
    }

    public boolean quitSafely() {
        return true;
    }
}
