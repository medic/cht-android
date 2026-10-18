package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class QrCodeHelperTest {

	private static final String SSID = "AndroidShare_1234";
	private static final String PASSWORD = "a-password";
	private static final String IP = "192.168.49.1";
	private static final int PORT = 8443;
	private static final String FINGERPRINT = "AB:CD:EF:01:23:45";

	private QrCodeHelper.HotspotCredentials credentials() {
		return new QrCodeHelper.HotspotCredentials(SSID, PASSWORD, IP, PORT, FINGERPRINT);
	}

	@Test public void buildPayload_carriesWhatAPeerNeedsToConnect() throws Exception {
		JSONObject payload = new JSONObject(QrCodeHelper.buildPayload(credentials()));

		assertEquals(SSID, payload.getString("ssid"));
		assertEquals(PASSWORD, payload.getString("pwd"));
		assertEquals(IP, payload.getString("ip"));
		assertEquals(PORT, payload.getInt("port"));
	}

	@Test public void validateQrPayload_acceptsAFreshPayload() throws Exception {
		QrValidation result = QrCodeHelper.validateQrPayload(QrCodeHelper.buildPayload(credentials()));

		assertTrue(result.getDetail(), result.isAccepted());
	}

	@Test public void validateQrPayload_rejectsEmptyInput() {
		assertFalse(QrCodeHelper.validateQrPayload(null).isAccepted());
		assertFalse(QrCodeHelper.validateQrPayload("").isAccepted());
	}

	@Test public void validateQrPayload_rejectsSomethingThatIsNotJson() {
		QrValidation result = QrCodeHelper.validateQrPayload("this is not a qr payload");

		assertFalse(result.isAccepted());
		assertNotNull(result.getCode());
	}

	@Test public void buildPayload_carriesTheCertificateFingerprint() throws Exception {
		JSONObject payload = new JSONObject(QrCodeHelper.buildPayload(credentials()));

		assertEquals(FINGERPRINT, payload.getString("fp"));
	}

	@Test public void buildPayload_refusesToBuildWithoutAFingerprint() {
		org.junit.Assert.assertThrows(org.json.JSONException.class,
				() -> QrCodeHelper.buildPayload(
						new QrCodeHelper.HotspotCredentials(SSID, PASSWORD, IP, PORT, "")));
		org.junit.Assert.assertThrows(org.json.JSONException.class,
				() -> QrCodeHelper.buildPayload(
						new QrCodeHelper.HotspotCredentials(SSID, PASSWORD, IP, PORT, null)));
	}

	@Test public void validateQrPayload_rejectsAPayloadWithoutAFingerprint() throws Exception {
		JSONObject payload = new JSONObject(QrCodeHelper.buildPayload(credentials()));
		payload.remove("fp");

		QrValidation result = QrCodeHelper.validateQrPayload(payload.toString());

		assertFalse(result.isAccepted());
		assertTrue(result.getDetail(), result.getDetail().contains("fp"));
	}

	@Test public void parsePayload_exposesTheFingerprintToPinAgainst() throws Exception {
		QrCodeHelper.QrPayload parsed = QrCodeHelper.parsePayload(QrCodeHelper.buildPayload(credentials()));

		assertEquals(FINGERPRINT, parsed.getCertFingerprint());
	}

	@Test public void validateQrPayload_rejectsAPayloadMissingTheSsid() throws Exception {
		JSONObject payload = new JSONObject(QrCodeHelper.buildPayload(credentials()));
		payload.remove("ssid");

		QrValidation result = QrCodeHelper.validateQrPayload(payload.toString());

		assertFalse(result.isAccepted());
		assertTrue(result.getDetail(), result.getDetail().contains("ssid"));
	}

	@Test public void buildPayload_carriesNothingTiedToTheClock() throws Exception {
		JSONObject payload = new JSONObject(QrCodeHelper.buildPayload(credentials()));

		assertFalse(payload.has("ts"));
		assertTrue(QrCodeHelper.validateQrPayload(payload.toString()).isAccepted());
	}

	@Test public void parsePayload_roundTripsWhatBuildPayloadProduced() throws Exception {
		QrCodeHelper.QrPayload parsed = QrCodeHelper.parsePayload(QrCodeHelper.buildPayload(credentials()));

		assertNotNull(parsed);
		assertEquals(SSID, parsed.getSsid());
	}

	@Test public void everyRejectionCarriesAStableCode() throws Exception {
		String[] badPayloads = {
			"",
			"not json at all",
			new JSONObject().put("type", "wrong").toString(),
			new JSONObject(QrCodeHelper.buildPayload(credentials())).put("v", 99).toString(),
			new JSONObject(QrCodeHelper.buildPayload(credentials())).put("port", 0).toString(),
			new JSONObject(QrCodeHelper.buildPayload(credentials())).put("ssid", "").toString(),
		};

		for (String payload : badPayloads) {
			QrValidation result = QrCodeHelper.validateQrPayload(payload);
			assertFalse("expected a rejection for: " + payload, result.isAccepted());
			assertTrue(
					"not a stable code: " + result.getCode(),
					result.getCode().matches("[a-z0-9_]+"));
		}
	}
}
