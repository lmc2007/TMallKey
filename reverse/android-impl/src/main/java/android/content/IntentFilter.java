package android.content;

public class IntentFilter {

    private final String action;

    public IntentFilter() {
        this(null);
    }

    public IntentFilter(String action) {
        this.action = action;
    }

    public String getAction(int index) {
        return index == 0 ? action : null;
    }

    public int countActions() {
        return action == null ? 0 : 1;
    }
}
