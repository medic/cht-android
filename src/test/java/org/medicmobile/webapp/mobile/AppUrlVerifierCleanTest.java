package org.medicmobile.webapp.mobile;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AppUrlVerifierCleanTest {

	private AppUrlVerifier verifier;

	@Before
	public void setup() {
		verifier = new AppUrlVerifier();
	}

	@Test
	public void testCleanValidUrl() {
		assertEquals("https://example.com/uri", verifier.clean("https://example.com/uri"));
	}

	@Test
	public void testLeadingSpacesUrl() {
		assertEquals("https://example.com/uri", verifier.clean("  https://example.com/uri"));
	}

	@Test
	public void testTrailingSpacesUrl() {
		assertEquals("https://example.com/uri", verifier.clean("https://example.com/uri "));
	}

	@Test
	public void testTrailingBarsUrl() {
		assertEquals("https://example.com/uri", verifier.clean("https://example.com/uri/"));
	}

	@Test
	public void testOnlyLastTrailingBarIsCleaned() {
		assertEquals("https://example.com/uri/to/here", verifier.clean("https://example.com/uri/to/here/"));
	}

	@Test
	public void testTrailingBarsAndSpacesUrl() {
		assertEquals("https://example.com/uri", verifier.clean("https://example.com/uri/ "));
	}

	@Test
	public void testAllMistakesUrl() {
		assertEquals("https://example.com/uri/res", verifier.clean(" https://example.com/uri/res// "));
	}
}
