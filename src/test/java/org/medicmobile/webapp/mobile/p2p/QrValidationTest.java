package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QrValidationTest {

	@Test
	public void accept_isAcceptedAndCarriesNoReason() {
		QrValidation validation = QrValidation.accept();

		assertTrue(validation.isAccepted());
		assertNull(validation.getCode());
		assertNull(validation.getDetail());
	}

	@Test
	public void reject_isNotAcceptedAndKeepsTheReason() {
		QrValidation validation = QrValidation.reject("unreadable_payload", "missing required field: ssid");

		assertFalse(validation.isAccepted());
		assertEquals("unreadable_payload", validation.getCode());
		assertEquals("missing required field: ssid", validation.getDetail());
	}

	@Test
	public void toString_saysWhichOutcomeItIs() {
		assertTrue(QrValidation.accept().toString().contains("accepted"));
		assertTrue(QrValidation.reject("unreadable_payload", "expired").toString().contains("expired"));
	}
}
