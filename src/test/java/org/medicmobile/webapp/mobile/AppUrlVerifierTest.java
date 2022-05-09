package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_apiNotReady;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_appNotFound;
import static org.medicmobile.webapp.mobile.R.string.errAppUrl_serverNotFound;
import static org.medicmobile.webapp.mobile.R.string.errInvalidUrl;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.AppUrlVerifier.AppUrlVerification;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.MalformedURLException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppUrlVerifierTest {
	@Rule
	public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

	@Mock
	private SimpleJsonClient2 mockJsonClient;

	private void mockJsonClientGet(JSONObject jsonResponse) {
		try {
			when(mockJsonClient.get((String) any())).thenReturn(jsonResponse);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void mockJsonClientGetException(Exception e) {
		try {
			when(mockJsonClient.get((String) any())).thenThrow(e);
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	private void mockJsonClientGetOk() {
		try {
			mockJsonClientGet(new JSONObject("{\"ready\":true,\"handler\":\"medic-api\"}"));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testCleanValidUrl() {
		mockJsonClientGetOk();
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "https://example.com/uri").call();
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testLeadingSpacesUrl() {
		mockJsonClientGetOk();
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "  https://example.com/uri").call();
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testTrailingSpacesUrl() {
		mockJsonClientGetOk();
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "https://example.com/uri ").call();
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testTrailingBarsUrl() {
		mockJsonClientGetOk();
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "https://example.com/uri/").call();
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testOnlyLastTrailingBarIsCleaned() {
		mockJsonClientGetOk();
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "https://example.com/uri/to/here/").call();
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri/to/here", verification.appUrl);
	}

	@Test
	public void testTrailingBarsAndSpacesUrl() {
		mockJsonClientGetOk();
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "https://example.com/uri/ ").call();
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri", verification.appUrl);
	}

	@Test
	public void testAllMistakesUrl() {
		mockJsonClientGetOk();
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, " https://example.com/uri/res/ ").call();
		assertTrue(verification.isOk);
		assertEquals("https://example.com/uri/res", verification.appUrl);
	}

	@Test
	public void testNullUrl() {
		try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
			utilsMock.when(Utils::isDebug).thenReturn(true);

			assertThrows(
				"AppUrlVerifier :: Cannot verify APP URL because it is not defined.",
				RuntimeException.class,
				() -> new AppUrlVerifier(mockJsonClient, null)
			);
		}
	}

	@Test
	public void testEmptyUrl() {
		try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
			utilsMock.when(Utils::isDebug).thenReturn(true);

			assertThrows(
				"AppUrlVerifier :: Cannot verify APP URL because it is not defined.",
				RuntimeException.class,
				() -> new AppUrlVerifier(mockJsonClient, "")
			);
		}
	}

	@Test
	public void testBlankUrl() {
		try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
			utilsMock.when(Utils::isDebug).thenReturn(true);

			assertThrows(
				"AppUrlVerifier :: Cannot verify APP URL because it is not defined.",
				RuntimeException.class,
				() -> new AppUrlVerifier(mockJsonClient, "   ")
			);
		}
	}

	@Test
	public void testMalformed() {
		mockJsonClientGetException(new JSONException("NOT A JSON"));
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "https://example.com/without/json").call();
		assertFalse(verification.isOk);
		assertEquals(errAppUrl_appNotFound, verification.failure);
	}

	@Test
	public void testWrongJson() throws JSONException {
		mockJsonClientGet(new JSONObject("{\"data\":\"irrelevant\"}"));
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "https://example.com/setup/poll").call();
		assertFalse(verification.isOk);
		assertEquals(errAppUrl_appNotFound, verification.failure);
	}

	@Test
	public void testApiNotReady() throws JSONException {
		mockJsonClientGet(new JSONObject("{\"ready\":false,\"handler\":\"medic-api\"}"));
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "https://example.com/setup/poll").call();
		assertFalse(verification.isOk);
		assertEquals(errAppUrl_apiNotReady, verification.failure);
	}

	@Test
	public void testInvalidUrl() throws JSONException {
		mockJsonClientGetException(new MalformedURLException("Nop"));
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "\\|NOT a URL***").call();
		assertFalse(verification.isOk);
		assertEquals(errInvalidUrl, verification.failure);
	}

	@Test
	public void testServerNotFound() {
		mockJsonClientGetException(new IOException("Ups"));
		AppUrlVerification verification = new AppUrlVerifier(mockJsonClient, "https://example.com/setup/poll").call();
		assertFalse(verification.isOk);
		assertEquals(errAppUrl_serverNotFound, verification.failure);
	}
}
