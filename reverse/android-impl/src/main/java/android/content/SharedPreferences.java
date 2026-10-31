package android.content;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 内存版 SharedPreferences：SG 用它存设备指纹缓存、初始化标记。 */
public class SharedPreferences {

    private final Map<String, Object> values = new HashMap<>();
    private final String name;

    public SharedPreferences(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    public String getString(String key, String defValue) {
        Object v = values.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object v = values.get(key);
        return v instanceof Set ? (Set<String>) v : defValues;
    }

    public int getInt(String key, int defValue) {
        Object v = values.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    public long getLong(String key, long defValue) {
        Object v = values.get(key);
        return v instanceof Long ? (Long) v : defValue;
    }

    public float getFloat(String key, float defValue) {
        Object v = values.get(key);
        return v instanceof Float ? (Float) v : defValue;
    }

    public boolean getBoolean(String key, boolean defValue) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public Editor edit() {
        return new Editor();
    }

    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
    }

    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
    }

    public interface OnSharedPreferenceChangeListener {
        void onSharedPreferenceChanged(SharedPreferences prefs, String key);
    }

    public class Editor {

        private final Map<String, Object> pending = new HashMap<>();
        private final Set<String> removes = new HashSet<>();
        private boolean clear;

        public Editor putString(String key, String value) {
            pending.put(key, value);
            return this;
        }

        public Editor putStringSet(String key, Set<String> value) {
            pending.put(key, value);
            return this;
        }

        public Editor putInt(String key, int value) {
            pending.put(key, value);
            return this;
        }

        public Editor putLong(String key, long value) {
            pending.put(key, value);
            return this;
        }

        public Editor putFloat(String key, float value) {
            pending.put(key, value);
            return this;
        }

        public Editor putBoolean(String key, boolean value) {
            pending.put(key, value);
            return this;
        }

        public Editor remove(String key) {
            removes.add(key);
            return this;
        }

        public Editor clear() {
            clear = true;
            return this;
        }

        public boolean commit() {
            applyNow();
            return true;
        }

        public void apply() {
            applyNow();
        }

        private void applyNow() {
            if (clear) {
                values.clear();
            }
            for (String k : removes) {
                values.remove(k);
            }
            values.putAll(pending);
            pending.clear();
            removes.clear();
            clear = false;
        }
    }
}
