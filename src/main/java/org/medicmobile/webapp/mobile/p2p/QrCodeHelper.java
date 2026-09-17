package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.error;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Map;

/**
	* Generates and validates QR codes for P2P WiFi hotspot credential exchange.
	*
	* QR Payload:
	* {
	*   "type": "cht-p2p",
	*   "v": 1,
	*   "ssid": "CHT-P2P-a3f7",
	*   "pwd": "randomPassword123",
	*   "ip": "192.168.43.1",
	*   "fp": "3A:7F:...:C2",          // SHA-256 of the host's public key, colon-separated hex
	*   "port": 8443,
	*   "ts": 1711152000000
	* }
	*
	*   Timestamp must be within 10 minutes of current time
	*   Type field must be "cht-p2p"
	*   TLS fingerprint must match server cert on connection
	*   QR regenerated each session, old codes are invalid
	*/
public final class QrCodeHelper {


	private static final int QR_SIZE = 512; // pixels
	private static final String PAYLOAD_TYPE = "cht-p2p";
	private static final int PAYLOAD_VERSION = 1;
	private static final long MAX_TIMESTAMP_DRIFT_MS = 10L * 60 * 1000; // 10 minutes

	// QR payload JSON keys
	/** SHA-256 of the host's session certificate; the peer refuses anything else. */
	private static final String KEY_FINGERPRINT = "fp";
	private static final String KEY_SSID = "ssid";
	private static final String KEY_PWD = "pwd";
	private static final String KEY_IP = "ip";
	private static final String KEY_PORT = "port";
	private static final String KEY_TS = "ts";

	private QrCodeHelper() {
		// Static utility class
	}

	/**
		* Hotspot credentials grouped for QR code generation.
		*/
	@SuppressWarnings("java:S107") // Data class — all fields are logically related
	public static final class HotspotCredentials {
		final String ssid;
		final String password;
		final String ipAddress;
		final int port;
		final String certFingerprint;

		public HotspotCredentials(String ssid, String password, String ipAddress, int port,
									String certFingerprint) {
			this.ssid = ssid;
			this.password = password;
			this.ipAddress = ipAddress;
			this.port = port;
			this.certFingerprint = certFingerprint;
		}
	}

	/**
		* Generate a QR code bitmap from hotspot credentials.
		*
		* @param creds hotspot credentials
		* @return QR code bitmap, or null if generation fails
		*/
	public static Bitmap generateQrCode(HotspotCredentials creds) {
		return generateQrCode(creds, QR_SIZE);
	}

	/**
		* Generate a QR code bitmap with custom dimensions.
		*
		* @param creds hotspot credentials
		* @param size  bitmap width and height in pixels
		* @return QR code bitmap, or null if generation fails
		*/
	public static Bitmap generateQrCode(HotspotCredentials creds, int size) {
		try {
			String payload = buildPayload(creds);
			return encodeQrBitmap(payload, size);
		} catch (WriterException | JSONException e) {
			error(e, "Failed to generate QR code");
			return null;
		}
	}


	/**
		* Build the QR payload JSON string from hotspot credentials.
		*
		* @param creds hotspot credentials
		* @return JSON string matching
		* @throws JSONException if JSON construction fails
		*/
	public static String buildPayload(HotspotCredentials creds) throws JSONException {
		return buildPayload(creds.ssid, creds.password, creds.ipAddress, creds.port,
				creds.certFingerprint);
	}

	/**
		* Build the QR payload JSON string.
		*
		* @param ssid		   the WiFi hotspot SSID
		* @param password	   the WiFi hotspot WPA2 password
		* @param ipAddress	  the supervisor device IP on the hotspot network
		* @param port		   the HTTPS port for the P2P HTTP server
		* @return JSON string matching
		* @throws JSONException if JSON construction fails
		*/
	public static String buildPayload(String ssid, String password, String ipAddress, int port,
										String certFingerprint) throws JSONException {
		validatePayloadParams(ssid, password, ipAddress, port);
		if (certFingerprint == null || certFingerprint.trim().isEmpty()) {
			// a payload without one would pair over a connection nothing can verify
			throw new JSONException("certFingerprint is required");
		}

		JSONObject payload = new JSONObject();
		payload.put("type", PAYLOAD_TYPE);
		payload.put("v", PAYLOAD_VERSION);
		payload.put(KEY_SSID, ssid);
		payload.put(KEY_PWD, password);
		payload.put(KEY_IP, ipAddress);
		payload.put(KEY_FINGERPRINT, certFingerprint);
		payload.put(KEY_PORT, port);
		payload.put(KEY_TS, System.currentTimeMillis());
		return payload.toString();
	}

	private static void validatePayloadParams(String ssid, String password,
												String ipAddress, int port) {
		requireNonEmpty(ssid, "ssid");
		requireNonEmpty(password, "password");
		requireNonEmpty(ipAddress, "ipAddress");
		if (port <= 0 || port > 65535) {
			throw new IllegalArgumentException("port must be between 1 and 65535, got: " + port);
		}
	}

	private static void requireNonEmpty(String value, String name) {
		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException(name + " must not be null or empty");
		}
	}

	/**
		* Validate a scanned QR payload.
		*
		* Checks:
		*   Type must be "cht-p2p"
		*   Timestamp within 10 minutes of current time
		*   Required fields: ssid, pwd, ip, port
		*
		* @param payloadJson the scanned QR code content
		* @return QrValidation.accept() if valid,
		*         QrValidation.reject(reason) if invalid
		*/
	public static QrValidation validateQrPayload(String payloadJson) {
		if (payloadJson == null || payloadJson.isEmpty()) {
			return QrValidation.reject("empty_payload");
		}

		JSONObject payload;
		try {
			payload = new JSONObject(payloadJson);
		} catch (JSONException e) {
			return QrValidation.reject("invalid_json: " + e.getMessage());
		}

		QrValidation headerCheck = checkQrTypeAndVersion(payload);
		if (headerCheck != null) {
			return headerCheck;
		}

		QrValidation tsCheck = checkQrTimestamp(payload);
		if (tsCheck != null) {
			return tsCheck;
		}

		return validateRequiredFields(payload);
	}

	private static QrValidation checkQrTypeAndVersion(JSONObject payload) {
		String type = payload.optString("type", "");
		if (!PAYLOAD_TYPE.equals(type)) {
			return QrValidation.reject("invalid type '" + type +
					"', expected '" + PAYLOAD_TYPE + "'");
		}
		int version = payload.optInt("v", -1);
		if (version != PAYLOAD_VERSION) {
			return QrValidation.reject("unsupported version: " + version +
					", expected " + PAYLOAD_VERSION);
		}
		return null;
	}

	private static QrValidation checkQrTimestamp(JSONObject payload) {
		long timestamp = payload.optLong(KEY_TS, 0);
		if (timestamp == 0) {
			return QrValidation.reject("missing timestamp");
		}
		long drift = Math.abs(System.currentTimeMillis() - timestamp);
		if (drift > MAX_TIMESTAMP_DRIFT_MS) {
			return QrValidation.reject("QR code expired. Timestamp drift: " +
					(drift / 1000) + "s (max " + (MAX_TIMESTAMP_DRIFT_MS / 1000) + "s)");
		}
		return null;
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
					json.getString(KEY_SSID),
					json.getString(KEY_PWD),
					json.getString(KEY_IP),
					json.getInt(KEY_PORT),
					json.getString(KEY_FINGERPRINT),
					json.getLong(KEY_TS)
			);
		} catch (JSONException e) {
			error(e, "Failed to parse QR payload");
			return null;
		}
	}

	// --- Private helpers ---

	/**
		* Validate required fields in the QR payload.
		*
		* The fingerprint counts as required: a payload without one would let a peer pair over a
		* connection it cannot verify, which is the attack the pinning exists to stop.
		*/
	private static QrValidation validateRequiredFields(JSONObject payload) {
		if (isEmptyField(payload, "ssid")) {
			return QrValidation.reject("missing required field: ssid");
		}
		if (isEmptyField(payload, "pwd")) {
			return QrValidation.reject("missing required field: pwd");
		}
		if (isEmptyField(payload, "ip")) {
			return QrValidation.reject("missing required field: ip");
		}
		if (isEmptyField(payload, KEY_FINGERPRINT)) {
			return QrValidation.reject("missing required field: fp");
		}
		int port = payload.optInt(KEY_PORT, 0);
		if (port <= 0 || port > 65535) {
			return QrValidation.reject("invalid port: " + port);
		}
		return QrValidation.accept();
	}

	private static boolean isEmptyField(JSONObject obj, String key) {
		return obj.optString(key, "").isEmpty();
	}

	/**
		* Encode a string into a QR code Bitmap using ZXing.
		*
		* @param content the string content to encode
		* @param size	bitmap width and height in pixels
		* @return the QR code as a Bitmap
		* @throws WriterException if ZXing encoding fails
		*/
	private static Bitmap encodeQrBitmap(String content, int size) throws WriterException {
		Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
		hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
		hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
		hints.put(EncodeHintType.MARGIN, 2);

		QRCodeWriter writer = new QRCodeWriter();
		BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

		return renderBitmapPixels(bitMatrix);
	}

	private static Bitmap renderBitmapPixels(BitMatrix bitMatrix) {
		int width = bitMatrix.getWidth();
		int height = bitMatrix.getHeight();
		int[] pixels = fillPixelArray(bitMatrix, width, height);

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
		return bitmap;
	}

	private static int[] fillPixelArray(BitMatrix bitMatrix, int width, int height) {
		int[] pixels = new int[width * height];
		for (int y = 0; y < height; y++) {
			fillRow(bitMatrix, pixels, y, width);
		}
		return pixels;
	}

	private static void fillRow(BitMatrix bitMatrix, int[] pixels, int y, int width) {
		int offset = y * width;
		for (int x = 0; x < width; x++) {
			pixels[offset + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
		}
	}

	/**
		* Parsed QR payload data class.
		* Holds the extracted fields from a validated QR code.
		*/
	@SuppressWarnings("java:S107") // Data class — all fields are logically related
	public static final class QrPayload {
		private final String ssid;
		private final String password;
		private final String ipAddress;
		private final int port;
		private final String certFingerprint;
		private final long timestamp;

		QrPayload(String ssid, String password, String ipAddress,
					int port, String certFingerprint, long timestamp) {
			this.ssid = ssid;
			this.password = password;
			this.ipAddress = ipAddress;
			this.port = port;
			this.certFingerprint = certFingerprint;
			this.timestamp = timestamp;
		}

		/** The certificate the host must present. Anything else means it is not the host. */
		public String getCertFingerprint() {
			return certFingerprint;
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


		public long getTimestamp() {
			return timestamp;
		}

		@Override
		public String toString() {
			return "QrPayload{ssid='" + ssid + "', ip='" + ipAddress +
					"', port=" + port + ", ts=" + timestamp + '}';
		}
	}
}
