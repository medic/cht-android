package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONObject;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Verifies P2P JWT tokens using ECDSA P-256 (ES256).
 * Uses only standard Java crypto — no external dependencies.
 *
 * Guards: G6 (signature valid), G7 (not expired)
 */
public final class JwtVerifier {

    private final PublicKey serverPublicKey;

    /**
     * @param pemPublicKey PEM-encoded ECDSA P-256 public key (with or without headers)
     */
    public JwtVerifier(String pemPublicKey) throws JwtVerificationException {
        try {
            this.serverPublicKey = parsePemPublicKey(pemPublicKey);
        } catch (Exception e) {
            throw new JwtVerificationException("Failed to parse public key: " + e.getMessage());
        }
    }

    /**
     * Verify a JWT token and return the decoded payload.
     *
     * @param jwt The complete JWT string (header.payload.signature)
     * @return Decoded payload as JSONObject
     * @throws JwtVerificationException if verification fails for any reason
     */
    public JSONObject verify(String jwt) throws JwtVerificationException {
        if (jwt == null || jwt.isEmpty()) {
            throw new JwtVerificationException("token_empty");
        }

        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new JwtVerificationException("token_malformed: expected 3 parts, got " + parts.length);
        }

        verifyHeader(parts[0]);
        verifySignature(parts[0], parts[1], parts[2]);
        JSONObject payload = decodePayload(parts[1]);
        verifyExpiry(payload);

        return payload;
    }

    private void verifyHeader(String headerPart) throws JwtVerificationException {
        JSONObject header;
        try {
            header = new JSONObject(new String(base64UrlDecode(headerPart)));
        } catch (Exception e) {
            throw new JwtVerificationException("token_malformed: invalid header");
        }

        String alg = header.optString("alg", "");
        if (!"ES256".equals(alg)) {
            throw new JwtVerificationException("token_invalid: unsupported algorithm " + alg);
        }
    }

    private void verifySignature(String headerPart, String payloadPart, String signaturePart)
            throws JwtVerificationException {
        try {
            byte[] signatureInput = (headerPart + "." + payloadPart).getBytes("UTF-8");
            byte[] signatureBytes = base64UrlDecode(signaturePart);
            byte[] derSignature = rawToDer(signatureBytes);

            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(serverPublicKey);
            sig.update(signatureInput);

            if (!sig.verify(derSignature)) {
                throw new JwtVerificationException("token_invalid: signature verification failed");
            }
        } catch (JwtVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtVerificationException("token_invalid: " + e.getMessage());
        }
    }

    private JSONObject decodePayload(String payloadPart) throws JwtVerificationException {
        try {
            return new JSONObject(new String(base64UrlDecode(payloadPart)));
        } catch (Exception e) {
            throw new JwtVerificationException("token_malformed: invalid payload");
        }
    }

    private static void verifyExpiry(JSONObject payload) throws JwtVerificationException {
        long exp = payload.optLong("exp", 0);
        long now = System.currentTimeMillis() / 1000;
        if (exp > 0 && exp < now) {
            throw new JwtVerificationException("token_expired");
        }
    }

    /**
     * Parse a PEM-encoded public key to a Java PublicKey object.
     */
    private static PublicKey parsePemPublicKey(String pem)
            throws java.security.NoSuchAlgorithmException,
                   java.security.spec.InvalidKeySpecException {
        String cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Base64URL decode (RFC 7515).
     */
    private static byte[] base64UrlDecode(String input) {
        String base64 = input
                .replace('-', '+')
                .replace('_', '/');
        // Add padding if needed
        int remainder = base64.length() % 4;
        if (remainder == 2) {
            base64 += "==";
        } else if (remainder == 3) {
            base64 += "=";
        }
        return android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
    }

    /**
     * Convert raw R||S signature (64 bytes for P-256) to DER format
     * that Java's Signature class expects.
     */
    private static byte[] rawToDer(byte[] raw) throws JwtVerificationException {
        if (raw.length != 64) {
            throw new JwtVerificationException("token_invalid: unexpected signature length " + raw.length
                    + ", ES256 requires 64 bytes (R||S)");
        }

        byte[] r = Arrays.copyOfRange(raw, 0, 32);
        byte[] s = Arrays.copyOfRange(raw, 32, 64);

        r = trimLeadingZeros(r);
        s = trimLeadingZeros(s);

        // Add leading zero if high bit set (positive integer in DER)
        if ((r[0] & 0x80) != 0) {
            byte[] padded = new byte[r.length + 1];
            System.arraycopy(r, 0, padded, 1, r.length);
            r = padded;
        }
        if ((s[0] & 0x80) != 0) {
            byte[] padded = new byte[s.length + 1];
            System.arraycopy(s, 0, padded, 1, s.length);
            s = padded;
        }

        int seqLen = 2 + r.length + 2 + s.length;
        byte[] der = new byte[2 + seqLen];
        int offset = 0;
        der[offset++] = 0x30; // SEQUENCE
        der[offset++] = (byte) seqLen;
        der[offset++] = 0x02; // INTEGER
        der[offset++] = (byte) r.length;
        System.arraycopy(r, 0, der, offset, r.length);
        offset += r.length;
        der[offset++] = 0x02; // INTEGER
        der[offset++] = (byte) s.length;
        System.arraycopy(s, 0, der, offset, s.length);

        return der;
    }

    private static byte[] trimLeadingZeros(byte[] bytes) {
        int start = 0;
        while (start < bytes.length - 1 && bytes[start] == 0) {
            start++;
        }
        if (start == 0) return bytes;
        return Arrays.copyOfRange(bytes, start, bytes.length);
    }

    /**
     * Exception thrown when JWT verification fails.
     */
    public static class JwtVerificationException extends Exception {
        private static final long serialVersionUID = 1L;
        public JwtVerificationException(String message) {
            super(message);
        }
    }
}
