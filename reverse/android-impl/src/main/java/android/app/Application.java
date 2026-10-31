package android.app;

import android.content.Context;
import android.os.Bundle;

/**
 * SG 里多处判断 `context instanceof Application`（InvocationHandlerAdapter / LifeCycle），
 * 所以宿主侧必须传 Application 而不是裸 Context。
 */
public class Application extends Context {

    public Application() {
        super();
    }

    public Application(String packageName, String dataDir) {
        super(packageName, dataDir);
    }

    public void onCreate() {
    }

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
    }

    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity activity, Bundle savedInstanceState);

        void onActivityStarted(Activity activity);

        void onActivityResumed(Activity activity);

        void onActivityPaused(Activity activity);

        void onActivityStopped(Activity activity);

        void onActivitySaveInstanceState(Activity activity, Bundle outState);

        void onActivityDestroyed(Activity activity);
    }
}
