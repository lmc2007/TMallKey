package android.content.pm;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

public class Signature {

    private final byte[] signature;

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
