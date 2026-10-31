package android.content;

/** 只保留电量广播的两个 extra key（SecurityBodyAdapter 用）。 */
public class Intent {

    public static final String ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_STATUS = "status";
    public static final int ACTION_VIEW = 0;

    private String action;

    public Intent() {
    }

    public Intent(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public Intent setAction(String action) {
        this.action = action;
        return this;
    }

    public int getIntExtra(String name, int defaultValue) {
        if (EXTRA_LEVEL.equals(name)) {
            return 80;
        }
        if (EXTRA_STATUS.equals(name)) {
            return 3; // BATTERY_STATUS_DISCHARGING
        }
        return defaultValue;
    }

    public String getStringExtra(String name) {
        return null;
    }

    public Intent putExtra(String name, int value) {
        return this;
    }

    public Intent putExtra(String name, String value) {
        return this;
    }
}
