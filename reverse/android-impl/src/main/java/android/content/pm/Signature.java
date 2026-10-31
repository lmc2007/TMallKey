package android.content.pm;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

public class Signature {

    private final byte[] signature;

    private static byte[] OFFICIAL = null;

    /**
     * 官方 APK 的签名证书 DER（从 APK 的 META-INF/CERT.RSA 解出）。
     * SG 会校验 APK 签名，用它算 HMAC/摘要；给个假的两字节数组会让 SG 走降级路径。
     */
    public static synchronized byte[] official() {
        if (OFFICIAL != null) {
            return OFFICIAL;
        }
        String explicit = System.getProperty("sg.cert");
        java.io.File f = explicit != null ? new java.io.File(explicit) : findCert();
        if (f != null && f.isFile()) {
            try {
                OFFICIAL = java.nio.file.Files.readAllBytes(f.toPath());
            } catch (Exception e) {
                OFFICIAL = new byte[]{0x30, (byte) 0x82};
            }
        } else {
            OFFICIAL = new byte[]{0x30, (byte) 0x82};
        }
        return OFFICIAL;
    }

    private static java.io.File findCert() {
        for (String prefix : new String[]{"", "../", "../../", "../../../", "../../../../"}) {
            java.io.File f = new java.io.File(prefix + "tools/unidbg-tmall/cert.der");
            if (f.isFile()) {
                return f.getAbsoluteFile();
            }
        }
        return null;
    }

    public Signature(byte[] signature) {
        this.signature = signature == null ? new byte[0] : signature;
    }

    public Signature(Certificate cert) throws CertificateEncodingException {
        this(cert == null ? null : cert.getEncoded());
    }

    public byte[] toByteArray() {
        return signature;
    }

    public char[] toChars() {
        return toCharsString().toCharArray();
    }

    public String toCharsString() {
        StringBuilder sb = new StringBuilder(signature.length * 2);
        for (byte b : signature) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String toSha256() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(signature)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(signature);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Signature
                && java.util.Arrays.equals(signature, ((Signature) obj).signature);
    }
}
