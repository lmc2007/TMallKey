package android.os;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** 单线程调度版 Handler：post / postDelayed / removeCallbacks / handleMessage 都能用。 */
public class Handler {

    private static final ScheduledExecutorService POOL =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "android-handler");
                t.setDaemon(true);
                return t;
            });

    private final Looper looper;
    private final Map<Runnable, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public Handler() {
        this(Looper.getMainLooper());
    }

    public Handler(Looper looper) {
        this.looper = looper;
    }

    public Handler(Callback callback) {
        this.looper = Looper.getMainLooper();
    }

    public final Looper getLooper() {
        return looper;
    }

    public final boolean post(Runnable r) {
        return postDelayed(r, 0L);
    }

    public final boolean postDelayed(Runnable r, long delayMillis) {
        ScheduledFuture<?> f = POOL.schedule(() -> {
            pending.remove(r);
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
        pending.put(r, f);
        return true;
    }

    public final void removeCallbacks(Runnable r) {
        ScheduledFuture<?> f = pending.remove(r);
        if (f != null) {
            f.cancel(false);
        }
    }

    public final void removeCallbacksAndMessages(Object token) {
        for (Map.Entry<Runnable, ScheduledFuture<?>> e : pending.entrySet()) {
            e.getValue().cancel(false);
        }
        pending.clear();
    }

    public final boolean sendEmptyMessage(int what) {
        Message m = new Message();
        m.what = what;
        return sendMessage(m);
    }

    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message m = new Message();
        m.what = what;
        return sendMessageDelayed(m, delayMillis);
    }

    public final boolean sendMessage(Message msg) {
        return sendMessageDelayed(msg, 0L);
    }

    public final boolean sendMessageDelayed(Message msg, long delayMillis) {
        postDelayed(() -> dispatchMessage(msg), delayMillis);
        return true;
    }

    public void dispatchMessage(Message msg) {
        handleMessage(msg);
    }

    public void handleMessage(Message msg) {
    }

    public interface Callback {
        boolean handleMessage(Message msg);
    }
}
