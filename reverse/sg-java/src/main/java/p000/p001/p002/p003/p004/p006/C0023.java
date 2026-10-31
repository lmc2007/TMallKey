package p000.p001.p002.p003.p004.p006;

/* renamed from: а.а.а.а.а.б.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0023 {

    /* renamed from: а, reason: contains not printable characters */
    private static final char[] f85 = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /* renamed from: а, reason: contains not printable characters */
    public static String m97(byte[] bArr) {
        if (bArr == null || bArr.length == 0) {
            return null;
        }
        int length = bArr.length;
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            sb.append(f85[(bArr[i] >> 4) & 15]);
            sb.append(f85[bArr[i] & 15]);
        }
        return sb.toString();
    }

    /* renamed from: а, reason: contains not printable characters */
    public static byte[] m98(String str) {
        if (str == null || "".equals(str)) {
            return null;
        }
        int length = str.length();
        byte[] bArr = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            bArr[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
        }
        return bArr;
    }
}
