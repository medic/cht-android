package org.medicmobile.webapp.mobile;

import java.io.IOException;
import java.net.URL;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_apiNotReady;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_appNotFound;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_serverNotFound;
import static org.medicmobile.webapp.mobile.R.string.errInvalidUrl;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AppUrlVerifierTest {

	private AppUrlVerifier buildAppUrlVerifier(JSONObject jsonResponse) {
		return new AppUrlVerifier() {
			@Override protected SimpleJsonClient2 getJsonClient() {
				return new SimpleJsonClient2() {
					@Override public JSONObject get(URL url) {
						return jsonResponse;
					}
				};
			}
		};
	}

	private AppUrlVerifier buildAppUrlVerifierWithException(Exception e) {
		return new AppUrlVerifier() {
			@Override protected SimpleJsonClient2 getJsonClient() {
				return new SimpleJsonClient2() {
					@Override public JSONObject get(URL url) throws JSONException, IOException {
						try {
							throw e;
						} catch (JSONException | IOException exception) {
							throw exception;
						} catch (Exception otherException) {
							throw new RuntimeException(otherException);
						}
					}
				};
			}
		};
	}

	private AppUrlVerifier buildAppUrlVerifierOk() {
		try {
			return buildAppUrlVerifier(new JSONObject("{\"ready\":true,\"handler\":\"medic-api\"}"));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testCleanValidUrl() {
		AppUrlVerifier verifier = buildAppUrlVerifierOk();
		AppUrlVerification verification = verifier.verify("https://example.com/uri");
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testLeadingSpacesUrl() {
		AppUrlVerifier verifier = buildAppUrlVerifierOk();
		AppUrlVerification verification = verifier.verify("  https://example.com/uri");
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testTrailingSpacesUrl() {
		AppUrlVerifier verifier = buildAppUrlVerifierOk();
		AppUrlVerification verification = verifier.verify("https://example.com/uri ");
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testTrailingBarsUrl() {
		AppUrlVerifier verifier = buildAppUrlVerifierOk();
		AppUrlVerification verification = verifier.verify("https://example.com/uri/");
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testOnlyLastTrailingBarIsCleaned() {
		AppUrlVerifier verifier = buildAppUrlVerifierOk();
		AppUrlVerification verification = verifier.verify("https://example.com/uri/to/here/");
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri/to/here", verification.appUrl);
	}

	@Test
	public void testTrailingBarsAndSpacesUrl() {
		AppUrlVerifier verifier = buildAppUrlVerifierOk();
		AppUrlVerification verification = verifier.verify("https://example.com/uri/ ");
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testAllMistakesUrl() {
		AppUrlVerifier verifier = buildAppUrlVerifierOk();
		AppUrlVerification verification = verifier.verify(" https://example.com/uri/res/ ");
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri/res", verification.appUrl);
	}

	@Test
	public void testUrlMalformed() {
		AppUrlVerifier verifier = buildAppUrlVerifierOk();
		AppUrlVerification verification = verifier.verify(" https://example.com/uri/res/ ");
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri/res", verification.appUrl);
	}

	@Test
	public void testMalformed() {
		AppUrlVerifier verifier = buildAppUrlVerifierWithException(new JSONException("NOT A JSON"));
		AppUrlVerification verification = verifier.verify("https://example.com/without/json");
		assertFalse(verification.isOk);
		assertEquals(errAppUrl_appNotFound, verification.failure);
	}

	@Test
	public void testWrongJson() throws JSONException {
		AppUrlVerifier verifier = buildAppUrlVerifier(new JSONObject("{\"data\":\"irrelevant\"}"));
		AppUrlVerification verification = verifier.verify("https://example.com/setup/poll");
		assertFalse(verification.isOk);
		assertEquals(errAppUrl_appNotFound, verification.failure);
	}

	@Test
	public void testApiNotReady() throws JSONException {
		AppUrlVerifier verifier = buildAppUrlVerifier(new JSONObject("{\"ready\":false,\"handler\":\"medic-api\"}"));
		AppUrlVerification verification = verifier.verify("https://example.com/setup/poll");
		assertFalse(verification.isOk);
		assertEquals(errAppUrl_apiNotReady, verification.failure);
	}

	@Test
	public void testInvalidUrl() throws JSONException {
		AppUrlVerifier verifier = buildAppUrlVerifier(new JSONObject("{\"ready\":true,\"handler\":\"medic-api\"}"));
		AppUrlVerification verification = verifier.verify("\\|NOT a URL***");
		assertFalse(verification.isOk);
		assertEquals(errInvalidUrl, verification.failure);
	}

	@Test
	public void testServerNotFound() {
		AppUrlVerifier verifier = buildAppUrlVerifierWithException(new IOException("Ups"));
		AppUrlVerification verification = verifier.verify("https://example.com/setup/poll");
		assertFalse(verification.isOk);
		assertEquals(errAppUrl_serverNotFound, verification.failure);
	}
}
