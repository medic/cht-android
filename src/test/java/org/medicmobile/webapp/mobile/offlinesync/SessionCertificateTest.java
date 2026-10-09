package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SessionCertificateTest {

	@Test public void sanitise_keepsAnOrdinaryModelName() {
		assertEquals("SM-A125F", SessionCertificate.sanitise("SM-A125F"));
	}

	@Test public void sanitise_dropsCharactersThatWouldBreakTheSubject() {
		String cleaned = SessionCertificate.sanitise("Pixel 6a, CN=evil");

		assertEquals("Pixel 6a CNevil", cleaned);
		assertFalse(cleaned.contains(","));
		assertFalse(cleaned.contains("="));
	}

	@Test public void sanitise_fallsBackWhenNothingPrintableIsLeft() {
		assertEquals("CHT device", SessionCertificate.sanitise("नेपाल"));
		assertEquals("CHT device", SessionCertificate.sanitise("???"));
		assertEquals("CHT device", SessionCertificate.sanitise("   "));
		assertEquals("CHT device", SessionCertificate.sanitise(null));
	}
}
