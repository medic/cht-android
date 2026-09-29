package org.medicmobile.webapp.mobile.offlinesync;

import static org.medicmobile.webapp.mobile.MedicLog.error;

import android.graphics.Bitmap;
import android.util.Base64;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Map;

/**
	* Generates and validates QR codes for offline sync WiFi hotspot credential exchange.
	*
	* QR Payload:
	* {
	*   "type": "cht-offline-sync",
	*   "v": 1,
	*   "ssid": "CHT-OFFLINE-SYNC-a3f7",
	*   "pwd": "randomPassword123",
	*   "ip": "192.168.43.1",
	*   "port": 8443,
	*   "ts": 1711152000000
	* }
	*
	*   Timestamp must be within 10 minutes of current time
	*   Type field must be "cht-offline-sync"
	*   TLS fingerprint must match server cert on connection
	*   QR regenerated each session, old codes are invalid
	*/
public final class QrCodeHelper {


	private static final int QR_SIZE = 512; // pixels
	private static final String PAYLOAD_TYPE = "cht-offline-sync";
	private static final int PAYLOAD_VERSION = 1;
	private static final long MAX_TIMESTAMP_DRIFT_MS = 10L * 60 * 1000; // 10 minutes

	// QR payload JSON keys
	/** SHA-256 of the host's session certificate; the peer refuses anything else. */
	private static final String KEY_FINGERPRINT = "fp";
	private static final String KEY_SSID = "ssid";
	private static final String KEY_PWD = "pwd";
	private static final String KEY_IP = "ip";
	private static final String KEY_PORT = "port";
	private static final int MAX_PORT = 65535;
	// Every validation failure means the same thing to the user: the code cannot be used, ask
	// for another. The prose behind it goes to the log, never across the bridge.
	private static final String UNREADABLE = "unreadable_payload";
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
		* Renders the payload as a PNG data URL, for the webapp to show.
		*
		* Encoding here rather than in the webapp keeps the QR library on one side: ZXing is already
		* a dependency for scanning, so the browser needs none.
		*
		* @return a data: URL, or null if the payload could not be encoded
		*/
	public static String generateQrDataUrl(String payloadJson) {
		try {
			Bitmap bitmap = encodeQrBitmap(payloadJson, QR_SIZE);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
			bitmap.recycle();
			return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
		} catch (WriterException e) {
			error(e, "Could not render the pairing code");
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
		validatePayloadParams(creds.ssid, creds.password, creds.ipAddress, creds.port);
		if (creds.certFingerprint == null || creds.certFingerprint.trim().isEmpty()) {
			// a payload without one would pair over a connection nothing can verify
			throw new JSONException("certFingerprint is required");
		}

		JSONObject payload = new JSONObject();
		payload.put("type", PAYLOAD_TYPE);
		payload.put("v", PAYLOAD_VERSION);
		payload.put(KEY_SSID, creds.ssid);
		payload.put(KEY_PWD, creds.password);
		payload.put(KEY_IP, creds.ipAddress);
		payload.put(KEY_FINGERPRINT, creds.certFingerprint);
		payload.put(KEY_PORT, creds.port);
		payload.put(KEY_TS, System.currentTimeMillis());
		return payload.toString();
	}

	private static void validatePayloadParams(String ssid, String password,
												String ipAddress, int port) {
		requireNonEmpty(ssid, "ssid");
		requireNonEmpty(password, "password");
		requireNonEmpty(ipAddress, "ipAddress");
		if (port <= 0 || port > MAX_PORT) {
			throw new IllegalArgumentException("port must be between 1 and " + MAX_PORT + ", got: " + port);
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
		*   Type must be "cht-offline-sync"
		*   Timestamp within 10 minutes of current time
		*   Required fields: ssid, pwd, ip, port
		*
		* @param payloadJson the scanned QR code content
		* @return QrValidation.accept() if valid,
		*         QrValidation.reject(reason) if invalid
		*/
	public static QrValidation validateQrPayload(String payloadJson) {
		if (payloadJson == null || payloadJson.isEmpty()) {
			return QrValidation.reject(UNREADABLE, "empty payload");
		}

		JSONObject payload;
		try {
			payload = new JSONObject(payloadJson);
		} catch (JSONException e) {
			return QrValidation.reject(UNREADABLE, "invalid json: " + e.getMessage());
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
			return QrValidation.reject(UNREADABLE, "invalid type '" + type +
					"', expected '" + PAYLOAD_TYPE + "'");
		}
		int version = payload.optInt("v", -1);
		if (version != PAYLOAD_VERSION) {
			return QrValidation.reject(UNREADABLE, "unsupported version: " + version +
					", expected " + PAYLOAD_VERSION);
		}
		return null;
	}

	private static QrValidation checkQrTimestamp(JSONObject payload) {
		long timestamp = payload.optLong(KEY_TS, 0);
		if (timestamp == 0) {
			return QrValidation.reject(UNREADABLE, "missing timestamp");
		}
		long drift = Math.abs(System.currentTimeMillis() - timestamp);
		if (drift > MAX_TIMESTAMP_DRIFT_MS) {
			return QrValidation.reject(UNREADABLE, "code expired, timestamp drift: " +
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
	private static final String[] REQUIRED_FIELDS = { KEY_SSID, KEY_PWD, KEY_IP, KEY_FINGERPRINT };

	private static QrValidation validateRequiredFields(JSONObject payload) {
		for (String field : REQUIRED_FIELDS) {
			if (isEmptyField(payload, field)) {
				return QrValidation.reject(UNREADABLE, "missing required field: " + field);
			}
		}
		int port = payload.optInt(KEY_PORT, 0);
		if (port <= 0 || port > MAX_PORT) {
			return QrValidation.reject(UNREADABLE, "invalid port: " + port);
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
