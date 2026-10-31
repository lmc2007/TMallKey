package android.os;

import java.util.HashMap;
import java.util.Map;

public class Bundle {

    private final Map<String, Object> map = new HashMap<>();

    public Bundle() {
    }

    public void putString(String key, String value) {
        map.put(key, value);
    }

    public void putInt(String key, int value) {
        map.put(key, value);
    }

    public void putLong(String key, long value) {
        map.put(key, value);
    }

    public void putBoolean(String key, boolean value) {
        map.put(key, value);
    }

    public String getString(String key) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : null;
    }

    public int getInt(String key, int def) {
        Object v = map.get(key);
        return v instanceof Integer ? (Integer) v : def;
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }
}
