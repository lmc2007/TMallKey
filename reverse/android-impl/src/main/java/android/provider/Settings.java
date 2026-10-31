package android.provider;

/** SG 读 android_id 之类；宿主上没有，统一返回 null（调用方都会判空）。 */
public class Settings {

    public static class Secure {
        public static String getString(Object resolver, String name) {
            return null;
        }

        public static int getInt(Object resolver, String name, int def) {
            return def;
        }
    }

    public static class System {
        public static String getString(Object resolver, String name) {
            return null;
        }

        public static int getInt(Object resolver, String name, int def) {
            return def;
        }
    }

    public static class Global {
        public static String getString(Object resolver, String name) {
            return null;
        }
    }
}
