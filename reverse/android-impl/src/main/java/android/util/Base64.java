package android.util;

public class Base64 {

    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    public static String encodeToString(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encodeToString(input == null ? new byte[0] : input);
    }

    public static String encodeToString(byte[] input, int offset, int len, int flags) {
        byte[] slice = new byte[len];
        System.arraycopy(input, offset, slice, 0, len);
        return encodeToString(slice, flags);
    }

    public static byte[] encode(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encode(input == null ? new byte[0] : input);
    }

    public static byte[] decode(String str, int flags) {
        try {
            return java.util.Base64.getMimeDecoder().decode(str);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static byte[] decode(byte[] input, int flags) {
        return decode(new String(input), flags);
    }
}
