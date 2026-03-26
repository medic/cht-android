package org.medicmobile.webapp.mobile.p2p;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates and validates QR codes for P2P WiFi hotspot credential exchange.
 *
 * QR Payload (CONTRACT.md Section 9):
 * {
 *   "type": "cht-p2p",
 *   "v": 1,
 *   "ssid": "CHT-P2P-a3f7",
 *   "pwd": "randomPassword123",
 *   "ip": "192.168.43.1",
 *   "port": 8443,
 *   "tls": "sha256:AB:CD:EF:...",
 *   "ts": 1711152000000
 * }
 *
 * Guards:
 *   G18 — Timestamp must be within 10 minutes of current time
 *   G19 — type field must be "cht-p2p"
 *   G20 — TLS fingerprint must match server cert on connection
 *   G21 — QR regenerated each session, old codes are invalid
 */
public final class QrCodeHelper {

    private static final String TAG = "QrCodeHelper";

    private static final int QR_SIZE = 512; // pixels
    private static final String PAYLOAD_TYPE = "cht-p2p";
    private static final int PAYLOAD_VERSION = 1;
    private static final long MAX_TIMESTAMP_DRIFT_MS = 10 * 60 * 1000; // G18: 10 minutes

    private QrCodeHelper() {
        // Static utility class
    }

    /**
     * Generate a QR code bitmap from hotspot credentials.
     *
     * @param ssid           the WiFi hotspot SSID
     * @param password       the WiFi hotspot WPA2 password
     * @param ipAddress      the supervisor device IP on the hotspot network
     * @param port           the HTTPS port for the P2P HTTP server
     * @param tlsFingerprint SHA-256 fingerprint of the server's TLS certificate
     * @return QR code bitmap, or null if generation fails
     */
    public static Bitmap generateQrCode(String ssid, String password,
                                         String ipAddress, int port,
                                         String tlsFingerprint) {
        try {
            String payload = buildPayload(ssid, password, ipAddress, port, tlsFingerprint);
            return encodeQrBitmap(payload, QR_SIZE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate QR code", e);
            return null;
        }
    }

    /**
     * Generate a QR code bitmap with custom dimensions.
     *
     * @param ssid           the WiFi hotspot SSID
     * @param password       the WiFi hotspot WPA2 password
     * @param ipAddress      the supervisor device IP on the hotspot network
     * @param port           the HTTPS port for the P2P HTTP server
     * @param tlsFingerprint SHA-256 fingerprint of the server's TLS certificate
     * @param size           bitmap width and height in pixels
     * @return QR code bitmap, or null if generation fails
     */
    public static Bitmap generateQrCode(String ssid, String password,
                                         String ipAddress, int port,
                                         String tlsFingerprint, int size) {
        try {
            String payload = buildPayload(ssid, password, ipAddress, port, tlsFingerprint);
            return encodeQrBitmap(payload, size);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate QR code", e);
            return null;
        }
    }

    /**
     * Generate a QR code as a data URL string (data:image/png;base64,...).
     * This is the format expected by the webapp for rendering in an img tag.
     *
     * @param payloadJson the QR payload JSON string to encode
     * @return data URL string, or null if generation fails
     */
    public static String generateQrDataUrl(String payloadJson) {
        try {
            Bitmap bitmap = encodeQrBitmap(payloadJson, QR_SIZE);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            bitmap.recycle();
            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate QR data URL", e);
            return null;
        }
    }

    /**
     * Build the QR payload JSON string.
     *
     * @param ssid           the WiFi hotspot SSID
     * @param password       the WiFi hotspot WPA2 password
     * @param ipAddress      the supervisor device IP on the hotspot network
     * @param port           the HTTPS port for the P2P HTTP server
     * @param tlsFingerprint SHA-256 fingerprint of the server's TLS certificate
     * @return JSON string matching CONTRACT.md Section 9 format
     * @throws JSONException if JSON construction fails
     */
    public static String buildPayload(String ssid, String password,
                                       String ipAddress, int port,
                                       String tlsFingerprint) throws JSONException {
        if (ssid == null || ssid.isEmpty()) {
            throw new IllegalArgumentException("ssid must not be null or empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be null or empty");
        }
        if (ipAddress == null || ipAddress.isEmpty()) {
            throw new IllegalArgumentException("ipAddress must not be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got: " + port);
        }

        JSONObject payload = new JSONObject();
        payload.put("type", PAYLOAD_TYPE);
        payload.put("v", PAYLOAD_VERSION);
        payload.put("ssid", ssid);
        payload.put("pwd", password);
        payload.put("ip", ipAddress);
        payload.put("port", port);
        payload.put("tls", tlsFingerprint != null ? tlsFingerprint : "");
        payload.put("ts", System.currentTimeMillis());
        return payload.toString();
    }

    /**
     * Validate a scanned QR payload.
     *
     * Checks:
     *   G19: type must be "cht-p2p"
     *   G18: timestamp within 10 minutes of current time
     *   Required fields: ssid, pwd, ip, port
     *
     * @param payloadJson the scanned QR code content
     * @return ValidationResult.accept(IN_SCOPE) if valid,
     *         ValidationResult.reject(reason) if invalid
     */
    public static ValidationResult validateQrPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isEmpty()) {
            return ValidationResult.reject("empty_payload");
        }

        JSONObject payload;
        try {
            payload = new JSONObject(payloadJson);
        } catch (JSONException e) {
            return ValidationResult.reject("invalid_json: " + e.getMessage());
        }

        // G19: type must be "cht-p2p"
        String type = payload.optString("type", "");
        if (!PAYLOAD_TYPE.equals(type)) {
            return ValidationResult.reject("G19: invalid type '" + type
                    + "', expected '" + PAYLOAD_TYPE + "'");
        }

        // Check version
        int version = payload.optInt("v", -1);
        if (version != PAYLOAD_VERSION) {
            return ValidationResult.reject("unsupported version: " + version
                    + ", expected " + PAYLOAD_VERSION);
        }

        // G18: timestamp within 10 minutes
        long timestamp = payload.optLong("ts", 0);
        if (timestamp == 0) {
            return ValidationResult.reject("G18: missing timestamp");
        }
        long now = System.currentTimeMillis();
        long drift = Math.abs(now - timestamp);
        if (drift > MAX_TIMESTAMP_DRIFT_MS) {
            return ValidationResult.reject("G18: QR code expired. Timestamp drift: "
                    + (drift / 1000) + "s (max " + (MAX_TIMESTAMP_DRIFT_MS / 1000) + "s)");
        }

        // Required fields
        String ssid = payload.optString("ssid", "");
        if (ssid.isEmpty()) {
            return ValidationResult.reject("missing required field: ssid");
        }

        String password = payload.optString("pwd", "");
        if (password.isEmpty()) {
            return ValidationResult.reject("missing required field: pwd");
        }

        String ip = payload.optString("ip", "");
        if (ip.isEmpty()) {
            return ValidationResult.reject("missing required field: ip");
        }

        int port = payload.optInt("port", 0);
        if (port <= 0 || port > 65535) {
            return ValidationResult.reject("invalid port: " + port);
        }

        return ValidationResult.accept(DocScope.IN_SCOPE);
    }

    /**
     * Parse a validated QR payload into its constituent fields.
     * Call validateQrPayload() first to ensure the payload is valid.
     *
     * @param payloadJson valid QR payload JSON
     * @return parsed QrPayload object, or null if parsing fails
     */
    public static QrPayload parsePayload(String payloadJson) {
        try {
            JSONObject json = new JSONObject(payloadJson);
            return new QrPayload(
                    json.getString("ssid"),
                    json.getString("pwd"),
                    json.getString("ip"),
                    json.getInt("port"),
                    json.optString("tls", ""),
                    json.getLong("ts")
            );
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse QR payload", e);
            return null;
        }
    }

    // --- Private helpers ---

    /**
     * Encode a string into a QR code Bitmap using ZXing.
     *
     * @param content the string content to encode
     * @param size    bitmap width and height in pixels
     * @return the QR code as a Bitmap
     * @throws WriterException if ZXing encoding fails
     */
    private static Bitmap encodeQrBitmap(String content, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    /**
     * Parsed QR payload data class.
     * Holds the extracted fields from a validated QR code.
     */
    public static final class QrPayload {
        private final String ssid;
        private final String password;
        private final String ipAddress;
        private final int port;
        private final String tlsFingerprint;
        private final long timestamp;

        QrPayload(String ssid, String password, String ipAddress,
                  int port, String tlsFingerprint, long timestamp) {
            this.ssid = ssid;
            this.password = password;
            this.ipAddress = ipAddress;
            this.port = port;
            this.tlsFingerprint = tlsFingerprint;
            this.timestamp = timestamp;
        }

        public String getSsid() {
            return ssid;
        }

        public String getPassword() {
            return password;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public int getPort() {
            return port;
        }

        public String getTlsFingerprint() {
            return tlsFingerprint;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "QrPayload{ssid='" + ssid + "', ip='" + ipAddress
                    + "', port=" + port + ", ts=" + timestamp + '}';
        }
    }
}
